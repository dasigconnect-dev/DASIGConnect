package com.dasigconnect.backend.external;

import com.dasigconnect.backend.model.dto.ai.CaptionVariantDto;
import com.dasigconnect.backend.model.dto.ai.MediaClassificationDto;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Delegating AI client that dynamically routes requests to the configured AI provider (Gemini, Claude, etc.).
 * Allows switching providers globally via app.ai.provider or environment variable AI_PROVIDER.
 * Automatically falls back to the alternate provider if the primary provider fails (e.g. rate limit, quota exhaustion, 503).
 */
@Service
@Primary
public class DelegatingAiVisionClient implements AiVisionClient {

    private static final Logger log = LoggerFactory.getLogger(DelegatingAiVisionClient.class);

    private final GeminiVisionClient geminiVisionClient;
    private final ClaudeVisionClient claudeVisionClient;
    private final String activeProvider;

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

    public AiVisionClient fallbackClient() {
        if ("claude".equalsIgnoreCase(activeProvider) || "anthropic".equalsIgnoreCase(activeProvider)) {
            return geminiVisionClient;
        }
        return claudeVisionClient;
    }

    @Override
    public List<CaptionVariantDto> generateCaptions(
            List<String> imageUrls, String mediaMetadata, String eventTitle,
            String eventDate, String institutionName, String existingCaption,
            String prompt, String targetTone) {
        AiVisionClient primary = activeClient();
        try {
            return primary.generateCaptions(
                    imageUrls, mediaMetadata, eventTitle, eventDate, institutionName, existingCaption, prompt, targetTone);
        } catch (AiApiException e) {
            AiVisionClient fallback = fallbackClient();
            if (fallback != null && fallback != primary) {
                log.warn("Primary AI provider ({}) failed: {}. Falling back to provider ({})",
                        primary.providerName(), e.getMessage(), fallback.providerName());
                try {
                    return fallback.generateCaptions(
                            imageUrls, mediaMetadata, eventTitle, eventDate, institutionName, existingCaption, prompt, targetTone);
                } catch (Exception fallbackErr) {
                    log.error("Fallback AI provider ({}) also failed: {}", fallback.providerName(), fallbackErr.getMessage());
                }
            }
            throw e;
        }
    }

    @Override
    public String generateText(String systemPrompt, String userPrompt) {
        AiVisionClient primary = activeClient();
        try {
            return primary.generateText(systemPrompt, userPrompt);
        } catch (AiApiException e) {
            AiVisionClient fallback = fallbackClient();
            if (fallback != null && fallback != primary) {
                log.warn("Primary AI provider ({}) failed: {}. Falling back to provider ({})",
                        primary.providerName(), e.getMessage(), fallback.providerName());
                try {
                    return fallback.generateText(systemPrompt, userPrompt);
                } catch (Exception fallbackErr) {
                    log.error("Fallback AI provider ({}) also failed: {}", fallback.providerName(), fallbackErr.getMessage());
                }
            }
            throw e;
        }
    }

    @Override
    public String generateText(String systemPrompt, String userPrompt, int maxTokens) {
        AiVisionClient primary = activeClient();
        try {
            return primary.generateText(systemPrompt, userPrompt, maxTokens);
        } catch (AiApiException e) {
            AiVisionClient fallback = fallbackClient();
            if (fallback != null && fallback != primary) {
                log.warn("Primary AI provider ({}) failed: {}. Falling back to provider ({})",
                        primary.providerName(), e.getMessage(), fallback.providerName());
                try {
                    return fallback.generateText(systemPrompt, userPrompt, maxTokens);
                } catch (Exception fallbackErr) {
                    log.error("Fallback AI provider ({}) also failed: {}", fallback.providerName(), fallbackErr.getMessage());
                }
            }
            throw e;
        }
    }

    @Override
    public MediaClassificationDto classifyMedia(List<String> imageUrls) {
        AiVisionClient primary = activeClient();
        try {
            return primary.classifyMedia(imageUrls);
        } catch (AiApiException e) {
            AiVisionClient fallback = fallbackClient();
            if (fallback != null && fallback != primary) {
                log.warn("Primary AI provider ({}) failed: {}. Falling back to provider ({})",
                        primary.providerName(), e.getMessage(), fallback.providerName());
                try {
                    return fallback.classifyMedia(imageUrls);
                } catch (Exception fallbackErr) {
                    log.error("Fallback AI provider ({}) also failed: {}", fallback.providerName(), fallbackErr.getMessage());
                }
            }
            throw e;
        }
    }

    @Override
    public String providerName() {
        return activeClient().providerName();
    }
}

