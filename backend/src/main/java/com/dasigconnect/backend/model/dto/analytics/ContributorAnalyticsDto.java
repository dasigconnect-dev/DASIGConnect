package com.dasigconnect.backend.model.dto.analytics;

public record ContributorAnalyticsDto(
        long submittedPosts,
        long publishedPosts,
        long revisionRequestCount,
        long rejectedOrNeedsRevisionCount,
        double rejectedOrNeedsRevisionRate) {
}
