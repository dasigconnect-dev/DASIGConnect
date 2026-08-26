package com.dasigconnect.backend.model.dto.notification;

import java.time.Instant;

public record MessengerLinkCodeDto(
        String code,
        Instant expiresAt
) {}
