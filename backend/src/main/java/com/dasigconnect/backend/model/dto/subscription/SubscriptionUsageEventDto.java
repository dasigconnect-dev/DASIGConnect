package com.dasigconnect.backend.model.dto.subscription;

import com.dasigconnect.backend.model.entity.SubscriptionUsageEvent;
import com.dasigconnect.backend.model.entity.SubscriptionUsageEventType;
import com.dasigconnect.backend.model.entity.SubscriptionUsageUnit;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionUsageEventDto(
        UUID id,
        UUID organizationId,
        UUID institutionId,
        UUID userId,
        SubscriptionUsageEventType eventType,
        long quantity,
        SubscriptionUsageUnit unit,
        String sourceType,
        UUID sourceId,
        Instant occurredAt) {

    public static SubscriptionUsageEventDto from(SubscriptionUsageEvent event) {
        return new SubscriptionUsageEventDto(
                event.getId(),
                event.getOrganizationId(),
                event.getInstitutionId(),
                event.getUserId(),
                event.getEventType(),
                event.getQuantity(),
                event.getUnit(),
                event.getSourceType(),
                event.getSourceId(),
                event.getOccurredAt());
    }
}
