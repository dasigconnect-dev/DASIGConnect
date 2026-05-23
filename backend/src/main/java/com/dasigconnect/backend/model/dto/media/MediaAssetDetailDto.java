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
    private String fileType;
    private long fileSizeBytes;
    private String aiCategory;
    private BigDecimal aiConfidence;
    private String aiDescription;
    private Instant createdAt;
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
        dto.fileType = asset.getFileType().name();
        dto.fileSizeBytes = asset.getFileSizeBytes();
        dto.aiCategory = asset.getAiCategory();
        dto.aiConfidence = asset.getAiConfidence();
        dto.aiDescription = asset.getAiDescription();
        dto.createdAt = asset.getCreatedAt();
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

    public String getFileType() {
        return fileType;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public String getAiCategory() {
        return aiCategory;
    }

    public BigDecimal getAiConfidence() {
        return aiConfidence;
    }

    public String getAiDescription() {
        return aiDescription;
    }

    public Instant getCreatedAt() {
        return createdAt;
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
