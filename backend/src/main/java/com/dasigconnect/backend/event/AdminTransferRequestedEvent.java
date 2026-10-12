package com.dasigconnect.backend.event;

import java.time.Instant;

import com.dasigconnect.backend.model.entity.User;

/**
 * The Admin Owner initiated an Admin Owner transfer to {@code target} (must be
 * an active Administrator). The target must confirm via
 * {@code POST /users/admin-transfer/confirm} before {@code expiresAt} (24h).
 */
public record AdminTransferRequestedEvent(
        User target,
        String requestedByEmail,
        Instant expiresAt) {
}

