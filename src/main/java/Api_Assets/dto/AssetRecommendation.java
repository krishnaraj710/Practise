// java
package Api_Assets.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssetRecommendation {
    private String symbol;
    private String riskLevel;
    private BigDecimal avgBuyPrice;
    private BigDecimal currentPrice;
    private BigDecimal profitPercent;

    // Convenience constructor used by RecommendationService (symbol, riskLevel, profitPercent)
    public AssetRecommendation(String symbol, String riskLevel, BigDecimal profitPercent) {
        this.symbol = symbol;
        this.riskLevel = riskLevel;
        this.profitPercent = profitPercent;
    }
}

