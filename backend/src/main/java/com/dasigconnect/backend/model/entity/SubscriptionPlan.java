package com.dasigconnect.backend.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlan {

    @Id
    private UUID id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int version;

    @Column(name = "monthly_price_centavos", nullable = false)
    private long monthlyPriceCentavos;

    @Column(name = "institution_limit", nullable = false)
    private int institutionLimit;

    @Column(name = "active_user_limit", nullable = false)
    private int activeUserLimit;

    @Column(name = "media_storage_bytes", nullable = false)
    private long mediaStorageBytes;

    @Column(name = "monthly_submission_limit", nullable = false)
    private int monthlySubmissionLimit;

    @Column(name = "monthly_ai_caption_limit", nullable = false)
    private int monthlyAiCaptionLimit;

    @Column(name = "monthly_ai_media_suggestion_limit", nullable = false)
    private int monthlyAiMediaSuggestionLimit;

    @Column(name = "per_user_daily_caption_limit", nullable = false)
    private int perUserDailyCaptionLimit;

    @Column(name = "analytics_retention_days")
    private Integer analyticsRetentionDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "export_level", nullable = false, length = 30)
    private SubscriptionExportLevel exportLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "support_level", nullable = false, length = 30)
    private SubscriptionSupportLevel supportLevel;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_until")
    private Instant effectiveUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public long getMonthlyPriceCentavos() { return monthlyPriceCentavos; }
    public void setMonthlyPriceCentavos(long monthlyPriceCentavos) { this.monthlyPriceCentavos = monthlyPriceCentavos; }
    public int getInstitutionLimit() { return institutionLimit; }
    public void setInstitutionLimit(int institutionLimit) { this.institutionLimit = institutionLimit; }
    public int getActiveUserLimit() { return activeUserLimit; }
    public void setActiveUserLimit(int activeUserLimit) { this.activeUserLimit = activeUserLimit; }
    public long getMediaStorageBytes() { return mediaStorageBytes; }
    public void setMediaStorageBytes(long mediaStorageBytes) { this.mediaStorageBytes = mediaStorageBytes; }
    public int getMonthlySubmissionLimit() { return monthlySubmissionLimit; }
    public void setMonthlySubmissionLimit(int monthlySubmissionLimit) { this.monthlySubmissionLimit = monthlySubmissionLimit; }
    public int getMonthlyAiCaptionLimit() { return monthlyAiCaptionLimit; }
    public void setMonthlyAiCaptionLimit(int monthlyAiCaptionLimit) { this.monthlyAiCaptionLimit = monthlyAiCaptionLimit; }
    public int getMonthlyAiMediaSuggestionLimit() { return monthlyAiMediaSuggestionLimit; }
    public void setMonthlyAiMediaSuggestionLimit(int monthlyAiMediaSuggestionLimit) { this.monthlyAiMediaSuggestionLimit = monthlyAiMediaSuggestionLimit; }
    public int getPerUserDailyCaptionLimit() { return perUserDailyCaptionLimit; }
    public void setPerUserDailyCaptionLimit(int perUserDailyCaptionLimit) { this.perUserDailyCaptionLimit = perUserDailyCaptionLimit; }
    public Integer getAnalyticsRetentionDays() { return analyticsRetentionDays; }
    public void setAnalyticsRetentionDays(Integer analyticsRetentionDays) { this.analyticsRetentionDays = analyticsRetentionDays; }
    public SubscriptionExportLevel getExportLevel() { return exportLevel; }
    public void setExportLevel(SubscriptionExportLevel exportLevel) { this.exportLevel = exportLevel; }
    public SubscriptionSupportLevel getSupportLevel() { return supportLevel; }
    public void setSupportLevel(SubscriptionSupportLevel supportLevel) { this.supportLevel = supportLevel; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(Instant effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public Instant getEffectiveUntil() { return effectiveUntil; }
    public void setEffectiveUntil(Instant effectiveUntil) { this.effectiveUntil = effectiveUntil; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
