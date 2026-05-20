package com.dasigconnect.backend.model.dto.invitation;

import com.dasigconnect.backend.model.entity.InvitationToken;
import com.dasigconnect.backend.model.entity.UserRole;
import java.time.Instant;
import java.util.UUID;

public record PendingInvitationDto(
        UUID id,
        String recipientEmail,
        UserRole assignedRole,
        UUID institutionId,
        Instant expiresAt,
        Instant createdAt) {

    public static PendingInvitationDto from(InvitationToken token) {
        return new PendingInvitationDto(
                token.getId(),
                token.getRecipientEmail(),
                token.getAssignedRole(),
                token.getInstitution().getId(),
                token.getExpiresAt(),
                token.getCreatedAt());
    }
}
