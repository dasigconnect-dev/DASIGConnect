package com.dasigconnect.backend.schedule;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dasigconnect.backend.service.ReviewLockService;
import com.dasigconnect.backend.service.ScheduledJobHealthService;

/**
 * BR-VAL-01: Runs every minute. Finds all expired review locks, reverts
 * IN_REVIEW → PENDING, and removes the lock records so the submission
 * returns to the validation queue.
 */
@Component
public class ReviewLockCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(ReviewLockCleanupJob.class);

    private final ReviewLockService reviewLockService;
    private final ScheduledJobHealthService scheduledJobHealthService;

    public ReviewLockCleanupJob(
            ReviewLockService reviewLockService,
            ScheduledJobHealthService scheduledJobHealthService) {
        this.reviewLockService = reviewLockService;
        this.scheduledJobHealthService = scheduledJobHealthService;
    }

    @Scheduled(fixedDelay = 60_000)
    public void releaseExpiredLocks() {
        Instant startedAt = Instant.now();
        try {
            reviewLockService.releaseExpiredLocks();
            scheduledJobHealthService.recordSuccess("ReviewLockCleanupJob", startedAt);
        } catch (Exception ex) {
            log.error("ReviewLockCleanupJob failed: {}", ex.getMessage(), ex);
            scheduledJobHealthService.recordFailure("ReviewLockCleanupJob", startedAt, ex);
        }
    }
}
