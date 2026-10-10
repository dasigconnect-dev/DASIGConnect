package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import com.dasigconnect.backend.event.TokenPublishingSuspendedEvent;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.repository.SubmissionRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PublishingStateServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private SlotReservationService slotReservationService;
    @Mock
    private FacebookPublisherService facebookPublisherService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PublishingStateService publishingStateService;

    @Test
    void findAndMarkMissedReview_transitionsPendingAndInReviewAndReleasesSlot() {
        Instant cutoff = Instant.now().minus(5, ChronoUnit.MINUTES);

        Submission pending = submission(SubmissionStatus.pending);
        Submission inReview = submission(SubmissionStatus.in_review);
        when(submissionRepository.findMissedReviewSubmissions(any(), any()))
                .thenReturn(List.of(pending, inReview));

        List<Submission> result = publishingStateService.findAndMarkMissedReview(cutoff);

        assertThat(result).hasSize(2);
        assertThat(pending.getStatus()).isEqualTo(SubmissionStatus.missed_review);
        assertThat(inReview.getStatus()).isEqualTo(SubmissionStatus.missed_review);
        verify(slotReservationService).release(pending.getId());
        verify(slotReservationService).release(inReview.getId());
        verify(submissionRepository).saveAll(result);
    }

    @Test
    void findAndMarkStuckFastTrackFailed_transitionsToPublishFailedAndKeepsTokenBlockedAlone() {
        Instant cutoff = Instant.now().minus(5, ChronoUnit.MINUTES);

        Submission stuck = new Submission();
        stuck.setId(UUID.randomUUID());
        stuck.setStatus(SubmissionStatus.publishing);

        Submission directPostStuck = new Submission();
        directPostStuck.setId(UUID.randomUUID());
        directPostStuck.setStatus(SubmissionStatus.direct_post_publishing);

        Submission tokenBlocked = new Submission();
        tokenBlocked.setId(UUID.randomUUID());
        tokenBlocked.setStatus(SubmissionStatus.publishing);
        tokenBlocked.setTokenBlockedAt(Instant.now().minus(10, ChronoUnit.MINUTES));

        when(submissionRepository.findStuckFastTrackPublishing(any()))
                .thenReturn(new ArrayList<>(List.of(stuck, directPostStuck, tokenBlocked)));

        List<Submission> result = publishingStateService.findAndMarkStuckFastTrackFailed(cutoff);

        assertThat(result).containsExactlyInAnyOrder(stuck, directPostStuck);
        assertThat(stuck.getStatus()).isEqualTo(SubmissionStatus.publish_failed);
        assertThat(directPostStuck.getStatus()).isEqualTo(SubmissionStatus.direct_post_failed);
        assertThat(tokenBlocked.getStatus()).isEqualTo(SubmissionStatus.publishing); // untouched
        verify(submissionRepository).saveAll(result);
    }

    @Test
    void escalateAfterTwentyFourHours_sendsEscalationOnce() {
        Submission submission = submission(SubmissionStatus.scheduled);
        submission.setTokenBlockedAt(Instant.now().minusSeconds(25 * 60 * 60));

        publishingStateService.escalateAfterTwentyFourHours(submission);

        verify(facebookPublisherService).recordAttempt(
                eq(submission),
                eq(1),
                eq("failed"),
                org.mockito.ArgumentMatchers.startsWith(FacebookPublisherService.TOKEN_EXPIRED_24H_PREFIX),
                eq(null));
        verify(eventPublisher).publishEvent(any(TokenPublishingSuspendedEvent.class));
        verify(submissionRepository).save(submission);
    }

    @Test
    void failAfterFortyEightHours_marksFailedOnce() {
        Submission submission = submission(SubmissionStatus.scheduled);
        submission.setTokenBlockedAt(Instant.now().minusSeconds(49 * 60 * 60));

        publishingStateService.failAfterFortyEightHours(submission);

        verify(facebookPublisherService).recordAttempt(
                eq(submission),
                eq(1),
                eq("failed"),
                org.mockito.ArgumentMatchers.startsWith(FacebookPublisherService.TOKEN_EXPIRED_48H_PREFIX),
                eq(null));
        verify(facebookPublisherService).markFailed(
                eq(submission),
                eq("Facebook Page Access Token was not reauthorized within 48 hours."));
        verify(submissionRepository).save(submission);
    }

    private static Submission submission(SubmissionStatus status) {
        Submission s = new Submission();
        s.setId(UUID.randomUUID());
        s.setStatus(status);
        s.setScheduledAt(Instant.now().minus(30, ChronoUnit.MINUTES));
        return s;
    }
}

