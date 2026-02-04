package com.example.Api_Assets.service;

import Api_Assets.dto.RiskAssessment;
import Api_Assets.entity.UserAsset;
import Api_Assets.repository.UserAssetRepository;
import Api_Assets.service.AssetService;
import Api_Assets.service.CryptoService;
import Api_Assets.service.StockService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock
    private UserAssetRepository userAssetRepository;

    @Mock
    private StockService stockService;

    @Mock
    private CryptoService cryptoService;

    @InjectMocks
    private AssetService assetService;

    @Test
    void checkSellRisk_noHoldings_returnsNoHoldings() {
        when(userAssetRepository.findBySymbol("NOP")).thenReturn(Collections.emptyList());

        RiskAssessment r = assetService.checkSellRisk("NOP", 1);

        assertEquals("NO_HOLDINGS", r.getRiskLevel());
    }

    @Test
    void checkSellRisk_insufficientQuantity_returnsInsufficient() {
        UserAsset a = new UserAsset();
        a.setSymbol("ABC");
        a.setAssetType("STOCK");
        a.setBuyPrice(new BigDecimal("100"));
        a.setQty(5);
        when(userAssetRepository.findBySymbol("ABC")).thenReturn(List.of(a));

        RiskAssessment r = assetService.checkSellRisk("ABC", 10);

        assertEquals("INSUFFICIENT_QUANTITY", r.getRiskLevel());
    }

    @Test
    void checkSellRisk_highRisk_whenLargeLoss() {
        UserAsset a = new UserAsset();
        a.setSymbol("LOSS");
        a.setAssetType("STOCK");
        a.setBuyPrice(new BigDecimal("100"));
        a.setQty(10);
        when(userAssetRepository.findBySymbol("LOSS")).thenReturn(List.of(a));
        when(stockService.getCurrentPrice("LOSS")).thenReturn(new BigDecimal("80")); // -20% => HIGH

        RiskAssessment r = assetService.checkSellRisk("LOSS", 5);

        assertEquals("HIGH", r.getRiskLevel());
        assertTrue(r.getRecommendation().toLowerCase().contains("high risk"));
    }

    @Test
    void checkBuyRisk_noHoldings_returnsLow() {
        when(userAssetRepository.findBySymbol("NEW")).thenReturn(Collections.emptyList());
        when(stockService.getCurrentPrice("NEW")).thenReturn(new BigDecimal("50"));

        RiskAssessment r = assetService.checkBuyRisk("NEW", 10);

        assertEquals("LOW", r.getRiskLevel());
        assertEquals(new BigDecimal("50.0000"), r.getCurrentPrice());
    }

    @Test
    void checkBuyRisk_mediumRisk_whenPriceAboveAverage() {
        UserAsset a = new UserAsset();
        a.setSymbol("ABC");
        a.setAssetType("STOCK");
        a.setBuyPrice(new BigDecimal("100"));
        a.setQty(1);
        when(userAssetRepository.findBySymbol("ABC")).thenReturn(List.of(a));
        when(stockService.getCurrentPrice("ABC")).thenReturn(new BigDecimal("104")); // +4% -> MEDIUM

        RiskAssessment r = assetService.checkBuyRisk("ABC", 2);

        assertEquals("MEDIUM", r.getRiskLevel());
    }
}
