package com.dasigconnect.backend.schedule;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionMediaAsset;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.service.FacebookPublisherService;
import com.dasigconnect.backend.service.PublishingQueryService;
import com.dasigconnect.backend.service.PublishingStateService;
import com.dasigconnect.backend.service.ScheduledJobHealthService;

@ExtendWith(MockitoExtension.class)
class TokenPublishingEscalationJobTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private PublishingQueryService publishingQueryService;

    @Mock
    private FacebookPublisherService facebookPublisherService;

    @Mock
    private PublishingStateService publishingStateService;

    @Mock
    private ScheduledJobHealthService scheduledJobHealthService;

    @Test
    void run_retriesBlockedSubmissionsWhenTokenIsUsableAgain() {
        TokenPublishingEscalationJob job = job();
        Submission submission = submission();
        List<SubmissionMediaAsset> mediaLinks = List.of(new SubmissionMediaAsset());

        when(submissionRepository.findTokenBlockedScheduledSubmissions()).thenReturn(List.of(submission));
        when(facebookPublisherService.hasUsableActiveToken()).thenReturn(true);
        when(publishingQueryService.claimForPublishing(submission)).thenReturn(Optional.of(submission));
        when(publishingQueryService.loadMediaLinksForSubmission(submission.getId())).thenReturn(mediaLinks);

        job.run();

        verify(facebookPublisherService).publishMediaLinks(submission, mediaLinks);
        verify(publishingStateService, never()).failAfterFortyEightHours(any());
        verify(publishingStateService, never()).escalateAfterTwentyFourHours(any());
    }

    @Test
    void run_sendsTwentyFourHourEscalationOnce() {
        TokenPublishingEscalationJob job = job();
        Submission submission = submission();

        submission.setTokenBlockedAt(Instant.now().minusSeconds(25 * 60 * 60));

        when(submissionRepository.findTokenBlockedScheduledSubmissions()).thenReturn(List.of(submission));
        when(facebookPublisherService.hasUsableActiveToken()).thenReturn(false);

        job.run();

        verify(publishingStateService).escalateAfterTwentyFourHours(submission);
        verify(publishingStateService, never()).failAfterFortyEightHours(any());
    }

    @Test
    void run_failsAfterFortyEightHours() {
        TokenPublishingEscalationJob job = job();
        Submission submission = submission();

        submission.setTokenBlockedAt(Instant.now().minusSeconds(49 * 60 * 60));

        when(submissionRepository.findTokenBlockedScheduledSubmissions()).thenReturn(List.of(submission));
        when(facebookPublisherService.hasUsableActiveToken()).thenReturn(false);

        job.run();

        verify(publishingStateService).failAfterFortyEightHours(submission);
        verify(publishingStateService, never()).escalateAfterTwentyFourHours(any());
    }

    private TokenPublishingEscalationJob job() {
        return new TokenPublishingEscalationJob(
                submissionRepository,
                publishingQueryService,
                facebookPublisherService,
                publishingStateService,
                scheduledJobHealthService);
    }

    private static Submission submission() {
        Submission submission = new Submission();
        submission.setId(UUID.randomUUID());
        submission.setEventTitle("Scheduled post");
        return submission;
    }
}
