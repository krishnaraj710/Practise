package Api_Assets.service;

import Api_Assets.dto.MarketCryptoItem;
import Api_Assets.dto.StockQuote;
import Api_Assets.dto.UserAssetRecommendation;
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

    private List<UserAssetRecommendation> deduplicateRecommendations(List<UserAssetRecommendation> recs) {
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

    private UserAssetRecommendation calculateRecommendation(Map.Entry<String, List<UserAsset>> entry) {
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

        return new UserAssetRecommendation(symbol, riskLevel, profitPercent);
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

    public List<UserAssetRecommendation> getTopNStocks(int n) {
        List<UserAsset> stocks = userAssetRepository.findAllStocks();
        if (stocks.isEmpty()) return List.of();

        Map<String, List<UserAsset>> grouped =
                stocks.stream().collect(Collectors.groupingBy(UserAsset::getSymbol));

        List<UserAssetRecommendation> recs = new ArrayList<>();
        for (Map.Entry<String, List<UserAsset>> entry : grouped.entrySet()) {
            UserAssetRecommendation rec = calculateRecommendation(entry);
            if (rec != null) recs.add(rec);
        }

        recs = deduplicateRecommendations(recs);

        return recs.stream()
                .sorted(Comparator.comparing(UserAssetRecommendation::getProfitPercent).reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    public List<UserAssetRecommendation> getTopNCrypto(int n) {
        List<UserAsset> crypto = userAssetRepository.findAllCrypto();
        if (crypto.isEmpty()) return List.of();

        Map<String, List<UserAsset>> grouped =
                crypto.stream().collect(Collectors.groupingBy(UserAsset::getSymbol));

        List<UserAssetRecommendation> recs = new ArrayList<>();
        for (Map.Entry<String, List<UserAsset>> entry : grouped.entrySet()) {
            UserAssetRecommendation rec = calculateRecommendation(entry);
            if (rec != null) recs.add(rec);
        }

        recs = deduplicateRecommendations(recs);

        return recs.stream()
                .sorted(Comparator.comparing(UserAssetRecommendation::getProfitPercent).reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    public List<UserAssetRecommendation> getTopNAssets(int n) {
        List<UserAsset> allAssets = userAssetRepository.findAll();
        if (allAssets.isEmpty()) return List.of();

        Map<String, List<UserAsset>> grouped =
                allAssets.stream().collect(Collectors.groupingBy(UserAsset::getSymbol));

        List<UserAssetRecommendation> recs = new ArrayList<>();
        for (Map.Entry<String, List<UserAsset>> entry : grouped.entrySet()) {
            UserAssetRecommendation rec = calculateRecommendation(entry);
            if (rec != null) recs.add(rec);
        }

        recs = deduplicateRecommendations(recs);

        return recs.stream()
                .sorted(Comparator.comparing(UserAssetRecommendation::getProfitPercent).reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    public List<UserAsset> getAllStocks() {
        return userAssetRepository.findAllStocks();
    }

    /** Top N stocks: from portfolio first, then fill from market using live StockData day_change. */
    public List<UserAssetRecommendation> getTopNStocksSuggestions(int n) {
        List<UserAssetRecommendation> list = new ArrayList<>(getTopNStocks(n));
        Set<String> have = list.stream().map(r -> normalizeSymbol(r.getSymbol())).collect(Collectors.toSet());

        for (String symbol : TOP_MARKET_STOCKS) {
            if (list.size() >= n) break;
            String sym = symbol.toUpperCase();
            if (have.contains(sym)) continue;
            have.add(sym);

            BigDecimal perf;
            StockQuote quote = stockService.getStockQuote(symbol);
            if (quote != null && quote.getDayChangePercent() != null) {
                perf = quote.getDayChangePercent();
            } else {
                perf = MARKET_PERFORMANCE.getOrDefault(symbol, BigDecimal.ZERO);
            }
            list.add(new UserAssetRecommendation(sym, calculateRisk(perf), perf));
        }
        list.sort(Comparator.comparing(UserAssetRecommendation::getProfitPercent, Comparator.nullsLast(Comparator.reverseOrder())));
        return list.stream().limit(n).collect(Collectors.toList());
    }

    private static final List<String> TOP_MARKET_CRYPTO = Arrays.asList("BTC", "ETH", "SOL", "BNB", "XRP", "ADA", "AVAX", "DOGE", "DOT", "LINK");

    /** Top N crypto: from portfolio first, then fill from CoinGecko, then fallback list. */
    public List<UserAssetRecommendation> getTopNCryptoSuggestions(int n) {
        List<UserAssetRecommendation> list = new ArrayList<>(getTopNCrypto(n));
        Set<String> have = list.stream().map(r -> normalizeSymbol(r.getSymbol())).collect(Collectors.toSet());

        List<MarketCryptoItem> market = cryptoService.getTopCryptoFromMarket(Math.max(n + 20, 50));
        for (MarketCryptoItem m : market) {
            if (list.size() >= n) break;
            String sym = m.getSymbol() != null ? m.getSymbol().toUpperCase() : null;
            if (sym == null || have.contains(sym)) continue;
            have.add(sym);
            BigDecimal change = m.getPriceChangePercent24h() != null ? m.getPriceChangePercent24h() : BigDecimal.ZERO;
            list.add(new UserAssetRecommendation(sym, calculateRisk(change), change));
        }

        // Fallback: if CoinGecko returns few, fill from static list
        for (String sym : TOP_MARKET_CRYPTO) {
            if (list.size() >= n) break;
            if (have.contains(sym)) continue;
            have.add(sym);
            list.add(new UserAssetRecommendation(sym, "LOW", BigDecimal.ZERO));
        }

        list.sort(Comparator.comparing(UserAssetRecommendation::getProfitPercent, Comparator.nullsLast(Comparator.reverseOrder())));
        return list.stream().limit(n).collect(Collectors.toList());
    }

    /** Top N assets: from portfolio first, then fill from market (stocks via StockService, crypto via CryptoService). */
    public List<UserAssetRecommendation> getTopNAssetsSuggestions(int n) {
        List<UserAssetRecommendation> list = new ArrayList<>(getTopNAssets(n));
        Set<String> have = list.stream().map(r -> normalizeSymbol(r.getSymbol())).collect(Collectors.toSet());
        int need = n - list.size();

        for (String symbol : TOP_MARKET_STOCKS) {
            if (need <= 0) break;
            String sym = symbol.toUpperCase();
            if (have.contains(sym)) continue;
            have.add(sym);
            BigDecimal perf;
            StockQuote quote = stockService.getStockQuote(symbol);
            if (quote != null && quote.getDayChangePercent() != null) {
                perf = quote.getDayChangePercent();
            } else {
                perf = MARKET_PERFORMANCE.getOrDefault(symbol, BigDecimal.ZERO);
            }
            list.add(new UserAssetRecommendation(sym, calculateRisk(perf), perf));
            need--;
        }
        if (need > 0) {
            for (MarketCryptoItem m : cryptoService.getTopCryptoFromMarket(need + 10)) {
                if (need <= 0) break;
                String sym = m.getSymbol() != null ? m.getSymbol().toUpperCase() : null;
                if (sym == null || have.contains(sym)) continue;
                have.add(sym);
                BigDecimal change = m.getPriceChangePercent24h() != null ? m.getPriceChangePercent24h() : BigDecimal.ZERO;
                list.add(new UserAssetRecommendation(sym, calculateRisk(change), change));
                need--;
            }
        }
        list.sort(Comparator.comparing(UserAssetRecommendation::getProfitPercent, Comparator.nullsLast(Comparator.reverseOrder())));
        return list.stream().limit(n).collect(Collectors.toList());
    }

    /** All holdings (stocks + crypto) with qty > 0 for diversification analysis. */
    public List<UserAsset> getAllHoldings() {
        List<UserAsset> all = userAssetRepository.findAll();
        return all.stream()
                .filter(a -> a.getQty() != null && a.getQty() > 0)
                .collect(Collectors.toList());
    }
}
