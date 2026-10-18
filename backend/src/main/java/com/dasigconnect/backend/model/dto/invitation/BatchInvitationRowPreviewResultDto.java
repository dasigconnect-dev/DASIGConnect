package com.dasigconnect.backend.model.dto.invitation;

import java.util.UUID;

public record BatchInvitationRowPreviewResultDto(
        int index,
        String email,
        String role,
        UUID institutionId,
        String institutionName,
        String status,
        String reason
) {}

