package com.dasigconnect.backend.model.dto.analytics;

import java.time.LocalDate;

/**
 * One day of content-insight metrics. Carries each KPI's own daily value so the Views /
 * Engagement charts can re-plot the series for whichever KPI tile the user selects.
 */
public record ContentInsightDailyPointDto(
        LocalDate date,
        long postsPublished,
        long views,
        long uniqueViews,
        long reach,
        long engagements,
        long reactions,
        long comments,
        long shares,
        long fifteenSecondViews,
        long sixtySecondViews,
        double watchTimeSeconds) {
}
