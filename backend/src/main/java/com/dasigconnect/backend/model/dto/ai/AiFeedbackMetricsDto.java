package com.dasigconnect.backend.model.dto.ai;

import java.util.List;

/**
 * UC-4.6 precision/acceptance metrics across the AI surfaces. {@code acceptanceRate} and
 * {@code thumbsRate} are null when the denominator is zero (report n, never a ratio from no data).
 */
public record AiFeedbackMetricsDto(boolean networkScope, List<Surface> surfaces) {

    public record Surface(
            String surface,
            long total,
            long applied,
            long dismissed,
            long thumbsUp,
            long thumbsDown,
            Double acceptanceRate,
            Double thumbsRate) {
    }
}
