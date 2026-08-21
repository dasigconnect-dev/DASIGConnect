package com.dasigconnect.backend.model.dto.notification;

import java.time.Instant;

public record MessengerConnectionDto(boolean connected, boolean enabled, Instant linkedAt) {}
