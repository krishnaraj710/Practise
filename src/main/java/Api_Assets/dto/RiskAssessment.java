// java
package Api_Assets.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RiskAssessment {
    private String action; // "BUY" or "SELL"
    private String riskLevel; // "LOW", "MEDIUM", "HIGH", "INSUFFICIENT_QUANTITY", "NO_HOLDINGS"
    private BigDecimal avgBuyPrice;
    private BigDecimal currentPrice;
    private BigDecimal percentDifference; // (current - avg) / avg * 100
    private BigDecimal monetaryImpact; // difference * quantity
    private int requestedQuantity;
    private int availableQuantity;
    private String recommendation;
}
