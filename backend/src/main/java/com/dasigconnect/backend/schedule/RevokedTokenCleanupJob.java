package com.dasigconnect.backend.schedule;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dasigconnect.backend.repository.RevokedTokenRepository;
import com.dasigconnect.backend.service.ScheduledJobHealthService;

/**
 * Deletes {@code revoked_tokens} rows whose underlying JWT has already
 * expired — such a token would fail signature/expiry validation regardless,
 * so the blacklist row no longer serves any purpose. Purely housekeeping to
 * keep the table from growing without bound; runs hourly.
 */
@Component
public class RevokedTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RevokedTokenCleanupJob.class);

    private final RevokedTokenRepository revokedTokenRepository;
    private final ScheduledJobHealthService scheduledJobHealthService;

    public RevokedTokenCleanupJob(
            RevokedTokenRepository revokedTokenRepository,
            ScheduledJobHealthService scheduledJobHealthService) {
        this.revokedTokenRepository = revokedTokenRepository;
        this.scheduledJobHealthService = scheduledJobHealthService;
    }

    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void purgeExpired() {
        Instant startedAt = Instant.now();
        try {
            int removed = revokedTokenRepository.deleteByExpiresAtBefore(startedAt);
            if (removed > 0) {
                log.debug("RevokedTokenCleanupJob removed {} expired revoked-token rows", removed);
            }
            scheduledJobHealthService.recordSuccess("RevokedTokenCleanupJob", startedAt);
        } catch (Exception ex) {
            log.error("RevokedTokenCleanupJob failed: {}", ex.getMessage(), ex);
            scheduledJobHealthService.recordFailure("RevokedTokenCleanupJob", startedAt, ex);
        }
    }
}
