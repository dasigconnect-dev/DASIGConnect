package com.dasigconnect.backend.model.dto.media;

import com.dasigconnect.backend.model.entity.MediaAssetRights;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * UC-4.12 Phase 7D: an asset's rights/consent record plus derived governance state.
 * {@code present=false} means no record has been captured yet.
 */
public record MediaAssetRightsDto(
        UUID assetId,
        boolean present,
        String rightsHolder,
        String rightsBasis,
        String licenseUri,
        String consentReference,
        LocalDate clearanceDate,
        LocalDate expiresAt,
        List<String> permittedChannels,
        String restrictions,
        String supportingDocumentUrl,
        UUID verifiedBy,
        Instant verifiedAt,
        String derivedState,
        boolean permitsPublicClearance,
        Instant updatedAt) {

    public static MediaAssetRightsDto from(MediaAssetRights r, LocalDate today) {
        return new MediaAssetRightsDto(
                r.getAssetId(),
                true,
                r.getRightsHolder(),
                r.getRightsBasis(),
                r.getLicenseUri(),
                r.getConsentReference(),
                r.getClearanceDate(),
                r.getExpiresAt(),
                r.getPermittedChannels() == null ? List.of() : List.of(r.getPermittedChannels()),
                r.getRestrictions(),
                r.getSupportingDocumentUrl(),
                r.getVerifiedBy(),
                r.getVerifiedAt(),
                r.derivedState(today),
                r.permitsPublicClearance(today),
                r.getUpdatedAt());
    }

    /** No rights record captured yet — incomplete and cannot clear for public use. */
    public static MediaAssetRightsDto empty(UUID assetId) {
        return new MediaAssetRightsDto(
                assetId, false, null, "unknown", null, null, null, null,
                List.of(), null, null, null, null, "incomplete", false, null);
    }
}
