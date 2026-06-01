package com.dasigconnect.backend.model.dto.media;

import com.dasigconnect.backend.model.entity.MediaImportBatch;
import java.time.Instant;
import java.util.UUID;

public class MediaImportBatchResponseDto {

    private UUID id;
    private UUID institutionId;
    private UUID uploadedBy;
    private int assetCount;
    private Instant createdAt;

    public static MediaImportBatchResponseDto from(MediaImportBatch batch) {
        MediaImportBatchResponseDto dto = new MediaImportBatchResponseDto();
        dto.id = batch.getId();
        dto.institutionId = batch.getInstitution().getId();
        dto.uploadedBy = batch.getUploadedBy().getId();
        dto.assetCount = batch.getAssetCount();
        dto.createdAt = batch.getCreatedAt();
        return dto;
    }

    public UUID getId() { return id; }
    public UUID getInstitutionId() { return institutionId; }
    public UUID getUploadedBy() { return uploadedBy; }
    public int getAssetCount() { return assetCount; }
    public Instant getCreatedAt() { return createdAt; }
}
