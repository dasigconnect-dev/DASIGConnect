package com.dasigconnect.backend.model.dto.user;

import com.dasigconnect.backend.model.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequestDto(
        @NotNull UserStatus accountState) {
}
