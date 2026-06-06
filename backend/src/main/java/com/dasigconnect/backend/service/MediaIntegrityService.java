package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.media.MediaIntegrityCheckDto;
import com.dasigconnect.backend.model.dto.media.MediaIntegrityResultDto;
import com.dasigconnect.backend.model.entity.MediaIntegrityStatus;
import com.dasigconnect.backend.model.entity.MediaIntegrityTrigger;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.MediaAssetIntegrityCheckRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.MediaIntegrityTarget;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MediaIntegrityService {

    private static final Logger log = LoggerFactory.getLogger(MediaIntegrityService.class);

    private final MediaAssetRepository mediaAssetRepository;
    private final MediaAssetIntegrityCheckRepository checkRepository;
    private final SupabaseStorageService storageService;
    private final MediaIntegrityRecordService recordService;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;
    private final MediaIntegrityAlertService alertService;

    public MediaIntegrityService(MediaAssetRepository mediaAssetRepository,
                                 MediaAssetIntegrityCheckRepository checkRepository,
                                 SupabaseStorageService storageService,
                                 MediaIntegrityRecordService recordService,
                                 AuditLogService auditLogService,
                                 UserRepository userRepository,
                                 MediaIntegrityAlertService alertService) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.checkRepository = checkRepository;
        this.storageService = storageService;
        this.recordService = recordService;
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
        this.alertService = alertService;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MediaIntegrityResultDto verifyManual(UUID assetId, JwtUserDetails user) {
        MediaIntegrityTarget target = requireTarget(assetId);
        assertAccess(target, user);
        return verify(target, MediaIntegrityTrigger.MANUAL, user);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void verifyIngest(UUID assetId) {
        mediaAssetRepository.findIntegrityTargetById(assetId)
                .ifPresent(target -> verify(target, MediaIntegrityTrigger.INGEST, null));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MediaIntegrityResultDto verifyScheduled(MediaIntegrityTarget target) {
        return verify(target, MediaIntegrityTrigger.SCHEDULED, null);
    }

    @Transactional(readOnly = true)
    public List<MediaIntegrityCheckDto> history(UUID assetId, JwtUserDetails user) {
        MediaIntegrityTarget target = requireTarget(assetId);
        assertAccess(target, user);
        return checkRepository.findByAssetIdAndInstitutionIdOrderByCheckedAtDesc(
                        assetId, target.institutionId())
                .stream()
                .map(MediaIntegrityCheckDto::from)
                .toList();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MediaIntegrityResultDto acknowledgeReview(UUID assetId, JwtUserDetails user) {
        MediaIntegrityTarget target = requireTarget(assetId);
        assertAccess(target, user);
        boolean allowed = user.role() != null
                && (user.role().toLowerCase(Locale.ROOT).contains("validator")
                    || user.role().toLowerCase(Locale.ROOT).contains("admin"));
        if (!allowed) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only validators and administrators can acknowledge integrity failures.");
        }
        MediaIntegrityResultDto result = recordService.acknowledge(
                assetId,
                target.institutionId(),
                user.userId());
        auditReviewAcknowledgement(assetId, user);
        return result;
    }

    private MediaIntegrityResultDto verify(MediaIntegrityTarget target,
                                           MediaIntegrityTrigger trigger,
                                           JwtUserDetails user) {
        String observedSha256 = null;
        MediaIntegrityStatus failureStatus = null;
        String failureReason = null;
        try (InputStream input = storageService.openObject(target.storageUrl())) {
            observedSha256 = sha256(input);
        } catch (SupabaseStorageService.StorageObjectNotFoundException ex) {
            failureStatus = MediaIntegrityStatus.MISSING;
            failureReason = ex.getMessage();
        } catch (Exception ex) {
            failureStatus = MediaIntegrityStatus.ERROR;
            failureReason = safeMessage(ex);
        }

        MediaIntegrityRecordOutcome outcome = recordService.record(
                target.assetId(),
                target.institutionId(),
                observedSha256,
                failureStatus,
                trigger,
                failureReason);
        MediaIntegrityResultDto result = outcome.result();
        if (outcome.alertRequired()) {
            try {
                alertService.notifyFailure(
                        target,
                        MediaIntegrityStatus.valueOf(result.getIntegrityStatus()));
            } catch (Exception ex) {
                log.warn("Failed to send integrity alert for asset {}: {}",
                        target.assetId(), ex.getMessage());
            }
        }
        audit(target.assetId(), trigger, result.getIntegrityStatus(), user);
        return result;
    }

    private String sha256(InputStream input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private MediaIntegrityTarget requireTarget(UUID assetId) {
        return mediaAssetRepository.findIntegrityTargetById(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media asset not found."));
    }

    private void assertAccess(MediaIntegrityTarget target, JwtUserDetails user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required.");
        }
        boolean admin = user.role() != null
                && user.role().toLowerCase(Locale.ROOT).contains("admin");
        if (!admin && !target.institutionId().equals(user.institutionId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media asset not found.");
        }
    }

    private void audit(UUID assetId, MediaIntegrityTrigger trigger, String status, JwtUserDetails user) {
        try {
            Map<String, Object> metadata = Map.of(
                    "trigger", trigger.name(),
                    "status", status == null ? "UNKNOWN" : status);
            if (user == null) {
                auditLogService.recordSystemAction("MEDIA_ASSET_INTEGRITY_CHECKED", assetId, metadata);
            } else {
                User actor = userRepository.findById(user.userId()).orElse(null);
                auditLogService.record(actor, "MEDIA_ASSET_INTEGRITY_CHECKED",
                        null, null, assetId, metadata);
            }
        } catch (Exception ex) {
            log.warn("Failed to audit integrity check for asset {}: {}", assetId, ex.getMessage());
        }
    }

    private void auditReviewAcknowledgement(UUID assetId, JwtUserDetails user) {
        try {
            User actor = userRepository.findById(user.userId()).orElse(null);
            auditLogService.record(
                    actor,
                    "MEDIA_ASSET_INTEGRITY_REVIEW_ACKNOWLEDGED",
                    null,
                    null,
                    assetId,
                    Map.of("reviewStatus", "ACKNOWLEDGED"));
        } catch (Exception ex) {
            log.warn("Failed to audit integrity review acknowledgement for asset {}: {}",
                    assetId, ex.getMessage());
        }
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "Integrity verification failed.";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
