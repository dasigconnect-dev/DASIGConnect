package com.dasigconnect.backend.model.dto.media;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.dasigconnect.backend.model.entity.MediaAsset;

public class MediaAssetTrashDto {

    private UUID id;
    private String assetCode;
    private String storageUrl;
    private String fileName;
    private String title;
    private String fileType;
    private long fileSizeBytes;
    private Instant deletedAt;
    private UUID deletedByUserId;
    private String deletedByName;
    private UUID institutionId;
    private String institutionName;
    private String uploaderName;
    private long daysRemaining;

    public static MediaAssetTrashDto from(MediaAsset asset, String deletedByName, int retentionDays) {
        MediaAssetTrashDto dto = new MediaAssetTrashDto();
        dto.id = asset.getId();
        dto.assetCode = asset.getAssetCode();
        dto.storageUrl = asset.getStorageUrl();
        dto.fileName = asset.getFileName();
        dto.title = asset.getTitle();
        dto.fileType = asset.getFileType() != null ? asset.getFileType().name() : "";
        dto.fileSizeBytes = asset.getFileSizeBytes();
        dto.deletedAt = asset.getDeletedAt();
        dto.deletedByUserId = asset.getDeletedByUserId();
        dto.deletedByName = deletedByName;
        if (asset.getInstitution() != null) {
            dto.institutionId = asset.getInstitution().getId();
            dto.institutionName = asset.getInstitution().getName();
        }
        if (asset.getUploader() != null) {
            String disp = asset.getUploader().getDisplayName();
            if (disp != null && !disp.isBlank()) {
                dto.uploaderName = disp;
            } else {
                String first = asset.getUploader().getFirstName() != null ? asset.getUploader().getFirstName().trim() : "";
                String last = asset.getUploader().getLastName() != null ? asset.getUploader().getLastName().trim() : "";
                String full = (first + " " + last).trim();
                dto.uploaderName = full.isEmpty() ? asset.getUploader().getEmail() : full;
            }
        }
        if (asset.getDeletedAt() != null) {
            long daysPassed = Duration.between(asset.getDeletedAt(), Instant.now()).toDays();
            dto.daysRemaining = Math.max(0, retentionDays - daysPassed);
        } else {
            dto.daysRemaining = retentionDays;
        }
        return dto;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
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

    public String getDeletedByName() {
        return deletedByName;
    }

    public void setDeletedByName(String deletedByName) {
        this.deletedByName = deletedByName;
    }

    public UUID getInstitutionId() {
        return institutionId;
    }

    public void setInstitutionId(UUID institutionId) {
        this.institutionId = institutionId;
    }

    public String getInstitutionName() {
        return institutionName;
    }

    public void setInstitutionName(String institutionName) {
        this.institutionName = institutionName;
    }

    public String getUploaderName() {
        return uploaderName;
    }

    public void setUploaderName(String uploaderName) {
        this.uploaderName = uploaderName;
    }

    public long getDaysRemaining() {
        return daysRemaining;
    }

    public void setDaysRemaining(long daysRemaining) {
        this.daysRemaining = daysRemaining;
    }
}
