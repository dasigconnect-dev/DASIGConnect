package com.dasigconnect.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.model.dto.exception.DirectPostRequestDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionMediaAsset;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.InstitutionRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionMediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.JwtUserDetails;

/**
 * UC-3.5 Category D — Direct Post (Strategic Override).
 *
 * Creates a submission record for an Admin-initiated direct post that bypasses the
 * standard contributor → validator → automated-publish workflow.
 *
 * Guard rail applicability:
 *   GR-H1 (conflict ±30 min) — warning only; admin must acknowledge via {@code acknowledgedGrH1Conflict}.
 *   GR-H2 (≥2h lead time)    — NOT enforced for direct posts.
 *   GR-H3 (≤30 days ahead)   — hard block for scheduled posts only.
 *   GR-S1/GR-S2               — informational only (not enforced here).
 *
 * Immediate posts: status = {@code direct_post_scheduled} with scheduledAt = now.
 *   The PublishingSchedulerJob picks this up within its 5-minute lookback window.
 * Scheduled posts: status = {@code direct_post_scheduled} with the requested scheduledAt.
 */
@Service
public class DirectPostService {

    private static final Logger log = LoggerFactory.getLogger(DirectPostService.class);
    private static final int REASON_MIN_LENGTH = 20;
    private static final Duration GR_H3_MAX_FUTURE = Duration.ofDays(30);

    private final SubmissionRepository submissionRepository;
    private final SubmissionMediaAssetRepository submissionMediaAssetRepository;
    private final InstitutionRepository institutionRepository;
    private final UserRepository userRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final SlotReservationService slotReservationService;
    private final AuditLogService auditLogService;
    private final GuardRailService guardRailService;
    private final FacebookPublisherService facebookPublisherService;
    private final PublishingQueryService publishingQueryService;

    public DirectPostService(
            SubmissionRepository submissionRepository,
            SubmissionMediaAssetRepository submissionMediaAssetRepository,
            InstitutionRepository institutionRepository,
            UserRepository userRepository,
            MediaAssetRepository mediaAssetRepository,
            SlotReservationService slotReservationService,
            AuditLogService auditLogService,
            GuardRailService guardRailService,
            FacebookPublisherService facebookPublisherService,
            PublishingQueryService publishingQueryService) {
        this.submissionRepository = submissionRepository;
        this.submissionMediaAssetRepository = submissionMediaAssetRepository;
        this.institutionRepository = institutionRepository;
        this.userRepository = userRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.slotReservationService = slotReservationService;
        this.auditLogService = auditLogService;
        this.guardRailService = guardRailService;
        this.facebookPublisherService = facebookPublisherService;
        this.publishingQueryService = publishingQueryService;
    }

