package com.dasigconnect.backend.model.dto.ai;

import java.util.List;

public record MediaClassificationDto(
        String category,
        String assetType,
        double confidence,
        String description,
        List<String> visibleObjects,
        List<String> specificSubjects,
        List<String> visualStyle,
        List<String> dominantColors,
        List<String> possibleUseCases,
        List<String> suggestedTags,
        List<String> excludedCategories,
        List<String> observedScenes,
        List<String> observedActivities,
        String peopleCountRange,
        List<String> equipmentSignals,
        List<String> recognitionSignals,
        List<String> ocrText,
        List<String> visibleDates,
        List<EventHypothesisDto> eventHypotheses,
        String temporalClassification,
        String possibleExpiration,
        List<String> visualQualitySignals,
        List<String> compositionSignals,
        String metadataAssetType,
        String assetSubtype,
        String communicationPurpose,
        String primarySubject,
        String setting,
        String peoplePresence,
        List<String> topics,
        String detectedTitle,
        List<String> textLanguages,
        String textDensity,
        Double ocrConfidence,
        String metadataSchemaVersion,
        String metadataPromptVersion
) {
    /** Backward-compatible constructor for the retained legacy classifier. */
    public MediaClassificationDto(
            String category,
            String assetType,
            double confidence,
            String description,
            List<String> visibleObjects,
            List<String> specificSubjects,
            List<String> visualStyle,
            List<String> dominantColors,
            List<String> possibleUseCases,
            List<String> suggestedTags,
            List<String> excludedCategories,
            List<String> observedScenes,
            List<String> observedActivities,
            String peopleCountRange,
            List<String> equipmentSignals,
            List<String> recognitionSignals,
            List<String> ocrText,
            List<String> visibleDates,
            List<EventHypothesisDto> eventHypotheses,
            String temporalClassification,
            String possibleExpiration,
            List<String> visualQualitySignals,
            List<String> compositionSignals) {
        this(category, assetType, confidence, description, visibleObjects, specificSubjects,
                visualStyle, dominantColors, possibleUseCases, suggestedTags, excludedCategories,
                observedScenes, observedActivities, peopleCountRange, equipmentSignals,
                recognitionSignals, ocrText, visibleDates, eventHypotheses,
                temporalClassification, possibleExpiration, visualQualitySignals,
                compositionSignals, "unknown", "unknown", "unknown", "", "", "uncertain",
                List.of(), "", List.of(), "none", null, null, null);
    }

    public MediaClassificationDto(String category, double confidence, String description, List<String> suggestedTags) {
        this(category, null, confidence, description, List.of(), List.of(), List.of(), List.of(), List.of(),
                suggestedTags, List.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of(),
                List.of(), null, null, List.of(), List.of(), "unknown", "unknown", "unknown", "", "",
                "uncertain", List.of(), "", List.of(), "none", null, null, null);
    }
}
