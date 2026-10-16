package com.dasigconnect.backend.model.dto.facebook;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record FacebookEngagementSnapshotDto(
        UUID id,
        UUID historicalPostId,
        String pageId,
        Instant fetchedAt,
        Long reactionsTotal,
        Map<String, Object> reactionBreakdown,
        Long commentsCount,
        Long sharesCount,
        Long reach,
        Long impressions,
        Long postClicks,
        Long linkClicks,
        Long photoViews,
        Long videoViews,
        Long followerCount,
        Map<String, Object> organicMetrics,
        Map<String, Object> paidMetrics,
        Map<String, Object> metricAvailability) {
}
