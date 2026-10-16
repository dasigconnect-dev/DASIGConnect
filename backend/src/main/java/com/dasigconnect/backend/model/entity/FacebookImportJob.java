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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "facebook_import_jobs")
public class FacebookImportJob {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facebook_page_token_id", nullable = false)
    private FacebookPageToken pageToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scope_institution_id")
    private Institution scopeInstitution;

    @Column(name = "page_id", nullable = false, length = 100)
    private String pageId;

    @Column(name = "page_name", nullable = false, length = 255)
    private String pageName;

    @Column(name = "date_from", nullable = false)
    private Instant dateFrom;

    @Column(name = "date_to", nullable = false)
    private Instant dateTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FacebookImportMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FacebookImportStatus status = FacebookImportStatus.QUEUED;

    @Column(name = "after_cursor", columnDefinition = "text")
    private String afterCursor;

    @Column(name = "posts_discovered", nullable = false)
    private long postsDiscovered;
    @Column(name = "posts_imported", nullable = false)
    private long postsImported;
    @Column(name = "posts_updated", nullable = false)
    private long postsUpdated;
    @Column(name = "media_discovered", nullable = false)
    private long mediaDiscovered;
    @Column(name = "media_imported", nullable = false)
    private long mediaImported;
    @Column(name = "media_failed", nullable = false)
    private long mediaFailed;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "started_by_user_id", nullable = false)
    private User startedBy;

    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "error_code", length = 80)
    private String errorCode;
    @Column(name = "error_message", length = 500)
    private String errorMessage;
    @Column(name = "graph_api_version", nullable = false, length = 20)
    private String graphApiVersion;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = FacebookImportStatus.QUEUED;
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public FacebookPageToken getPageToken() { return pageToken; }
    public void setPageToken(FacebookPageToken pageToken) { this.pageToken = pageToken; }
    public Institution getScopeInstitution() { return scopeInstitution; }
    public void setScopeInstitution(Institution scopeInstitution) { this.scopeInstitution = scopeInstitution; }
    public String getPageId() { return pageId; }
    public void setPageId(String pageId) { this.pageId = pageId; }
    public String getPageName() { return pageName; }
    public void setPageName(String pageName) { this.pageName = pageName; }
    public Instant getDateFrom() { return dateFrom; }
    public void setDateFrom(Instant dateFrom) { this.dateFrom = dateFrom; }
    public Instant getDateTo() { return dateTo; }
    public void setDateTo(Instant dateTo) { this.dateTo = dateTo; }
    public FacebookImportMode getMode() { return mode; }
    public void setMode(FacebookImportMode mode) { this.mode = mode; }
    public FacebookImportStatus getStatus() { return status; }
    public void setStatus(FacebookImportStatus status) { this.status = status; }
    public String getAfterCursor() { return afterCursor; }
    public void setAfterCursor(String afterCursor) { this.afterCursor = afterCursor; }
    public long getPostsDiscovered() { return postsDiscovered; }
    public void setPostsDiscovered(long value) { this.postsDiscovered = value; }
    public long getPostsImported() { return postsImported; }
    public void setPostsImported(long value) { this.postsImported = value; }
    public long getPostsUpdated() { return postsUpdated; }
    public void setPostsUpdated(long value) { this.postsUpdated = value; }
    public long getMediaDiscovered() { return mediaDiscovered; }
    public void setMediaDiscovered(long value) { this.mediaDiscovered = value; }
    public long getMediaImported() { return mediaImported; }
    public void setMediaImported(long value) { this.mediaImported = value; }
    public long getMediaFailed() { return mediaFailed; }
    public void setMediaFailed(long value) { this.mediaFailed = value; }
    public User getStartedBy() { return startedBy; }
    public void setStartedBy(User startedBy) { this.startedBy = startedBy; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getHeartbeatAt() { return heartbeatAt; }
    public void setHeartbeatAt(Instant heartbeatAt) { this.heartbeatAt = heartbeatAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getGraphApiVersion() { return graphApiVersion; }
    public void setGraphApiVersion(String graphApiVersion) { this.graphApiVersion = graphApiVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
