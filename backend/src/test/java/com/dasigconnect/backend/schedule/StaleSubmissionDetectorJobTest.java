package com.dasigconnect.backend.schedule;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

import com.dasigconnect.backend.event.PublishFailedEvent;
import com.dasigconnect.backend.event.SubmissionMissedReviewEvent;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.service.ScheduledJobHealthService;
import com.dasigconnect.backend.service.SlotReservationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StaleSubmissionDetectorJobTest {

    @Mock private SubmissionRepository submissionRepository;
    @Mock private SlotReservationService slotReservationService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ScheduledJobHealthService scheduledJobHealthService;

    @InjectMocks private StaleSubmissionDetectorJob job;

    @Test
    void findAndMarkMissedReview_transitionsPendingAndInReviewAndReleasesSlot() {
        Instant cutoff = Instant.now().minus(5, ChronoUnit.MINUTES);

        Submission pending = submission(SubmissionStatus.pending);
        Submission inReview = submission(SubmissionStatus.in_review);
        when(submissionRepository.findMissedReviewSubmissions(any()))
                .thenReturn(List.of(pending, inReview));

        List<Submission> result = job.findAndMarkMissedReview(cutoff);

        assertThat(result).hasSize(2);
        assertThat(pending.getStatus()).isEqualTo(SubmissionStatus.missed_review);
        assertThat(inReview.getStatus()).isEqualTo(SubmissionStatus.missed_review);
        verify(slotReservationService).release(pending.getId());
        verify(slotReservationService).release(inReview.getId());
        verify(submissionRepository).saveAll(result);
    }

    @Test
    void run_emitsMissedReviewEventPerSubmission() {
        Submission pending = submission(SubmissionStatus.pending);
        when(submissionRepository.findMissedScheduledSubmissions(any())).thenReturn(new java.util.ArrayList<>());
        when(submissionRepository.findMissedReviewSubmissions(any())).thenReturn(List.of(pending));

        job.run();

        verify(eventPublisher).publishEvent(any(SubmissionMissedReviewEvent.class));
    }

    @Test
    void findAndMarkStuckFastTrackFailed_transitionsToPublishFailedAndKeepsTokenBlockedAlone() {
        // Regression: a Fast-Track submission claimed by FastTrackPublishingListener
        // but never resolved (app crash mid-publish) has no scheduledAt for
        // findAndMarkFailed's query to match -- it would otherwise be stuck in
        // PUBLISHING forever with no recovery path. A token-expiry block is a
        // different case (handled by TokenPublishingEscalationJob) and must be
        // left alone here, same as findAndMarkFailed already does.
        Instant cutoff = Instant.now().minus(5, ChronoUnit.MINUTES);

        Submission stuck = new Submission();
        stuck.setId(UUID.randomUUID());
        stuck.setStatus(SubmissionStatus.publishing);
        // Fast-Track: no scheduledAt at all, unlike submission(status) helper below.

        Submission directPostStuck = new Submission();
        directPostStuck.setId(UUID.randomUUID());
        directPostStuck.setStatus(SubmissionStatus.direct_post_publishing);

        Submission tokenBlocked = new Submission();
        tokenBlocked.setId(UUID.randomUUID());
        tokenBlocked.setStatus(SubmissionStatus.publishing);
        tokenBlocked.setTokenBlockedAt(Instant.now().minus(10, ChronoUnit.MINUTES));

        when(submissionRepository.findStuckFastTrackPublishing(any()))
                .thenReturn(new java.util.ArrayList<>(List.of(stuck, directPostStuck, tokenBlocked)));

        List<Submission> result = job.findAndMarkStuckFastTrackFailed(cutoff);

        assertThat(result).containsExactlyInAnyOrder(stuck, directPostStuck);
        assertThat(stuck.getStatus()).isEqualTo(SubmissionStatus.publish_failed);
        assertThat(directPostStuck.getStatus()).isEqualTo(SubmissionStatus.direct_post_failed);
        assertThat(tokenBlocked.getStatus()).isEqualTo(SubmissionStatus.publishing); // untouched
        verify(submissionRepository).saveAll(result);
    }

    @Test
    void run_emitsPublishFailedEventForStuckFastTrackSubmission() {
        Submission stuck = new Submission();
        stuck.setId(UUID.randomUUID());
        stuck.setStatus(SubmissionStatus.publishing);
        when(submissionRepository.findMissedScheduledSubmissions(any())).thenReturn(new java.util.ArrayList<>());
        when(submissionRepository.findStuckFastTrackPublishing(any()))
                .thenReturn(new java.util.ArrayList<>(List.of(stuck)));
        when(submissionRepository.findMissedReviewSubmissions(any())).thenReturn(List.of());

        job.run();

        verify(eventPublisher).publishEvent(any(PublishFailedEvent.class));
    }

    private static Submission submission(SubmissionStatus status) {
        Submission s = new Submission();
        s.setId(UUID.randomUUID());
        s.setStatus(status);
        s.setScheduledAt(Instant.now().minus(30, ChronoUnit.MINUTES));
        return s;
    }
}
