package com.dasigconnect.backend.model.dto.auth;

import java.util.UUID;

public record LoginResponseDto(
        String accessToken,
        String role,
        UUID institutionId) {}
