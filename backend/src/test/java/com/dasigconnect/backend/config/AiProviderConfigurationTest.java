package com.dasigconnect.backend.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AiProviderConfigurationTest {

    @Test
    void claudePrimaryUsesAnthropicKeyInsteadOfGeminiKey() {
        assertTrue(AiProviderConfiguration.isPrimaryProviderConfigured(
                "claude", "anthropic-key", ""));
        assertFalse(AiProviderConfiguration.isPrimaryProviderConfigured(
                "claude", "", "gemini-key"));
    }

    @Test
    void geminiPrimaryStillUsesGeminiKey() {
        assertTrue(AiProviderConfiguration.isPrimaryProviderConfigured(
                "gemini", "", "gemini-key"));
    }
}
