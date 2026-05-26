package com.dasigconnect.backend.model.dto.analytics;

public record OperationalHealthDto(
        long submissionsEnteredWorkflow,
        long validationDeadlineRisks,
        double validationTimeoutRiskRate,
        long overrideAuditEvents,
        double overrideRate,
        long publicationAttempts,
        long successfulPublicationAttempts,
        double publishingSuccessRate,
        long onTimePublications,
        double onTimePublicationRate,
        long administratorActions) {
}
