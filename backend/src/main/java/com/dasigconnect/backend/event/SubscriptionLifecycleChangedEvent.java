package com.dasigconnect.backend.event;

import com.dasigconnect.backend.model.entity.SubscriptionLifecycleStatus;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionLifecycleChangedEvent(
        UUID subscriptionId,
        String organizationName,
        SubscriptionLifecycleStatus previousStatus,
        SubscriptionLifecycleStatus currentStatus,
        Instant currentPeriodEnd,
        Instant gracePeriodEndsAt) {
}
