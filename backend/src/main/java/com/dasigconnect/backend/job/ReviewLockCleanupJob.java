package com.dasigconnect.backend.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dasigconnect.backend.service.ReviewLockService;

/**
 * BR-VAL-01: Runs every minute. Finds all expired review locks, reverts
 * IN_REVIEW → PENDING, and removes the lock records so the submission
 * returns to the validation queue.
 */
@Component
public class ReviewLockCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(ReviewLockCleanupJob.class);

    private final ReviewLockService reviewLockService;

    public ReviewLockCleanupJob(ReviewLockService reviewLockService) {
        this.reviewLockService = reviewLockService;
    }

    @Scheduled(fixedDelay = 60_000)
    public void releaseExpiredLocks() {
        try {
            reviewLockService.releaseExpiredLocks();
        } catch (Exception ex) {
            log.error("ReviewLockCleanupJob: unexpected error during expired lock cleanup", ex);
        }
    }
}
