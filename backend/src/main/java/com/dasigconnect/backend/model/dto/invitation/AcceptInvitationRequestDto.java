package com.dasigconnect.backend.model.dto.invitation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInvitationRequestDto(
        @NotBlank String token,
        @NotBlank @Size(min = 8) String password) {}
