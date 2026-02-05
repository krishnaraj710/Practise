package Api_Assets.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Service
public class CryptoService {

    private final RestTemplate restTemplate = new RestTemplate();

    public BigDecimal getCryptoPrice(String symbol) {
        try {
            String coinId = mapToCoinGeckoId(symbol);

            String url = "https://api.coingecko.com/api/v3/simple/price"
                    + "?ids=" + coinId
                    + "&vs_currencies=usd";

            ResponseEntity<JsonNode> response =
                    restTemplate.getForEntity(url, JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful()
                    && response.getBody() != null
                    && response.getBody().has(coinId)) {

                return response.getBody()
                        .get(coinId)
                        .get("usd")
                        .decimalValue();
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
            default -> symbol.toLowerCase();
        };
    }

    private BigDecimal getFallbackPrice(String symbol) {
        return BigDecimal.valueOf(100);
    }
}
