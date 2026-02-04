package Api_Assets.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

@Service
public class GeminiService {

    @Value("${gemini.api.base-url:https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=}")
    private String baseUrl;

    @Value("${gemini.api.key:}")
    private String apiKey;

    private String apiUrl;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        if (!StringUtils.hasText(apiKey)) {
            System.err.println("❌ NO GEMINI API KEY FOUND! Using fallback responses.");
            this.apiUrl = null;
            return;
        }

        this.apiUrl = baseUrl + apiKey.trim();
        System.out.println("🔑 Gemini API initialized: " + apiUrl.substring(0, 60) + "...");
    }

    public String generateResponse(String prompt) {
        if (!StringUtils.hasText(apiUrl)) {
            return "🤖 Gemini unavailable - check API key in application.properties";
        }

        try {
            String requestBody = """
                {
                  "contents": [{"parts":[{"text": "%s"}]}],
                  "generationConfig": {
                    "temperature": 0.7,
                    "maxOutputTokens": 1024
                  }
                }
                """.formatted(prompt);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return parseGeminiResponse(response.getBody());
            } else {
                System.err.println("❌ Gemini HTTP " + response.getStatusCode() + ": " + response.getBody());
                return null;
            }
        } catch (Exception e) {
            System.err.println("❌ Gemini error: " + e.getMessage());
            return null;
        }
    }

    private String parseGeminiResponse(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode candidates = root.path("candidates");
            if (candidates.size() > 0) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.size() > 0) {
                    return parts.get(0).path("text").asText();
                }
            }
            return null;
        } catch (Exception e) {
            System.err.println("❌ JSON parse error: " + e.getMessage());
            return null;
        }
    }
}

