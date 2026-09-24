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
        List<String> compositionSignals
) {
    public MediaClassificationDto(String category, double confidence, String description, List<String> suggestedTags) {
        this(category, null, confidence, description, List.of(), List.of(), List.of(), List.of(), List.of(),
                suggestedTags, List.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of(),
                List.of(), null, null, List.of(), List.of());
    }
}
