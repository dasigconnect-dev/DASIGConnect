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
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "media_assets")
public class MediaAsset {

    @Id
    private UUID id;

    // Nullable: a STAGED asset (uploaded to a draft submission) has no institution
    // until the submission is submitted for review. See V73__media_asset_staging.sql.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id")
    private Institution institution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploader_id", nullable = false)
    private User uploader;

    @Column(name = "asset_code", nullable = false, unique = true, length = 50)
    private String assetCode;

    @Column(name = "storage_url", nullable = false, columnDefinition = "text")
    private String storageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_album_id")
    private MediaAlbum mediaAlbum;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    /**
     * Optional display name, independent of {@link #fileName} and the R2
     * storage key (which stays keyed by asset id + original filename and is
     * never touched by a rename). Null means "use fileName" — see
     * {@link #getDisplayTitle()}.
     */
    @Column(name = "display_title", length = 255)
    private String displayTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 10)
    private MediaFileType fileType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "ai_category", length = 50)
    private String aiCategory;

    @Column(name = "ai_confidence", precision = 5, scale = 4)
    private java.math.BigDecimal aiConfidence;

    @Column(name = "ai_description", columnDefinition = "text")
    private String aiDescription;

    @Column(name = "asset_type")
    private String assetType;

    @Column(name = "visible_objects")
    private String[] visibleObjects;

    @Column(name = "specific_subjects")
    private String[] specificSubjects;

    @Column(name = "visual_style")
    private String[] visualStyle;

    @Column(name = "dominant_colors")
    private String[] dominantColors;

    @Column(name = "possible_use_cases")
    private String[] possibleUseCases;

    @Column(name = "ai_tags")
    private String[] aiTags;

    @Column(name = "excluded_categories")
    private String[] excludedCategories;

    @Column(name = "ai_classified_at")
    private Instant aiClassifiedAt;

    @Column(name = "ai_classification_model", length = 100)
    private String aiClassificationModel;

    @Column(name = "ai_processing_version", length = 50)
    private String aiProcessingVersion;

    @Column(name = "observed_scenes")
    private String[] observedScenes;
    @Column(name = "observed_activities")
    private String[] observedActivities;
    @Column(name = "people_count_range", length = 30)
    private String peopleCountRange;
    @Column(name = "equipment_signals")
    private String[] equipmentSignals;
    @Column(name = "recognition_signals")
    private String[] recognitionSignals;
    @Column(name = "ocr_text")
    private String[] ocrText;
    @Column(name = "visible_dates")
    private String[] visibleDates;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_hypotheses", columnDefinition = "jsonb")
    private String eventHypotheses = "[]";
    @Column(name = "temporal_classification", length = 30)
    private String temporalClassification;
    @Column(name = "possible_expiration", length = 80)
    private String possibleExpiration;
    @Column(name = "visual_quality_signals")
    private String[] visualQualitySignals;
    @Column(name = "composition_signals")
    private String[] compositionSignals;

    // embedding VECTOR(1024) — managed via native queries; Hibernate does not map pgvector type natively
    // Use MediaAssetRepository.updateEmbedding() for writes and cosine search for reads
    @Column(name = "embedding_generated_at")
    private Instant embeddingGeneratedAt;

    @Column(name = "embedding_model", length = 100)
    private String embeddingModel;

    @Column(name = "reclassified_at")
    private Instant reclassifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MediaAssetStatus status;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by_user_id")
    private UUID deletedByUserId;

    @Column(name = "purged_at")
    private Instant purgedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = MediaAssetStatus.PROCESSING;
        }
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Institution getInstitution() {
        return institution;
    }

    public void setInstitution(Institution institution) {
        this.institution = institution;
    }

    public User getUploader() {
        return uploader;
    }

    public void setUploader(User uploader) {
        this.uploader = uploader;
    }

    public String getAssetCode() {
        return assetCode;
    }

    public void setAssetCode(String assetCode) {
        this.assetCode = assetCode;
    }

    public String getStorageUrl() {
        return storageUrl;
    }

    public void setStorageUrl(String storageUrl) {
        this.storageUrl = storageUrl;
    }

    public MediaAlbum getMediaAlbum() {
        return mediaAlbum;
    }

    public void setMediaAlbum(MediaAlbum mediaAlbum) {
        this.mediaAlbum = mediaAlbum;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getDisplayTitle() {
        return displayTitle;
    }

    public void setDisplayTitle(String displayTitle) {
        this.displayTitle = displayTitle;
    }

    /**
     * The actor-facing title — the rename if one was set, otherwise the
     * original filename.
     */
    public String getTitle() {
        return displayTitle != null && !displayTitle.isBlank() ? displayTitle : fileName;
    }

    public MediaFileType getFileType() {
        return fileType;
    }

    public void setFileType(MediaFileType fileType) {
        this.fileType = fileType;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getAiCategory() {
        return aiCategory;
    }

    public void setAiCategory(String aiCategory) {
        this.aiCategory = aiCategory;
    }

    public java.math.BigDecimal getAiConfidence() {
        return aiConfidence;
    }

    public void setAiConfidence(java.math.BigDecimal aiConfidence) {
        this.aiConfidence = aiConfidence;
    }

    public String getAiDescription() {
        return aiDescription;
    }

    public void setAiDescription(String aiDescription) {
        this.aiDescription = aiDescription;
    }

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    public String[] getVisibleObjects() {
        return visibleObjects;
    }

    public void setVisibleObjects(String[] visibleObjects) {
        this.visibleObjects = visibleObjects;
    }

    public String[] getSpecificSubjects() {
        return specificSubjects;
    }

    public void setSpecificSubjects(String[] specificSubjects) {
        this.specificSubjects = specificSubjects;
    }

    public String[] getVisualStyle() {
        return visualStyle;
    }

    public void setVisualStyle(String[] visualStyle) {
        this.visualStyle = visualStyle;
    }

    public String[] getDominantColors() {
        return dominantColors;
    }

    public void setDominantColors(String[] dominantColors) {
        this.dominantColors = dominantColors;
    }

    public String[] getPossibleUseCases() {
        return possibleUseCases;
    }

    public void setPossibleUseCases(String[] possibleUseCases) {
        this.possibleUseCases = possibleUseCases;
    }

    public String[] getAiTags() {
        return aiTags;
    }

    public void setAiTags(String[] aiTags) {
        this.aiTags = aiTags;
    }

    public String[] getExcludedCategories() {
        return excludedCategories;
    }

    public void setExcludedCategories(String[] excludedCategories) {
        this.excludedCategories = excludedCategories;
    }

    public Instant getAiClassifiedAt() {
        return aiClassifiedAt;
    }

    public void setAiClassifiedAt(Instant aiClassifiedAt) {
        this.aiClassifiedAt = aiClassifiedAt;
    }

    public String getAiClassificationModel() {
        return aiClassificationModel;
    }

    public void setAiClassificationModel(String aiClassificationModel) {
        this.aiClassificationModel = aiClassificationModel;
    }

    public String getAiProcessingVersion() {
        return aiProcessingVersion;
    }

    public void setAiProcessingVersion(String aiProcessingVersion) {
        this.aiProcessingVersion = aiProcessingVersion;
    }

    public String[] getObservedScenes() { return observedScenes; }
    public void setObservedScenes(String[] value) { this.observedScenes = value; }
    public String[] getObservedActivities() { return observedActivities; }
    public void setObservedActivities(String[] value) { this.observedActivities = value; }
    public String getPeopleCountRange() { return peopleCountRange; }
    public void setPeopleCountRange(String value) { this.peopleCountRange = value; }
    public String[] getEquipmentSignals() { return equipmentSignals; }
    public void setEquipmentSignals(String[] value) { this.equipmentSignals = value; }
    public String[] getRecognitionSignals() { return recognitionSignals; }
    public void setRecognitionSignals(String[] value) { this.recognitionSignals = value; }
    public String[] getOcrText() { return ocrText; }
    public void setOcrText(String[] value) { this.ocrText = value; }
    public String[] getVisibleDates() { return visibleDates; }
    public void setVisibleDates(String[] value) { this.visibleDates = value; }
    public String getEventHypotheses() { return eventHypotheses; }
    public void setEventHypotheses(String value) { this.eventHypotheses = value; }
    public String getTemporalClassification() { return temporalClassification; }
    public void setTemporalClassification(String value) { this.temporalClassification = value; }
    public String getPossibleExpiration() { return possibleExpiration; }
    public void setPossibleExpiration(String value) { this.possibleExpiration = value; }
    public String[] getVisualQualitySignals() { return visualQualitySignals; }
    public void setVisualQualitySignals(String[] value) { this.visualQualitySignals = value; }
    public String[] getCompositionSignals() { return compositionSignals; }
    public void setCompositionSignals(String[] value) { this.compositionSignals = value; }

    public Instant getEmbeddingGeneratedAt() {
        return embeddingGeneratedAt;
    }

    public void setEmbeddingGeneratedAt(Instant embeddingGeneratedAt) {
        this.embeddingGeneratedAt = embeddingGeneratedAt;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public Instant getReclassifiedAt() {
        return reclassifiedAt;
    }

    public void setReclassifiedAt(Instant reclassifiedAt) {
        this.reclassifiedAt = reclassifiedAt;
    }

    public MediaAssetStatus getStatus() {
        return status;
    }

    public void setStatus(MediaAssetStatus status) {
        this.status = status;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public UUID getDeletedByUserId() {
        return deletedByUserId;
    }

    public void setDeletedByUserId(UUID deletedByUserId) {
        this.deletedByUserId = deletedByUserId;
    }

    public Instant getPurgedAt() {
        return purgedAt;
    }

    public void setPurgedAt(Instant purgedAt) {
        this.purgedAt = purgedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
