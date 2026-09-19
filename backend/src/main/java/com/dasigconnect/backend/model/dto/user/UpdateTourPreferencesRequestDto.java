package com.dasigconnect.backend.model.dto.user;

import java.util.List;

/**
 * Full-replace update for the caller's onboarding-guide preferences — mirrors
 * the shape the frontend previously kept in localStorage one-for-one, so the
 * client can send back its whole in-memory state on every mutation instead of
 * needing separate partial-update endpoints for "mark seen" vs "toggle".
 */
public record UpdateTourPreferencesRequestDto(boolean enabled, List<String> seenScreens) {}
