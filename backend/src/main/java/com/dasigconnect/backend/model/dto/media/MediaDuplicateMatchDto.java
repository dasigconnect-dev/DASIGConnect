package com.dasigconnect.backend.model.dto.media;

/**
 * One upload-time exact-duplicate hit: the submitted SHA-256 and the existing asset that already
 * holds those exact bytes in the institution.
 */
public record MediaDuplicateMatchDto(String sha256, MediaAssetSummaryDto existingAsset) {
}
