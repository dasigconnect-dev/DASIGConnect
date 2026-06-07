package com.dasigconnect.backend.model.dto.analytics;

import java.time.Instant;

public record FacebookEngagementDto(
        long syncedPosts,
        long reactions,
        long comments,
        long shares,
        long reachSampleSize,
        double averageReach,
        double averageImpressions,
        Instant latestFetchedAt) {
}
