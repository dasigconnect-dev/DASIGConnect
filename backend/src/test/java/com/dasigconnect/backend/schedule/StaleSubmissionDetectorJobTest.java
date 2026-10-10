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
import com.dasigconnect.backend.service.PublishingStateService;
import com.dasigconnect.backend.service.ScheduledJobHealthService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StaleSubmissionDetectorJobTest {

    @Mock private PublishingStateService publishingStateService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ScheduledJobHealthService scheduledJobHealthService;

    @InjectMocks private StaleSubmissionDetectorJob job;

    @Test
    void run_emitsMissedReviewEventPerSubmission() {
        Submission pending = submission(SubmissionStatus.pending);
        when(publishingStateService.findAndMarkFailed(any())).thenReturn(new java.util.ArrayList<>());
        when(publishingStateService.findAndMarkStuckFastTrackFailed(any())).thenReturn(new java.util.ArrayList<>());
        when(publishingStateService.findAndMarkMissedReview(any())).thenReturn(List.of(pending));

        job.run();

        verify(eventPublisher).publishEvent(any(SubmissionMissedReviewEvent.class));
    }

    @Test
    void run_emitsPublishFailedEventForStuckFastTrackSubmission() {
        Submission stuck = new Submission();
        stuck.setId(UUID.randomUUID());
        stuck.setStatus(SubmissionStatus.publishing);
        when(publishingStateService.findAndMarkFailed(any())).thenReturn(new java.util.ArrayList<>());
        when(publishingStateService.findAndMarkStuckFastTrackFailed(any()))
                .thenReturn(new java.util.ArrayList<>(List.of(stuck)));
        when(publishingStateService.findAndMarkMissedReview(any())).thenReturn(List.of());

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
