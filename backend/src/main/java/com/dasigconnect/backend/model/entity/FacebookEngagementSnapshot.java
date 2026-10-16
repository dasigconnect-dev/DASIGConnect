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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "facebook_engagement_snapshots")
public class FacebookEngagementSnapshot {

    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facebook_historical_post_id", nullable = false)
    private FacebookHistoricalPost historicalPost;
    @Column(name = "page_id", nullable = false, length = 100)
    private String pageId;
    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;
    @Column(name = "reactions_total")
    private Long reactionsTotal;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reaction_breakdown", nullable = false, columnDefinition = "jsonb")
    private String reactionBreakdown = "{}";
    @Column(name = "comments_count")
    private Long commentsCount;
    @Column(name = "shares_count")
    private Long sharesCount;
    private Long reach;
    private Long impressions;
    @Column(name = "post_clicks")
    private Long postClicks;
    @Column(name = "link_clicks")
    private Long linkClicks;
    @Column(name = "photo_views")
    private Long photoViews;
    @Column(name = "video_views")
    private Long videoViews;
    @Column(name = "follower_count")
    private Long followerCount;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "organic_metrics", nullable = false, columnDefinition = "jsonb")
    private String organicMetrics = "{}";
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "paid_metrics", nullable = false, columnDefinition = "jsonb")
    private String paidMetrics = "{}";
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metric_availability", nullable = false, columnDefinition = "jsonb")
    private String metricAvailability = "{}";
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        if (fetchedAt == null) fetchedAt = now;
        if (createdAt == null) createdAt = now;
        reactionBreakdown = jsonOrEmpty(reactionBreakdown);
        organicMetrics = jsonOrEmpty(organicMetrics);
        paidMetrics = jsonOrEmpty(paidMetrics);
        metricAvailability = jsonOrEmpty(metricAvailability);
    }

    private static String jsonOrEmpty(String value) {
        return value == null || value.isBlank() ? "{}" : value;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public FacebookHistoricalPost getHistoricalPost() { return historicalPost; }
    public void setHistoricalPost(FacebookHistoricalPost value) { this.historicalPost = value; }
    public String getPageId() { return pageId; }
    public void setPageId(String pageId) { this.pageId = pageId; }
    public Instant getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Instant fetchedAt) { this.fetchedAt = fetchedAt; }
    public Long getReactionsTotal() { return reactionsTotal; }
    public void setReactionsTotal(Long value) { this.reactionsTotal = value; }
    public String getReactionBreakdown() { return reactionBreakdown; }
    public void setReactionBreakdown(String value) { this.reactionBreakdown = value; }
    public Long getCommentsCount() { return commentsCount; }
    public void setCommentsCount(Long value) { this.commentsCount = value; }
    public Long getSharesCount() { return sharesCount; }
    public void setSharesCount(Long value) { this.sharesCount = value; }
    public Long getReach() { return reach; }
    public void setReach(Long value) { this.reach = value; }
    public Long getImpressions() { return impressions; }
    public void setImpressions(Long value) { this.impressions = value; }
    public Long getPostClicks() { return postClicks; }
    public void setPostClicks(Long value) { this.postClicks = value; }
    public Long getLinkClicks() { return linkClicks; }
    public void setLinkClicks(Long value) { this.linkClicks = value; }
    public Long getPhotoViews() { return photoViews; }
    public void setPhotoViews(Long value) { this.photoViews = value; }
    public Long getVideoViews() { return videoViews; }
    public void setVideoViews(Long value) { this.videoViews = value; }
    public Long getFollowerCount() { return followerCount; }
    public void setFollowerCount(Long value) { this.followerCount = value; }
    public String getOrganicMetrics() { return organicMetrics; }
    public void setOrganicMetrics(String value) { this.organicMetrics = value; }
    public String getPaidMetrics() { return paidMetrics; }
    public void setPaidMetrics(String value) { this.paidMetrics = value; }
    public String getMetricAvailability() { return metricAvailability; }
    public void setMetricAvailability(String value) { this.metricAvailability = value; }
    public Instant getCreatedAt() { return createdAt; }
}
