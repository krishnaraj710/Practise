package Api_Assets.controller;

import Api_Assets.dto.DashboardAsset;
import Api_Assets.dto.RiskAssessment;
import Api_Assets.dto.SellRequest;
import Api_Assets.entity.UserAsset;
import Api_Assets.repository.UserAssetRepository;
import Api_Assets.service.AssetService;
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
public class AssetController {

    @Autowired private UserAssetRepository repo;
    @Autowired private StockService stockService;
    @Autowired private CryptoService cryptoService;
    @Autowired private AssetService assetService;

    private static final int PERCENT_SCALE = 2;
    private static final int DIVIDE_SCALE = 8;

    // ------------------- ADD ASSET -------------------
    @PostMapping
    public UserAsset addAsset(@RequestBody UserAsset asset) {
        asset.setCurrentUpdated(LocalDateTime.now());
        asset.setLastUpdated(LocalDateTime.now());

        BigDecimal livePrice;
        if ("CRYPTO".equalsIgnoreCase(asset.getAssetType())) {
            livePrice = cryptoService.getCryptoPrice(asset.getSymbol().toLowerCase());
        } else {
            livePrice = stockService.getCurrentPrice(asset.getSymbol());
        }

        asset.setCurrentPrice(livePrice);
        return repo.save(asset);
    }

