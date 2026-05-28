package com.dasigconnect.backend.model.dto.analytics;

public record ValidatorAnalyticsDto(
        long institutionSubmissionVolume,
        long pendingReviewCount,
        long inReviewCount,
        double averageValidationTurnaroundDays,
        long queueAgingOver24Hours) {
}
