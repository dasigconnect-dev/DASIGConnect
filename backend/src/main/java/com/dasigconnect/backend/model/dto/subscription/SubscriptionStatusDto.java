package com.dasigconnect.backend.model.dto.subscription;

import com.dasigconnect.backend.model.entity.OrganizationStatus;
import com.dasigconnect.backend.model.entity.SubscriptionLifecycleStatus;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionStatusDto(
        UUID organizationId,
        String organizationName,
        String organizationSlug,
        OrganizationStatus organizationStatus,
        UUID subscriptionId,
        SubscriptionLifecycleStatus subscriptionStatus,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        Instant gracePeriodEndsAt,
        SubscriptionPlanDto plan,
        boolean enforcementEnabled) {
}
