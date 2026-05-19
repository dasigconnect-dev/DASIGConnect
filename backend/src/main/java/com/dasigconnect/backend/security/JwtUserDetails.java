package com.dasigconnect.backend.security;

import java.util.UUID;

public record JwtUserDetails(UUID userId, String email, String role, UUID institutionId) {}
