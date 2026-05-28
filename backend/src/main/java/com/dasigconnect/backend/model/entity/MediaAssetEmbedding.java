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

@Entity
@Table(name = "media_asset_embeddings")
public class MediaAssetEmbedding {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private MediaAsset asset;

    @Column(name = "embedding_type", nullable = false, length = 20)
    private String embeddingType;

    // embedding VECTOR(1024) is managed through native repository queries.

    @Column(name = "model", nullable = false, columnDefinition = "text")
    private String model;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public MediaAsset getAsset() { return asset; }
    public void setAsset(MediaAsset asset) { this.asset = asset; }

    public String getEmbeddingType() { return embeddingType; }
    public void setEmbeddingType(String embeddingType) { this.embeddingType = embeddingType; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
