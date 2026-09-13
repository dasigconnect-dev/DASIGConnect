package com.dasigconnect.backend.model.dto.analytics;

import java.time.Instant;

/**
 * Page-level Facebook insights for the selected range (admin only). Sourced live
 * from the {@code /{page-id}/insights} edge — aggregate, not per-post — so it
 * covers the reach gap left by Meta removing per-post reach from the Graph API.
 * Any field is 0 when its configured metric was unavailable.
 */
public record PagePerformanceDto(
        long reach,
        long engagements,
        long newFollows,
        long views,
        String pageId,
        Instant periodStart,
        Instant periodEnd) {
}
