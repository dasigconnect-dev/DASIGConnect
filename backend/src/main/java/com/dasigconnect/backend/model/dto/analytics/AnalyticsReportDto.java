package com.dasigconnect.backend.model.dto.analytics;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AnalyticsReportDto(
        String metric,
        String range,
        Instant periodStart,
        Instant periodEnd,
        List<DailyAnalyticsPointDto> dailyBreakdown,
        List<SubmissionAnalyticsRowDto> submissions,
        List<Map<String, Object>> aggregateRows) {
}
