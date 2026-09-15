package com.dasigconnect.backend.model.dto.submission;

import java.util.UUID;

import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetStatus;

/** Compact first-media payload used only by submission list cards. */
public record SubmissionMediaPreviewDto(
        UUID id,
        String storageUrl,
        String fileName,
        String fileType,
        long fileSizeBytes) {

    public static SubmissionMediaPreviewDto from(MediaAsset asset) {
        boolean deleted = asset.getDeletedAt() != null
                || asset.getStatus() == MediaAssetStatus.DELETED;
        return new SubmissionMediaPreviewDto(
                asset.getId(),
                deleted ? null : asset.getStorageUrl(),
                asset.getFileName(),
                asset.getFileType().name(),
                asset.getFileSizeBytes());
    }
}
