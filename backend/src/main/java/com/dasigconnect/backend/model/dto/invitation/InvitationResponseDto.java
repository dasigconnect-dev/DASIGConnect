package com.dasigconnect.backend.model.dto.invitation;

import com.dasigconnect.backend.model.entity.UserRole;
import java.time.Instant;
import java.util.UUID;

public record InvitationResponseDto(
        UUID id,
        String recipientEmail,
        UserRole assignedRole,
        UUID institutionId,
        Instant expiresAt,
        Instant createdAt) {}
