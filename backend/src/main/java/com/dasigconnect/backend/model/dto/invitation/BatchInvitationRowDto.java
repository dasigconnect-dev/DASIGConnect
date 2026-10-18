package com.dasigconnect.backend.model.dto.invitation;

import java.util.UUID;

public record BatchInvitationRowDto(
        String email,
        String role,
        UUID institutionId,
        String institutionCode
) {}

