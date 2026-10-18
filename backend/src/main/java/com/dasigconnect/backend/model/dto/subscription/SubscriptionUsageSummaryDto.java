package com.dasigconnect.backend.model.dto.subscription;

import com.dasigconnect.backend.model.entity.SubscriptionLifecycleStatus;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public record SubscriptionUsageSummaryDto(
        UUID organizationId,
        YearMonth month,
        Instant periodStart,
        Instant periodEnd,
        SubscriptionLifecycleStatus subscriptionStatus,
        boolean enforcementEnabled,
        List<SubscriptionUsageMetricDto> metrics) {
}
