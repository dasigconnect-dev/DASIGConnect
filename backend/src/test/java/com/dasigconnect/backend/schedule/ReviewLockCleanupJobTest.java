package com.dasigconnect.backend.schedule;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import com.dasigconnect.backend.service.ReviewLockService;
import com.dasigconnect.backend.service.ScheduledJobHealthService;

class ReviewLockCleanupJobTest {

    private final ReviewLockService reviewLockService = mock(ReviewLockService.class);
    private final ScheduledJobHealthService health = mock(ScheduledJobHealthService.class);
    private final ReviewLockCleanupJob job = new ReviewLockCleanupJob(reviewLockService, health);

    @Test
    void releaseExpiredLocks_recordsSuccess() {
        job.releaseExpiredLocks();

        verify(reviewLockService).releaseExpiredLocks();
        verify(health).recordSuccess(eq("ReviewLockCleanupJob"), any());
        verify(health, never()).recordFailure(any(), any(), any());
    }

    @Test
    void releaseExpiredLocks_recordsFailureWhenDelegateThrows() {
        doThrow(new RuntimeException("db down")).when(reviewLockService).releaseExpiredLocks();

        job.releaseExpiredLocks();

        verify(health).recordFailure(eq("ReviewLockCleanupJob"), any(), any());
        verify(health, never()).recordSuccess(any(), any());
    }
}
