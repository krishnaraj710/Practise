package Api_Assets.controller;

import Api_Assets.dto.DashboardAsset;
import Api_Assets.dto.RiskAssessment;
import Api_Assets.dto.SellRequest;
import Api_Assets.entity.UserAsset;
import Api_Assets.repository.UserAssetRepository;
import Api_Assets.service.UserAssetService;
import Api_Assets.service.CryptoService;
import Api_Assets.service.StockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/assets")
public class UserAssetController {

    @Autowired private UserAssetRepository repo;
    @Autowired private StockService stockService;
    @Autowired private CryptoService cryptoService;
    @Autowired private UserAssetService userAssetService;

    private static final int PERCENT_SCALE = 2;
    private static final int DIVIDE_SCALE = 8;

    @PostMapping
    public UserAsset addAsset(@RequestBody UserAsset asset) {
        // Set timestamps
        asset.setCurrentUpdated(LocalDateTime.now());
        asset.setLastUpdated(LocalDateTime.now());

        BigDecimal livePrice = getLivePrice(asset.getAssetType(), asset.getSymbol());
        asset.setCurrentPrice(livePrice);

        return repo.save(asset);
    }

    @PostMapping("/sell/{id}")
    public String sellAssetById(@PathVariable Long id) {
        UserAsset asset = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Asset not found"));

        BigDecimal currentPrice = getLivePrice(asset.getAssetType(), asset.getSymbol());
        ProfitLossResult result = calculateProfitLoss(asset.getBuyPrice(), currentPrice, asset.getQty());

        // Mark as sold
        asset.setSellingPrice(currentPrice);
        asset.setSellingDate(LocalDateTime.now());
        asset.setQty(0);
        asset.setLastUpdated(LocalDateTime.now());

        repo.save(asset);

        return String.format("Sold %s with %s of %.1f%%",
                asset.getSymbol(), result.status(), result.percent());
    }

    @PostMapping("/sell")
    public String sellAsset(@RequestBody SellRequest request) {
        List<UserAsset> assets = repo.findBySymbol(request.getSymbol());
        if (assets.isEmpty()) {
            throw new RuntimeException("Asset not found: " + request.getSymbol());
        }

        BigDecimal currentPrice = getLivePrice(assets.get(0).getAssetType(), request.getSymbol());
        BigDecimal totalQtyToSell = BigDecimal.valueOf(request.getQuantityToSell());
        BigDecimal soldQty = BigDecimal.ZERO;

        for (UserAsset asset : assets) {
            if (soldQty.compareTo(totalQtyToSell) >= 0) break;

            BigDecimal remainingQty = BigDecimal.valueOf(asset.getQty());
            BigDecimal qtyToSell = totalQtyToSell.subtract(soldQty).min(remainingQty);

            asset.setQty(asset.getQty() - qtyToSell.intValue());
            asset.setSellingPrice(currentPrice);
            asset.setSellingDate(LocalDateTime.now());
            asset.setLastUpdated(LocalDateTime.now());

            if (asset.getQty() <= 0) {
                repo.delete(asset);
            } else {
                repo.save(asset);
            }

            soldQty = soldQty.add(qtyToSell);
        }

        return String.format("Sold %d units of %s", soldQty.intValue(), request.getSymbol());
    }

    // ASSET QUERIES
    @GetMapping
    public List<UserAsset> getAllAssets() {
        return repo.findAll();
    }

    @GetMapping("/stocks")
    public List<UserAsset> getAllStocks() {
        return repo.findAllStocks();
    }

    @GetMapping("/crypto")
    public List<UserAsset> getAllCrypto() {
        return repo.findAllCrypto();
    }

    @GetMapping("/stock/{symbol}")
    public List<UserAsset> getStocksBySymbol(@PathVariable String symbol) {
        return repo.findBySymbol(symbol).stream()
                .filter(a -> "STOCK".equalsIgnoreCase(a.getAssetType()))
                .collect(Collectors.toList());
    }

    @GetMapping("/crypto/{symbol}")
    public List<UserAsset> getCryptoBySymbol(@PathVariable String symbol) {
        return repo.findBySymbol(symbol).stream()
                .filter(a -> "CRYPTO".equalsIgnoreCase(a.getAssetType()))
                .collect(Collectors.toList());
    }
    @GetMapping("/dashboard")
    public List<DashboardAsset> getDashboard() {
        return repo.findAll().stream()
                .map(this::buildDashboardAsset)
                .collect(Collectors.toList());
    }

