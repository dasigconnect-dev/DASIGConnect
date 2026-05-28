package com.dasigconnect.backend.model.dto.analytics;

import java.time.LocalDate;

public record DailyAnalyticsPointDto(
        LocalDate date,
        double value,
        Long secondaryValue) {
}
