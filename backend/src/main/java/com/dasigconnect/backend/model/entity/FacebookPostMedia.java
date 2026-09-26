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

@Entity
@Table(name = "facebook_post_media")
public class FacebookPostMedia {

    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facebook_historical_post_id", nullable = false)
    private FacebookHistoricalPost historicalPost;
    @Column(name = "page_id", nullable = false, length = 100)
    private String pageId;
    @Column(name = "graph_media_id", nullable = false, length = 150)
    private String graphMediaId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_asset_id")
    private MediaAsset mediaAsset;
    @Column(nullable = false)
    private int position;
    @Column(name = "media_type", nullable = false, length = 50)
    private String mediaType;
    private Integer width;
    private Integer height;
    @Column(name = "source_alt_text", columnDefinition = "text")
    private String sourceAltText;
    @Column(name = "source_url_expires_at")
    private Instant sourceUrlExpiresAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public FacebookHistoricalPost getHistoricalPost() { return historicalPost; }
    public void setHistoricalPost(FacebookHistoricalPost value) { this.historicalPost = value; }
    public String getPageId() { return pageId; }
    public void setPageId(String pageId) { this.pageId = pageId; }
    public String getGraphMediaId() { return graphMediaId; }
    public void setGraphMediaId(String graphMediaId) { this.graphMediaId = graphMediaId; }
    public MediaAsset getMediaAsset() { return mediaAsset; }
    public void setMediaAsset(MediaAsset mediaAsset) { this.mediaAsset = mediaAsset; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }
    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }
    public String getSourceAltText() { return sourceAltText; }
    public void setSourceAltText(String sourceAltText) { this.sourceAltText = sourceAltText; }
    public Instant getSourceUrlExpiresAt() { return sourceUrlExpiresAt; }
    public void setSourceUrlExpiresAt(Instant value) { this.sourceUrlExpiresAt = value; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
