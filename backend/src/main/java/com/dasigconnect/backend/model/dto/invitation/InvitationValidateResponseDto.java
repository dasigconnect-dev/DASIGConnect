package com.dasigconnect.backend.model.dto.invitation;

import com.dasigconnect.backend.model.entity.UserRole;
import java.time.Instant;

public record InvitationValidateResponseDto(
        String recipientEmail,
        UserRole assignedRole,
        String institutionName,
        Instant expiresAt) {}
