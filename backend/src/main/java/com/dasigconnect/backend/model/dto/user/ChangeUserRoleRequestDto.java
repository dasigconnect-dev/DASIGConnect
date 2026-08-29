package com.dasigconnect.backend.model.dto.user;

import java.util.UUID;

import com.dasigconnect.backend.model.entity.UserRole;

import jakarta.validation.constraints.NotNull;

/**
 * Promote or demote an account. {@code institutionId} is required when
 * {@code role} is {@code contributor} (contributors are institution-scoped) and
 * ignored for the network-wide roles.
 */
public record ChangeUserRoleRequestDto(
        @NotNull UserRole role,
        UUID institutionId) {
}
