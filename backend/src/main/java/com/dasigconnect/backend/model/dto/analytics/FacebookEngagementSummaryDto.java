package com.dasigconnect.backend.model.dto.analytics;

/**
 * UC-3.4: aggregate Facebook engagement for published posts in scope.
 * pendingCount = posts not yet synced (A2). pageId is the connected Page id (or
 * null) so the admin UI can deep-link to Meta's own insights for reach, which is
 * no longer available per-post via the Graph API.
 */
public record FacebookEngagementSummaryDto(
        double averageReach,
        long totalReactions,
        long totalComments,
        long totalShares,
        long sampleSize,
        long pendingCount,
        String pageId) {
}
