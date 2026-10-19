package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.ai.EventHypothesisDto;
import com.dasigconnect.backend.model.dto.ai.MediaClassificationDto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Canonical validation and normalization for image-only media metadata. */
public final class MediaMetadataTaxonomy {

    public static final String SCHEMA_VERSION = "media-metadata-v2";
    public static final String PROMPT_VERSION = "gemini-image-only-v2";

    public static final Set<String> ASSET_TYPES = Set.of(
            "photograph", "graphic_design", "screenshot", "document",
            "composite_image", "unknown");

    public static final Set<String> ASSET_SUBTYPES = Set.of(
            "group_photo", "speaker_photo", "workshop_activity_photo", "event_photo",
            "product_photo", "food_photo", "lab_activity_photo", "portrait",
            "landscape_photo", "building_photo", "artwork_photo", "event_poster",
            "informational_infographic", "birthday_greeting", "program_announcement",
            "presentation_slide", "online_meeting_screenshot", "application_screenshot",
            "certificate_or_document", "photo_collage", "other", "unknown");

    public static final Set<String> COMMUNICATION_PURPOSES = Set.of(
            "event_documentation", "event_promotion", "program_promotion", "recognition",
            "public_information", "meeting_documentation", "educational_information",
            "project_showcase", "general_documentation", "unknown");

    public static final Set<String> PEOPLE_PRESENCE = Set.of("present", "absent", "uncertain");
    public static final Set<String> TEXT_DENSITIES = Set.of("none", "low", "medium", "high");
    public static final Set<String> CONTENT_CATEGORIES = Set.of(
            "Food", "People", "Event", "Technology", "Research", "Education",
            "Sports", "Culture", "Nature", "Document", "Product", "Architecture",
            "Artwork", "Other");

    private MediaMetadataTaxonomy() {}

    public static MediaClassificationDto normalize(MediaClassificationDto input) {
        if (input == null) throw new IllegalArgumentException("Media metadata is required.");
        double confidence = confidence(input.confidence(), "classification_confidence");
        Double ocrConfidence = input.ocrConfidence() == null
                ? 0.0 : confidence(input.ocrConfidence(), "ocr_confidence");
        String broadType = enumValue(input.metadataAssetType(), ASSET_TYPES, "unknown");
        String subtype = enumValue(input.assetSubtype(), ASSET_SUBTYPES, "unknown");
        broadType = consistentBroadType(broadType, subtype);
        String purpose = enumValue(input.communicationPurpose(), COMMUNICATION_PURPOSES, "unknown");
        String people = enumValue(input.peoplePresence(), PEOPLE_PRESENCE, "uncertain");
        String density = enumValue(input.textDensity(), TEXT_DENSITIES, "none");
        String category = canonicalContentCategory(input.category());
        List<String> ocrText = values(input.ocrText(), 30, 200, false);
        if (ocrText.isEmpty() && "none".equals(density)) ocrConfidence = 0.0;

        return new MediaClassificationDto(
                category,
                legacyAssetType(broadType, subtype),
                confidence,
                text(input.description(), 500),
                values(input.visibleObjects(), 20, 60, false),
                values(input.specificSubjects(), 10, 100, false),
                values(input.visualStyle(), 8, 60, false),
                values(input.dominantColors(), 6, 30, true),
                values(input.possibleUseCases(), 8, 60, true),
                values(input.suggestedTags(), 15, 60, true),
                values(input.excludedCategories(), 8, 80, false),
                values(input.observedScenes(), 8, 100, false),
                values(input.observedActivities(), 12, 100, false),
                text(input.peopleCountRange(), 30),
                values(input.equipmentSignals(), 10, 80, false),
                values(input.recognitionSignals(), 10, 80, false),
                ocrText,
                values(input.visibleDates(), 10, 60, false),
                hypotheses(input.eventHypotheses()),
                text(input.temporalClassification(), 30),
                text(input.possibleExpiration(), 80),
                values(input.visualQualitySignals(), 10, 80, false),
                values(input.compositionSignals(), 10, 80, false),
                broadType,
                subtype,
                purpose,
                text(input.primarySubject(), 255),
                text(input.setting(), 160),
                people,
                values(input.topics(), 12, 60, true),
                text(input.detectedTitle(), 255),
                values(input.textLanguages(), 5, 40, true),
                density,
                ocrConfidence,
                SCHEMA_VERSION,
                PROMPT_VERSION);
    }

