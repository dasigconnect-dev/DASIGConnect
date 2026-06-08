package com.dasigconnect.backend.model.dto.analytics;

public record ContentTypeInsightDto(
        String contentType,
        long postCount,
        long views,
        long reach,
        long engagements,
        double engagementRate,
        double averageWatchTimeSeconds,
        long impressions,
        long postClicks) {
}
