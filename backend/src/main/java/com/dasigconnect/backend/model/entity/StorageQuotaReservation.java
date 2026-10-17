package com.dasigconnect.backend.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "storage_quota_reservations")
public class StorageQuotaReservation {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "institution_id", nullable = false)
    private UUID institutionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "object_key", nullable = false, unique = true, length = 1024)
    private String objectKey;

    @Column(name = "reserved_bytes", nullable = false)
    private long reservedBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StorageQuotaReservationStatus status;

    @Column(name = "media_asset_id")
    private UUID mediaAssetId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = StorageQuotaReservationStatus.RESERVED;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getInstitutionId() { return institutionId; }
    public void setInstitutionId(UUID institutionId) { this.institutionId = institutionId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public long getReservedBytes() { return reservedBytes; }
    public void setReservedBytes(long reservedBytes) { this.reservedBytes = reservedBytes; }
    public StorageQuotaReservationStatus getStatus() { return status; }
    public void setStatus(StorageQuotaReservationStatus status) { this.status = status; }
    public UUID getMediaAssetId() { return mediaAssetId; }
    public void setMediaAssetId(UUID mediaAssetId) { this.mediaAssetId = mediaAssetId; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public void setConsumedAt(Instant consumedAt) { this.consumedAt = consumedAt; }
    public Instant getReleasedAt() { return releasedAt; }
    public void setReleasedAt(Instant releasedAt) { this.releasedAt = releasedAt; }
}
