package com.dasigconnect.backend.model.dto.exception;

import jakarta.validation.constraints.NotBlank;

/**
 * Owner-only: connects a different Facebook Page — the only in-app way to
 * change which page the system publishes to. See
 * {@code TokenManagementService.connectPage}.
 */
public record ConnectFacebookPageRequestDto(
        @NotBlank String pageId,
        @NotBlank String accessToken) {}
