package com.dasigconnect.backend.model.dto.systemhealth;

public record OperationalMetricDto(
        String key,
        String label,
        HealthStatus status,
        double value,
        String unit,
        long sampleSize,
        String detail) {
}
