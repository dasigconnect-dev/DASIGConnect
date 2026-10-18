package com.dasigconnect.backend.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscription_refunds")
public class SubscriptionRefund {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false) private SubscriptionPayment payment;
    @Column(nullable = false, length = 30) private String provider;
    @Column(name = "external_refund_id", nullable = false, unique = true) private String externalRefundId;
    @Column(name = "amount_centavos", nullable = false) private long amountCentavos;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private SubscriptionRefundStatus status;
    @Column(length = 100) private String reason;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist void onCreate() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (provider == null) provider = "PAYMONGO";
        if (status == null) status = SubscriptionRefundStatus.PENDING;
        createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public SubscriptionPayment getPayment() { return payment; }
    public void setPayment(SubscriptionPayment value) { this.payment = value; }
    public String getExternalRefundId() { return externalRefundId; }
    public void setExternalRefundId(String value) { this.externalRefundId = value; }
    public long getAmountCentavos() { return amountCentavos; }
    public void setAmountCentavos(long value) { this.amountCentavos = value; }
    public SubscriptionRefundStatus getStatus() { return status; }
    public void setStatus(SubscriptionRefundStatus value) { this.status = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { this.reason = value; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
