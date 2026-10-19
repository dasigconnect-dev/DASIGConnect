package com.dasigconnect.backend.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.dasigconnect.backend.service.MediaMetadataTaxonomy;
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
    void parseClassificationJson_mapsImageOnlyContract() {
        var result = client("test-key").parseClassificationJson(validJson(
                "photograph", "workshop_activity_photo", "event_documentation"));

        assertThat(result.category()).isEqualTo("Technology");
        assertThat(result.metadataAssetType()).isEqualTo("photograph");
        assertThat(result.assetSubtype()).isEqualTo("workshop_activity_photo");
        assertThat(result.assetType()).isEqualTo("Event Photo");
        assertThat(result.description()).isEqualTo(
                "Several people work at tables with laptops while a presentation is displayed.");
        assertThat(result.primarySubject()).isEqualTo("people working at tables with laptops");
        assertThat(result.topics()).containsExactly("innovation", "collaboration");
        assertThat(result.ocrText()).containsExactly("INNOVATION WORKSHOP", "OCTOBER 19, 2026");
        assertThat(result.textLanguages()).containsExactly("english");
        assertThat(result.ocrConfidence()).isEqualTo(0.91);
        assertThat(result.metadataSchemaVersion()).isEqualTo(MediaMetadataTaxonomy.SCHEMA_VERSION);
        assertThat(result.metadataPromptVersion()).isEqualTo(MediaMetadataTaxonomy.PROMPT_VERSION);
    }

    @Test
    void parseClassificationJson_normalizesUnsupportedTaxonomyAndDuplicateTerms() {
        String json = validJson("made_up_type", "made_up_subtype", "made_up_purpose")
                .replace("\"innovation\", \"collaboration\"",
                        "\"Innovation\", \"innovation\", \"COLLABORATION\"")
                .replace("\"laptop workshop\", \"team collaboration\"",
                        "\"Laptop Workshop\", \"laptop workshop\", \"team collaboration\"");

        var result = client("test-key").parseClassificationJson(json);

        assertThat(result.metadataAssetType()).isEqualTo("unknown");
        assertThat(result.assetSubtype()).isEqualTo("unknown");
        assertThat(result.communicationPurpose()).isEqualTo("unknown");
        assertThat(result.topics()).containsExactly("innovation", "collaboration");
        assertThat(result.suggestedTags()).containsExactly("laptop workshop", "team collaboration");
    }

    @Test
    void parseClassificationJson_noReadableText_usesExplicitEmptyOcr() {
        String json = validJson("photograph", "group_photo", "event_documentation")
                .replace("[\"INNOVATION WORKSHOP\", \"OCTOBER 19, 2026\"]", "[]")
                .replace("\"detected_title\": \"INNOVATION WORKSHOP\"", "\"detected_title\": \"\"")
                .replace("\"text_language\": [\"English\"]", "\"text_language\": []")
                .replace("\"text_density\": \"medium\"", "\"text_density\": \"none\"")
                .replace("\"ocr_confidence\": 0.91", "\"ocr_confidence\": 0.72");

        var result = client("test-key").parseClassificationJson(json);

        assertThat(result.ocrText()).isEmpty();
        assertThat(result.detectedTitle()).isEmpty();
        assertThat(result.textLanguages()).isEmpty();
        assertThat(result.textDensity()).isEqualTo("none");
        assertThat(result.ocrConfidence()).isZero();
    }

    @Test
    void parseClassificationJson_rejectsMalformedOrUnsafeConfidence() {
        assertThatThrownBy(() -> client("test-key").parseClassificationJson("not-json"))
                .isInstanceOf(GeminiVisionClient.GeminiApiException.class);
        assertThatThrownBy(() -> client("test-key").parseClassificationJson(
                validJson("photograph", "group_photo", "event_documentation")
                        .replace("\"classification_confidence\": 0.94",
                                "\"classification_confidence\": null")))
                .isInstanceOf(GeminiVisionClient.GeminiApiException.class);
        assertThatThrownBy(() -> client("test-key").parseClassificationJson(
                validJson("photograph", "group_photo", "event_documentation")
                        .replace("\"ocr_confidence\": 0.91", "\"ocr_confidence\": 1.5")))
                .isInstanceOf(GeminiVisionClient.GeminiApiException.class);
        assertThatThrownBy(() -> client("test-key").parseClassificationJson(
                validJson("photograph", "group_photo", "event_documentation")
                        .replace("\"topics\": [\"innovation\", \"collaboration\"]",
                                "\"topics\": \"innovation\"")))
                .isInstanceOf(GeminiVisionClient.GeminiApiException.class);
    }

    @Test
    void classifyMedia_withoutApiKey_failsBeforeNetworkCall() {
        assertThatThrownBy(() -> client("").classifyMedia(List.of("https://example.com/a.jpg")))
                .isInstanceOf(GeminiVisionClient.GeminiApiException.class)
                .hasMessageContaining("not configured");
    }

    private static String validJson(String assetType, String subtype, String purpose) {
        return """
                {
                  "content_category": "Technology",
                  "asset_type": "%s",
                  "asset_subtype": "%s",
                  "communication_purpose": "%s",
                  "caption": "Several people work at tables with laptops while a presentation is displayed.",
                  "primary_subject": "people working at tables with laptops",
                  "setting": "indoor training room",
                  "people_presence": "present",
                  "visible_objects": ["laptops", "tables", "presentation screen"],
                  "activities": ["working on laptops", "viewing a presentation"],
                  "visual_style": ["documentary photography"],
                  "dominant_colors": ["blue", "white"],
                  "topics": ["innovation", "collaboration"],
                  "search_terms": ["laptop workshop", "team collaboration"],
                  "ocr_text": ["INNOVATION WORKSHOP", "OCTOBER 19, 2026"],
                  "detected_dates": ["OCTOBER 19, 2026"],
                  "text_language": ["English"],
                  "detected_title": "INNOVATION WORKSHOP",
                  "text_density": "medium",
                  "ocr_confidence": 0.91,
                  "classification_confidence": 0.94
                }
                """.formatted(assetType, subtype, purpose);
    }
}
