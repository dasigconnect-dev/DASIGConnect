package com.dasigconnect.backend.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "paymongo_webhook_receipts")
public class PaymongoWebhookReceipt {
    @Id private UUID id;
    @Column(name = "external_event_id", nullable = false, unique = true) private String externalEventId;
    @Column(name = "event_type", nullable = false, length = 100) private String eventType;
    @Column(name = "live_mode", nullable = false) private boolean liveMode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private PaymongoWebhookReceiptStatus status;
    @Column(name = "payload_sha256", nullable = false, length = 64) private String payloadSha256;
    @Column(name = "received_at", nullable = false, updatable = false) private Instant receivedAt;
    @Column(name = "processed_at") private Instant processedAt;
    @Column(name = "error_detail", length = 1000) private String errorDetail;

    @PrePersist void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = PaymongoWebhookReceiptStatus.RECEIVED;
        if (receivedAt == null) receivedAt = Instant.now();
    }
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getExternalEventId() { return externalEventId; }
    public void setExternalEventId(String value) { this.externalEventId = value; }
    public String getEventType() { return eventType; }
    public void setEventType(String value) { this.eventType = value; }
    public boolean isLiveMode() { return liveMode; }
    public void setLiveMode(boolean value) { this.liveMode = value; }
    public PaymongoWebhookReceiptStatus getStatus() { return status; }
    public void setStatus(PaymongoWebhookReceiptStatus value) { this.status = value; }
    public String getPayloadSha256() { return payloadSha256; }
    public void setPayloadSha256(String value) { this.payloadSha256 = value; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant value) { this.processedAt = value; }
    public String getErrorDetail() { return errorDetail; }
    public void setErrorDetail(String value) { this.errorDetail = value; }
}
