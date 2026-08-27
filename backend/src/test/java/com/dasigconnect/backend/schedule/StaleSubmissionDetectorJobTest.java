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

    private static Submission submission(SubmissionStatus status) {
        Submission s = new Submission();
        s.setId(UUID.randomUUID());
        s.setStatus(status);
        s.setScheduledAt(Instant.now().minus(30, ChronoUnit.MINUTES));
        return s;
    }
}
