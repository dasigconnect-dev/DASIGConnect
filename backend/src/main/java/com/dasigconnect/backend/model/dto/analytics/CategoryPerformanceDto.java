package com.dasigconnect.backend.model.dto.analytics;

public record CategoryPerformanceDto(
        String category,
        long postCount,
        double completenessRate) {
}
