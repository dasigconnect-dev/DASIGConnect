package com.dasigconnect.backend.schedule;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.service.FacebookPublisherService;
import com.dasigconnect.backend.service.PublishingQueryService;

@ExtendWith(MockitoExtension.class)
class PublishingSchedulerJobTest {

    @Mock
    private PublishingQueryService publishingQueryService;

    @Mock
    private FacebookPublisherService facebookPublisherService;

    @Test
    void run_skipsSubmissionWhenClaimAlreadyTaken() {
        PublishingSchedulerJob job =
                new PublishingSchedulerJob(publishingQueryService, facebookPublisherService);
        Submission due = submission(UUID.randomUUID(), SubmissionStatus.scheduled);

        when(facebookPublisherService.isConfigured()).thenReturn(true);
        when(publishingQueryService.loadDueSubmissions(
                org.mockito.ArgumentMatchers.any(Instant.class),
                org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(List.of(due));
        when(publishingQueryService.claimForPublishing(due)).thenReturn(Optional.empty());

        job.run();

        verify(publishingQueryService, never()).loadAssetsForSubmission(due.getId());
        verify(facebookPublisherService, never()).publish(
                org.mockito.ArgumentMatchers.any(Submission.class),
                org.mockito.ArgumentMatchers.<List<MediaAsset>>any());
    }

    @Test
    void run_publishesOnlyAfterClaimSucceeds() {
        PublishingSchedulerJob job =
                new PublishingSchedulerJob(publishingQueryService, facebookPublisherService);
        UUID submissionId = UUID.randomUUID();
        Submission due = submission(submissionId, SubmissionStatus.scheduled);
        Submission claimed = submission(submissionId, SubmissionStatus.publishing);
        List<MediaAsset> assets = List.of(new MediaAsset());

        when(facebookPublisherService.isConfigured()).thenReturn(true);
        when(publishingQueryService.loadDueSubmissions(
                org.mockito.ArgumentMatchers.any(Instant.class),
                org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(List.of(due));
        when(publishingQueryService.claimForPublishing(due)).thenReturn(Optional.of(claimed));
        when(publishingQueryService.loadAssetsForSubmission(submissionId)).thenReturn(assets);

        job.run();

        verify(facebookPublisherService).publish(claimed, assets);
    }

    private static Submission submission(UUID id, SubmissionStatus status) {
        Submission submission = new Submission();
        submission.setId(id);
        submission.setStatus(status);
        return submission;
    }
}
