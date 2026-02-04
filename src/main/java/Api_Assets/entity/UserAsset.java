package Api_Assets.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data @NoArgsConstructor @AllArgsConstructor
@Table(name = "user_assets")
public class UserAsset {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String assetType;
    private String symbol;
    private String name;

    private BigDecimal buyPrice;
    private Integer qty;

    private BigDecimal currentPrice;
    private LocalDateTime currentUpdated;

    // SELL INFO (updated when sold)
    private BigDecimal sellingPrice;
    private LocalDateTime sellingDate;

    private LocalDateTime lastUpdated;
}
