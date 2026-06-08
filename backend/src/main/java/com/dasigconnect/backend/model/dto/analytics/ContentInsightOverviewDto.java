package com.dasigconnect.backend.model.dto.analytics;

import java.time.Instant;

public record ContentInsightOverviewDto(
        long syncedPosts,
        long totalViews,
        long uniqueViews,
        long totalEngagements,
        long reactions,
        long comments,
        long shares,
        long postClicks,
        long fifteenSecondViews,
        long sixtySecondViews,
        double averageReach,
        double averageImpressions,
        double totalWatchTimeSeconds,
        double averageWatchTimeSeconds,
        double engagementRate,
        Instant latestFetchedAt) {
}
