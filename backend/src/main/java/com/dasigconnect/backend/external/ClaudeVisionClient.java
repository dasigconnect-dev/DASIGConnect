package com.dasigconnect.backend.external;

import com.dasigconnect.backend.model.dto.ai.CaptionVariantDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Calls the Anthropic Claude Vision API to generate Facebook caption variants.
 *
 * Images are supplied as publicly-accessible URLs (Supabase Storage CDN).
 * A structured prompt requests exactly three variants: Professional, Community, Energetic.
 * Times out after 10 seconds to honour the SDD constraint (GR-T-AI).
 */
@Service
public class ClaudeVisionClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeVisionClient.class);
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    @Value("${anthropic.api.key:}")
    private String apiKey;

    @Value("${anthropic.api.model:claude-haiku-4-5-20251001}")
    private String model;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Generates three caption variants for a social media post.
     *
     * @param imageUrls publicly accessible image URLs (Supabase CDN)
     * @param eventTitle the event title for additional context
     * @return list of three CaptionVariantDto objects (professional / community / energetic)
     * @throws ClaudeApiException on timeout or non-2xx response
     */
    public List<CaptionVariantDto> generateCaptions(List<String> imageUrls, String eventTitle) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ClaudeApiException("Anthropic API key is not configured.");
        }

        String payload = buildPayload(imageUrls, eventTitle);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.http.HttpTimeoutException e) {
            throw new ClaudeApiException("Claude API timed out after 10 seconds.");
        } catch (Exception e) {
            throw new ClaudeApiException("Claude API request failed: " + e.getMessage());
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("Claude API returned status {}: {}", response.statusCode(), response.body());
            throw new ClaudeApiException("Claude API error (HTTP " + response.statusCode() + ").");
        }

        return parseVariants(response.body());
    }

    private String buildPayload(List<String> imageUrls, String eventTitle) {
        try {
            var contentArray = objectMapper.createArrayNode();

            // Attach up to 4 images as URL-type content blocks
            int imgCount = Math.min(imageUrls.size(), 4);
            for (int i = 0; i < imgCount; i++) {
                var imgBlock = objectMapper.createObjectNode();
                imgBlock.put("type", "image");
                var source = objectMapper.createObjectNode();
                source.put("type", "url");
                source.put("url", imageUrls.get(i));
                imgBlock.set("source", source);
                contentArray.add(imgBlock);
            }

            // Text prompt
            var textBlock = objectMapper.createObjectNode();
            textBlock.put("type", "text");
            textBlock.put("text", buildPrompt(eventTitle));
            contentArray.add(textBlock);

            var message = objectMapper.createObjectNode();
            message.put("role", "user");
            message.set("content", contentArray);

            var messagesArray = objectMapper.createArrayNode();
            messagesArray.add(message);

            var root = objectMapper.createObjectNode();
            root.put("model", model);
            root.put("max_tokens", 1024);
            root.set("messages", messagesArray);

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new ClaudeApiException("Failed to build Claude API payload: " + e.getMessage());
        }
    }

    private String buildPrompt(String eventTitle) {
        return """
            You are a social media content assistant for DASIG (DOST Academe-Science and \
            Innovation Group), a Philippine government science agency network.

            Based on the image(s) and event title "%s", generate exactly 3 Facebook caption \
            variants. Each caption MUST be between 80 and 280 characters. Include 2-3 relevant \
            hashtags. Do NOT include offensive or inappropriate content.

            Return ONLY a valid JSON array with exactly 3 objects, no markdown, no explanation:
            [
              {"tone": "professional", "caption": "..."},
              {"tone": "community",    "caption": "..."},
              {"tone": "energetic",    "caption": "..."}
            ]
            """.formatted(eventTitle);
    }

    private List<CaptionVariantDto> parseVariants(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String text = root.path("content").get(0).path("text").asText();

            // Strip any markdown code fences if present
            text = text.strip();
            if (text.startsWith("```")) {
                text = text.replaceAll("```[a-z]*\\n?", "").strip();
            }

            JsonNode variantsNode = objectMapper.readTree(text);
            List<CaptionVariantDto> variants = new ArrayList<>();
            for (JsonNode node : variantsNode) {
                variants.add(new CaptionVariantDto(
                        node.path("tone").asText(),
                        node.path("caption").asText()
                ));
            }
            if (variants.isEmpty()) {
                throw new ClaudeApiException("Claude returned an empty variants array.");
            }
            return variants;
        } catch (ClaudeApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to parse Claude response: {}", e.getMessage());
            throw new ClaudeApiException("Could not parse caption variants from Claude response.");
        }
    }

    public static class ClaudeApiException extends RuntimeException {
        public ClaudeApiException(String message) { super(message); }
    }
}
