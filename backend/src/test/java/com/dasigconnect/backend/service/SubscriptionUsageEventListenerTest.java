package com.dasigconnect.backend.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.event.InstitutionUsageChangedEvent;
import com.dasigconnect.backend.event.SubmissionPendingEvent;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubscriptionUsageEventType;
import com.dasigconnect.backend.model.entity.SubscriptionUsageUnit;
import com.dasigconnect.backend.repository.SubmissionMediaAssetRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubscriptionUsageEventListenerTest {

    @Test
    void pendingSubmission_recordsOneIdempotentSubmissionEvent() {
        SubscriptionUsageService usageService = mock(SubscriptionUsageService.class);
        SubmissionMediaAssetRepository mediaRepository = mock(SubmissionMediaAssetRepository.class);
        SubscriptionUsageEventListener listener = new SubscriptionUsageEventListener(usageService, mediaRepository);
        UUID submissionId = UUID.randomUUID();
        Instant submittedAt = Instant.parse("2026-10-18T00:00:00Z");
        Submission submission = mock(Submission.class);
        when(submission.getId()).thenReturn(submissionId);
        when(submission.getSubmittedAt()).thenReturn(submittedAt);
        when(mediaRepository.findMediaAssetsBySubmissionId(submissionId)).thenReturn(List.of());

        listener.onSubmissionPending(new SubmissionPendingEvent(submission));

        verify(usageService).recordForSubmission(
                submissionId,
                null,
                SubscriptionUsageEventType.SUBMISSION,
                1,
                SubscriptionUsageUnit.COUNT,
                "submission:" + submissionId,
                "SUBMISSION",
                submittedAt);
    }

    @Test
    void institutionDeletion_keepsHistoricalInstitutionIdentifier() {
        SubscriptionUsageService usageService = mock(SubscriptionUsageService.class);
        SubscriptionUsageEventListener listener = new SubscriptionUsageEventListener(
                usageService, mock(SubmissionMediaAssetRepository.class));
        UUID organizationId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();

        listener.onInstitutionChanged(new InstitutionUsageChangedEvent(
                organizationId, institutionId, -1));

        verify(usageService).recordForOrganization(
                eq(organizationId),
                eq(institutionId),
                eq(null),
                eq(SubscriptionUsageEventType.INSTITUTION),
                eq(-1L),
                eq(SubscriptionUsageUnit.COUNT),
                eq("institution:" + institutionId + ":deleted"),
                eq("INSTITUTION"),
                eq(institutionId),
                any(Instant.class));
    }
}
