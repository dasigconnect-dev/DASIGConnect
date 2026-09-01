package com.dasigconnect.backend.model.dto.engagement;

import java.time.Instant;
import java.util.List;

public record EngagementRecommendationDto(
        boolean available,
        String source,
        String notice,
        String timezone,
        int sampleSize,
        List<RecommendedSlotDto> slots) {

    public record RecommendedSlotDto(
            Instant scheduledAt,
            String windowLabel,
            double score,
            List<String> warnings) {}
}
