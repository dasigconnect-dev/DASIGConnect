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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_assets")
public class MediaAsset {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploader_id", nullable = false)
    private User uploader;

    @Column(name = "asset_code", nullable = false, unique = true, length = 50)
    private String assetCode;

    @Column(name = "storage_url", nullable = false, columnDefinition = "text")
    private String storageUrl;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

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

    @Column(name = "title", columnDefinition = "text")
    private String title;

    @Column(name = "curated_at")
    private Instant curatedAt;

    @Column(name = "blur_score")
    private BigDecimal blurScore;

    @Column(name = "perceptual_hash")
    private Long perceptualHash;

    @Column(name = "duplicate_of_id")
    private UUID duplicateOfId;

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

    @Column(name = "folder_id")
    private UUID folderId;

    @Column(name = "import_batch_id")
    private UUID importBatchId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by_user_id")
    private UUID deletedByUserId;

    @Column(name = "purged_at")
    private Instant purgedAt;

    @Column(name = "content_sha256", length = 64)
    private String contentSha256;

    @Column(name = "checksum_generated_at")
    private Instant checksumGeneratedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "integrity_status", nullable = false, length = 20)
    private MediaIntegrityStatus integrityStatus;

    @Column(name = "integrity_checked_at")
    private Instant integrityCheckedAt;

    @Column(name = "integrity_failure_reason", columnDefinition = "text")
    private String integrityFailureReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "integrity_review_status", nullable = false, length = 20)
    private MediaIntegrityReviewStatus integrityReviewStatus;

    @Column(name = "integrity_review_acknowledged_at")
    private Instant integrityReviewAcknowledgedAt;

    @Column(name = "integrity_review_acknowledged_by")
    private UUID integrityReviewAcknowledgedBy;

    // UC-4.x consent/visibility gate: internal_only | cleared_for_public.
    @Column(name = "visibility", nullable = false)
    private String visibility;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = MediaAssetStatus.PROCESSING;
        if (integrityStatus == null) integrityStatus = MediaIntegrityStatus.PENDING;
        if (integrityReviewStatus == null) integrityReviewStatus = MediaIntegrityReviewStatus.NONE;
        if (visibility == null) visibility = "internal_only";
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }

    public Institution getInstitution() { return institution; }
    public void setInstitution(Institution institution) { this.institution = institution; }

    public User getUploader() { return uploader; }
    public void setUploader(User uploader) { this.uploader = uploader; }

    public String getAssetCode() { return assetCode; }
    public void setAssetCode(String assetCode) { this.assetCode = assetCode; }

    public String getStorageUrl() { return storageUrl; }
    public void setStorageUrl(String storageUrl) { this.storageUrl = storageUrl; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public MediaFileType getFileType() { return fileType; }
    public void setFileType(MediaFileType fileType) { this.fileType = fileType; }

    public long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public String getAiCategory() { return aiCategory; }
    public void setAiCategory(String aiCategory) { this.aiCategory = aiCategory; }

    public java.math.BigDecimal getAiConfidence() { return aiConfidence; }
    public void setAiConfidence(java.math.BigDecimal aiConfidence) { this.aiConfidence = aiConfidence; }

    public String getAiDescription() { return aiDescription; }
    public void setAiDescription(String aiDescription) { this.aiDescription = aiDescription; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Instant getCuratedAt() { return curatedAt; }
    public void setCuratedAt(Instant curatedAt) { this.curatedAt = curatedAt; }

    public BigDecimal getBlurScore() { return blurScore; }
    public void setBlurScore(BigDecimal blurScore) { this.blurScore = blurScore; }

    public Long getPerceptualHash() { return perceptualHash; }
    public void setPerceptualHash(Long perceptualHash) { this.perceptualHash = perceptualHash; }

    public UUID getDuplicateOfId() { return duplicateOfId; }
    public void setDuplicateOfId(UUID duplicateOfId) { this.duplicateOfId = duplicateOfId; }

    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }

    public String[] getVisibleObjects() { return visibleObjects; }
    public void setVisibleObjects(String[] visibleObjects) { this.visibleObjects = visibleObjects; }

    public String[] getSpecificSubjects() { return specificSubjects; }
    public void setSpecificSubjects(String[] specificSubjects) { this.specificSubjects = specificSubjects; }

    public String[] getVisualStyle() { return visualStyle; }
    public void setVisualStyle(String[] visualStyle) { this.visualStyle = visualStyle; }

    public String[] getDominantColors() { return dominantColors; }
    public void setDominantColors(String[] dominantColors) { this.dominantColors = dominantColors; }

    public String[] getPossibleUseCases() { return possibleUseCases; }
    public void setPossibleUseCases(String[] possibleUseCases) { this.possibleUseCases = possibleUseCases; }

    public String[] getAiTags() { return aiTags; }
    public void setAiTags(String[] aiTags) { this.aiTags = aiTags; }

    public String[] getExcludedCategories() { return excludedCategories; }
    public void setExcludedCategories(String[] excludedCategories) { this.excludedCategories = excludedCategories; }

    public Instant getAiClassifiedAt() { return aiClassifiedAt; }
    public void setAiClassifiedAt(Instant aiClassifiedAt) { this.aiClassifiedAt = aiClassifiedAt; }

    public String getAiClassificationModel() { return aiClassificationModel; }
    public void setAiClassificationModel(String aiClassificationModel) { this.aiClassificationModel = aiClassificationModel; }

    public Instant getEmbeddingGeneratedAt() { return embeddingGeneratedAt; }
    public void setEmbeddingGeneratedAt(Instant embeddingGeneratedAt) { this.embeddingGeneratedAt = embeddingGeneratedAt; }

    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }

    public Instant getReclassifiedAt() { return reclassifiedAt; }
    public void setReclassifiedAt(Instant reclassifiedAt) { this.reclassifiedAt = reclassifiedAt; }

    public MediaAssetStatus getStatus() { return status; }
    public void setStatus(MediaAssetStatus status) { this.status = status; }

    public UUID getFolderId() { return folderId; }
    public void setFolderId(UUID folderId) { this.folderId = folderId; }

    public UUID getImportBatchId() { return importBatchId; }
    public void setImportBatchId(UUID importBatchId) { this.importBatchId = importBatchId; }

    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    public UUID getDeletedByUserId() { return deletedByUserId; }
    public void setDeletedByUserId(UUID deletedByUserId) { this.deletedByUserId = deletedByUserId; }

    public Instant getPurgedAt() { return purgedAt; }
    public void setPurgedAt(Instant purgedAt) { this.purgedAt = purgedAt; }

    public String getContentSha256() { return contentSha256; }
    public void setContentSha256(String contentSha256) { this.contentSha256 = contentSha256; }

    public Instant getChecksumGeneratedAt() { return checksumGeneratedAt; }
    public void setChecksumGeneratedAt(Instant checksumGeneratedAt) { this.checksumGeneratedAt = checksumGeneratedAt; }

    public MediaIntegrityStatus getIntegrityStatus() { return integrityStatus; }
    public void setIntegrityStatus(MediaIntegrityStatus integrityStatus) { this.integrityStatus = integrityStatus; }

    public Instant getIntegrityCheckedAt() { return integrityCheckedAt; }
    public void setIntegrityCheckedAt(Instant integrityCheckedAt) { this.integrityCheckedAt = integrityCheckedAt; }

    public String getIntegrityFailureReason() { return integrityFailureReason; }
    public void setIntegrityFailureReason(String integrityFailureReason) { this.integrityFailureReason = integrityFailureReason; }

    public MediaIntegrityReviewStatus getIntegrityReviewStatus() { return integrityReviewStatus; }
    public void setIntegrityReviewStatus(MediaIntegrityReviewStatus integrityReviewStatus) { this.integrityReviewStatus = integrityReviewStatus; }

    public Instant getIntegrityReviewAcknowledgedAt() { return integrityReviewAcknowledgedAt; }
    public void setIntegrityReviewAcknowledgedAt(Instant integrityReviewAcknowledgedAt) { this.integrityReviewAcknowledgedAt = integrityReviewAcknowledgedAt; }

    public UUID getIntegrityReviewAcknowledgedBy() { return integrityReviewAcknowledgedBy; }
    public void setIntegrityReviewAcknowledgedBy(UUID integrityReviewAcknowledgedBy) { this.integrityReviewAcknowledgedBy = integrityReviewAcknowledgedBy; }

    public Instant getCreatedAt() { return createdAt; }

    public boolean isDeleted() { return deletedAt != null; }
}
