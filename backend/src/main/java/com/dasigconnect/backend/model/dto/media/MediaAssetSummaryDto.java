package com.dasigconnect.backend.model.dto.media;

import com.dasigconnect.backend.model.entity.MediaAsset;
import java.time.Instant;
import java.util.UUID;

public class MediaAssetSummaryDto {

    private UUID id;
    private String assetCode;
    private String storageUrl;
    private String fileName;
    private String fileType;
    private long fileSizeBytes;
    private String aiCategory;
    private Instant createdAt;

    public static MediaAssetSummaryDto from(MediaAsset asset) {
        MediaAssetSummaryDto dto = new MediaAssetSummaryDto();
        dto.id = asset.getId();
        dto.assetCode = asset.getAssetCode();
        dto.storageUrl = asset.getStorageUrl();
        dto.fileName = asset.getFileName();
        dto.fileType = asset.getFileType().name();
        dto.fileSizeBytes = asset.getFileSizeBytes();
        dto.aiCategory = asset.getAiCategory();
        dto.createdAt = asset.getCreatedAt();
        return dto;
    }

    public UUID getId() { return id; }
    public String getAssetCode() { return assetCode; }
    public String getStorageUrl() { return storageUrl; }
    public String getFileName() { return fileName; }
    public String getFileType() { return fileType; }
    public long getFileSizeBytes() { return fileSizeBytes; }
    public String getAiCategory() { return aiCategory; }
    public Instant getCreatedAt() { return createdAt; }
}
