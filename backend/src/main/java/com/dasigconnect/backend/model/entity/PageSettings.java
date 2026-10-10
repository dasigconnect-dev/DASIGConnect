package com.dasigconnect.backend.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "page_settings")
public class PageSettings {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id")
    private Institution institution;
    // Legacy — the app no longer reads or writes these. Watermark on/off + layout
    // live in WatermarkConfiguration (WatermarkApplicationService). Columns kept
    // (default false / null) to avoid a migration; drop in a future cleanup.
    @Column(name = "watermark_enabled", nullable = false)
    private boolean watermarkEnabled;
    @Column(name = "watermark_text", length = 150)
    private String watermarkText;
    // Legacy — never read by anything that actually publishes. The live
    // publishing target is FacebookPageToken.pageId (bootstrapped from
    // app.facebook.page-id, managed in System Health -> Tokens). Column kept
    // (unused) to avoid a migration; drop in a future cleanup, same as the
    // watermark columns above.
    @Column(name = "facebook_page_id", length = 255)
    private String facebookPageId;
    /**
     * Network-wide scheduling guard-rail switch. Only meaningful on the
     * no-institution row (institution_id IS NULL); see GuardRailSettingsService.
     */
    @Column(name = "guardrails_enforced", nullable = false)
    private boolean guardrailsEnforced = true;
    @Column(name = "posting_window_start_hour", nullable = false)
    private int postingWindowStartHour = 8;
    @Column(name = "posting_window_end_hour", nullable = false)
    private int postingWindowEndHour = 20;
    
    @Column(name = "conflict_buffer_minutes", nullable = false)
    private int conflictBufferMinutes = 30;
    
    @Column(name = "minimum_lead_time_hours", nullable = false)
    private int minimumLeadTimeHours = 2;
    
    @Column(name = "maximum_lead_time_days", nullable = false)
    private int maximumLeadTimeDays = 30;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist void create() { if (id == null) id = UUID.randomUUID(); updatedAt = Instant.now(); }
    @PreUpdate void update() { updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public Institution getInstitution() { return institution; }
    public void setInstitution(Institution institution) { this.institution = institution; }
    public boolean isWatermarkEnabled() { return watermarkEnabled; }
    public void setWatermarkEnabled(boolean value) { watermarkEnabled = value; }
    public String getWatermarkText() { return watermarkText; }
    public void setWatermarkText(String value) { watermarkText = value; }
    public String getFacebookPageId() { return facebookPageId; }
    public void setFacebookPageId(String value) { facebookPageId = value; }
    public boolean isGuardrailsEnforced() { return guardrailsEnforced; }
    public void setGuardrailsEnforced(boolean value) { guardrailsEnforced = value; }
    public int getPostingWindowStartHour() { return postingWindowStartHour; }
    public void setPostingWindowStartHour(int value) { postingWindowStartHour = value; }
    public int getPostingWindowEndHour() { return postingWindowEndHour; }
    public void setPostingWindowEndHour(int value) { postingWindowEndHour = value; }
    
    public int getConflictBufferMinutes() { return conflictBufferMinutes; }
    public void setConflictBufferMinutes(int value) { conflictBufferMinutes = value; }
    
    public int getMinimumLeadTimeHours() { return minimumLeadTimeHours; }
    public void setMinimumLeadTimeHours(int value) { minimumLeadTimeHours = value; }
    
    public int getMaximumLeadTimeDays() { return maximumLeadTimeDays; }
    public void setMaximumLeadTimeDays(int value) { maximumLeadTimeDays = value; }
    public void setUpdatedBy(User value) { updatedBy = value; }
    public Instant getUpdatedAt() { return updatedAt; }
}
