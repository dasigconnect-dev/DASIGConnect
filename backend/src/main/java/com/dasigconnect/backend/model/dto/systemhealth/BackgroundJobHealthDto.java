package com.dasigconnect.backend.model.dto.systemhealth;

import java.time.Instant;

public record BackgroundJobHealthDto(
        String jobName,
        HealthStatus status,
        Instant lastStartedAt,
        Instant lastSuccessAt,
        Instant lastFailureAt,
        Long lastDurationMs,
        String lastError,
        String detail) {
}
