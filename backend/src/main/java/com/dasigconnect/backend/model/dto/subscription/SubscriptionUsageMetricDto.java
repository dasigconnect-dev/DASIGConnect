package com.dasigconnect.backend.model.dto.subscription;

public record SubscriptionUsageMetricDto(
        String resource,
        long used,
        long limit,
        String unit) {
}