    static String legacyAssetType(String broadType, String subtype) {
        return switch (subtype) {
            case "group_photo" -> "Group Photo";
            case "speaker_photo", "portrait" -> "Portrait";
            case "workshop_activity_photo", "event_photo" -> "Event Photo";
            case "product_photo" -> "Product Photo";
            case "food_photo" -> "Food Photo";
            case "lab_activity_photo" -> "Lab Photo";
            case "landscape_photo" -> "Landscape";
            case "building_photo" -> "Building Photo";
            case "artwork_photo" -> "Artwork Photo";
            case "event_poster", "birthday_greeting", "program_announcement" -> "Poster";
            case "informational_infographic" -> "Infographic";
            case "presentation_slide" -> "Project Presentation";
            case "online_meeting_screenshot", "application_screenshot" -> "Screenshot";
            case "certificate_or_document" -> "Document";
            default -> switch (broadType) {
                case "screenshot" -> "Screenshot";
                case "document" -> "Document";
                default -> "Other";
            };
        };
    }

    private static String consistentBroadType(String broadType, String subtype) {
        return switch (subtype) {
            case "group_photo", "speaker_photo", "workshop_activity_photo", "event_photo",
                    "product_photo", "food_photo", "lab_activity_photo", "portrait",
                    "landscape_photo", "building_photo", "artwork_photo" -> "photograph";
            case "event_poster", "informational_infographic", "birthday_greeting",
                    "program_announcement" -> "graphic_design";
            case "online_meeting_screenshot", "application_screenshot" -> "screenshot";
            case "certificate_or_document" -> "document";
            case "photo_collage" -> "composite_image";
            default -> broadType;
        };
    }

    private static double confidence(double value, String field) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(field + " must be between 0 and 1.");
        }
        return value;
    }

    private static String enumValue(String value, Set<String> allowed, String fallback) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
        return allowed.contains(normalized) ? normalized : fallback;
    }

    private static String canonicalContentCategory(String value) {
        if (value == null) return "Other";
        return CONTENT_CATEGORIES.stream()
                .filter(category -> category.equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElse("Other");
    }

    private static String text(String value, int maxLength) {
        if (value == null) return "";
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength).trim();
    }

    private static List<String> values(
            List<String> source, int maxItems, int maxLength, boolean lowerCase) {
        if (source == null || source.isEmpty()) return List.of();
        Map<String, String> unique = new LinkedHashMap<>();
        for (String raw : source) {
            String value = text(raw, maxLength);
            if (value.isBlank()) continue;
            if (lowerCase) value = value.toLowerCase(Locale.ROOT);
            unique.putIfAbsent(value.toLowerCase(Locale.ROOT), value);
            if (unique.size() >= maxItems) break;
        }
        return List.copyOf(unique.values());
    }

    private static List<EventHypothesisDto> hypotheses(List<EventHypothesisDto> source) {
        if (source == null || source.isEmpty()) return List.of();
        List<EventHypothesisDto> normalized = new ArrayList<>();
        for (EventHypothesisDto item : source) {
            if (item == null) continue;
            String eventType = text(item.eventType(), 80);
            if (eventType.isBlank()) continue;
            normalized.add(new EventHypothesisDto(
                    eventType, confidence(item.confidence(), "event_hypothesis confidence"),
                    values(item.evidence(), 6, 100, false)));
            if (normalized.size() >= 5) break;
        }
        return List.copyOf(normalized);
    }
}
