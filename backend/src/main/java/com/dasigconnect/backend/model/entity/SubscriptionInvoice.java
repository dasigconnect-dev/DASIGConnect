package com.dasigconnect.backend.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscription_invoices")
public class SubscriptionInvoice {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_subscription_id", nullable = false)
    private OrganizationSubscription subscription;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_plan_id", nullable = false)
    private SubscriptionPlan plan;
    @Column(name = "invoice_number", nullable = false, unique = true, length = 50)
    private String invoiceNumber;
    @Column(name = "amount_centavos", nullable = false) private long amountCentavos;
    @Column(nullable = false, length = 3) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private SubscriptionInvoiceStatus status;
    @Column(name = "billing_period_start", nullable = false) private Instant billingPeriodStart;
    @Column(name = "billing_period_end", nullable = false) private Instant billingPeriodEnd;
    @Column(name = "due_at", nullable = false) private Instant dueAt;
    @Column(name = "paid_at") private Instant paidAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by") private User createdBy;
    @Version @Column(nullable = false) private long version;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist void onCreate() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (currency == null) currency = "PHP";
        if (status == null) status = SubscriptionInvoiceStatus.OPEN;
        createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public OrganizationSubscription getSubscription() { return subscription; }
    public void setSubscription(OrganizationSubscription subscription) { this.subscription = subscription; }
    public SubscriptionPlan getPlan() { return plan; }
    public void setPlan(SubscriptionPlan plan) { this.plan = plan; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public long getAmountCentavos() { return amountCentavos; }
    public void setAmountCentavos(long amountCentavos) { this.amountCentavos = amountCentavos; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public SubscriptionInvoiceStatus getStatus() { return status; }
    public void setStatus(SubscriptionInvoiceStatus status) { this.status = status; }
    public Instant getBillingPeriodStart() { return billingPeriodStart; }
    public void setBillingPeriodStart(Instant billingPeriodStart) { this.billingPeriodStart = billingPeriodStart; }
    public Instant getBillingPeriodEnd() { return billingPeriodEnd; }
    public void setBillingPeriodEnd(Instant billingPeriodEnd) { this.billingPeriodEnd = billingPeriodEnd; }
    public Instant getDueAt() { return dueAt; }
    public void setDueAt(Instant dueAt) { this.dueAt = dueAt; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
