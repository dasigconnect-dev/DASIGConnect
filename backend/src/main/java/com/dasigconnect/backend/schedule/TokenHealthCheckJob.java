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

    public TokenHealthCheckJob(
            FacebookPublisherService facebookPublisherService,
            ApplicationEventPublisher eventPublisher) {
        this.facebookPublisherService = facebookPublisherService;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "UTC")
    public void run() {
        if (!facebookPublisherService.isConfigured()) return;

        try {
            Instant expiresAt = facebookPublisherService.validateToken();

            if (expiresAt == null) {
                log.error("TokenHealthCheckJob: token validation failed — emitting critical alert.");
                eventPublisher.publishEvent(new TokenValidationFailedEvent());
                return;
            }

            long daysUntilExpiry = ChronoUnit.DAYS.between(Instant.now(), expiresAt);
            if (daysUntilExpiry <= EXPIRY_WARNING_DAYS) {
                log.warn("TokenHealthCheckJob: token expires in {} day(s) — emitting expiry warning.", daysUntilExpiry);
                eventPublisher.publishEvent(new TokenExpiryWarningEvent((int) daysUntilExpiry));
            } else {
                log.info("TokenHealthCheckJob: token is valid, expires in {} day(s).", daysUntilExpiry);
            }
        } catch (Exception ex) {
            log.error("TokenHealthCheckJob failed unexpectedly: {}", ex.getMessage(), ex);
        }
    }
}
