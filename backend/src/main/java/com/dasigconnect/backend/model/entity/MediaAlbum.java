package com.dasigconnect.backend.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A curated, many-to-many collection of assets (Google Photos model). An asset can belong
 * to several albums without moving from its folder. {@code source} is {@code manual} for
 * user-created albums; Phase 2 AI auto-grouping writes {@code ai_suggested} albums into the
 * same structure. See ADR-0004.
 */
@Entity
@Table(name = "media_albums")
public class MediaAlbum {

    public static final String SOURCE_MANUAL = "manual";
    public static final String SOURCE_AI_SUGGESTED = "ai_suggested";

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @Column(name = "name", nullable = false, columnDefinition = "text")
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "source", nullable = false, columnDefinition = "text")
    private String source = SOURCE_MANUAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cover_asset_id")
    private MediaAsset coverAsset;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Institution getInstitution() { return institution; }
    public void setInstitution(Institution institution) { this.institution = institution; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public MediaAsset getCoverAsset() { return coverAsset; }
    public void setCoverAsset(MediaAsset coverAsset) { this.coverAsset = coverAsset; }

    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}
