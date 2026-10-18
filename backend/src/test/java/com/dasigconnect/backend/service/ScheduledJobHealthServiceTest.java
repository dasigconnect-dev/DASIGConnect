package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dasigconnect.backend.model.entity.ScheduledJobRun;
import com.dasigconnect.backend.repository.ScheduledJobRunRepository;

@ExtendWith(MockitoExtension.class)
class ScheduledJobHealthServiceTest {

    @Mock
    private ScheduledJobRunRepository scheduledJobRunRepository;

    private ScheduledJobHealthService service;

    @BeforeEach
    void setUp() {
        service = new ScheduledJobHealthService(scheduledJobRunRepository);
    }

    @Test
    void recordSuccess_savesSuccessRunWithoutError() {
        Instant startedAt = Instant.now().minusMillis(150);

        service.recordSuccess("PublishingSchedulerJob", startedAt);

        ArgumentCaptor<ScheduledJobRun> captor = ArgumentCaptor.forClass(ScheduledJobRun.class);
        verify(scheduledJobRunRepository).save(captor.capture());
        ScheduledJobRun run = captor.getValue();

        assertThat(run.getJobName()).isEqualTo("PublishingSchedulerJob");
        assertThat(run.getStatus()).isEqualTo("SUCCESS");
        assertThat(run.getStartedAt()).isEqualTo(startedAt);
        assertThat(run.getCompletedAt()).isNotNull();
        assertThat(run.getDurationMs()).isGreaterThanOrEqualTo(0);
        assertThat(run.getErrorMessage()).isNull();
    }

    @Test
    void recordFailure_withException_savesFailedRunAndTruncatesLongError() {
        Instant startedAt = Instant.now().minusMillis(200);
        String veryLongMessage = "x".repeat(1200);
        IllegalStateException ex = new IllegalStateException(veryLongMessage);

        service.recordFailure("StaleSubmissionDetectorJob", startedAt, ex);

        ArgumentCaptor<ScheduledJobRun> captor = ArgumentCaptor.forClass(ScheduledJobRun.class);
        verify(scheduledJobRunRepository).save(captor.capture());
        ScheduledJobRun run = captor.getValue();

        assertThat(run.getJobName()).isEqualTo("StaleSubmissionDetectorJob");
        assertThat(run.getStatus()).isEqualTo("FAILED");
        assertThat(run.getStartedAt()).isEqualTo(startedAt);
        assertThat(run.getCompletedAt()).isNotNull();
        assertThat(run.getDurationMs()).isGreaterThanOrEqualTo(0);
        assertThat(run.getErrorMessage()).isNotNull();
        assertThat(run.getErrorMessage().length()).isLessThanOrEqualTo(1000);
        assertThat(run.getErrorMessage()).startsWith("IllegalStateException: ");
    }

    @Test
    void recordFailure_withNullException_savesUnknownFailure() {
        Instant startedAt = Instant.now().minusMillis(50);

        service.recordFailure("TokenHealthCheckJob", startedAt, null);

        ArgumentCaptor<ScheduledJobRun> captor = ArgumentCaptor.forClass(ScheduledJobRun.class);
        verify(scheduledJobRunRepository).save(captor.capture());
        ScheduledJobRun run = captor.getValue();

        assertThat(run.getJobName()).isEqualTo("TokenHealthCheckJob");
        assertThat(run.getStatus()).isEqualTo("FAILED");
        assertThat(run.getErrorMessage()).isEqualTo("Unknown failure");
    }

    @Test
    void recordFailure_withExceptionWithoutMessage_summarizesClassSimpleName() {
        Instant startedAt = Instant.now().minusMillis(50);
        RuntimeException ex = new RuntimeException((String) null);

        service.recordFailure("CleanupJob", startedAt, ex);

        ArgumentCaptor<ScheduledJobRun> captor = ArgumentCaptor.forClass(ScheduledJobRun.class);
        verify(scheduledJobRunRepository).save(captor.capture());
        ScheduledJobRun run = captor.getValue();

        assertThat(run.getErrorMessage()).isEqualTo("RuntimeException");
    }
}
