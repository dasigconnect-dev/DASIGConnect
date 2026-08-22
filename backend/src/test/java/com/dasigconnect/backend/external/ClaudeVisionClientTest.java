package com.dasigconnect.backend.external;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeVisionClientTest {

    @Test
    void determineCaptionMaxTokens_withoutWordCount_usesDefaultBudget() {
        assertThat(ClaudeVisionClient.determineCaptionMaxTokens("Make it warm and inviting."))
                .isEqualTo(512);
    }

    @Test
    void determineCaptionMaxTokens_withFiveHundredWordPrompt_expandsBudget() {
        assertThat(ClaudeVisionClient.determineCaptionMaxTokens(
                "Create a caption of 500 words that tells students to participate in the hackathon."))
                .isGreaterThanOrEqualTo(1200);
    }

    @Test
    void extractRequestedWordCount_supportsHyphenatedWordCount() {
        assertThat(ClaudeVisionClient.extractRequestedWordCount("Write a 500-word caption for students."))
                .isEqualTo(500);
    }

    @Test
    void normalizeCaptionTone_defaultsUnknownToneToProfessional() {
        assertThat(ClaudeVisionClient.normalizeCaptionTone("invalid"))
                .isEqualTo("professional");
    }
}
