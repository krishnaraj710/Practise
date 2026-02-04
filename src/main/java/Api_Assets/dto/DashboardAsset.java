package Api_Assets.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DashboardAsset {
    private Long id;
    private String type;
    private String symbol;
    private String name;
    private BigDecimal buyPrice;
    private Integer qty;
    private BigDecimal currentPrice;
    private LocalDateTime currentDate;
    private BigDecimal differencePercent;
    private BigDecimal percent;
    private String status;

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
