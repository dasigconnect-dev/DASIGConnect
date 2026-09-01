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
    @Column(name = "facebook_page_id", length = 255)
    private String facebookPageId;
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
    public void setUpdatedBy(User value) { updatedBy = value; }
    public Instant getUpdatedAt() { return updatedAt; }
}
