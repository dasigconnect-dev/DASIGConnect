package com.dasigconnect.backend.model.dto.media;

import com.dasigconnect.backend.model.entity.MediaAsset;
import java.time.Instant;
import java.util.UUID;

public class MediaIntegrityResultDto {

    private UUID assetId;
    private String contentSha256;
    private String integrityStatus;
    private Instant checksumGeneratedAt;
    private Instant integrityCheckedAt;
    private String integrityFailureReason;
    private String integrityReviewStatus;
    private Instant integrityReviewAcknowledgedAt;
    private UUID integrityReviewAcknowledgedBy;

    public static MediaIntegrityResultDto from(MediaAsset asset) {
        MediaIntegrityResultDto dto = new MediaIntegrityResultDto();
        dto.assetId = asset.getId();
        dto.contentSha256 = asset.getContentSha256();
        dto.integrityStatus = asset.getIntegrityStatus() == null ? null : asset.getIntegrityStatus().name();
        dto.checksumGeneratedAt = asset.getChecksumGeneratedAt();
        dto.integrityCheckedAt = asset.getIntegrityCheckedAt();
        dto.integrityFailureReason = asset.getIntegrityFailureReason();
        dto.integrityReviewStatus = asset.getIntegrityReviewStatus() == null
                ? null : asset.getIntegrityReviewStatus().name();
        dto.integrityReviewAcknowledgedAt = asset.getIntegrityReviewAcknowledgedAt();
        dto.integrityReviewAcknowledgedBy = asset.getIntegrityReviewAcknowledgedBy();
        return dto;
    }

    public UUID getAssetId() { return assetId; }
    public String getContentSha256() { return contentSha256; }
    public String getIntegrityStatus() { return integrityStatus; }
    public Instant getChecksumGeneratedAt() { return checksumGeneratedAt; }
    public Instant getIntegrityCheckedAt() { return integrityCheckedAt; }
    public String getIntegrityFailureReason() { return integrityFailureReason; }
    public String getIntegrityReviewStatus() { return integrityReviewStatus; }
    public Instant getIntegrityReviewAcknowledgedAt() { return integrityReviewAcknowledgedAt; }
    public UUID getIntegrityReviewAcknowledgedBy() { return integrityReviewAcknowledgedBy; }
}
