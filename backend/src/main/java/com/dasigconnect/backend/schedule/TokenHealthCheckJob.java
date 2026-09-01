package com.dasigconnect.backend.schedule;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dasigconnect.backend.event.TokenExpiryWarningEvent;
import com.dasigconnect.backend.event.TokenValidationFailedEvent;
import com.dasigconnect.backend.service.FacebookPublisherService;
import com.dasigconnect.backend.service.FacebookPublisherService.TokenValidation;
import com.dasigconnect.backend.service.ScheduledJobHealthService;

/**
 * GR-T4: Runs daily at 08:00 UTC. Validates the Facebook Page Access Token
 * via the Graph API debug_token endpoint.
 *
 * - Invalid token → fires TokenValidationFailedEvent (T13 — admin critical alert).
 * - Token expiring within 7 days → fires TokenExpiryWarningEvent (T12 — admin warning).
 */
@Component
public class TokenHealthCheckJob {

    private static final Logger log = LoggerFactory.getLogger(TokenHealthCheckJob.class);
    private static final long EXPIRY_WARNING_DAYS = 7L;

    private final FacebookPublisherService facebookPublisherService;
    private final ApplicationEventPublisher eventPublisher;
    private final ScheduledJobHealthService scheduledJobHealthService;

    public TokenHealthCheckJob(
            FacebookPublisherService facebookPublisherService,
            ApplicationEventPublisher eventPublisher,
            ScheduledJobHealthService scheduledJobHealthService) {
        this.facebookPublisherService = facebookPublisherService;
        this.eventPublisher = eventPublisher;
        this.scheduledJobHealthService = scheduledJobHealthService;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "UTC")
    public void run() {
        Instant startedAt = Instant.now();
        if (!facebookPublisherService.isConfigured()) {
            scheduledJobHealthService.recordSuccess("TokenHealthCheckJob", startedAt);
            return;
        }

        try {
            TokenValidation result = facebookPublisherService.validateToken();
            switch (result.outcome()) {
                case NOT_CONFIGURED -> {
                    log.info("TokenHealthCheckJob: nothing to validate — {}", result.detail());
                    scheduledJobHealthService.recordSuccess("TokenHealthCheckJob", startedAt);
                }
                case VALID_NON_EXPIRING -> {
                    log.info("TokenHealthCheckJob: token is valid and does not expire.");
                    scheduledJobHealthService.recordSuccess("TokenHealthCheckJob", startedAt);
                }
                case VALID -> {
                    long daysUntilExpiry = ChronoUnit.DAYS.between(Instant.now(), result.expiresAt());
                    if (daysUntilExpiry <= EXPIRY_WARNING_DAYS) {
                        log.warn("TokenHealthCheckJob: token expires in {} day(s) — emitting expiry warning.", daysUntilExpiry);
                        eventPublisher.publishEvent(new TokenExpiryWarningEvent((int) Math.max(daysUntilExpiry, 0)));
                    } else {
                        log.info("TokenHealthCheckJob: token is valid, expires in {} day(s).", daysUntilExpiry);
                    }
                    scheduledJobHealthService.recordSuccess("TokenHealthCheckJob", startedAt);
                }
                case REJECTED -> {
                    log.error("TokenHealthCheckJob: Facebook rejected the page token — {}", result.detail());
                    eventPublisher.publishEvent(new TokenValidationFailedEvent());
                    scheduledJobHealthService.recordFailure("TokenHealthCheckJob", startedAt,
                            new IllegalStateException("Facebook rejected the page token: " + result.detail()));
                }
                case UNREACHABLE -> {
                    log.warn("TokenHealthCheckJob: could not validate token this run — {}", result.detail());
                    scheduledJobHealthService.recordFailure("TokenHealthCheckJob", startedAt,
                            new IllegalStateException("Token validation unreachable: " + result.detail()));
                }
            }
        } catch (Exception ex) {
            log.error("TokenHealthCheckJob failed unexpectedly: {}", ex.getMessage(), ex);
            scheduledJobHealthService.recordFailure("TokenHealthCheckJob", startedAt, ex);
        }
    }
}
