package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.media.MediaIntegrityResultDto;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetIntegrityCheck;
import com.dasigconnect.backend.model.entity.MediaIntegrityStatus;
import com.dasigconnect.backend.model.entity.MediaIntegrityReviewStatus;
import com.dasigconnect.backend.model.entity.MediaIntegrityTrigger;
import com.dasigconnect.backend.repository.MediaAssetIntegrityCheckRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MediaIntegrityRecordService {

    private final MediaAssetRepository mediaAssetRepository;
    private final MediaAssetIntegrityCheckRepository checkRepository;

    public MediaIntegrityRecordService(MediaAssetRepository mediaAssetRepository,
                                       MediaAssetIntegrityCheckRepository checkRepository) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.checkRepository = checkRepository;
    }

    @Transactional
    public MediaIntegrityRecordOutcome record(UUID assetId,
                                              UUID institutionId,
                                              String observedSha256,
                                              MediaIntegrityStatus downloadFailureStatus,
                                              MediaIntegrityTrigger trigger,
                                              String failureReason) {
        MediaAsset asset = mediaAssetRepository.findActiveByIdForIntegrityUpdate(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media asset not found."));
        if (!asset.getInstitution().getId().equals(institutionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media asset not found.");
        }

        Instant checkedAt = Instant.now();
        MediaIntegrityStatus previousStatus = asset.getIntegrityStatus();
        MediaIntegrityStatus status = downloadFailureStatus;
        String expectedSha256 = asset.getContentSha256();
        String effectiveFailureReason = failureReason;

        if (observedSha256 != null) {
            if (expectedSha256 == null) {
                expectedSha256 = observedSha256;
                asset.setContentSha256(observedSha256);
                asset.setChecksumGeneratedAt(checkedAt);
            }
            status = expectedSha256.equals(observedSha256)
                    ? MediaIntegrityStatus.VERIFIED
                    : MediaIntegrityStatus.MISMATCH;
            effectiveFailureReason = status == MediaIntegrityStatus.MISMATCH
                    ? "Observed SHA-256 does not match the stored ingest checksum."
                    : null;
        }
        if (status == null) {
            status = MediaIntegrityStatus.ERROR;
            effectiveFailureReason = "Integrity verification did not produce a result.";
        }

        asset.setIntegrityStatus(status);
        asset.setIntegrityCheckedAt(checkedAt);
        asset.setIntegrityFailureReason(effectiveFailureReason);
        boolean failed = status == MediaIntegrityStatus.MISMATCH
                || status == MediaIntegrityStatus.MISSING
                || status == MediaIntegrityStatus.ERROR;
        boolean previousFailed = previousStatus == MediaIntegrityStatus.MISMATCH
                || previousStatus == MediaIntegrityStatus.MISSING
                || previousStatus == MediaIntegrityStatus.ERROR;
        boolean alertRequired = failed && (!previousFailed || previousStatus != status);
        if (failed) {
            if (alertRequired || asset.getIntegrityReviewStatus() == null
                    || asset.getIntegrityReviewStatus() == MediaIntegrityReviewStatus.NONE
                    || asset.getIntegrityReviewStatus() == MediaIntegrityReviewStatus.RESOLVED) {
                asset.setIntegrityReviewStatus(MediaIntegrityReviewStatus.OPEN);
                asset.setIntegrityReviewAcknowledgedAt(null);
                asset.setIntegrityReviewAcknowledgedBy(null);
            }
        } else if (previousFailed
                || asset.getIntegrityReviewStatus() == MediaIntegrityReviewStatus.OPEN
                || asset.getIntegrityReviewStatus() == MediaIntegrityReviewStatus.ACKNOWLEDGED) {
            asset.setIntegrityReviewStatus(MediaIntegrityReviewStatus.RESOLVED);
        } else if (asset.getIntegrityReviewStatus() == null) {
            asset.setIntegrityReviewStatus(MediaIntegrityReviewStatus.NONE);
        }
        mediaAssetRepository.save(asset);

        MediaAssetIntegrityCheck check = new MediaAssetIntegrityCheck();
        check.setInstitution(asset.getInstitution());
        check.setAsset(asset);
        check.setExpectedSha256(expectedSha256);
        check.setObservedSha256(observedSha256);
        check.setStatus(status);
        check.setTriggerType(trigger);
        check.setFailureReason(effectiveFailureReason);
        check.setCheckedAt(checkedAt);
        checkRepository.save(check);

        return new MediaIntegrityRecordOutcome(
                MediaIntegrityResultDto.from(asset),
                previousStatus,
                alertRequired);
    }

    @Transactional
    public MediaIntegrityResultDto acknowledge(UUID assetId, UUID institutionId, UUID actorId) {
        MediaAsset asset = mediaAssetRepository.findActiveByIdForIntegrityUpdate(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media asset not found."));
        if (!asset.getInstitution().getId().equals(institutionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media asset not found.");
        }
        MediaIntegrityStatus status = asset.getIntegrityStatus();
        boolean failed = status == MediaIntegrityStatus.MISMATCH
                || status == MediaIntegrityStatus.MISSING
                || status == MediaIntegrityStatus.ERROR;
        if (!failed) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only an active integrity failure can be acknowledged.");
        }
        asset.setIntegrityReviewStatus(MediaIntegrityReviewStatus.ACKNOWLEDGED);
        asset.setIntegrityReviewAcknowledgedAt(Instant.now());
        asset.setIntegrityReviewAcknowledgedBy(actorId);
        mediaAssetRepository.save(asset);
        return MediaIntegrityResultDto.from(asset);
    }
}
