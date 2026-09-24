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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "facebook_historical_posts")
public class FacebookHistoricalPost {

    @Id
    private UUID id;
    @Column(name = "page_id", nullable = false, length = 100)
    private String pageId;
    @Column(name = "graph_post_id", nullable = false, length = 150)
    private String graphPostId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id")
    private Institution institution;
    @Column(columnDefinition = "text")
    private String message;
    @Column(name = "permalink_url", columnDefinition = "text")
    private String permalinkUrl;
    @Column(name = "created_time", nullable = false)
    private Instant createdTime;
    @Column(name = "updated_time")
    private Instant updatedTime;
    @Column(name = "post_type", length = 50)
    private String postType;
    @Column(name = "media_type", length = 50)
    private String mediaType;
    @Column(name = "attachment_count", nullable = false)
    private int attachmentCount;
    @Column(name = "is_published", nullable = false)
    private boolean published = true;
    @Column(name = "is_deleted_at_source", nullable = false)
    private boolean deletedAtSource;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_metadata", nullable = false, columnDefinition = "jsonb")
    private String sourceMetadata = "{}";
    @Column(name = "first_imported_at", nullable = false, updatable = false)
    private Instant firstImportedAt;
    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        if (sourceMetadata == null || sourceMetadata.isBlank()) sourceMetadata = "{}";
        if (firstImportedAt == null) firstImportedAt = now;
        if (lastSyncedAt == null) lastSyncedAt = now;
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getPageId() { return pageId; }
    public void setPageId(String pageId) { this.pageId = pageId; }
    public String getGraphPostId() { return graphPostId; }
    public void setGraphPostId(String graphPostId) { this.graphPostId = graphPostId; }
    public Institution getInstitution() { return institution; }
    public void setInstitution(Institution institution) { this.institution = institution; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getPermalinkUrl() { return permalinkUrl; }
    public void setPermalinkUrl(String permalinkUrl) { this.permalinkUrl = permalinkUrl; }
    public Instant getCreatedTime() { return createdTime; }
    public void setCreatedTime(Instant createdTime) { this.createdTime = createdTime; }
    public Instant getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(Instant updatedTime) { this.updatedTime = updatedTime; }
    public String getPostType() { return postType; }
    public void setPostType(String postType) { this.postType = postType; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    public int getAttachmentCount() { return attachmentCount; }
    public void setAttachmentCount(int attachmentCount) { this.attachmentCount = attachmentCount; }
    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }
    public boolean isDeletedAtSource() { return deletedAtSource; }
    public void setDeletedAtSource(boolean value) { this.deletedAtSource = value; }
    public String getSourceMetadata() { return sourceMetadata; }
    public void setSourceMetadata(String sourceMetadata) { this.sourceMetadata = sourceMetadata; }
    public Instant getFirstImportedAt() { return firstImportedAt; }
    public void setFirstImportedAt(Instant value) { this.firstImportedAt = value; }
    public Instant getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(Instant value) { this.lastSyncedAt = value; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
