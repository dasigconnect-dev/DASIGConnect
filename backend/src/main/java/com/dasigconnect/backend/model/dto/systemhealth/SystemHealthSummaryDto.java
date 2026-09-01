package com.dasigconnect.backend.model.dto.systemhealth;

import java.time.Instant;
import java.util.List;

public record SystemHealthSummaryDto(
        Instant generatedAt,
        HealthStatus overallStatus,
        List<StorageMetricDto> storage,
        List<ExternalServiceHealthDto> externalServices,
        List<BackgroundJobHealthDto> backgroundJobs,
        List<OperationalMetricDto> operationalMetrics,
        int warningCount,
        int unhealthyCount,
        int unavailableCount) {
}
