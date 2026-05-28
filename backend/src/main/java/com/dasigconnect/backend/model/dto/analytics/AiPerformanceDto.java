package com.dasigconnect.backend.model.dto.analytics;

public record AiPerformanceDto(
        long captionSuggestionEvents,
        long captionAcceptedEvents,
        double captionAcceptanceRate,
        long tagClassificationEvents,
        long tagCorrectionEvents,
        double tagCorrectionRate,
        long mediaRecommendationEvents,
        long mediaRecommendationRelevantEvents,
        double mediaRecommendationRelevanceRate,
        boolean insufficientData) {
}
