package com.dasigconnect.backend.schedule;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.service.ScheduledJobHealthService;
import com.dasigconnect.backend.service.SubscriptionLifecycleService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionLifecycleJobTest {

    @Mock private SubscriptionLifecycleService lifecycleService;
    @Mock private ScheduledJobHealthService healthService;

    @Test
    void advanceLifecycle_recordsSuccessfulRun() {
        when(lifecycleService.evaluate(any())).thenReturn(Optional.empty());

        new SubscriptionLifecycleJob(lifecycleService, healthService).advanceLifecycle();

        verify(healthService).recordSuccess(eq("SubscriptionLifecycleJob"), any(Instant.class));
    }

    @Test
    void advanceLifecycle_recordsFailureWithoutEscapingScheduler() {
        IllegalStateException failure = new IllegalStateException("database unavailable");
        when(lifecycleService.evaluate(any())).thenThrow(failure);

        new SubscriptionLifecycleJob(lifecycleService, healthService).advanceLifecycle();

        verify(healthService).recordFailure(
                eq("SubscriptionLifecycleJob"), any(Instant.class), eq(failure));
    }
}
