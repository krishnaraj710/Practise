package Api_Assets.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @AllArgsConstructor
public class UserAssetDTO {
    private Long id;
    private String assetType;
    private String symbol;
    private String name;
    private BigDecimal currentPrice;     // LIVE API
    private LocalDateTime currentUpdated; // LIVE DATE
    // NO sellingPrice/sellingDate here!
}
