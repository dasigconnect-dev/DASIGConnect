package com.dasigconnect.backend.model.dto.user;

import java.time.Instant;
import java.util.UUID;

public record AdminTransferResponseDto(
        UUID targetUserId,
        UUID requestedBy,
        Instant expiresAt,
        String status) {
}
