package com.dasigconnect.backend.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "organization_subscriptions")
public class OrganizationSubscription {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, unique = true)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_plan_id", nullable = false)
    private SubscriptionPlan plan;

    @Column(name = "plan_code_snapshot", nullable = false, length = 50)
    private String planCodeSnapshot;

    @Column(name = "plan_version_snapshot", nullable = false)
    private int planVersionSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "entitlements_snapshot", nullable = false, columnDefinition = "jsonb")
    private String entitlementsSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubscriptionLifecycleStatus status;

    @Column(name = "current_period_start", nullable = false)
    private Instant currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    @Column(name = "grace_period_ends_at")
    private Instant gracePeriodEndsAt;

    @Column(name = "external_provider", length = 50)
    private String externalProvider;

    @Column(name = "external_customer_id", length = 255)
    private String externalCustomerId;

    @Column(name = "external_subscription_id", length = 255)
    private String externalSubscriptionId;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }
    public SubscriptionPlan getPlan() { return plan; }
    public void setPlan(SubscriptionPlan plan) { this.plan = plan; }
    public String getPlanCodeSnapshot() { return planCodeSnapshot; }
    public void setPlanCodeSnapshot(String planCodeSnapshot) { this.planCodeSnapshot = planCodeSnapshot; }
    public int getPlanVersionSnapshot() { return planVersionSnapshot; }
    public void setPlanVersionSnapshot(int planVersionSnapshot) { this.planVersionSnapshot = planVersionSnapshot; }
    public String getEntitlementsSnapshot() { return entitlementsSnapshot; }
    public void setEntitlementsSnapshot(String entitlementsSnapshot) { this.entitlementsSnapshot = entitlementsSnapshot; }
    public SubscriptionLifecycleStatus getStatus() { return status; }
    public void setStatus(SubscriptionLifecycleStatus status) { this.status = status; }
    public Instant getCurrentPeriodStart() { return currentPeriodStart; }
    public void setCurrentPeriodStart(Instant currentPeriodStart) { this.currentPeriodStart = currentPeriodStart; }
    public Instant getCurrentPeriodEnd() { return currentPeriodEnd; }
    public void setCurrentPeriodEnd(Instant currentPeriodEnd) { this.currentPeriodEnd = currentPeriodEnd; }
    public Instant getGracePeriodEndsAt() { return gracePeriodEndsAt; }
    public void setGracePeriodEndsAt(Instant gracePeriodEndsAt) { this.gracePeriodEndsAt = gracePeriodEndsAt; }
    public String getExternalProvider() { return externalProvider; }
    public void setExternalProvider(String externalProvider) { this.externalProvider = externalProvider; }
    public String getExternalCustomerId() { return externalCustomerId; }
    public void setExternalCustomerId(String externalCustomerId) { this.externalCustomerId = externalCustomerId; }
    public String getExternalSubscriptionId() { return externalSubscriptionId; }
    public void setExternalSubscriptionId(String externalSubscriptionId) { this.externalSubscriptionId = externalSubscriptionId; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
