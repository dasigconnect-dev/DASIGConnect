package com.dasigconnect.backend.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * UC-4.12 Phase 7D: the current rights/consent record for one media asset (unique per asset).
 * Lightweight by design — institution and asset are stored as plain UUIDs (no lazy relations)
 * because this row is read/written on its own, never navigated from.
 */
@Entity
@Table(name = "media_asset_rights")
public class MediaAssetRights {

    /** Number of days before {@code expiresAt} that a record counts as "expiring soon". */
    public static final long EXPIRING_SOON_DAYS = 30;

    @Id
    private UUID id;

    @Column(name = "institution_id", nullable = false)
    private UUID institutionId;

    @Column(name = "asset_id", nullable = false, unique = true)
    private UUID assetId;

    @Column(name = "rights_holder", columnDefinition = "text")
    private String rightsHolder;

    @Column(name = "rights_basis", nullable = false, length = 20)
    private String rightsBasis;

    @Column(name = "license_uri", columnDefinition = "text")
    private String licenseUri;

    @Column(name = "consent_reference", columnDefinition = "text")
    private String consentReference;

    @Column(name = "clearance_date")
    private LocalDate clearanceDate;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Column(name = "permitted_channels")
    private String[] permittedChannels;

    @Column(name = "restrictions", columnDefinition = "text")
    private String restrictions;

    @Column(name = "supporting_document_url", columnDefinition = "text")
    private String supportingDocumentUrl;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (rightsBasis == null) rightsBasis = "unknown";
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** A clear, attributable basis: a holder is named and the basis is not "unknown". */
    public boolean isComplete() {
        return rightsHolder != null && !rightsHolder.isBlank()
                && rightsBasis != null && !rightsBasis.isBlank()
                && !"unknown".equalsIgnoreCase(rightsBasis);
    }

    public boolean isExpired(LocalDate today) {
        return expiresAt != null && expiresAt.isBefore(today);
    }

    /** The publishing gate: a complete, non-expired record may clear an asset for public use. */
    public boolean permitsPublicClearance(LocalDate today) {
        return isComplete() && !isExpired(today);
    }

    /** UI/Repository-Health state: {@code incomplete | expired | expiring_soon | cleared}. */
    public String derivedState(LocalDate today) {
        if (!isComplete()) return "incomplete";
        if (isExpired(today)) return "expired";
        if (expiresAt != null && !expiresAt.isAfter(today.plusDays(EXPIRING_SOON_DAYS))) {
            return "expiring_soon";
        }
        return "cleared";
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getInstitutionId() { return institutionId; }
    public void setInstitutionId(UUID institutionId) { this.institutionId = institutionId; }
    public UUID getAssetId() { return assetId; }
    public void setAssetId(UUID assetId) { this.assetId = assetId; }
    public String getRightsHolder() { return rightsHolder; }
    public void setRightsHolder(String rightsHolder) { this.rightsHolder = rightsHolder; }
    public String getRightsBasis() { return rightsBasis; }
    public void setRightsBasis(String rightsBasis) { this.rightsBasis = rightsBasis; }
    public String getLicenseUri() { return licenseUri; }
    public void setLicenseUri(String licenseUri) { this.licenseUri = licenseUri; }
    public String getConsentReference() { return consentReference; }
    public void setConsentReference(String consentReference) { this.consentReference = consentReference; }
    public LocalDate getClearanceDate() { return clearanceDate; }
    public void setClearanceDate(LocalDate clearanceDate) { this.clearanceDate = clearanceDate; }
    public LocalDate getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDate expiresAt) { this.expiresAt = expiresAt; }
    public String[] getPermittedChannels() { return permittedChannels; }
    public void setPermittedChannels(String[] permittedChannels) { this.permittedChannels = permittedChannels; }
    public String getRestrictions() { return restrictions; }
    public void setRestrictions(String restrictions) { this.restrictions = restrictions; }
    public String getSupportingDocumentUrl() { return supportingDocumentUrl; }
    public void setSupportingDocumentUrl(String supportingDocumentUrl) { this.supportingDocumentUrl = supportingDocumentUrl; }
    public UUID getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(UUID verifiedBy) { this.verifiedBy = verifiedBy; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
