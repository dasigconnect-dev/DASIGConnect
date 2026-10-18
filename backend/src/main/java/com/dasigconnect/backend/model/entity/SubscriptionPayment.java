package com.dasigconnect.backend.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscription_payments")
public class SubscriptionPayment {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false) private SubscriptionInvoice invoice;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_link_id", nullable = false) private SubscriptionPaymentLink paymentLink;
    @Column(nullable = false, length = 30) private String provider;
    @Column(name = "external_payment_id", nullable = false, unique = true) private String externalPaymentId;
    @Column(name = "amount_centavos", nullable = false) private long amountCentavos;
    @Column(nullable = false, length = 3) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private SubscriptionPaymentStatus status;
    @Column(name = "paid_at", nullable = false) private Instant paidAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist void onCreate() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (provider == null) provider = "PAYMONGO";
        if (currency == null) currency = "PHP";
        if (status == null) status = SubscriptionPaymentStatus.PAID;
        createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public SubscriptionInvoice getInvoice() { return invoice; }
    public void setInvoice(SubscriptionInvoice invoice) { this.invoice = invoice; }
    public SubscriptionPaymentLink getPaymentLink() { return paymentLink; }
    public void setPaymentLink(SubscriptionPaymentLink paymentLink) { this.paymentLink = paymentLink; }
    public String getExternalPaymentId() { return externalPaymentId; }
    public void setExternalPaymentId(String value) { this.externalPaymentId = value; }
    public long getAmountCentavos() { return amountCentavos; }
    public void setAmountCentavos(long value) { this.amountCentavos = value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { this.currency = value; }
    public SubscriptionPaymentStatus getStatus() { return status; }
    public void setStatus(SubscriptionPaymentStatus value) { this.status = value; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant value) { this.paidAt = value; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
