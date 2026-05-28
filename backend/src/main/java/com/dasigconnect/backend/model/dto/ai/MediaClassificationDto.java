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
        List<String> excludedCategories
) {
    public MediaClassificationDto(String category, double confidence, String description, List<String> suggestedTags) {
        this(category, null, confidence, description, List.of(), List.of(), List.of(), List.of(), List.of(),
                suggestedTags, List.of());
    }
}
