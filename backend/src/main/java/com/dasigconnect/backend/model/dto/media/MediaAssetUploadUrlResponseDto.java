package com.dasigconnect.backend.model.dto.media;

import java.time.Instant;
import java.util.UUID;

public class MediaAssetUploadUrlResponseDto {

    private String signedUrl;
    private String publicUrl;
    private String path;
    private UUID reservationId;
    private Instant reservationExpiresAt;
    private boolean storageWarning;

    public MediaAssetUploadUrlResponseDto(String signedUrl, String publicUrl, String path) {
        this(signedUrl, publicUrl, path, null, null, false);
    }

    public MediaAssetUploadUrlResponseDto(
            String signedUrl,
            String publicUrl,
            String path,
            UUID reservationId,
            Instant reservationExpiresAt,
            boolean storageWarning) {
        this.signedUrl = signedUrl;
        this.publicUrl = publicUrl;
        this.path = path;
        this.reservationId = reservationId;
        this.reservationExpiresAt = reservationExpiresAt;
        this.storageWarning = storageWarning;
    }

    public String getSignedUrl() { return signedUrl; }
    public String getPublicUrl() { return publicUrl; }
    public String getPath() { return path; }
    public UUID getReservationId() { return reservationId; }
    public Instant getReservationExpiresAt() { return reservationExpiresAt; }
    public boolean isStorageWarning() { return storageWarning; }
}
