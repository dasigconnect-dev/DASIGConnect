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

    public static TokenStatusDto from(FacebookPageToken token) {
        TokenStatusDto dto = new TokenStatusDto();
        dto.id = token.getId();
        dto.pageId = token.getPageId();
        dto.tokenStatus = computeStatus(token);
        dto.expiresAt = token.getExpiresAt();
        dto.lastValidatedAt = token.getLastValidatedAt();
        return dto;
    }

    private static String computeStatus(FacebookPageToken token) {
        if (!token.isActive()) return "INVALID";
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
}
