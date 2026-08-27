package com.dasigconnect.backend.model.dto.systemhealth;

public record StorageMetricDto(
        String name,
        HealthStatus status,
        long usedBytes,
        long limitBytes,
        double usedPercent,
        double warningThresholdPercent,
        String detail) {
}
