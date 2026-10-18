package com.dasigconnect.backend.external;

import com.dasigconnect.backend.model.dto.ai.CaptionVariantDto;
import com.dasigconnect.backend.model.dto.ai.MediaClassificationDto;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Delegating AI client that dynamically routes requests to the configured AI provider (Gemini, Claude, etc.).
 * Allows switching providers globally via app.ai.provider or environment variable AI_PROVIDER.
 */
@Service
@Primary
public class DelegatingAiVisionClient implements AiVisionClient {

    private final GeminiVisionClient geminiVisionClient;
    private final ClaudeVisionClient claudeVisionClient;
    private final String activeProvider;

    @Autowired
    public DelegatingAiVisionClient(
            GeminiVisionClient geminiVisionClient,
            ClaudeVisionClient claudeVisionClient,
            @Value("${app.ai.provider:gemini}") String activeProvider) {
        this.geminiVisionClient = geminiVisionClient;
        this.claudeVisionClient = claudeVisionClient;
        this.activeProvider = activeProvider == null ? "gemini" : activeProvider.trim().toLowerCase();
    }

    public AiVisionClient activeClient() {
        if ("claude".equalsIgnoreCase(activeProvider) || "anthropic".equalsIgnoreCase(activeProvider)) {
            return claudeVisionClient;
        }
        return geminiVisionClient;
    }

    @Override
    public List<CaptionVariantDto> generateCaptions(
            List<String> imageUrls, String mediaMetadata, String eventTitle,
            String eventDate, String institutionName, String existingCaption,
            String prompt, String targetTone) {
        return activeClient().generateCaptions(
                imageUrls, mediaMetadata, eventTitle, eventDate, institutionName, existingCaption, prompt, targetTone);
    }

    @Override
    public String generateText(String systemPrompt, String userPrompt) {
        return activeClient().generateText(systemPrompt, userPrompt);
    }

    @Override
    public String generateText(String systemPrompt, String userPrompt, int maxTokens) {
        return activeClient().generateText(systemPrompt, userPrompt, maxTokens);
    }

    @Override
    public MediaClassificationDto classifyMedia(List<String> imageUrls) {
        return activeClient().classifyMedia(imageUrls);
    }

    @Override
    public String providerName() {
        return activeClient().providerName();
    }
}

