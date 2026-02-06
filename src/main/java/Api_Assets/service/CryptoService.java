package Api_Assets.service;

import Api_Assets.dto.MarketCryptoItem;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class CryptoService {

    private final RestTemplate restTemplate = new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofSeconds(10))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();

    public BigDecimal getCryptoPrice(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return getFallbackPrice("?");
        }

        try {
            String coinId = mapToCoinGeckoId(symbol);
            String url = "https://api.coingecko.com/api/v3/simple/price"
                    + "?ids=" + coinId
                    + "&vs_currencies=usd";

            ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode body = response.getBody();
                if (body.has(coinId)) {
                    JsonNode coinData = body.get(coinId);
                    if (coinData.has("usd") && !coinData.get("usd").isNull()) {
                        BigDecimal price = coinData.get("usd").decimalValue();
                        if (price.compareTo(BigDecimal.ZERO) > 0) {
                            return price;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("CoinGecko API error for " + symbol + ": " + e.getMessage());
        }

        return getFallbackPrice(symbol);
    }

    private String mapToCoinGeckoId(String symbol) {
        return switch (symbol.toLowerCase()) {
            case "bitcoin", "btc" -> "bitcoin";
            case "ethereum", "eth" -> "ethereum";
            case "solana", "sol" -> "solana";
            case "cardano", "ada" -> "cardano";
            case "ripple", "xrp" -> "ripple";
            case "dogecoin", "doge" -> "dogecoin";
            case "tether", "usdt" -> "tether";
            case "usd-coin", "usdc" -> "usd-coin";
            case "polygon", "matic" -> "matic-network";
            case "polkadot", "dot" -> "polkadot";
            case "chainlink", "link" -> "chainlink";
            case "avalanche", "avax" -> "avalanche-2";
            case "uniswap", "uni" -> "uniswap";
            case "litecoin", "ltc" -> "litecoin";
            default -> symbol.toLowerCase();
        };
    }

    private BigDecimal getFallbackPrice(String symbol) {
        return switch (symbol.toUpperCase()) {
            case "BTC" -> BigDecimal.valueOf(67000);
            case "ETH" -> BigDecimal.valueOf(3500);
            case "SOL" -> BigDecimal.valueOf(180);
            case "ADA" -> BigDecimal.valueOf(0.55);
            case "XRP" -> BigDecimal.valueOf(0.62);
            case "DOGE" -> BigDecimal.valueOf(0.15);
            default -> BigDecimal.valueOf(100);
        };
    }

    /** Top coins by market cap from CoinGecko (for suggestions when portfolio has fewer than N). */
    public List<MarketCryptoItem> getTopCryptoFromMarket(int limit) {
        List<MarketCryptoItem> out = new ArrayList<>();
        try {
            String url = "https://api.coingecko.com/api/v3/coins/markets"
                    + "?vs_currency=usd&order=market_cap_desc&per_page=" + Math.min(limit, 100) + "&page=1";
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) return out;
            JsonNode arr = response.getBody();
            for (int i = 0; i < arr.size() && out.size() < limit; i++) {
                JsonNode c = arr.get(i);
                String symbol = c.has("symbol") ? c.get("symbol").asText().toUpperCase() : null;
                if (symbol == null) continue;
                BigDecimal price = c.has("current_price") && !c.get("current_price").isNull()
                        ? c.get("current_price").decimalValue() : BigDecimal.ZERO;
                BigDecimal change = BigDecimal.ZERO;
                if (c.has("price_change_percentage_24h") && !c.get("price_change_percentage_24h").isNull())
                    change = c.get("price_change_percentage_24h").decimalValue();
                out.add(new MarketCryptoItem(symbol, price, change));
            }
        } catch (Exception e) {
            System.err.println("CoinGecko markets error: " + e.getMessage());
        }
        return out;
    }
}
