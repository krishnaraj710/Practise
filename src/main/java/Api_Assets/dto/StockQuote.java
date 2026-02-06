package Api_Assets.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Live quote data from StockData.org API */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockQuote {
    private String symbol;
    private BigDecimal price;
    /** Day change % (price vs previous close) */
    private BigDecimal dayChangePercent;
}
