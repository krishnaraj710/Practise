package Api_Assets.service;

import Api_Assets.dto.RiskAssessment;
import Api_Assets.entity.UserAsset;
import Api_Assets.repository.UserAssetRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class AssetService {

    private static final BigDecimal HIGH_RISK_THRESHOLD = BigDecimal.valueOf(10); // percent
    private static final BigDecimal MEDIUM_RISK_THRESHOLD = BigDecimal.valueOf(3); // percent
    private static final int DIVIDE_SCALE = 8;
    private static final int PERCENT_SCALE = 2;

    private final UserAssetRepository userAssetRepository;
    private final StockService stockService;
    private final CryptoService cryptoService;

    public AssetService(UserAssetRepository userAssetRepository,
                        StockService stockService,
                        CryptoService cryptoService) {
        this.userAssetRepository = userAssetRepository;
        this.stockService = stockService;
        this.cryptoService = cryptoService;
    }

    public RiskAssessment checkSellRisk(String symbol, int quantityToSell) {
        List<UserAsset> assets = userAssetRepository.findBySymbol(symbol);

        if (assets.isEmpty()) {
            return new RiskAssessment(
                    "SELL",
                    "NO_HOLDINGS",
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    quantityToSell,
                    0,
                    "No holdings for symbol; cannot sell."
            );
        }

        int totalQty = assets.stream()
                .mapToInt(a -> a.getQty() == null ? 0 : a.getQty())
                .sum();

        if (quantityToSell <= 0) {
            return new RiskAssessment(
                    "SELL",
                    "INVALID_QUANTITY",
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    quantityToSell,
                    totalQty,
                    "Requested quantity must be > 0."
            );
        }

        if (quantityToSell > totalQty) {
            return new RiskAssessment(
                    "SELL",
                    "INSUFFICIENT_QUANTITY",
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    quantityToSell,
                    totalQty,
                    "Requested quantity exceeds available holdings."
            );
        }

        BigDecimal weightedSum = BigDecimal.ZERO;
        for (UserAsset a : assets) {
            BigDecimal buy = a.getBuyPrice() == null ? BigDecimal.ZERO : a.getBuyPrice();
            int q = a.getQty() == null ? 0 : a.getQty();
            weightedSum = weightedSum.add(buy.multiply(BigDecimal.valueOf(q)));
        }

        BigDecimal avgBuyPrice = totalQty == 0
                ? BigDecimal.ZERO
                : weightedSum.divide(BigDecimal.valueOf(totalQty), DIVIDE_SCALE, RoundingMode.HALF_UP);

        String assetType = assets.get(0).getAssetType();
        BigDecimal currentPrice = getCurrentPrice(symbol, assetType);

        BigDecimal percent;
        if (avgBuyPrice.compareTo(BigDecimal.ZERO) == 0) {
            percent = BigDecimal.ZERO.setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
        } else {
            percent = currentPrice.subtract(avgBuyPrice)
                    .divide(avgBuyPrice, DIVIDE_SCALE, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal monetaryImpact = currentPrice.subtract(avgBuyPrice)
                .multiply(BigDecimal.valueOf(quantityToSell))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal absPercent = percent.abs();
        String riskLevel;
        if (percent.compareTo(BigDecimal.ZERO) >= 0) {
            riskLevel = "LOW";
        } else if (absPercent.compareTo(HIGH_RISK_THRESHOLD) >= 0) {
            riskLevel = "HIGH";
        } else if (absPercent.compareTo(MEDIUM_RISK_THRESHOLD) >= 0) {
            riskLevel = "MEDIUM";
        } else {
            riskLevel = "LOW";
        }

        String recommendation = buildSellRecommendation(riskLevel, percent, monetaryImpact);

        return new RiskAssessment(
                "SELL",
                riskLevel,
                avgBuyPrice.setScale(4, RoundingMode.HALF_UP),
                currentPrice.setScale(4, RoundingMode.HALF_UP),
                percent,
                monetaryImpact,
                quantityToSell,
                totalQty,
                recommendation
        );
    }

    public RiskAssessment checkBuyRisk(String symbol, int quantityToBuy) {
        List<UserAsset> assets = userAssetRepository.findBySymbol(symbol);

        if (assets.isEmpty()) {
            // naive inference; adjust to your needs
            String typeGuess = symbol.toUpperCase().matches(".*(BTC|ETH|SOL|ADA|XRP).*")
                    ? "CRYPTO"
                    : "STOCK";
            BigDecimal currentPrice = getCurrentPrice(symbol, typeGuess);

            return new RiskAssessment(
                    "BUY",
                    "LOW",
                    BigDecimal.ZERO,
                    currentPrice.setScale(4, RoundingMode.HALF_UP),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    quantityToBuy,
                    0,
                    "No previous holdings; treat as low risk but consider market volatility."
            );
        }

        int totalQty = assets.stream()
                .mapToInt(a -> a.getQty() == null ? 0 : a.getQty())
                .sum();

        BigDecimal weightedSum = BigDecimal.ZERO;
        for (UserAsset a : assets) {
            BigDecimal buy = a.getBuyPrice() == null ? BigDecimal.ZERO : a.getBuyPrice();
            int q = a.getQty() == null ? 0 : a.getQty();
            weightedSum = weightedSum.add(buy.multiply(BigDecimal.valueOf(q)));
        }

        BigDecimal avgBuyPrice = totalQty == 0
                ? BigDecimal.ZERO
                : weightedSum.divide(BigDecimal.valueOf(totalQty), DIVIDE_SCALE, RoundingMode.HALF_UP);

        String assetType = assets.get(0).getAssetType();
        BigDecimal currentPrice = getCurrentPrice(symbol, assetType);

        BigDecimal percent;
        if (avgBuyPrice.compareTo(BigDecimal.ZERO) == 0) {
            percent = BigDecimal.ZERO.setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
        } else {
            percent = currentPrice.subtract(avgBuyPrice)
                    .divide(avgBuyPrice, DIVIDE_SCALE, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal monetaryImpact = currentPrice.subtract(avgBuyPrice)
                .multiply(BigDecimal.valueOf(quantityToBuy))
                .setScale(2, RoundingMode.HALF_UP);

        String riskLevel;
        if (percent.compareTo(BigDecimal.ZERO) <= 0) {
            riskLevel = "LOW";
        } else if (percent.compareTo(HIGH_RISK_THRESHOLD) >= 0) {
            riskLevel = "HIGH";
        } else if (percent.compareTo(MEDIUM_RISK_THRESHOLD) >= 0) {
            riskLevel = "MEDIUM";
        } else {
            riskLevel = "LOW";
        }

        String recommendation = buildBuyRecommendation(riskLevel, percent, monetaryImpact);

        return new RiskAssessment(
                "BUY",
                riskLevel,
                avgBuyPrice.setScale(4, RoundingMode.HALF_UP),
                currentPrice.setScale(4, RoundingMode.HALF_UP),
                percent,
                monetaryImpact,
                quantityToBuy,
                totalQty,
                recommendation
        );
    }

    private BigDecimal getCurrentPrice(String symbol, String assetType) {
        if (assetType != null && assetType.equalsIgnoreCase("CRYPTO")) {
            return cryptoService.getCryptoPrice(symbol.toLowerCase());
        } else {
            return stockService.getCurrentPrice(symbol);
        }
    }

    private String buildSellRecommendation(String riskLevel, BigDecimal percent, BigDecimal monetaryImpact) {
        switch (riskLevel) {
            case "HIGH":
                return "High risk to sell: large loss. Consider holding or selling smaller amount.";
            case "MEDIUM":
                return "Medium risk to sell: moderate loss. Evaluate tax/portfolio needs.";
            case "LOW":
                if (percent.compareTo(BigDecimal.ZERO) >= 0) {
                    return "In profit: selling is acceptable if you want to realize gains.";
                } else {
                    return "Small loss: selling may be acceptable depending on strategy.";
                }
            default:
                return "";
        }
    }

    private String buildBuyRecommendation(String riskLevel, BigDecimal percent, BigDecimal monetaryImpact) {
        return switch (riskLevel) {
            case "HIGH" ->
                    "High risk to buy: current price significantly above previous average. Consider waiting or buying partial.";
            case "MEDIUM" -> "Medium risk to buy: price moderately above average. Consider dollar-cost averaging.";
            case "LOW" -> "Low risk to buy: price at or below average.";
            default -> "";
        };
    }
}
