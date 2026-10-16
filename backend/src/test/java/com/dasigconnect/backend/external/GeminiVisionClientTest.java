package com.dasigconnect.backend.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeminiVisionClientTest {

    private GeminiVisionClient client(String apiKey) {
        return new GeminiVisionClient(apiKey, "gemini-3.8-flash",
                mock(ClaudeVisionClient.class), mock(HttpClient.class), new ObjectMapper());
    }

    @Test
    void parseClassificationJson_mapsStructuredVisualMetadata() {
        String json = """
                {
                  "primary_category": "Food",
                  "asset_type": "Food Photo",
                  "ai_caption": "A cooked octopus dish served with green vegetables.",
                  "visible_objects": ["octopus", "plate", "green vegetables"],
                  "specific_subjects": ["cooked octopus"],
                  "visual_style": ["close-up food photography"],
                  "dominant_colors": ["red", "green"],
                  "possible_use_cases": ["food feature"],
                  "ai_tags": ["octopus dish", "seafood", "cooked food"],
                  "excluded_categories": ["Technology"],
                  "observed_scenes": ["plated meal"],
                  "observed_activities": [],
                  "people_count_range": "0",
                  "equipment_signals": [],
                  "recognition_signals": [],
                  "ocr_text": [],
                  "visible_dates": [],
                  "event_hypotheses": [],
                  "temporal_classification": "evergreen",
                  "possible_expiration": "",
                  "visual_quality_signals": ["sharp focus"],
                  "composition_signals": ["subject centered"],
                  "confidence": 0.94
                }
                """;

        var result = client("test-key").parseClassificationJson(json);

        assertThat(result.category()).isEqualTo("Food");
        assertThat(result.assetType()).isEqualTo("Food Photo");
        assertThat(result.description()).contains("octopus dish");
        assertThat(result.visibleObjects()).containsExactly("octopus", "plate", "green vegetables");
        assertThat(result.suggestedTags()).contains("seafood", "cooked food");
        assertThat(result.confidence()).isEqualTo(0.94);
    }

    @Test
    void classifyMedia_withoutApiKey_failsBeforeNetworkCall() {
        assertThatThrownBy(() -> client("").classifyMedia(List.of("https://example.com/a.jpg")))
                .isInstanceOf(GeminiVisionClient.GeminiApiException.class)
                .hasMessageContaining("not configured");
    }
}
