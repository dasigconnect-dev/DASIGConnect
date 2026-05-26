package com.dasigconnect.backend.model.dto.analytics;

import java.time.Instant;
import java.util.List;

public record AnalyticsSummaryDto(
        String range,
        Instant periodStart,
        Instant periodEnd,
        KpiMetricDto averagePostingDelay,
        KpiMetricDto contentCompleteness,
        KpiMetricDto totalPostsPublished,
        List<InstitutionPostsDto> postsByInstitution,
        AiPerformanceDto aiPerformance,
        OperationalHealthDto operationalHealth) {
}
