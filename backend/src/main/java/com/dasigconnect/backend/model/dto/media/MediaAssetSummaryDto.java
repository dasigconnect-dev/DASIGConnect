package com.dasigconnect.backend.model.dto.media;

import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.SubmissionMediaAsset;
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
    private UUID albumId;
    private String albumName;
    private Instant createdAt;
    private UUID institutionId;
    private String institutionName;
    private UUID uploaderId;
    private String uploaderEmail;
    private String caption;
    private boolean skipWatermark;
    /** MediaAssetStatus name. "STAGED" = a draft upload not yet bound to an institution. */
    private String status;

    public static MediaAssetSummaryDto from(MediaAsset asset) {
        MediaAssetSummaryDto dto = new MediaAssetSummaryDto();
        dto.id = asset.getId();
        dto.assetCode = asset.getAssetCode();
        dto.storageUrl = asset.getStorageUrl();
        dto.fileName = asset.getFileName();
        dto.fileType = asset.getFileType().name();
        dto.fileSizeBytes = asset.getFileSizeBytes();
        dto.aiCategory = asset.getAiCategory();
        if (asset.getMediaAlbum() != null) {
            dto.albumId = asset.getMediaAlbum().getId();
            dto.albumName = asset.getMediaAlbum().getName();
        }
        dto.createdAt = asset.getCreatedAt();
        // Null for a STAGED (draft-only) upload — it has no institution yet.
        if (asset.getInstitution() != null) {
            dto.institutionId = asset.getInstitution().getId();
            dto.institutionName = asset.getInstitution().getName();
        }
        dto.uploaderId = asset.getUploader().getId();
        dto.uploaderEmail = asset.getUploader().getEmail();
        dto.status = asset.getStatus() != null ? asset.getStatus().name() : null;
        return dto;
    }

    public static MediaAssetSummaryDto from(SubmissionMediaAsset link) {
        MediaAssetSummaryDto dto = from(link.getMediaAsset());
        dto.caption = link.getCaption();
        dto.skipWatermark = link.isSkipWatermark();
        return dto;
    }

    public UUID getId() { return id; }
    public String getAssetCode() { return assetCode; }
    public String getStorageUrl() { return storageUrl; }
    public String getFileName() { return fileName; }
    public String getFileType() { return fileType; }
    public long getFileSizeBytes() { return fileSizeBytes; }
    public String getAiCategory() { return aiCategory; }
    public UUID getAlbumId() { return albumId; }
    public String getAlbumName() { return albumName; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getInstitutionId() { return institutionId; }
    public String getInstitutionName() { return institutionName; }
    public UUID getUploaderId() { return uploaderId; }
    public String getUploaderEmail() { return uploaderEmail; }
    public String getCaption() { return caption; }
    public boolean isSkipWatermark() { return skipWatermark; }
    public String getStatus() { return status; }
}
