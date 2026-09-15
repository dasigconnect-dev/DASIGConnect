package com.dasigconnect.backend.model.dto.exception;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.dasigconnect.backend.model.entity.FacebookPageToken;

import static org.assertj.core.api.Assertions.assertThat;

class TokenStatusDtoTest {

    private FacebookPageToken token() {
        FacebookPageToken token = new FacebookPageToken();
        token.setId(UUID.randomUUID());
        token.setPageId("123456");
        token.setActive(true);
        return token;
    }

    @Test
    void from_activeWithNoExpiry_isActive() {
        TokenStatusDto dto = TokenStatusDto.from(token());

        assertThat(dto.getTokenStatus()).isEqualTo("ACTIVE");
        assertThat(dto.getValidationFailureReason()).isNull();
    }

    @Test
    void from_expiringWithinSevenDays_isExpiring() {
        FacebookPageToken token = token();
        token.setExpiresAt(Instant.now().plus(3, ChronoUnit.DAYS));

        assertThat(TokenStatusDto.from(token).getTokenStatus()).isEqualTo("EXPIRING");
    }

    @Test
    void from_expiryInThePast_isExpired() {
        FacebookPageToken token = token();
        token.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));

        assertThat(TokenStatusDto.from(token).getTokenStatus()).isEqualTo("EXPIRED");
    }

    @Test
    void from_inactiveRow_isInvalidRegardlessOfExpiry() {
        FacebookPageToken token = token();
        token.setActive(false);
        token.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));

        assertThat(TokenStatusDto.from(token).getTokenStatus()).isEqualTo("INVALID");
    }

    // Regression: previously a debug_token REJECTED result was never persisted
    // anywhere the status computation looked at, so the table kept showing
    // ACTIVE/EXPIRING for a token Facebook had just rejected.
    @Test
    void from_activeButFacebookRejectedIt_isInvalidNotActive() {
        FacebookPageToken token = token();
        token.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        token.setValidationFailedAt(Instant.now());
        token.setValidationFailureReason("Error validating access token: session expired.");

        TokenStatusDto dto = TokenStatusDto.from(token);

        assertThat(dto.getTokenStatus()).isEqualTo("INVALID");
        assertThat(dto.getValidationFailureReason()).isEqualTo("Error validating access token: session expired.");
    }

    @Test
    void from_inactiveRowWithStaleRejection_doesNotSurfaceTheReason() {
        FacebookPageToken token = token();
        token.setActive(false);
        token.setValidationFailedAt(Instant.now().minus(30, ChronoUnit.DAYS));
        token.setValidationFailureReason("stale reason from before this page was replaced");

        assertThat(TokenStatusDto.from(token).getValidationFailureReason()).isNull();
    }
}