    /**
     * Validates and persists the direct post submission record.
     * Returns the created submission ID and a flag indicating if a GR-H1 conflict exists.
     *
     * @return the persisted Submission (caller decides whether to publish immediately)
     */
    @Transactional
    public Submission createDirectPost(DirectPostRequestDto dto, JwtUserDetails admin) {
        validateRequest(dto);

        Institution institution = institutionRepository.findById(dto.getInstitutionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Institution not found."));

        User adminUser = userRepository.findById(admin.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Authenticated user not found."));

        Instant targetSlot = dto.isPublishImmediately() ? Instant.now() : dto.getScheduledAt();

        // GR-H3: hard block for scheduled posts more than 30 days ahead
        if (!dto.isPublishImmediately() && targetSlot.isAfter(Instant.now().plus(GR_H3_MAX_FUTURE))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "GR-H3: Direct posts cannot be scheduled more than 30 days in advance.");
        }

        // GR-H1: check conflict and require acknowledgment
        var guardRailResult = guardRailService.validate(institution.getId(), targetSlot);
        boolean hasH1Conflict = guardRailResult.getHardBlocks().stream()
                .anyMatch(v -> "GR-H1".equals(v.getCode()));

        if (hasH1Conflict && !dto.isAcknowledgedGrH1Conflict()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "GR-H1: A post is already scheduled within 30 minutes of this slot. "
                    + "Set acknowledgedGrH1Conflict=true to proceed.");
        }

        // Attach guard rail bypass metadata for audit
        String bypassedRules = hasH1Conflict ? "GR-H1" : "none";
        if (dto.isPublishImmediately()) {
            bypassedRules = bypassedRules.equals("none") ? "GR-H2" : bypassedRules + ", GR-H2";
        }

        // Build submission record using the admin as the contributor
        Submission s = new Submission();
        s.setContributor(adminUser);
        s.setInstitution(institution);
        s.setEventTitle(buildEventTitle(dto.getCaption()));
        s.setEventDate(LocalDate.now());
        s.setCaption(dto.getCaption());
        s.setStatus(SubmissionStatus.direct_post_scheduled);
        s.setScheduledAt(targetSlot);
        s.setSubmittedAt(Instant.now());
        submissionRepository.save(s);

        // Attach media assets
        if (dto.getMediaAssetIds() != null) {
            int order = 0;
            for (UUID assetId : dto.getMediaAssetIds()) {
                MediaAsset asset = mediaAssetRepository.findActiveById(assetId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Media asset not found: " + assetId));
                SubmissionMediaAsset junction = new SubmissionMediaAsset();
                junction.setSubmission(s);
                junction.setMediaAsset(asset);
                junction.setDisplayOrder(order++);
                submissionMediaAssetRepository.save(junction);
            }
        }

        // Lock the slot for scheduled posts
        if (!dto.isPublishImmediately()) {
            slotReservationService.reserveLockedSlot(s.getId(), institution.getId(), targetSlot);
        }

        // Write audit log at confirmation time (not at publication time for scheduled posts)
        auditLogService.record(adminUser, "DIRECT_POST_CREATED", null, null, s.getId(),
                Map.of("institutionId", institution.getId().toString(),
                       "institutionName", institution.getName(),
                       "scheduledAt", targetSlot.toString(),
                       "publishImmediately", String.valueOf(dto.isPublishImmediately()),
                       "reason", dto.getReason(),
                       "bypassedRules", bypassedRules,
                       "grH1Acknowledged", String.valueOf(dto.isAcknowledgedGrH1Conflict())));

        log.info("Admin {} created direct post {} for institution {} scheduled at {}.",
                admin.userId(), s.getId(), institution.getId(), targetSlot);
        return s;
    }

    /**
     * Publishes an immediate direct post via the Facebook API.
     * MUST be called outside any active DB transaction.
     * Uses Propagation.NOT_SUPPORTED to suspend any outer transaction.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void publishImmediately(UUID submissionId) {
        List<MediaAsset> assets = publishingQueryService.loadAssetsForSubmission(submissionId);
        Submission s = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found."));
        facebookPublisherService.publish(s, assets);
    }

    /**
     * Returns true if the given slot has a GR-H1 conflict (used by the controller for warning display).
     */
    @Transactional(readOnly = true)
    public boolean hasGrH1Conflict(UUID institutionId, Instant slot) {
        var result = guardRailService.validate(institutionId, slot);
        return result.getHardBlocks().stream().anyMatch(v -> "GR-H1".equals(v.getCode()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void validateRequest(DirectPostRequestDto dto) {
        if (dto.getInstitutionId() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Institution is required.");
        }
        if (dto.getCaption() == null || dto.getCaption().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Caption is required.");
        }
        if (dto.getReason() == null || dto.getReason().trim().length() < REASON_MIN_LENGTH) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Reason must be at least " + REASON_MIN_LENGTH + " characters.");
        }
        if (!dto.isPublishImmediately() && dto.getScheduledAt() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "scheduledAt is required when publishImmediately is false.");
        }
        if (!dto.isPublishImmediately() && dto.getScheduledAt() != null
                && dto.getScheduledAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "scheduledAt must be in the future.");
        }
    }

    private static String buildEventTitle(String caption) {
        if (caption == null) return "Admin Direct Post";
        String trimmed = caption.trim();
        return "Admin Direct Post: " + (trimmed.length() > 80 ? trimmed.substring(0, 80) + "..." : trimmed);
    }
}
