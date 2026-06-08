package com.dasigconnect.backend.model.dto.analytics;

import java.time.Instant;
import java.util.UUID;

public record RecentContentInsightDto(
        UUID submissionId,
        String eventTitle,
        String institutionName,
        String category,
        Instant publishedAt,
        String contentType,
        long mediaCount,
        long views,
        long reach,
        long reactions,
        long comments,
        long shares,
        long engagements,
        double engagementRate,
        double averageWatchTimeSeconds,
        long fifteenSecondViews,
        long sixtySecondViews) {
}
