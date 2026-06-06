package com.dasigconnect.backend.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * UC-4.12 Phase 7E: a lineage edge between two assets. {@code parent} is the source/original,
 * {@code child} is the version or derivative. Asset ids are plain UUID columns (no lazy
 * relations) because lineage is resolved with explicit, tenant-scoped queries.
 */
@Entity
@Table(name = "media_asset_relations")
public class MediaAssetRelation {

    @Id
    private UUID id;

    @Column(name = "institution_id", nullable = false)
    private UUID institutionId;

    @Column(name = "parent_asset_id", nullable = false)
    private UUID parentAssetId;

    @Column(name = "child_asset_id", nullable = false)
    private UUID childAssetId;

    @Column(name = "relation_type", nullable = false, length = 20)
    private String relationType;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getInstitutionId() { return institutionId; }
    public void setInstitutionId(UUID institutionId) { this.institutionId = institutionId; }
    public UUID getParentAssetId() { return parentAssetId; }
    public void setParentAssetId(UUID parentAssetId) { this.parentAssetId = parentAssetId; }
    public UUID getChildAssetId() { return childAssetId; }
    public void setChildAssetId(UUID childAssetId) { this.childAssetId = childAssetId; }
    public String getRelationType() { return relationType; }
    public void setRelationType(String relationType) { this.relationType = relationType; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
