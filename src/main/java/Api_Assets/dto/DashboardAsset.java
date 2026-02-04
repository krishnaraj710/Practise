package Api_Assets.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DashboardAsset {
    private Long id;
    private String type;           // assetType
    private String symbol;
    private String name;
    private BigDecimal buyPrice;   // From table
    private Integer qty;           // From table
    private BigDecimal currentPrice; // LIVE API
    private LocalDateTime currentDate; // LIVE time
    private BigDecimal differencePercent;
    private BigDecimal percent;// profit or loss amount
    private String status;         // PROFIT or LOSS

    public DashboardAsset(Long id, String type, String symbol, String name, BigDecimal buyPrice, Integer qty, BigDecimal currentPrice, LocalDateTime currentDate, BigDecimal difference,BigDecimal percent, String status) {
        this.id = id;
        this.type = type;
        this.symbol = symbol;
        this.name = name;
        this.buyPrice = buyPrice;
        this.qty = qty;
        this.currentPrice = currentPrice;
        this.currentDate = currentDate;
        this.differencePercent = difference;
        this.percent = percent;
        this.status = status;
    }

}
