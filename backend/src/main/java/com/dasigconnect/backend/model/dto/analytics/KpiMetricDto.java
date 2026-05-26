package com.dasigconnect.backend.model.dto.analytics;

public record KpiMetricDto(
        String id,
        String label,
        double value,
        String unit,
        long sampleSize,
        Double target,
        boolean targetMet) {
}
