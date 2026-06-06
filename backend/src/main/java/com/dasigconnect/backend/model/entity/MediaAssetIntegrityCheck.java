package com.dasigconnect.backend.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_asset_integrity_checks")
public class MediaAssetIntegrityCheck {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private MediaAsset asset;

    @Column(name = "expected_sha256", length = 64)
    private String expectedSha256;

    @Column(name = "observed_sha256", length = 64)
    private String observedSha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaIntegrityStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 30)
    private MediaIntegrityTrigger triggerType;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "checked_at", nullable = false, updatable = false)
    private Instant checkedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (checkedAt == null) checkedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Institution getInstitution() { return institution; }
    public void setInstitution(Institution institution) { this.institution = institution; }
    public MediaAsset getAsset() { return asset; }
    public void setAsset(MediaAsset asset) { this.asset = asset; }
    public String getExpectedSha256() { return expectedSha256; }
    public void setExpectedSha256(String expectedSha256) { this.expectedSha256 = expectedSha256; }
    public String getObservedSha256() { return observedSha256; }
    public void setObservedSha256(String observedSha256) { this.observedSha256 = observedSha256; }
    public MediaIntegrityStatus getStatus() { return status; }
    public void setStatus(MediaIntegrityStatus status) { this.status = status; }
    public MediaIntegrityTrigger getTriggerType() { return triggerType; }
    public void setTriggerType(MediaIntegrityTrigger triggerType) { this.triggerType = triggerType; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public Instant getCheckedAt() { return checkedAt; }
    public void setCheckedAt(Instant checkedAt) { this.checkedAt = checkedAt; }
}
