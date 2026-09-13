package com.dasigconnect.backend.model.dto.exception;

import jakarta.validation.constraints.NotBlank;

/**
 * Manually pastes a Page Access Token onto an existing {@code FacebookPageToken}
 * row — an alternative to the OAuth re-authorization flow for admins who
 * already generated a long-lived token via the Graph API Explorer. Only ever
 * updates the token for a page that's already tracked (seeded from
 * {@code FACEBOOK_PAGE_ID} at startup); it cannot change which page the
 * system publishes to — that's still fixed by {@code FACEBOOK_PAGE_ID} in the
 * environment.
 */
public record SetManualTokenRequestDto(@NotBlank String accessToken) {}
