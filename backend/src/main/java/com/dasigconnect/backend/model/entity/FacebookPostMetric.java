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
@Table(name = "facebook_post_metrics")
public class FacebookPostMetric {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @Column(name = "facebook_post_id", nullable = false, columnDefinition = "text")
    private String facebookPostId;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(nullable = false)
    private int reactions;

    @Column(nullable = false)
    private int comments;

    @Column(nullable = false)
    private int shares;

    @Column
    private Integer reach;

    @Column
    private Integer impressions;

    @Column(name = "raw_post", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String rawPost;

    @Column(name = "raw_insights", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String rawInsights;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (fetchedAt == null) {
            fetchedAt = now;
        }
        createdAt = now;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Submission getSubmission() { return submission; }
    public void setSubmission(Submission submission) { this.submission = submission; }

    public String getFacebookPostId() { return facebookPostId; }
    public void setFacebookPostId(String facebookPostId) { this.facebookPostId = facebookPostId; }

    public Instant getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Instant fetchedAt) { this.fetchedAt = fetchedAt; }

    public int getReactions() { return reactions; }
    public void setReactions(int reactions) { this.reactions = reactions; }

    public int getComments() { return comments; }
    public void setComments(int comments) { this.comments = comments; }

    public int getShares() { return shares; }
    public void setShares(int shares) { this.shares = shares; }

    public Integer getReach() { return reach; }
    public void setReach(Integer reach) { this.reach = reach; }

    public Integer getImpressions() { return impressions; }
    public void setImpressions(Integer impressions) { this.impressions = impressions; }

    public String getRawPost() { return rawPost; }
    public void setRawPost(String rawPost) { this.rawPost = rawPost; }

    public String getRawInsights() { return rawInsights; }
    public void setRawInsights(String rawInsights) { this.rawInsights = rawInsights; }

    public Instant getCreatedAt() { return createdAt; }
}
