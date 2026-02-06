package Api_Assets.service;

import Api_Assets.dto.StockQuote;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class StockService {

    @Value("${stockdata.api.key:}")
    private String apiKey;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BigDecimal getCurrentPrice(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return getFallbackPrice("?");
        }
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("StockData.org: No API key configured");
            return getFallbackPrice(symbol);
        }

        try {
            String encodedSymbol = URLEncoder.encode(symbol.trim().toUpperCase(), StandardCharsets.UTF_8);
            String url = "https://api.stockdata.org/v1/data/quote?symbols=" + encodedSymbol + "&api_token=" + apiKey;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .header("Accept", "application/json")
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            if (response.statusCode() == 200) {
                BigDecimal price = parsePriceFromJson(body, symbol);
                if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
                    return price;
                }
            } else {
                System.err.println("StockData.org HTTP " + response.statusCode() + " for " + symbol + ": " + (body != null && body.length() < 200 ? body : ""));
            }
        } catch (Exception e) {
            System.err.println("StockData.org failed for " + symbol + ": " + e.getMessage());
        }

        return getFallbackPrice(symbol);
    }

    /** Fetches full quote (price + day_change %) for recommendation ranking. Returns null on failure. */
    public StockQuote getStockQuote(String symbol) {
        if (symbol == null || symbol.isBlank() || apiKey == null || apiKey.isBlank()) {
            return null;
        }
        try {
            String encodedSymbol = URLEncoder.encode(symbol.trim().toUpperCase(), StandardCharsets.UTF_8);
            String url = "https://api.stockdata.org/v1/data/quote?symbols=" + encodedSymbol + "&api_token=" + apiKey;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .header("Accept", "application/json")
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return parseQuoteFromJson(response.body(), symbol);
            }
        } catch (Exception e) {
            System.err.println("StockData quote failed for " + symbol + ": " + e.getMessage());
        }
        return null;
    }

    private StockQuote parseQuoteFromJson(String json, String symbol) {
        if (json == null || json.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.has("error")) return null;
            JsonNode data = root.path("data");
            if (data.isArray() && data.size() > 0) {
                JsonNode first = data.get(0);
                BigDecimal price = null;
                if (first.has("price") && !first.get("price").isNull()) {
                    price = first.get("price").decimalValue();
                } else if (first.has("previous_close_price") && !first.get("previous_close_price").isNull()) {
                    price = first.get("previous_close_price").decimalValue();
                }
                if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) return null;

                BigDecimal dayChange = BigDecimal.ZERO;
                if (first.has("day_change") && !first.get("day_change").isNull()) {
                    dayChange = first.get("day_change").decimalValue();
                }
                return new StockQuote(symbol.toUpperCase(), price, dayChange);
            }
        } catch (Exception e) {
            System.err.println("StockData quote parse error for " + symbol + ": " + e.getMessage());
        }
        return null;
    }

    /** Parse price from StockData.org quote API: { "meta": {...}, "data": [ { "ticker": "AAPL", "price": 176.29 } ] } */
    private BigDecimal parsePriceFromJson(String json, String symbol) {
        if (json == null || json.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(json);
            // Check for API error response
            if (root.has("error")) {
                JsonNode err = root.get("error");
                String msg = err.has("message") ? err.get("message").asText() : err.toString();
                System.err.println("StockData.org API error for " + symbol + ": " + msg);
                return null;
            }
            JsonNode data = root.path("data");
            if (data.isArray() && data.size() > 0) {
                JsonNode first = data.get(0);
                if (first.has("price") && !first.get("price").isNull()) {
                    return first.get("price").decimalValue();
                }
                // Fallback: try close or previous_close_price if price missing
                if (first.has("previous_close_price") && !first.get("previous_close_price").isNull()) {
                    return first.get("previous_close_price").decimalValue();
                }
            }
        } catch (Exception e) {
            System.err.println("StockData JSON parse error for " + symbol + ": " + e.getMessage());
        }
        return null;
    }

    private BigDecimal getFallbackPrice(String symbol) {
        return switch (symbol.toUpperCase()) {
            case "AAPL" -> new BigDecimal("235.82");
            case "MSFT" -> new BigDecimal("425.50");
            case "GOOGL" -> new BigDecimal("185.20");
            case "TSLA" -> new BigDecimal("420.15");
            default -> new BigDecimal("150.00");
        };
    }
}
