package com.dasigconnect.backend.event;

import java.util.UUID;

import com.dasigconnect.backend.model.entity.User;

/**
 * {@code user} declined a pending Administrator promotion. Notifies the admin
 * who proposed it ({@code requestedByUserId}) that their reserved slot has
 * been released.
 */
public record ModeratorPromotionDeclinedEvent(
        User user,
        UUID requestedByUserId) {
}
