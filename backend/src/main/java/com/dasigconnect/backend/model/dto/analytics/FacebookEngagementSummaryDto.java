package com.dasigconnect.backend.model.dto.analytics;

/** UC-3.4: aggregate Facebook engagement for published posts in scope. pendingCount = posts not yet synced (A2). */
public record FacebookEngagementSummaryDto(
        double averageReach,
        long totalReactions,
        long totalComments,
        long totalShares,
        long sampleSize,
        long pendingCount) {
}
