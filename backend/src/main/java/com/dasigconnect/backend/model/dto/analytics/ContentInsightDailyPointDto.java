package com.dasigconnect.backend.model.dto.analytics;

import java.time.LocalDate;

public record ContentInsightDailyPointDto(
        LocalDate date,
        long postsPublished,
        long views,
        long reach,
        long engagements,
        double watchTimeSeconds) {
}
