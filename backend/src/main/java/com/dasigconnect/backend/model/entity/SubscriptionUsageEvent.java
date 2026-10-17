package com.dasigconnect.backend.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscription_usage_events")
public class SubscriptionUsageEvent {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "institution_id")
    private UUID institutionId;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 60)
    private SubscriptionUsageEventType eventType;

    @Column(nullable = false)
    private long quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionUsageUnit unit;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    @Column(name = "source_type", length = 60)
    private String sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getInstitutionId() { return institutionId; }
    public UUID getUserId() { return userId; }
    public SubscriptionUsageEventType getEventType() { return eventType; }
    public long getQuantity() { return quantity; }
    public SubscriptionUsageUnit getUnit() { return unit; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getSourceType() { return sourceType; }
    public UUID getSourceId() { return sourceId; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getCreatedAt() { return createdAt; }
}
