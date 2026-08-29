package com.dasigconnect.backend.security;

import java.util.UUID;

public record JwtUserDetails(
        UUID userId,
        String email,
        String role,
        UUID institutionId,
        boolean adminOwner) {

    public JwtUserDetails(UUID userId, String email, String role, UUID institutionId) {
        this(userId, email, role, institutionId, false);
    }
}
