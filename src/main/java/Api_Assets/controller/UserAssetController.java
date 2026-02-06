package Api_Assets.controller;

import Api_Assets.dto.DashboardAsset;
import Api_Assets.dto.RiskAssessment;
import Api_Assets.dto.SellRequest;
import Api_Assets.entity.UserAsset;
import Api_Assets.repository.UserAssetRepository;
import Api_Assets.service.CryptoService;
import Api_Assets.service.StockService;
import Api_Assets.service.UserAssetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/assets")
public class UserAssetController {

    private final UserAssetRepository repo;
    private final StockService stockService;
    private final CryptoService cryptoService;
    @Autowired
    private UserAssetService assetService;

    public UserAssetController(UserAssetRepository repo,
                               StockService stockService,
                               CryptoService cryptoService) {
        this.repo = repo;
        this.stockService = stockService;
        this.cryptoService = cryptoService;}

    // ---------------- BUY ----------------
    @PostMapping
    public UserAsset addAsset(@RequestBody UserAsset asset) {
        BigDecimal livePrice = getLivePrice(asset.getAssetType(), asset.getSymbol());

        asset.setCurrentPrice(livePrice);
        asset.setCurrentUpdated(LocalDateTime.now());
        asset.setLastUpdated(LocalDateTime.now());

        return repo.save(asset);
    }

    @GetMapping  // ← ADD THIS
    public List<UserAsset> getAllAssets() {
        return repo.findAll();
    }


    // ---------------- SELL ----------------
    @PostMapping("/sell")
    public String sellAsset(@RequestBody SellRequest request) {
        List<UserAsset> assets = repo.findBySymbol(request.getSymbol());

        if (assets.isEmpty()) {
            throw new RuntimeException("Asset not found");
        }

        int totalAvailable = assets.stream()
                .mapToInt(a -> a.getQty() != null ? a.getQty() : 0)
                .sum();
        int qtyToSell = request.getQuantityToSell();

        if (qtyToSell <= 0) {
            throw new RuntimeException("Sell quantity must be positive");
        }
        if (qtyToSell > totalAvailable) {
            throw new RuntimeException("Sell quantity (" + qtyToSell + ") exceeds available (" + totalAvailable + "). No sell performed.");
        }

        BigDecimal livePrice =
                getLivePrice(assets.get(0).getAssetType(), request.getSymbol());

        for (UserAsset asset : assets) {
            if (qtyToSell <= 0) break;

            int available = asset.getQty();
            int sellNow = Math.min(available, qtyToSell);

            asset.setQty(available - sellNow);

            if (asset.getQty() == 0) {
                asset.setSellingPrice(livePrice);
                asset.setSellingDate(LocalDateTime.now());
            }

            asset.setCurrentPrice(livePrice);
            asset.setCurrentUpdated(LocalDateTime.now());
            asset.setLastUpdated(LocalDateTime.now());

            repo.save(asset);
            qtyToSell -= sellNow;
        }

        return "Sell successful";
    }

    @PostMapping("/sell/{id}")
    public ResponseEntity<RiskAssessment> sellAsset(
            @PathVariable Long id,
            @RequestBody SellRequest sellRequest) {

        RiskAssessment risk = assetService.checkSellRisk(sellRequest.getSymbol(), sellRequest.getQuantityToSell());

        UserAsset asset = repo.findById(id).orElseThrow();

        // ✅ Uses your new method
        if (risk.isFullSell()) {
            asset.setQty(0);  // Sell everything
            asset.setSellingPrice(risk.getCurrentPrice());
        } else {
            asset.setQty(asset.getQty() - risk.getRequestedQuantity());  // Partial sell
        }

        repo.save(asset);
        return ResponseEntity.ok(risk);
    }


    // ---------------- HOLDINGS (LIVE PRICE) ----------------
    @GetMapping("/holdings")
    public List<DashboardAsset> getHoldings() {
        return repo.findAll().stream()
                .filter(a -> a.getQty() != null && a.getQty() > 0)
                .map(this::mapToDashboardSafe)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ---------------- HISTORY ----------------
    @GetMapping("/history")
    public List<UserAsset> getHistory() {
        return repo.findAll().stream()
                .filter(a -> a.getQty() != null && a.getQty() == 0)
                .collect(Collectors.toList());
    }

    // ---------------- HELPERS ----------------
    /** Safe mapping: if live price fails (e.g. rate limit for crypto), skip this asset so others still show. */
    private DashboardAsset mapToDashboardSafe(UserAsset asset) {
        try {
            return mapToDashboard(asset);
        } catch (Exception e) {
            System.err.println("Skipping asset " + asset.getSymbol() + " (" + asset.getAssetType() + "): " + e.getMessage());
            return null;
        }
    }

    private DashboardAsset mapToDashboard(UserAsset asset) {
        BigDecimal livePrice =
                getLivePrice(asset.getAssetType(), asset.getSymbol());

        BigDecimal buy = asset.getBuyPrice();
        int qty = asset.getQty();

        BigDecimal percent = buy.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : livePrice.subtract(buy)
                .divide(buy, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        return new DashboardAsset(
                asset.getId(),
                asset.getAssetType(),
                asset.getSymbol(),
                asset.getName(),
                buy,
                qty,
                livePrice,
                LocalDateTime.now(),
                livePrice.subtract(buy).multiply(BigDecimal.valueOf(qty)),
                percent,
                percent.signum() >= 0 ? "PROFIT" : "LOSS"
        );
    }

    private BigDecimal getLivePrice(String type, String symbol) {
        return "CRYPTO".equalsIgnoreCase(type)
                ? cryptoService.getCryptoPrice(symbol)
                : stockService.getCurrentPrice(symbol);
    }
}