    @GetMapping("/profit-loss")
    public List<DashboardAsset> getProfitLossReport() {
        return repo.findAll().stream()
                .map(this::buildProfitLossAsset)
                .collect(Collectors.toList());
    }
    @GetMapping("/risk/{symbol}")
    public String checkRisk(@PathVariable String symbol) {
        List<UserAsset> assets = repo.findBySymbol(symbol);
        if (assets.isEmpty()) {
            return "Low Risk to Buy (no previous records)";
        }

        BigDecimal avgBuyPrice = assets.stream()
                .map(UserAsset::getBuyPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(assets.size()), DIVIDE_SCALE, RoundingMode.HALF_UP);

        BigDecimal currentPrice = getLivePrice(assets.get(0).getAssetType(), symbol);
        boolean isBelowAvg = currentPrice.compareTo(avgBuyPrice) < 0;

        return String.format("Current: %s | Avg Buy: %s\n%s | %s",
                currentPrice, avgBuyPrice,
                isBelowAvg ? "High Risk to Sell" : "Low Risk to Sell",
                isBelowAvg ? "Low Risk to Buy" : "High Risk to Buy");
    }

    @GetMapping("/risk/buy/{symbol}/{qty}")
    public RiskAssessment checkBuyRisk(@PathVariable String symbol, @PathVariable int qty) {
        return userAssetService.checkBuyRisk(symbol, qty);
    }

    @GetMapping("/risk/sell/{symbol}/{qty}")
    public RiskAssessment checkSellRisk(@PathVariable String symbol, @PathVariable int qty) {
        return userAssetService.checkSellRisk(symbol, qty);
    }

    private BigDecimal getLivePrice(String assetType, String symbol) {
        return "CRYPTO".equalsIgnoreCase(assetType)
                ? cryptoService.getCryptoPrice(symbol.toLowerCase())
                : stockService.getCurrentPrice(symbol);
    }

    private ProfitLossResult calculateProfitLoss(BigDecimal buyPrice, BigDecimal currentPrice, Integer qty) {
        BigDecimal buyPriceSafe = buyPrice == null ? BigDecimal.ZERO : buyPrice;

        BigDecimal difference = currentPrice.subtract(buyPriceSafe)
                .multiply(BigDecimal.valueOf(qty));

        BigDecimal percent = buyPriceSafe.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO.setScale(PERCENT_SCALE, RoundingMode.HALF_UP)
                : currentPrice.subtract(buyPriceSafe)
                .divide(buyPriceSafe, DIVIDE_SCALE, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(PERCENT_SCALE, RoundingMode.HALF_UP);

        String status = difference.signum() >= 0 ? "PROFIT" : "LOSS";
        return new ProfitLossResult(status, percent);
    }

    private DashboardAsset buildDashboardAsset(UserAsset asset) {
        BigDecimal livePrice = getLivePrice(asset.getAssetType(), asset.getSymbol());

        // Update DB with live price
        asset.setCurrentPrice(livePrice);
        asset.setCurrentUpdated(LocalDateTime.now());
        repo.save(asset);

        ProfitLossResult result = calculateProfitLoss(asset.getBuyPrice(), livePrice, asset.getQty());

        // Calculate absolute difference for DashboardAsset
        BigDecimal buyPriceSafe = asset.getBuyPrice() == null ? BigDecimal.ZERO : asset.getBuyPrice();
        BigDecimal difference = livePrice.subtract(buyPriceSafe).multiply(BigDecimal.valueOf(asset.getQty())).abs();

        return new DashboardAsset(
                asset.getId(),
                asset.getAssetType(),
                asset.getSymbol(),
                asset.getName(),
                asset.getBuyPrice(),
                asset.getQty(),
                livePrice,
                LocalDateTime.now(),
                difference,
                result.percent(),
                result.status()
        );
    }

    private DashboardAsset buildProfitLossAsset(UserAsset asset) {
        BigDecimal currentPrice = getLivePrice(asset.getAssetType(), asset.getSymbol());
        ProfitLossResult result = calculateProfitLoss(asset.getBuyPrice(), currentPrice, asset.getQty());

        // Calculate absolute difference for DashboardAsset
        BigDecimal buyPriceSafe = asset.getBuyPrice() == null ? BigDecimal.ZERO : asset.getBuyPrice();
        BigDecimal difference = currentPrice.subtract(buyPriceSafe).multiply(BigDecimal.valueOf(asset.getQty())).abs();

        return new DashboardAsset(
                asset.getId(),
                asset.getAssetType(),
                asset.getSymbol(),
                asset.getName(),
                asset.getBuyPrice(),
                asset.getQty(),
                currentPrice,
                LocalDateTime.now(),
                difference,
                result.percent(),
                result.status()
        );
    }

    private record ProfitLossResult(String status, BigDecimal percent) {}
}
