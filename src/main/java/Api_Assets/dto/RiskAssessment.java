package Api_Assets.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RiskAssessment {
    private String action;
    private String riskLevel;
    private BigDecimal avgBuyPrice;
    private BigDecimal currentPrice;
    private BigDecimal percentDifference;
    private BigDecimal monetaryImpact;
    private int requestedQuantity;
    private int availableQuantity;
    private String recommendation;

    // ✅ FIXED: Check if selling ALL available quantity
    public boolean isFullSell() {
        return this.requestedQuantity >= this.availableQuantity;
    }

    // Bonus: Check risk level
    public boolean isHighRisk() {
        return "HIGH".equals(this.riskLevel);
    }
}
