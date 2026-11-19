package com.dasigconnect.backend.config;

/** Shared provider-selection rules for background AI work. */
public final class AiProviderConfiguration {

    private AiProviderConfiguration() {
    }

    public static boolean isPrimaryProviderConfigured(
            String provider, String anthropicApiKey, String geminiApiKey) {
        String normalized = provider == null ? "claude" : provider.trim().toLowerCase();
        if ("claude".equals(normalized) || "anthropic".equals(normalized)) {
            return anthropicApiKey != null && !anthropicApiKey.isBlank();
        }
        return geminiApiKey != null && !geminiApiKey.isBlank();
    }
}
