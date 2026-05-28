package com.dasigconnect.backend.model.dto.analytics;

import java.util.List;

public record KpiMetricDto(
        String id,
        String label,
        double value,
        String unit,
        long sampleSize,
        Double target,
        boolean targetMet,
        Double deltaPercent,
        List<Double> sparkline,
        String secondaryLabel,
        Long secondaryValue) {
}