    // ------------------- SELL ASSET BY ID -------------------
    @PostMapping("/sell/{id}")
    public String sellAssetById(@PathVariable Long id) {
        UserAsset asset = repo.findById(id).orElseThrow(() -> new RuntimeException("Asset not found"));

        BigDecimal currentPrice = "CRYPTO".equalsIgnoreCase(asset.getAssetType())
                ? cryptoService.getCryptoPrice(asset.getSymbol().toLowerCase())
                : stockService.getCurrentPrice(asset.getSymbol());

        BigDecimal difference = currentPrice.subtract(asset.getBuyPrice()).multiply(BigDecimal.valueOf(asset.getQty()));
        BigDecimal percent = asset.getBuyPrice().compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO.setScale(PERCENT_SCALE, RoundingMode.HALF_UP)
                : currentPrice.subtract(asset.getBuyPrice())
                .divide(asset.getBuyPrice(), DIVIDE_SCALE, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(PERCENT_SCALE, RoundingMode.HALF_UP);

        String status = difference.signum() >= 0 ? "PROFIT" : "LOSS";

        asset.setSellingPrice(currentPrice);
        asset.setSellingDate(LocalDateTime.now());
        asset.setQty(0);
        asset.setLastUpdated(LocalDateTime.now());

        repo.save(asset);

        return "Sold " + asset.getSymbol() + " with " + status + " of " + percent.toPlainString() + "%";
    }

    // ------------------- GET ALL ASSETS -------------------
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

    // ------------------- GET ASSET BY SYMBOL -------------------
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

    // ------------------- DASHBOARD -------------------
    @GetMapping("/dashboard")
    public List<DashboardAsset> getDashboard() {
        return repo.findAll().stream()
                .map(asset -> {
                    BigDecimal livePrice = "CRYPTO".equalsIgnoreCase(asset.getAssetType())
                            ? cryptoService.getCryptoPrice(asset.getSymbol())
                            : stockService.getCurrentPrice(asset.getSymbol());

                    asset.setCurrentPrice(livePrice);
                    asset.setCurrentUpdated(LocalDateTime.now());
                    repo.save(asset);

                    BigDecimal buyPrice = asset.getBuyPrice() == null ? BigDecimal.ZERO : asset.getBuyPrice();
                    BigDecimal difference = livePrice.subtract(buyPrice).multiply(BigDecimal.valueOf(asset.getQty()));
                    BigDecimal percent = buyPrice.compareTo(BigDecimal.ZERO) == 0
                            ? BigDecimal.ZERO.setScale(PERCENT_SCALE, RoundingMode.HALF_UP)
                            : livePrice.subtract(buyPrice)
                            .divide(buyPrice, DIVIDE_SCALE, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(PERCENT_SCALE, RoundingMode.HALF_UP);

                    String status = difference.signum() >= 0 ? "PROFIT" : "LOSS";

                    return new DashboardAsset(
                            asset.getId(),
                            asset.getAssetType(),
                            asset.getSymbol(),
                            asset.getName(),
                            asset.getBuyPrice(),
                            asset.getQty(),
                            livePrice,
                            LocalDateTime.now(),
                            difference.abs(),
                            percent,
                            status
                    );
                }).collect(Collectors.toList());
    }

    // ------------------- PROFIT/LOSS -------------------
    @GetMapping("/profit-loss")
    public List<DashboardAsset> getProfitOrLossForAllAssets() {
        return repo.findAll().stream().map(asset -> {
            BigDecimal currentPrice = "CRYPTO".equalsIgnoreCase(asset.getAssetType())
                    ? cryptoService.getCryptoPrice(asset.getSymbol().toLowerCase())
                    : stockService.getCurrentPrice(asset.getSymbol());

            BigDecimal buyPrice = asset.getBuyPrice() == null ? BigDecimal.ZERO : asset.getBuyPrice();
            BigDecimal difference = currentPrice.subtract(buyPrice).multiply(BigDecimal.valueOf(asset.getQty()));
            BigDecimal percent = buyPrice.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO.setScale(PERCENT_SCALE, RoundingMode.HALF_UP)
                    : currentPrice.subtract(buyPrice)
                    .divide(buyPrice, DIVIDE_SCALE, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(PERCENT_SCALE, RoundingMode.HALF_UP);

            String status = difference.signum() >= 0 ? "PROFIT" : "LOSS";

            return new DashboardAsset(
                    asset.getId(),
                    asset.getAssetType(),
                    asset.getSymbol(),
                    asset.getName(),
                    asset.getBuyPrice(),
                    asset.getQty(),
                    currentPrice,
                    LocalDateTime.now(),
                    difference.abs(),
                    percent,
                    status
            );
        }).collect(Collectors.toList());
    }

    // ------------------- SELL ASSET -------------------
    @PostMapping("/sell")
    public String sellAsset(@RequestBody SellRequest request) {

        List<UserAsset> assets = repo.findBySymbol(request.getSymbol());
        if (assets.isEmpty()) {
            throw new RuntimeException("Asset not found: " + request.getSymbol());
        }

        BigDecimal currentPrice = "CRYPTO".equalsIgnoreCase(assets.get(0).getAssetType())
                ? cryptoService.getCryptoPrice(request.getSymbol().toLowerCase())
                : stockService.getCurrentPrice(request.getSymbol());

        BigDecimal totalQtyToSell = BigDecimal.valueOf(request.getQuantityToSell());
        BigDecimal soldQty = BigDecimal.ZERO;

        for (UserAsset asset : assets) {
            if (soldQty.compareTo(totalQtyToSell) >= 0) break;

            BigDecimal remainingQty = BigDecimal.valueOf(asset.getQty());
            BigDecimal qtyToSell = totalQtyToSell.subtract(soldQty).min(remainingQty);
            BigDecimal difference = currentPrice.subtract(asset.getBuyPrice()).multiply(qtyToSell);

            BigDecimal percent = asset.getBuyPrice().compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO.setScale(PERCENT_SCALE, RoundingMode.HALF_UP)
                    : currentPrice.subtract(asset.getBuyPrice())
                    .divide(asset.getBuyPrice(), DIVIDE_SCALE, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(PERCENT_SCALE, RoundingMode.HALF_UP);

            String status = difference.signum() >= 0 ? "PROFIT" : "LOSS";

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

        return "Sold " + soldQty.intValue() + " units of " + request.getSymbol()
                + " with " + (currentPrice.compareTo(BigDecimal.ZERO) >= 0 ? "calculated profit/loss" : "") + "%";
    }

    // ------------------- RISK ASSESSMENT -------------------
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

        BigDecimal currentPrice = "CRYPTO".equalsIgnoreCase(assets.get(0).getAssetType())
                ? cryptoService.getCryptoPrice(symbol.toLowerCase())
                : stockService.getCurrentPrice(symbol);

        String sellRisk = currentPrice.compareTo(avgBuyPrice) < 0
                ? "High Risk to Sell"
                : "Low Risk to Sell";

        String buyRisk = currentPrice.compareTo(avgBuyPrice) > 0
                ? "High Risk to Buy"
                : "Low Risk to Buy";

        return "Current Price: " + currentPrice
                + ", Avg Buy Price: " + avgBuyPrice
                + "\n" + sellRisk + " | " + buyRisk;
    }
    @GetMapping("/risk/buy/{symbol}/{qty}")
    public RiskAssessment checkBuyRisk(@PathVariable String symbol, @PathVariable int qty) {
        return assetService.checkBuyRisk(symbol, qty);
    }

    @GetMapping("/risk/sell/{symbol}/{qty}")
    public RiskAssessment checkSellRisk(@PathVariable String symbol, @PathVariable int qty) {
        return assetService.checkSellRisk(symbol, qty);
    }
}
