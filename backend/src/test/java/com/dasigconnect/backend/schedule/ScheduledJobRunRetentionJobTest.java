package com.dasigconnect.backend.schedule;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.dasigconnect.backend.repository.ScheduledJobRunRepository;
import com.dasigconnect.backend.service.ScheduledJobHealthService;

class ScheduledJobRunRetentionJobTest {

    private final ScheduledJobRunRepository repository = mock(ScheduledJobRunRepository.class);
    private final ScheduledJobHealthService health = mock(ScheduledJobHealthService.class);
    private final ScheduledJobRunRetentionJob job = new ScheduledJobRunRetentionJob(repository, health);

    @Test
    void pruneOldRuns_deletesWithConfiguredCutoffAndRecordsSuccess() {
        ReflectionTestUtils.setField(job, "retentionDays", 30);
        when(repository.deleteOlderThan(any())).thenReturn(12);

        Instant before = Instant.now().minusSeconds(1);
        job.pruneOldRuns();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository).deleteOlderThan(cutoff.capture());
        Instant expectedLow = before.minusSeconds(30L * 86_400);
        Instant expectedHigh = after.minusSeconds(30L * 86_400);
        org.assertj.core.api.Assertions.assertThat(cutoff.getValue())
                .isBetween(expectedLow, expectedHigh);
        verify(health).recordSuccess(eq("ScheduledJobRunRetentionJob"), any());
    }

    @Test
    void pruneOldRuns_recordsFailureWhenDeleteThrows() {
        ReflectionTestUtils.setField(job, "retentionDays", 30);
        when(repository.deleteOlderThan(any())).thenThrow(new RuntimeException("locked"));

        job.pruneOldRuns();

        verify(health).recordFailure(eq("ScheduledJobRunRetentionJob"), any(), any());
        verify(health, never()).recordSuccess(any(), any());
    }
}
