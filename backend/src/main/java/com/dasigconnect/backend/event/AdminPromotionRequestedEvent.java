package com.dasigconnect.backend.event;

import java.time.Instant;

import com.dasigconnect.backend.model.entity.User;

/**
 * The Admin Owner (or a peer admin, via {@code requireActiveAdminOwner})
 * proposed promoting {@code user} to Administrator. The role has not changed
 * yet — {@code user} must confirm via {@code POST /users/promotion/confirm}
 * before {@code expiresAt}, or decline, for the promotion to resolve.
 */
public record AdminPromotionRequestedEvent(
        User user,
        String requestedByEmail,
        Instant expiresAt) {
}
