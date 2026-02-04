package Api_Assets.service;

import Api_Assets.dto.AssetRecommendation;
import Api_Assets.entity.UserAsset;
import Api_Assets.repository.UserAssetRepository;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    @Getter
    private final UserAssetRepository userAssetRepository;

    private final StockService stockService;
    private final CryptoService cryptoService;

    private final List<String> TOP_MARKET_STOCKS = Arrays.asList(
            "MSFT", "NVDA", "AAPL", "GOOGL", "AMZN", "META", "TSLA", "AVGO",
            "LLY", "JPM", "V", "WMT", "UNH", "MA", "PG"
    );

    private final Map<String, BigDecimal> MARKET_PERFORMANCE = Map.of(
            "NVDA", new BigDecimal("45.2"),
            "MSFT", new BigDecimal("18.7"),
            "TSLA", new BigDecimal("-12.3"),
            "AAPL", new BigDecimal("23.4"),
            "GOOGL", new BigDecimal("15.8"),
            "AMZN", new BigDecimal("12.1"),
            "META", new BigDecimal("28.4"),
            "AVGO", new BigDecimal("35.6")
    );

    private final Map<String, String> SYMBOL_MAP = Map.of(
            "bitcoin", "BTC", "btc-usd", "BTC", "BTCUSD", "BTC",
            "ethereum", "ETH", "eth-usd", "ETH", "ETHUSD", "ETH",
            "solana", "SOL", "sol", "SOL"
    );

    public RecommendationService(UserAssetRepository userAssetRepository,
                                 StockService stockService,
                                 CryptoService cryptoService) {
        this.userAssetRepository = userAssetRepository;
        this.stockService = stockService;
        this.cryptoService = cryptoService;
    }

    private String normalizeSymbol(String symbol) {
        return SYMBOL_MAP.getOrDefault(symbol.toLowerCase(), symbol.toUpperCase());
    }

    private List<AssetRecommendation> deduplicateRecommendations(List<AssetRecommendation> recs) {
        return recs.stream()
                .collect(Collectors.toMap(
                        rec -> normalizeSymbol(rec.getSymbol()),
                        rec -> rec,
                        (existing, replacement) -> existing
                ))
                .values()
                .stream()
                .collect(Collectors.toList());
    }

    private BigDecimal safeQty(Integer qty) {
        return BigDecimal.valueOf(qty == null ? 0 : qty);
    }

    private BigDecimal safePrice(BigDecimal price) {
        return price == null ? BigDecimal.ZERO : price;
    }

    private BigDecimal getCurrentPrice(String symbol, String type) {
        try {
            if ("STOCK".equalsIgnoreCase(type)) {
                return stockService.getCurrentPrice(symbol);
            } else if ("CRYPTO".equalsIgnoreCase(type)) {
                return cryptoService.getCryptoPrice(symbol.toLowerCase());
            } else {
                throw new IllegalArgumentException("Unknown asset type for " + symbol + ": " + type);
            }
        } catch (Exception e) {
            System.err.println("Price fetch failed for " + symbol + ": " + e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private AssetRecommendation calculateRecommendation(Map.Entry<String, List<UserAsset>> entry) {
        String symbol = normalizeSymbol(entry.getKey());
        List<UserAsset> assets = entry.getValue();

        int totalQtyInt = assets.stream()
                .mapToInt(a -> a.getQty() == null ? 0 : a.getQty())
                .sum();
        if (totalQtyInt == 0) {
            return null;
        }

        BigDecimal totalQty = BigDecimal.valueOf(totalQtyInt);

        BigDecimal weightedSum = assets.stream()
                .map(a -> safePrice(a.getBuyPrice()).multiply(safeQty(a.getQty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (weightedSum.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        BigDecimal avgBuyPrice = weightedSum
                .divide(totalQty, 4, RoundingMode.HALF_UP);

        String assetType = assets.get(0).getAssetType();
        BigDecimal currentPrice = getCurrentPrice(symbol, assetType);

        if (avgBuyPrice.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        BigDecimal profitPercent = currentPrice.subtract(avgBuyPrice)
                .divide(avgBuyPrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        String riskLevel = calculateRisk(profitPercent);

        return new AssetRecommendation(symbol, riskLevel, profitPercent);
    }

    private String calculateRisk(BigDecimal profitPercent) {
        if (profitPercent == null) return "UNKNOWN";
        int cmp = profitPercent.compareTo(BigDecimal.ZERO);
        if (cmp >= 20) return "HIGH";
        if (cmp >= 5) return "MEDIUM";
        if (cmp >= 0) return "LOW";
        if (cmp <= -20) return "HIGH";
        if (cmp <= -5) return "MEDIUM";
        return "LOW";
    }

    public List<AssetRecommendation> getTopNStocks(int n) {
        List<UserAsset> stocks = userAssetRepository.findAllStocks();
        if (stocks.isEmpty()) return List.of();

        Map<String, List<UserAsset>> grouped =
                stocks.stream().collect(Collectors.groupingBy(UserAsset::getSymbol));

        List<AssetRecommendation> recs = new ArrayList<>();
        for (Map.Entry<String, List<UserAsset>> entry : grouped.entrySet()) {
            AssetRecommendation rec = calculateRecommendation(entry);
            if (rec != null) recs.add(rec);
        }

        recs = deduplicateRecommendations(recs);

        return recs.stream()
                .sorted(Comparator.comparing(AssetRecommendation::getProfitPercent).reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    public List<AssetRecommendation> getTopNCrypto(int n) {
        List<UserAsset> crypto = userAssetRepository.findAllCrypto();
        if (crypto.isEmpty()) return List.of();

        Map<String, List<UserAsset>> grouped =
                crypto.stream().collect(Collectors.groupingBy(UserAsset::getSymbol));

        List<AssetRecommendation> recs = new ArrayList<>();
        for (Map.Entry<String, List<UserAsset>> entry : grouped.entrySet()) {
            AssetRecommendation rec = calculateRecommendation(entry);
            if (rec != null) recs.add(rec);
        }

        recs = deduplicateRecommendations(recs);

        return recs.stream()
                .sorted(Comparator.comparing(AssetRecommendation::getProfitPercent).reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    public List<AssetRecommendation> getTopNAssets(int n) {
        List<UserAsset> allAssets = userAssetRepository.findAll();
        if (allAssets.isEmpty()) return List.of();

        Map<String, List<UserAsset>> grouped =
                allAssets.stream().collect(Collectors.groupingBy(UserAsset::getSymbol));

        List<AssetRecommendation> recs = new ArrayList<>();
        for (Map.Entry<String, List<UserAsset>> entry : grouped.entrySet()) {
            AssetRecommendation rec = calculateRecommendation(entry);
            if (rec != null) recs.add(rec);
        }

        recs = deduplicateRecommendations(recs);

        return recs.stream()
                .sorted(Comparator.comparing(AssetRecommendation::getProfitPercent).reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    public List<UserAsset> getAllStocks() {
        return userAssetRepository.findAllStocks();
    }
}
