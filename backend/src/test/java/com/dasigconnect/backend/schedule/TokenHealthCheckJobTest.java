package com.dasigconnect.backend.schedule;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.event.TokenExpiryWarningEvent;
import com.dasigconnect.backend.event.TokenValidationFailedEvent;
import com.dasigconnect.backend.service.FacebookPublisherService;
import com.dasigconnect.backend.service.FacebookPublisherService.TokenValidation;
import com.dasigconnect.backend.service.FacebookPublisherService.TokenValidationOutcome;
import com.dasigconnect.backend.service.ScheduledJobHealthService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class TokenHealthCheckJobTest {

    private final FacebookPublisherService facebook = mock(FacebookPublisherService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final ScheduledJobHealthService health = mock(ScheduledJobHealthService.class);
    private final TokenHealthCheckJob job = new TokenHealthCheckJob(facebook, events, health);

    @BeforeEach
    void configured() {
        when(facebook.isConfigured()).thenReturn(true);
    }

    @Test
    void nonExpiringToken_recordsSuccess_noAlert() {
        when(facebook.validateToken()).thenReturn(new TokenValidation(
                TokenValidationOutcome.VALID_NON_EXPIRING, null, "Token is valid and does not expire."));

        job.run();

        verify(health).recordSuccess(eq("TokenHealthCheckJob"), any());
        verify(health, never()).recordFailure(any(), any(), any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void validTokenFarFromExpiry_recordsSuccess_noWarning() {
        when(facebook.validateToken()).thenReturn(new TokenValidation(
                TokenValidationOutcome.VALID, Instant.now().plus(60, ChronoUnit.DAYS), "Token is valid."));

        job.run();

        verify(health).recordSuccess(eq("TokenHealthCheckJob"), any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void validTokenNearingExpiry_emitsExpiryWarning_stillSuccess() {
        when(facebook.validateToken()).thenReturn(new TokenValidation(
                TokenValidationOutcome.VALID, Instant.now().plus(3, ChronoUnit.DAYS), "Token is valid."));

        job.run();

        verify(events).publishEvent(any(TokenExpiryWarningEvent.class));
        verify(health).recordSuccess(eq("TokenHealthCheckJob"), any());
        verify(health, never()).recordFailure(any(), any(), any());
    }

    @Test
    void rejectedToken_recordsFailure_andFiresCriticalAlert() {
        when(facebook.validateToken()).thenReturn(new TokenValidation(
                TokenValidationOutcome.REJECTED, null, "Error validating access token: session expired."));

        job.run();

        verify(events).publishEvent(any(TokenValidationFailedEvent.class));
        verify(health).recordFailure(eq("TokenHealthCheckJob"), any(), any());
        verify(health, never()).recordSuccess(any(), any());
    }

    @Test
    void notConfigured_recordsSuccess_noAlert() {
        when(facebook.validateToken()).thenReturn(new TokenValidation(
                TokenValidationOutcome.NOT_CONFIGURED, null, "Facebook app id/secret are not set; debug_token cannot be called."));

        job.run();

        verify(health).recordSuccess(eq("TokenHealthCheckJob"), any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void unreachable_recordsFailure_butNoCriticalAlert() {
        when(facebook.validateToken()).thenReturn(new TokenValidation(
                TokenValidationOutcome.UNREACHABLE, null, "Could not reach the Facebook Graph API to validate the token: timeout"));

        job.run();

        verify(health).recordFailure(eq("TokenHealthCheckJob"), any(), any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void notConfiguredPublisher_skipsValidationEntirely() {
        when(facebook.isConfigured()).thenReturn(false);

        job.run();

        verify(health).recordSuccess(eq("TokenHealthCheckJob"), any());
        verify(facebook, never()).validateToken();
    }
}
