package Api_Assets.service;

import Api_Assets.dto.UserAssetRecommendation;
import Api_Assets.entity.UserAsset;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChatService {

    private final RecommendationService recommendationService;
    private final GeminiService geminiService;

    public ChatService(RecommendationService recommendationService,
                       GeminiService geminiService) {
        this.recommendationService = recommendationService;
        this.geminiService = geminiService;
    }

    public String processMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Please enter a valid query. Examples: \"suggest top 5 stocks\", \"suggest top 3 crypto\", \"top 5 assets\", or \"is my portfolio concentrated?\"";
        }

        String msg = message.toLowerCase();
        int n = extractNumber(msg, 5);

        // Concentrated / diversified / diluted (user asking about portfolio spread)
        if (msg.contains("concentrated") || msg.contains("diversif") || msg.contains("diversification")
                || msg.contains("diluted") || msg.contains("dilute")) {
            return analyzeDiversificationWithReason();
        }

        if ((msg.contains("top") || msg.contains("suggest")) && msg.contains("stock")) {
            List<UserAssetRecommendation> topStocks = recommendationService.getTopNStocksSuggestions(n);
            return buildNumberedResponse(topStocks, n, "stocks", null, "stocks");
        }

        if ((msg.contains("top") || msg.contains("suggest")) && msg.contains("crypto")) {
            List<UserAssetRecommendation> topCrypto = recommendationService.getTopNCryptoSuggestions(n);
            return buildNumberedResponse(topCrypto, n, "crypto", null, "crypto");
        }

        if (msg.contains("top") || (msg.contains("suggest") && msg.contains("asset"))) {
            List<UserAssetRecommendation> topAssets = recommendationService.getTopNAssetsSuggestions(n);
            return buildNumberedResponse(topAssets, n, "assets", null, "stocks and crypto");
        }

        return getDefaultResponse(message);
    }

    /** Full diversification analysis with reason (stocks + crypto). */
    private String analyzeDiversificationWithReason() {
        List<UserAsset> holdings = recommendationService.getAllHoldings();
        String base = analyzeDiversification(holdings);

        long uniqueCount = holdings.stream()
                .map(a -> a.getSymbol() != null ? a.getSymbol().toUpperCase() : "")
                .filter(s -> !s.isEmpty())
                .distinct()
                .count();
        String geminiPrompt = "A portfolio has " + holdings.size() + " positions across "
                + uniqueCount + " different assets (stocks and/or crypto). "
                + "In 2-3 sentences, give a brief practical reason about whether this is concentrated or diversified. Be concise.";
        String geminiInsight = geminiService.generateResponse(geminiPrompt);

        if (isValidGeminiResponse(geminiInsight)) {
            return base + "\n\n**Insight:** " + geminiInsight.trim();
        }
        return base;
    }

    /** Analyze diversification across stocks + crypto, with clear reason. */
    private String analyzeDiversification(List<UserAsset> holdings) {
        if (holdings == null || holdings.isEmpty()) {
            return "**No holdings.** Why: Add assets (stocks or crypto) to analyze diversification.";
        }

        long uniqueSymbols = holdings.stream().map(a -> (a.getSymbol() != null ? a.getSymbol().toUpperCase() : "")).distinct().count();
        long stockCount = holdings.stream().filter(a -> "STOCK".equalsIgnoreCase(a.getAssetType())).map(UserAsset::getSymbol).distinct().count();
        long cryptoCount = holdings.stream().filter(a -> "CRYPTO".equalsIgnoreCase(a.getAssetType())).map(UserAsset::getSymbol).distinct().count();

        if (uniqueSymbols <= 2) {
            return "**Concentrated.** Why: You hold only " + uniqueSymbols + " asset(s) "
                    + (stockCount > 0 && cryptoCount > 0 ? "(stocks + crypto). " : "")
                    + "Risk is high if one fails. Add more to diversify.";
        }
        if (uniqueSymbols <= 4) {
            return "**Moderate.** Why: " + uniqueSymbols + " holdings. Add 1–2 more across stocks/crypto for better diversification.";
        }
        if (uniqueSymbols >= 8) {
            return "**Well diversified.** Why: " + uniqueSymbols + " assets — good spread, lower single-name risk.";
        }
        return "**Good diversification.** Why: " + uniqueSymbols + " holdings — balanced.";
    }

    /** Builds a numbered list of assets (from portfolio + market). Uses Gemini for insight when available. */
    private String buildNumberedResponse(List<UserAssetRecommendation> assets, int requestedN, String type,
                                         String diversification, String assetTypeForGemini) {
        if (assets.isEmpty()) {
            return "No " + type + " recommendations available. Try adding assets or check API keys.";
        }

        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (UserAssetRecommendation a : assets) {
            String action = actionFromRisk(a.getRiskLevel(), a.getProfitPercent());
            sb.append(index++).append(". **").append(a.getSymbol()).append("** – ")
                    .append(action).append(" – ").append(formatPercent(a.getProfitPercent())).append("\n");
        }
        sb.setLength(Math.max(0, sb.length() - 1)); // drop trailing newline

        if (diversification != null && !diversification.isEmpty()) {
            sb.append("\n\n").append(diversification);
        }

        // Gemini insight: brief market context for these recommendations
        String symbols = assets.stream().map(UserAssetRecommendation::getSymbol).limit(5).reduce((a, b) -> a + ", " + b).orElse("");
        String geminiPrompt = "Given these top " + assetTypeForGemini + " picks: " + symbols
                + ". In 1-2 sentences, give a brief market context or consideration. Be concise.";
        String geminiInsight = geminiService.generateResponse(geminiPrompt);
        if (isValidGeminiResponse(geminiInsight)) {
            sb.append("\n\n**Insight:** ").append(geminiInsight.trim());
        }

        return sb.toString().trim();
    }

    private String actionFromRisk(String riskLevel, BigDecimal profitPercent) {
        if (riskLevel != null) {
            if ("HIGH".equalsIgnoreCase(riskLevel)) return "Review";
            if ("MEDIUM".equalsIgnoreCase(riskLevel)) return "Hold";
            if ("LOW".equalsIgnoreCase(riskLevel)) return profitPercent != null && profitPercent.compareTo(BigDecimal.ZERO) > 0 ? "Buy" : "Hold";
        }
        return profitPercent != null && profitPercent.compareTo(BigDecimal.valueOf(20)) >= 0 ? "Buy" : "Hold";
    }

    private boolean isValidGeminiResponse(String s) {
        if (s == null || s.isBlank()) return false;
        String lower = s.toLowerCase();
        return !lower.contains("unavailable") && !lower.contains("check api");
    }

    private String getDefaultResponse(String message) {
        return "**Try:** suggest top 5 stocks | suggest top 3 crypto | top 5 assets | is my portfolio concentrated?";
    }

    private int extractNumber(String text, int defaultValue) {
        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group()) : defaultValue;
    }

    private String formatPercent(BigDecimal percent) {
        if (percent == null) return "0.0%";
        return (percent.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") +
                percent.setScale(1, RoundingMode.HALF_UP) + "%";
    }
}
