package com.dasigconnect.backend.model.dto.media;

import com.dasigconnect.backend.model.entity.MediaAsset;
import java.time.Instant;
import java.util.UUID;

public class MediaAssetSummaryDto {

    private UUID id;
    private String assetCode;
    private String storageUrl;
    private String fileName;
    private String title;
    private String fileType;
    private long fileSizeBytes;
    private String aiCategory;
    private Instant createdAt;
    private UUID institutionId;
    private String institutionName;
    private UUID uploaderId;
    private String uploaderEmail;
    private UUID folderId;
    private UUID importBatchId;
    private UUID duplicateOfId;
    private Instant curatedAt;

    public static MediaAssetSummaryDto from(MediaAsset asset) {
        MediaAssetSummaryDto dto = new MediaAssetSummaryDto();
        dto.id = asset.getId();
        dto.assetCode = asset.getAssetCode();
        dto.storageUrl = asset.getStorageUrl();
        dto.fileName = asset.getFileName();
        dto.title = asset.getTitle();
        dto.fileType = asset.getFileType().name();
        dto.fileSizeBytes = asset.getFileSizeBytes();
        dto.aiCategory = asset.getAiCategory();
        dto.createdAt = asset.getCreatedAt();
        dto.institutionId = asset.getInstitution().getId();
        dto.institutionName = asset.getInstitution().getName();
        dto.uploaderId = asset.getUploader().getId();
        dto.uploaderEmail = asset.getUploader().getEmail();
        dto.folderId = asset.getFolderId();
        dto.importBatchId = asset.getImportBatchId();
        dto.duplicateOfId = asset.getDuplicateOfId();
        dto.curatedAt = asset.getCuratedAt();
        return dto;
    }

    public UUID getId() { return id; }
    public String getAssetCode() { return assetCode; }
    public String getStorageUrl() { return storageUrl; }
    public String getFileName() { return fileName; }
    public String getTitle() { return title; }
    public String getFileType() { return fileType; }
    public long getFileSizeBytes() { return fileSizeBytes; }
    public String getAiCategory() { return aiCategory; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getInstitutionId() { return institutionId; }
    public String getInstitutionName() { return institutionName; }
    public UUID getUploaderId() { return uploaderId; }
    public String getUploaderEmail() { return uploaderEmail; }
    public UUID getFolderId() { return folderId; }
    public UUID getImportBatchId() { return importBatchId; }
    public UUID getDuplicateOfId() { return duplicateOfId; }
    public Instant getCuratedAt() { return curatedAt; }
}
