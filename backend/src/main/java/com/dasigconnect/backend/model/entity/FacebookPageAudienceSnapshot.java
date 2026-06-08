package com.dasigconnect.backend.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * UC-4.8 Phase 5b: a page-level audience snapshot for the DASIG Facebook Page. Append-only
 * time series — followers/fan count drive the growth trend; the JSON demographic columns are
 * best-effort (null when Meta's deprecated page_fans_* metrics are unavailable).
 */
@Entity
@Table(name = "facebook_page_audience_snapshots")
public class FacebookPageAudienceSnapshot {

    @Id
    private UUID id;

    @Column(name = "page_id", nullable = false, columnDefinition = "text")
    private String pageId;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "followers_count")
    private Integer followersCount;

    @Column(name = "fan_count")
    private Integer fanCount;

    @Column(name = "age_gender", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String ageGender;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String countries;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String cities;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String raw;

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

    public String getPageId() { return pageId; }
    public void setPageId(String pageId) { this.pageId = pageId; }

    public Instant getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Instant fetchedAt) { this.fetchedAt = fetchedAt; }

    public Integer getFollowersCount() { return followersCount; }
    public void setFollowersCount(Integer followersCount) { this.followersCount = followersCount; }

    public Integer getFanCount() { return fanCount; }
    public void setFanCount(Integer fanCount) { this.fanCount = fanCount; }

    public String getAgeGender() { return ageGender; }
    public void setAgeGender(String ageGender) { this.ageGender = ageGender; }

    public String getCountries() { return countries; }
    public void setCountries(String countries) { this.countries = countries; }

    public String getCities() { return cities; }
    public void setCities(String cities) { this.cities = cities; }

    public String getRaw() { return raw; }
    public void setRaw(String raw) { this.raw = raw; }

    public Instant getCreatedAt() { return createdAt; }
}
