package com.dasigconnect.backend.model.dto.analytics;

import java.time.Instant;
import java.util.UUID;

public record SubmissionAnalyticsRowDto(
        UUID submissionId,
        String eventTitle,
        Instant firstSubmittedAt,
        Instant publishedAt,
        String publicationState,
        double postingDelayDays,
        boolean complete,
        String contributorName,
        String institutionName,
        Long revisionCycles) {
}
