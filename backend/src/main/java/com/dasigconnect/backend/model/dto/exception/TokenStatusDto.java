package com.dasigconnect.backend.model.dto.exception;

import com.dasigconnect.backend.model.entity.FacebookPageToken;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public class TokenStatusDto {

    private UUID id;
    private String pageId;
    private String tokenStatus;
    private Instant expiresAt;
    private Instant lastValidatedAt;
    private String validationFailureReason;

    public static TokenStatusDto from(FacebookPageToken token) {
        TokenStatusDto dto = new TokenStatusDto();
        dto.id = token.getId();
        dto.pageId = token.getPageId();
        dto.tokenStatus = computeStatus(token);
        dto.expiresAt = token.getExpiresAt();
        dto.lastValidatedAt = token.getLastValidatedAt();
        // Only surface the reason when it's the thing actually driving INVALID —
        // an inactive-but-never-rejected row (e.g. a previously connected page)
        // shouldn't show a stale rejection message from before it was replaced.
        dto.validationFailureReason = token.isActive() && token.getValidationFailedAt() != null
                ? token.getValidationFailureReason()
                : null;
        return dto;
    }

    private static String computeStatus(FacebookPageToken token) {
        if (!token.isActive()) return "INVALID";
        // A rejection from the debug_token check overrides expiry-based status —
        // Facebook already told us this token doesn't work, regardless of what
        // expires_at says (it may not even be set for a revoked/never-expiring token).
        if (token.getValidationFailedAt() != null) return "INVALID";
        Instant now = Instant.now();
        if (token.getExpiresAt() == null) return "ACTIVE";
        if (token.getExpiresAt().isBefore(now)) return "EXPIRED";
        if (token.getExpiresAt().isBefore(now.plus(Duration.ofDays(7)))) return "EXPIRING";
        return "ACTIVE";
    }

    public UUID getId() { return id; }
    public String getPageId() { return pageId; }
    public String getTokenStatus() { return tokenStatus; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getLastValidatedAt() { return lastValidatedAt; }
    public String getValidationFailureReason() { return validationFailureReason; }
}
