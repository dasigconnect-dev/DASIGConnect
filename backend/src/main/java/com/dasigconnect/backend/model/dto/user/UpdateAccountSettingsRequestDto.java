package com.dasigconnect.backend.model.dto.user;

import jakarta.validation.constraints.Size;

public record UpdateAccountSettingsRequestDto(
        @Size(max = 150) String displayName,
        boolean notifyInApp,
        boolean notifyEmail) {}
