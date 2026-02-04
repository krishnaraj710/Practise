package Api_Assets.service;

import Api_Assets.dto.UserAssetRecommendation;
import Api_Assets.entity.UserAsset;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
            return "Please enter a valid query like: top 3 assets, top 3 stocks, or top 3 crypto.";
        }

        String msg = message.toLowerCase();
        int n = extractNumber(msg, 3);

        if (msg.contains("top") && msg.contains("stock")) {
            List<UserAsset> allStocks = recommendationService.getAllStocks();
            String diversification = analyzeStockDiversification(allStocks);

            List<UserAssetRecommendation> topStocks = recommendationService.getTopNStocks(n);
            if (topStocks.isEmpty()) return "No stocks found in your portfolio.";

            String stocksText = formatAssetList(topStocks, "STOCK");
            String prompt = createStockPrompt(n, stocksText, diversification);

            String geminiReply = geminiService.generateResponse(prompt);
            return geminiReply != null && !geminiReply.isBlank()
                    ? geminiReply
                    : fallbackResponse("STOCKS", topStocks, diversification);
        }

        if (msg.contains("top") && msg.contains("crypto")) {
            List<UserAssetRecommendation> topCrypto = recommendationService.getTopNCrypto(n);
            if (topCrypto.isEmpty()) return "No crypto assets found in your portfolio.";

            String cryptoText = formatAssetList(topCrypto, "CRYPTO");
            String prompt = createCryptoPrompt(n, cryptoText);

            String geminiReply = geminiService.generateResponse(prompt);
            return geminiReply != null && !geminiReply.isBlank()
                    ? geminiReply
                    : fallbackResponse("CRYPTO", topCrypto);
        }

        if (msg.contains("top")) {
            List<UserAssetRecommendation> topAssets = recommendationService.getTopNAssets(n);
            if (topAssets.isEmpty()) return "No assets found in your portfolio.";

            String assetsText = formatAssetList(topAssets, null);
            String prompt = createAssetsPrompt(n, assetsText);

            String geminiReply = geminiService.generateResponse(prompt);
            return geminiReply != null && !geminiReply.isBlank()
                    ? geminiReply
                    : fallbackResponse("ASSETS", topAssets);
        }

        return getDefaultResponse(message);
    }

    private String analyzeStockDiversification(List<UserAsset> stocks) {
        if (stocks.isEmpty()) return "**NO STOCKS**: Add stocks to analyze diversification.";

        long totalStocks = stocks.stream().map(UserAsset::getSymbol).distinct().count();

        if (totalStocks <= 2) {
            return "**CONCENTRATED** (" + totalStocks + " stocks): High risk - too few holdings.";
        }

        if (totalStocks <= 4) {
            return "**MODERATE** (" + totalStocks + " stocks): Consider 1-2 more for better diversification.";
        }

        if (totalStocks >= 8) {
            return "**WELL DIVERSIFIED** (" + totalStocks + " stocks): Excellent spread across holdings.";
        }

        return "**GOOD DIVERSIFICATION** (" + totalStocks + " stocks): Balanced portfolio size.";
    }

    private String formatAssetList(List<UserAssetRecommendation> assets, String type) {
        return assets.stream()
                .map(a -> "**" + a.getSymbol() + "** (" + formatPercent(a.getProfitPercent()) +
                        (type != null ? ", " + type : "") + ")")
                .collect(Collectors.joining(", "));
    }

    private String createStockPrompt(int n, String stocksText, String diversification) {
        String examples = "**AAPL**: HOLD (+23.4%) - Tech leader\n**NVDA**: BUY (+45.2%) - AI growth";
        return String.format(
                "STOCK RECOMMENDATIONS: My top %d STOCKS: %s\n\n" +
                        "**DIVERSIFICATION**: %s\n\n" +
                        "TASK: For EXACTLY %d stocks, provide recommendations in this EXACT format:\n%s\n\n" +
                        "RULES:\n1. EXACTLY %d lines, one per stock\n2. Use ONLY these symbols\n3. Match profit %% exactly\n4. Reason: 2-3 words only",
                n, stocksText, diversification, n, examples, n
        );
    }

    private String createCryptoPrompt(int n, String cryptoText) {
        String examples = "**BTC**: HOLD (+22.1%) - Market leader\n**SOL**: BUY (+89.3%) - High growth";
        return String.format(
                "CRYPTO RECOMMENDATIONS: My top %d CRYPTO: %s\n\nTASK: For EXACTLY %d crypto:\n%s\n\nRULES: %d lines, match profit %%, 2-3 word reasons",
                n, cryptoText, n, examples, n
        );
    }

    private String createAssetsPrompt(int n, String assetsText) {
        String examples = "**AAPL**: HOLD (+23.4%) - Tech stable\n**BTC**: HOLD (+22.1%) - Crypto leader";
        return String.format(
                "ASSET RECOMMENDATIONS: My top %d ASSETS: %s\nTASK: %d perfect recommendations:\n%s",
                n, assetsText, n, examples
        );
    }

    private String fallbackResponse(String type, List<UserAssetRecommendation> assets, String diversification) {
        String divText = "STOCKS".equals(type) ? "\n" + diversification : "";
        return "TOP " + type + ": " + formatAssetList(assets, type) + divText;
    }

    private String fallbackResponse(String type, List<UserAssetRecommendation> assets) {
        return fallbackResponse(type, assets, "");
    }

    private String getDefaultResponse(String message) {
        return """
            **PORTFOLIO CHATBOT** 
            **Commands:**
            • `top 3 stocks` → Best stocks + diversification analysis
            • `top 3 crypto` → Best crypto + BUY/SELL/HOLD
            • `top 5 assets` → Overall portfolio ranking
            """;
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
