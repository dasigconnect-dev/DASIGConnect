package com.dasigconnect.backend.model.dto.analytics;

public record PostingWindowInsightDto(
        String dayOfWeek,
        int hourOfDay,
        long postCount,
        double averageViews,
        double averageEngagements,
        double averageWatchTimeSeconds) {
}
