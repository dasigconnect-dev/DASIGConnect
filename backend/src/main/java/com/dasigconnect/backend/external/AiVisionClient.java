package com.dasigconnect.backend.external;

import com.dasigconnect.backend.model.dto.ai.CaptionVariantDto;
import com.dasigconnect.backend.model.dto.ai.MediaClassificationDto;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provider-agnostic client interface for AI vision and text operations.
 * Allows seamless switching between AI models (Gemini, Claude, etc.) without altering business logic.
 */
public interface AiVisionClient {

    int MAX_REQUESTED_CAPTION_WORDS = 2000;
    Pattern WORD_COUNT_PATTERN = Pattern.compile(
            "\\b(?:around\\s+|about\\s+|approximately\\s+|approx\\.?\\s+|at\\s+least\\s+|up\\s+to\\s+|minimum\\s+of\\s+|maximum\\s+of\\s+|max\\s+of\\s+)?(\\d{2,4})\\s*(?:-|\\s)?words?\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * Generates one selected-tone caption for a social media post.
     */
    List<CaptionVariantDto> generateCaptions(
            List<String> imageUrls,
            String mediaMetadata,
            String eventTitle,
            String eventDate,
            String institutionName,
            String existingCaption,
            String prompt,
            String targetTone);

    /**
     * Plain-text completion for system alerts / suggestions / proofreading / templates.
     */
    String generateText(String systemPrompt, String userPrompt);

    /**
     * Plain-text completion with explicit max output tokens budget.
     */
    String generateText(String systemPrompt, String userPrompt, int maxTokens);

    /**
     * Classifies media into taxonomy, tags, and bounded metadata.
     */
    default MediaClassificationDto classifyMedia(List<String> imageUrls) {
        throw new UnsupportedOperationException("Media classification is not implemented by this AI provider.");
    }

    String providerName();

    static int extractRequestedWordCount(String prompt) {
        if (prompt == null || prompt.isBlank()) return 0;
        Matcher matcher = WORD_COUNT_PATTERN.matcher(prompt);
        int largest = 0;
        while (matcher.find()) {
            largest = Math.max(largest, Integer.parseInt(matcher.group(1)));
        }
        return largest;
    }

    static String normalizeCaptionTone(String tone) {
        if (tone == null || tone.isBlank()) return "professional";
        String normalized = tone.trim().toLowerCase();
        return switch (normalized) {
            case "community", "energetic" -> normalized;
            default -> "professional";
        };
    }
}

