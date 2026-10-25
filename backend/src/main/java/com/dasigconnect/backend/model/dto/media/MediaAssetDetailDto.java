package com.dasigconnect.backend.model.dto.media;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.dasigconnect.backend.model.entity.MediaAsset;

public class MediaAssetDetailDto {

    private UUID id;
    private String assetCode;
    private String storageUrl;
    private String fileName;
    private String title;
    private String fileType;
    private long fileSizeBytes;
    private String aiCategory;
    private UUID albumId;
    private String albumName;
    private BigDecimal aiConfidence;
    private String aiDescription;
    private String metadataAssetType;
    private String assetSubtype;
    private String communicationPurpose;
    private String primarySubject;
    private String visualSetting;
    private String peoplePresence;
    private String[] semanticTopics;
    private String[] visibleObjects;
    private String[] activities;
    private String[] ocrText;
    private String detectedTitle;
    private String[] textLanguages;
    private String textDensity;
    private BigDecimal ocrConfidence;
    private String metadataSchemaVersion;
    private String metadataPromptVersion;
    private Instant aiClassifiedAt;
    private String aiClassificationModel;
    private Instant embeddingGeneratedAt;
    private String embeddingModel;
    private String processingVersion;
    private Instant createdAt;
    private UUID institutionId;
    private String institutionName;
    private UUID uploaderId;
    private String uploaderEmail;
    private List<MediaAssetUsageDto> usedIn;
    private List<AssetTagDto> tags;

    public static MediaAssetDetailDto from(MediaAsset asset, List<MediaAssetUsageDto> usedIn, List<AssetTagDto> tags) {
        MediaAssetDetailDto dto = new MediaAssetDetailDto();
        dto.id = asset.getId();
        dto.assetCode = asset.getAssetCode();
        dto.storageUrl = asset.getStorageUrl();
        dto.fileName = asset.getFileName();
        dto.title = asset.getTitle();
        dto.fileType = asset.getFileType().name();
        dto.fileSizeBytes = asset.getFileSizeBytes();
        dto.aiCategory = asset.getAiCategory();
        if (asset.getMediaAlbum() != null) {
            dto.albumId = asset.getMediaAlbum().getId();
            dto.albumName = asset.getMediaAlbum().getName();
        }
        dto.aiConfidence = asset.getAiConfidence();
        dto.aiDescription = asset.getAiDescription();
        dto.metadataAssetType = asset.getMetadataAssetType();
        dto.assetSubtype = asset.getAssetSubtype();
        dto.communicationPurpose = asset.getCommunicationPurpose();
        dto.primarySubject = asset.getPrimarySubject();
        dto.visualSetting = asset.getVisualSetting();
        dto.peoplePresence = asset.getPeoplePresence();
        dto.semanticTopics = asset.getSemanticTopics();
        dto.visibleObjects = asset.getVisibleObjects();
        dto.activities = asset.getObservedActivities();
        dto.ocrText = asset.getOcrText();
        dto.detectedTitle = asset.getDetectedTitle();
        dto.textLanguages = asset.getTextLanguages();
        dto.textDensity = asset.getTextDensity();
        dto.ocrConfidence = asset.getOcrConfidence();
        dto.metadataSchemaVersion = asset.getMetadataSchemaVersion();
        dto.metadataPromptVersion = asset.getMetadataPromptVersion();
        dto.aiClassifiedAt = asset.getAiClassifiedAt();
        dto.aiClassificationModel = asset.getAiClassificationModel();
        dto.embeddingGeneratedAt = asset.getEmbeddingGeneratedAt();
        dto.embeddingModel = asset.getEmbeddingModel();
        dto.processingVersion = asset.getAiProcessingVersion();
        dto.createdAt = asset.getCreatedAt();
        dto.institutionId = asset.getInstitution().getId();
        dto.institutionName = asset.getInstitution().getName();
        dto.uploaderId = asset.getUploader().getId();
        dto.uploaderEmail = asset.getUploader().getEmail();
        dto.usedIn = usedIn;
        dto.tags = tags;
        return dto;
    }

    public UUID getId() {
        return id;
    }

    public String getAssetCode() {
        return assetCode;
    }

    public String getStorageUrl() {
        return storageUrl;
    }

    public String getFileName() {
        return fileName;
    }

    public String getTitle() {
        return title;
    }

    public String getFileType() {
        return fileType;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public String getAiCategory() {
        return aiCategory;
    }

    public UUID getAlbumId() {
        return albumId;
    }

    public String getAlbumName() {
        return albumName;
    }

    public BigDecimal getAiConfidence() {
        return aiConfidence;
    }

    public String getAiDescription() {
        return aiDescription;
    }

    public String getMetadataAssetType() { return metadataAssetType; }
    public String getAssetSubtype() { return assetSubtype; }
    public String getCommunicationPurpose() { return communicationPurpose; }
    public String getPrimarySubject() { return primarySubject; }
    public String getVisualSetting() { return visualSetting; }
    public String getPeoplePresence() { return peoplePresence; }
    public String[] getSemanticTopics() { return semanticTopics; }
    public String[] getVisibleObjects() { return visibleObjects; }
    public String[] getActivities() { return activities; }
    public String[] getOcrText() { return ocrText; }
    public String getDetectedTitle() { return detectedTitle; }
    public String[] getTextLanguages() { return textLanguages; }
    public String getTextDensity() { return textDensity; }
    public BigDecimal getOcrConfidence() { return ocrConfidence; }
    public String getMetadataSchemaVersion() { return metadataSchemaVersion; }
    public String getMetadataPromptVersion() { return metadataPromptVersion; }

    public Instant getAiClassifiedAt() {
        return aiClassifiedAt;
    }

    public String getAiClassificationModel() {
        return aiClassificationModel;
    }

    public Instant getEmbeddingGeneratedAt() {
        return embeddingGeneratedAt;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public String getProcessingVersion() {
        return processingVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getInstitutionId() {
        return institutionId;
    }

    public String getInstitutionName() {
        return institutionName;
    }

    public UUID getUploaderId() {
        return uploaderId;
    }

    public String getUploaderEmail() {
        return uploaderEmail;
    }

    public List<MediaAssetUsageDto> getUsedIn() {
        return usedIn;
    }

    public List<AssetTagDto> getTags() {
        return tags;
    }
}
