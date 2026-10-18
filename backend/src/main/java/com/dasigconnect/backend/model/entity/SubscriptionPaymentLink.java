package com.dasigconnect.backend.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscription_payment_links")
public class SubscriptionPaymentLink {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false) private SubscriptionInvoice invoice;
    @Column(nullable = false, length = 30) private String provider;
    @Column(name = "external_link_id", nullable = false, unique = true) private String externalLinkId;
    @Column(name = "provider_reference_number") private String providerReferenceNumber;
    @Column(name = "checkout_url", nullable = false, columnDefinition = "text") private String checkoutUrl;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private SubscriptionPaymentLinkStatus status;
    @Column(name = "live_mode", nullable = false) private boolean liveMode;
    @Column(name = "archived_at") private Instant archivedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist void onCreate() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (provider == null) provider = "PAYMONGO";
        if (status == null) status = SubscriptionPaymentLinkStatus.ACTIVE;
        createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public SubscriptionInvoice getInvoice() { return invoice; }
    public void setInvoice(SubscriptionInvoice invoice) { this.invoice = invoice; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getExternalLinkId() { return externalLinkId; }
    public void setExternalLinkId(String externalLinkId) { this.externalLinkId = externalLinkId; }
    public String getProviderReferenceNumber() { return providerReferenceNumber; }
    public void setProviderReferenceNumber(String value) { this.providerReferenceNumber = value; }
    public String getCheckoutUrl() { return checkoutUrl; }
    public void setCheckoutUrl(String checkoutUrl) { this.checkoutUrl = checkoutUrl; }
    public SubscriptionPaymentLinkStatus getStatus() { return status; }
    public void setStatus(SubscriptionPaymentLinkStatus status) { this.status = status; }
    public boolean isLiveMode() { return liveMode; }
    public void setLiveMode(boolean liveMode) { this.liveMode = liveMode; }
    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
