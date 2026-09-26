package com.dasigconnect.backend.model.dto.systemhealth;

public record MediaAiStageMetricDto(
        String stage,
        long sampleSize,
        double averageDurationMs,
        double p95DurationMs,
        double failureRatePercent,
        double retryRatePercent,
        long distinctAssets,
        long providerCalls,
        double providerCallsPerAsset,
        long duplicateProviderCallsAvoided) {
}
