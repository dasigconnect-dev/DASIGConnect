package com.dasigconnect.backend.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Junction row linking a {@link MediaAlbum} to a {@link MediaAsset} (many-to-many).
 * Surrogate id + unique(album_id, asset_id), mirroring {@link SubmissionMediaAsset}.
 */
@Entity
@Table(name = "album_assets")
public class AlbumAsset {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "album_id", nullable = false)
    private MediaAlbum album;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private MediaAsset asset;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        addedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public MediaAlbum getAlbum() { return album; }
    public void setAlbum(MediaAlbum album) { this.album = album; }

    public MediaAsset getAsset() { return asset; }
    public void setAsset(MediaAsset asset) { this.asset = asset; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public Instant getAddedAt() { return addedAt; }
}
