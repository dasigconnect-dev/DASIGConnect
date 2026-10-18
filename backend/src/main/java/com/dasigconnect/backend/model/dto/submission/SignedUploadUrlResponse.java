package com.dasigconnect.backend.model.dto.submission;

import java.time.Instant;
import java.util.UUID;

public class SignedUploadUrlResponse {

    private final String signedUrl;
    private final String publicUrl;
    private final String path;
    private final UUID reservationId;
    private final Instant reservationExpiresAt;
    private final boolean storageWarning;

    public SignedUploadUrlResponse(String signedUrl, String publicUrl, String path) {
        this(signedUrl, publicUrl, path, null, null, false);
    }

    public SignedUploadUrlResponse(
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
