package com.dasigconnect.backend.model.dto.analytics;

public record AiPerformanceDto(
        /** Captions generated. */
        long captionSuggestionEvents,
        /** Generated captions applied (as-is or then edited). */
        long captionAcceptedEvents,
        double captionAcceptanceRate,
        /** AI media suggestion sets shown in the picker. */
        long mediaRecommendationEvents,
        /** Times suggested media was added from the AI tab. */
        long mediaRecommendationRelevantEvents,
        double mediaRecommendationRelevanceRate,
        /** Submitted posts Album Auto-Match had proposed an album for. */
        long albumMatchEvents,
        /** Of those, posts that kept an album the AI proposed. */
        long albumMatchKeptEvents,
        double albumMatchKeptRate,
        long templateDraftsGenerated,
        long templateDraftsSaved,
        double templateDraftSaveRate,
        boolean insufficientData) {
}
