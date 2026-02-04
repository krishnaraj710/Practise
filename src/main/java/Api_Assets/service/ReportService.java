package Api_Assets.service;

import Api_Assets.entity.UserAsset;
import Api_Assets.repository.UserAssetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class ReportService {

    @Autowired
    private UserAssetRepository userAssetRepository;

    public String buildWeeklyAssetReport() {
        List<UserAsset> assets = userAssetRepository.findAll();

        if (assets.isEmpty()) {
            return "Weekly Asset Report\n\nNo assets found.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📅 WEEKLY ASSET REPORT\n\n");
        sb.append("Total Assets: ").append(assets.size()).append("\n\n");

        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalProfit = BigDecimal.ZERO;

        sb.append("Assets:\n");
        for (UserAsset asset : assets) {
            BigDecimal qty = BigDecimal.valueOf(asset.getQty());
            BigDecimal currentValue = qty.multiply(asset.getCurrentPrice());

            BigDecimal profitPercent = asset.getCurrentPrice()
                    .subtract(asset.getBuyPrice())
                    .divide(asset.getBuyPrice(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            totalValue = totalValue.add(currentValue);
            totalProfit = totalProfit.add(profitPercent);

            sb.append("• ")
                    .append(asset.getSymbol())
                    .append(" (")
                    .append(asset.getAssetType())
                    .append(") → ₹")
                    .append(currentValue.setScale(2, RoundingMode.HALF_UP))
                    .append(" (")
                    .append(profitPercent.setScale(1, RoundingMode.HALF_UP))
                    .append("%)\n");
        }

        BigDecimal avgProfit = totalProfit.divide(BigDecimal.valueOf(assets.size()), 2, RoundingMode.HALF_UP);

        sb.append("\n📊 SUMMARY:\n");
        sb.append("Total Value: ₹").append(totalValue.setScale(2, RoundingMode.HALF_UP)).append("\n");
        sb.append("Avg Profit: ").append(avgProfit.setScale(1, RoundingMode.HALF_UP)).append("%\n");

        return sb.toString();
    }
}
