package com.dasigconnect.backend.model.dto.analytics;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ContentInsightsDto(
        String range,
        Instant periodStart,
        Instant periodEnd,
        Instant lastUpdated,
        String scopeRole,
        boolean adminView,
        UUID selectedInstitutionId,
        boolean metricsReady,
        ContentInsightOverviewDto overview,
        List<ContentInsightDailyPointDto> dailyTrend,
        List<ContentTypeInsightDto> contentTypes,
        List<PostingWindowInsightDto> postingWindows,
        List<RecentContentInsightDto> recentPosts) {
}
