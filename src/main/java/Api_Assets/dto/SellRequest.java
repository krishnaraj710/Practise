package Api_Assets.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SellRequest {
    private BigDecimal sellingPrice;
    private LocalDateTime sellingDate;
    private String symbol;
    private Integer quantityToSell;
}
