package com.dasigconnect.backend.model.dto.invitation;

import com.dasigconnect.backend.model.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateInvitationRequestDto(
        @NotBlank @Email String recipientEmail,
        @NotNull UUID institutionId,
        @NotNull UserRole assignedRole) {}
