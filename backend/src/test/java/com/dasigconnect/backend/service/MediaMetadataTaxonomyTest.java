package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dasigconnect.backend.model.dto.ai.MediaClassificationDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class MediaMetadataTaxonomyTest {

    @Test
    void normalize_supportsEveryBroadAssetType() {
        for (String assetType : MediaMetadataTaxonomy.ASSET_TYPES) {
            assertThat(MediaMetadataTaxonomy.normalize(metadata(assetType, "other", 0.8))
                    .metadataAssetType()).isEqualTo(assetType);
        }
    }

    @Test
    void normalize_supportsRepresentativeDasigSubtypes() {
        assertThat(List.of(
                "group_photo", "speaker_photo", "workshop_activity_photo", "event_poster",
                "informational_infographic", "birthday_greeting", "program_announcement",
                "presentation_slide", "online_meeting_screenshot", "certificate_or_document",
                "photo_collage"))
                .allSatisfy(subtype -> assertThat(MediaMetadataTaxonomy
                        .normalize(metadata("photograph", subtype, 0.8)).assetSubtype())
                        .isEqualTo(subtype));
    }

    @Test
    void normalize_boundsTextAndCaseInsensitiveDuplicates() {
        MediaClassificationDto raw = metadata(" Photograph ", "Group Photo", 0.8);
        raw = copyWithTopics(raw, List.of("Innovation", " innovation ", "COLLABORATION"));

        MediaClassificationDto result = MediaMetadataTaxonomy.normalize(raw);

        assertThat(result.metadataAssetType()).isEqualTo("photograph");
        assertThat(result.assetSubtype()).isEqualTo("group_photo");
        assertThat(result.category()).isEqualTo("Event");
        assertThat(result.topics()).containsExactly("innovation", "collaboration");
        assertThat(result.visibleObjects()).containsExactly("laptop");
        assertThat(result.metadataSchemaVersion()).isEqualTo(MediaMetadataTaxonomy.SCHEMA_VERSION);
    }

    @Test
    void normalize_rejectsInvalidConfidence() {
        assertThatThrownBy(() -> MediaMetadataTaxonomy.normalize(
                metadata("photograph", "group_photo", 1.1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("classification_confidence");
    }

    @Test
    void normalize_reconcilesUnambiguousSubtypeWithBroadType() {
        assertThat(MediaMetadataTaxonomy.normalize(
                metadata("photograph", "event_poster", 0.8)).metadataAssetType())
                .isEqualTo("graphic_design");
        assertThat(MediaMetadataTaxonomy.normalize(
                metadata("graphic_design", "group_photo", 0.8)).metadataAssetType())
                .isEqualTo("photograph");
    }

    private static MediaClassificationDto metadata(String assetType, String subtype, double confidence) {
        return new MediaClassificationDto(
                "event", "", confidence, "  People collaborate around laptops.  ",
                List.of("laptop", "Laptop"), List.of("participants"),
                List.of("documentary photography"), List.of("Blue", "blue"),
                List.of("event documentation"), List.of("workshop", "Workshop"),
                List.of(), List.of("training room"), List.of("using laptops"), "",
                List.of(), List.of(), List.of(), List.of(), List.of(), "unknown", "",
                List.of(), List.of(), assetType, subtype, "event_documentation",
                "participants with laptops", "training room", "present", List.of(), "",
                List.of(), "none", 0.0, null, null);
    }

    private static MediaClassificationDto copyWithTopics(
            MediaClassificationDto source, List<String> topics) {
        return new MediaClassificationDto(
                source.category(), source.assetType(), source.confidence(), source.description(),
                source.visibleObjects(), source.specificSubjects(), source.visualStyle(),
                source.dominantColors(), source.possibleUseCases(), source.suggestedTags(),
                source.excludedCategories(), source.observedScenes(), source.observedActivities(),
                source.peopleCountRange(), source.equipmentSignals(), source.recognitionSignals(),
                source.ocrText(), source.visibleDates(), source.eventHypotheses(),
                source.temporalClassification(), source.possibleExpiration(),
                source.visualQualitySignals(), source.compositionSignals(),
                source.metadataAssetType(), source.assetSubtype(), source.communicationPurpose(),
                source.primarySubject(), source.setting(), source.peoplePresence(), topics,
                source.detectedTitle(), source.textLanguages(), source.textDensity(),
                source.ocrConfidence(), source.metadataSchemaVersion(), source.metadataPromptVersion());
    }
}
