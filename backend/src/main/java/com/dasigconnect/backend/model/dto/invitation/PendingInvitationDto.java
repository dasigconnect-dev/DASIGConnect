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
        Instant createdAt,
        UUID createdByUserId,
        /** Whether the requesting user may resend/cancel this invitation. */
        boolean canManage) {

    public static PendingInvitationDto from(InvitationToken token) {
        return from(token, true);
    }

    public static PendingInvitationDto from(InvitationToken token, boolean canManage) {
        return new PendingInvitationDto(
                token.getId(),
                token.getRecipientEmail(),
                token.getAssignedRole(),
                token.getInstitution() != null ? token.getInstitution().getId() : null,
                token.getExpiresAt(),
                token.getCreatedAt(),
                token.getCreatedByUserId(),
                canManage);
    }
}
