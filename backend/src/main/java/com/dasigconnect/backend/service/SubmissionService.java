package com.dasigconnect.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.context.ApplicationEventPublisher;

import com.dasigconnect.backend.event.FastTrackSubmissionEvent;
import com.dasigconnect.backend.event.SubmissionPendingEvent;
import com.dasigconnect.backend.event.SubmissionRescheduledEvent;
import com.dasigconnect.backend.exception.GuardRailViolationException;
import com.dasigconnect.backend.exception.MediaAssetNotFoundException;
import com.dasigconnect.backend.exception.SubmissionNotFoundException;
import com.dasigconnect.backend.model.dto.submission.RescheduleRequestDto;
import com.dasigconnect.backend.model.dto.guardrail.GuardRailResult;
import com.dasigconnect.backend.model.dto.media.MediaAssetSummaryDto;
import com.dasigconnect.backend.model.dto.submission.AttachAssetDto;
import com.dasigconnect.backend.model.dto.submission.AttachMediaDto;
import com.dasigconnect.backend.model.dto.submission.SignedUploadUrlRequest;
import com.dasigconnect.backend.model.dto.submission.SignedUploadUrlResponse;
import com.dasigconnect.backend.model.dto.submission.SlotEvaluateRequestDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionCreateDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionBucketCountsDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionMediaOrderDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionMediaPreviewDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionPageDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionResponseDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionSummaryDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionUpdateDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.AssetTag;
import com.dasigconnect.backend.model.entity.MediaAlbum;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetStatus;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.NotificationEventType;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionMediaAsset;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.repository.AssetTagRepository;
import com.dasigconnect.backend.repository.InstitutionRepository;
import com.dasigconnect.backend.repository.MediaAlbumRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.ReviewLockRepository;
import com.dasigconnect.backend.repository.SubmissionMediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.JwtUserDetails;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Business logic for UC-1.3: Content Submission and Self-Service Scheduling.
 *
 * State machine (submissions this service owns): DRAFT → PENDING (submit)
 * NEEDS_REVISION → PENDING (re-submit) DRAFT → deleted (delete)
 *
 * Transitions owned by other services (ValidationService — Module 2): PENDING →
 * IN_REVIEW → APPROVED/NEEDS_REVISION/REJECTED → SCHEDULED
 */
@Service
@Transactional
public class SubmissionService {

    private static final Logger log = LoggerFactory.getLogger(SubmissionService.class);

    private static final int MAX_MEDIA_PER_SUBMISSION = 10;
    private static final long MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024;
    // Matches the frontend composer's CAPTION_CHAR_LIMIT (code-point count).
    private static final int MAX_CAPTION_CHARS = 3000;
    private static final int DEFAULT_SUBMISSION_PAGE_SIZE = 20;
    private static final int MAX_SUBMISSION_PAGE_SIZE = 50;

    // UC-3.1: a Moderator's calendar reschedule power is deliberately bounded —
    // Admin is exempt from both limits (final override authority already).
    private static final int MODERATOR_MAX_RESCHEDULES = 2;
    private static final Duration MODERATOR_RESCHEDULE_WINDOW = Duration.ofDays(1);

    private final SubmissionRepository submissionRepository;
    private final InstitutionRepository institutionRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final SubmissionMediaAssetRepository submissionMediaAssetRepository;
    private final ReviewLockRepository reviewLockRepository;
    private final SlotReservationService slotReservationService;
    private final GuardRailService guardRailService;
    private final AuditLogService auditLogService;
    private final MediaStorageService mediaStorage;
    private final NotificationService notificationService;
    private final EmailDeliveryService emailDeliveryService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @PersistenceContext
    private EntityManager entityManager;

    private final GuardRailSettingsService guardRailSettings;

    private final AssetTagRepository assetTagRepository;
    private final MediaAlbumRepository mediaAlbumRepository;

    public SubmissionService(
            SubmissionRepository submissionRepository,
            InstitutionRepository institutionRepository,
            MediaAssetRepository mediaAssetRepository,
            SubmissionMediaAssetRepository submissionMediaAssetRepository,
            ReviewLockRepository reviewLockRepository,
            SlotReservationService slotReservationService,
            GuardRailService guardRailService,
            AuditLogService auditLogService,
            MediaStorageService mediaStorage,
            NotificationService notificationService,
            EmailDeliveryService emailDeliveryService,
            UserRepository userRepository,
            AssetTagRepository assetTagRepository,
            MediaAlbumRepository mediaAlbumRepository,
            ApplicationEventPublisher eventPublisher,
            GuardRailSettingsService guardRailSettings) {
        this.submissionRepository = submissionRepository;
        this.institutionRepository = institutionRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.submissionMediaAssetRepository = submissionMediaAssetRepository;
        this.reviewLockRepository = reviewLockRepository;
        this.slotReservationService = slotReservationService;
        this.guardRailService = guardRailService;
        this.auditLogService = auditLogService;
        this.mediaStorage = mediaStorage;
        this.notificationService = notificationService;
        this.emailDeliveryService = emailDeliveryService;
        this.userRepository = userRepository;
        this.assetTagRepository = assetTagRepository;
        this.mediaAlbumRepository = mediaAlbumRepository;
        this.eventPublisher = eventPublisher;
        this.guardRailSettings = guardRailSettings;
    }

    @Transactional(readOnly = true)
    public SignedUploadUrlResponse createSignedUploadUrl(UUID submissionId, SignedUploadUrlRequest dto, JwtUserDetails user) {
        Submission submission = loadOwnedSubmission(submissionId, user);
        assertEditableStatus(submission);
        return signedUploadUrlFor(submission, dto);
    }

    /**
     * Media-upload URL core — caller owns the auth/status checks. Reused by
     * ValidationService.
     */
    SignedUploadUrlResponse signedUploadUrlFor(Submission submission, SignedUploadUrlRequest dto) {
        validateMediaFile(dto.getFileType(), dto.getFileSizeBytes());
        String safeFileName = dto.getFileName().replaceAll("[^a-zA-Z0-9._-]", "-");
        String objectPath = submission.getId() + "/" + UUID.randomUUID() + "-" + safeFileName;
        String signedUrl = mediaStorage.createSignedUploadUrl(objectPath);
        String publicUrl = mediaStorage.getPublicUrl(objectPath);
        return new SignedUploadUrlResponse(signedUrl, publicUrl, objectPath);
    }

    /**
     * Creates a new submission in DRAFT status. If scheduledAt is provided,
     * validates guard rails and reserves the slot. Guard rail violations are
     * returned as HTTP 409 with the violation details.
     */
    public SubmissionResponseDto create(SubmissionCreateDto dto, JwtUserDetails user) {
        UUID institutionId = resolveSubmissionInstitutionId(dto.getInstitutionId(), user);
        User contributor = entityManager.getReference(User.class, user.userId());
        Institution institution = entityManager.getReference(Institution.class, institutionId);

        Submission submission = new Submission();
        submission.setContributor(contributor);
        submission.setInstitution(institution);
        submission.setEventTitle(dto.getEventTitle());
        submission.setEventDate(dto.getEventDate());
        validateCaptionCharLimit(dto.getCaption(), HttpStatus.BAD_REQUEST);
        submission.setCaption(dto.getCaption());
        submission.setDescription(dto.getDescription());
        submission.setStatus(SubmissionStatus.draft);
        submission.setCategory(dto.getCategory());
        submission.setAlbumName(normalizeOptional(dto.getAlbumName()));
        submission.setMediaTags(joinTags(dto.getMediaTags()));
        submission.setTemplateId(dto.getTemplateId() == null || dto.getTemplateId().isBlank()
                ? null
                : dto.getTemplateId());
        submission.setFastTrack(dto.isFastTrack());
        submission.setLiveEventName(normalizeOptional(dto.getLiveEventName()));
        if (submission.isFastTrack()) {
            submission.setCategory(null);
            submission.setDescription(null);
        }
        if (dto.getTags() != null && !dto.getTags().isEmpty()) {
            submission.setTags(String.join(",", dto.getTags()));
        }
        if (submission.isFastTrack()) {
            submission.setTags(null);
        }

        submission = submissionRepository.save(submission);

        if (dto.getScheduledAt() != null && !submission.isFastTrack()) {
            submission.setScheduledAt(dto.getScheduledAt());
            slotReservationService.reserve(submission.getId(), institutionId, dto.getScheduledAt());
            submission = submissionRepository.save(submission);
        }

        auditLogService.record(contributor, "SUBMISSION_CREATED", null, null,
                submission.getId(), Map.of("eventTitle", submission.getEventTitle()));

        log.info("Submission {} created as DRAFT by user {}", submission.getId(), user.userId());
        return buildResponse(submission);
    }

    /**
     * Returns a pending approval submission to DRAFT before review begins.
     */
    public SubmissionResponseDto withdraw(UUID submissionId, JwtUserDetails user) {
        Submission submission = loadOwnedSubmission(submissionId, user);

        if (submission.getStatus() != SubmissionStatus.pending) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only pending approval submissions can be withdrawn. Current status: "
                    + submission.getStatus());
        }
        if (reviewLockRepository.findBySubmissionId(submissionId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This submission is already under review and can no longer be withdrawn.");
        }

        submission.setStatus(SubmissionStatus.draft);
        submission.setSubmittedAt(null);
        submission = submissionRepository.save(submission);

        auditLogService.record(
                entityManager.getReference(User.class, user.userId()),
                "SUBMISSION_WITHDRAWN", null, null,
                submissionId,
                Map.of());

        log.info("Submission {} withdrawn to DRAFT by user {}", submissionId, user.userId());
        return buildResponse(submission);
    }

    /**
     * Updates a DRAFT or NEEDS_REVISION submission (auto-save support). If
     * scheduledAt changes, releases the old slot and reserves the new one.
     */
    public SubmissionResponseDto update(UUID submissionId, SubmissionUpdateDto dto, JwtUserDetails user) {
        Submission submission = loadOwnedSubmission(submissionId, user);
        assertEditableStatus(submission);
        maybeRehomeSubmission(submission, dto.getInstitutionId(), user);
        submission = applySubmissionEdits(submission, dto, user);
        return buildResponse(submission);
    }

    /**
     * Moves an editable draft to a different institution when a network-wide
     * composer picks a new "Posting As" scope. Selected media is kept because
     * reviewers/admins may reuse vetted library assets across institutions. Any
     * held slot is released, and the schedule is cleared because guard rails
     * are evaluated per institution. No-op when the id is unchanged/absent.
     */
    private void maybeRehomeSubmission(Submission submission, UUID requestedInstitutionId, JwtUserDetails user) {
        if (requestedInstitutionId == null
                || requestedInstitutionId.equals(submission.getInstitution().getId())) {
            return;
        }
        boolean isAdmin = "moderator".equalsIgnoreCase(user.role())
                || "admin".equalsIgnoreCase(user.role());
        if (!isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only network moderators can change a submission's institution.");
        }
        Institution target = institutionRepository.findById(requestedInstitutionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Institution not found: " + requestedInstitutionId));

        UUID previousInstitutionId = submission.getInstitution().getId();

        // No attached asset is detached here, regardless of source: a STAGED
        // upload has no institution yet and simply gets bound to `target` the
        // next time submit()'s reconciliation runs (see the loop there); a
        // library pick already has its own institution/album and keeps both
        // untouched — it stays attached to this submission (now under a
        // different institution) but administratively still belongs to
        // whichever institution's library it was picked from. This is
        // intentional (reviewers/admins reusing vetted assets across
        // institutions), not a bug — do not add auto-detach here without
        // confirming that's actually the desired UX, since it would silently
        // strip media an admin deliberately chose to reuse.
        slotReservationService.deleteAllForSubmission(submission.getId());

        submission.setInstitution(target);
        submission.setScheduledAt(null);
        refreshManualPublishingFlag(submission);
        log.info("Submission {} re-homed from institution {} to {} by user {} (media kept)",
                submission.getId(), previousInstitutionId, requestedInstitutionId, user.userId());
    }

    /**
     * Applies the editable-field subset of a SubmissionUpdateDto to an
     * already-loaded submission and saves it. Shared by the contributor-facing
     * update() above and ValidationService's admin Edit & Approve action —
     * callers own their own ownership/status checks before calling this.
     */
    Submission applySubmissionEdits(Submission submission, SubmissionUpdateDto dto, JwtUserDetails user) {
        UUID submissionId = submission.getId();

        if (dto.getEventTitle() != null) {
            submission.setEventTitle(dto.getEventTitle());
        }
        if (dto.getEventDate() != null) {
            submission.setEventDate(dto.getEventDate());
        }
        if (dto.getCaption() != null) {
            validateCaptionCharLimit(dto.getCaption(), HttpStatus.BAD_REQUEST);
            submission.setCaption(dto.getCaption());
        }
        if (dto.getDescription() != null) {
            submission.setDescription(dto.getDescription());
        }
        if (dto.getCategory() != null) {
            submission.setCategory(dto.getCategory());
        }
        if (dto.getTemplateId() != null) {
            submission.setTemplateId(dto.getTemplateId().isBlank() ? null : dto.getTemplateId());
        }
        if (dto.getAlbumName() != null) {
            submission.setAlbumName(normalizeOptional(dto.getAlbumName()));
        }
        if (dto.getMediaTags() != null) {
            submission.setMediaTags(joinTags(dto.getMediaTags()));
        }
        if (dto.getFastTrack() != null) {
            submission.setFastTrack(dto.getFastTrack());
            if (dto.getFastTrack()) {
                submission.setScheduledAt(null);
                submission.setCategory(null);
                submission.setDescription(null);
                submission.setTags(null);
                slotReservationService.release(submissionId);
            } else {
                submission.setLiveEventName(null);
            }
        }
        if (dto.getLiveEventName() != null) {
            submission.setLiveEventName(normalizeOptional(dto.getLiveEventName()));
        }
        if (dto.getTags() != null) {
            submission.setTags(dto.getTags().isEmpty() ? null : String.join(",", dto.getTags()));
        }
        if (submission.isFastTrack()) {
            submission.setCategory(null);
            submission.setDescription(null);
            submission.setTags(null);
        }

        if (!submission.isFastTrack() && dto.getScheduledAt() != null && !dto.getScheduledAt().equals(submission.getScheduledAt())) {
            boolean isAdmin = isAdmin(user);
            boolean isReviewer = isAdmin || isModerator(user);
            // Reviewers always have guard rails applied on a schedule change; for a
            // contributor's own edit it follows the app.guardrails.enforced flag.
            if (guardRailSettings.enforced() || isReviewer) {
                GuardRailResult gr = guardRailService.validate(submission.getInstitution().getId(), dto.getScheduledAt(), submission.getId());
                if (gr.isBlocked()) {
                    String reason = dto.getOverrideReason() == null ? "" : dto.getOverrideReason().trim();
                    if (isAdmin) {
                        // Only an administrator can bypass a hard guard rail — with a reason, audited.
                        if (reason.isEmpty()) {
                            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                                    "This slot is blocked by a guard rail. Provide an override reason to bypass it.");
                        }
                        auditLogService.record(
                                entityManager.getReference(User.class, user.userId()),
                                "SCHEDULE_GUARDRAIL_OVERRIDE", null, null, submissionId,
                                Map.of("newSlot", dto.getScheduledAt().toString(),
                                        "overrideReason", reason,
                                        "violations", gr.getHardBlocks().toString()));
                    } else if (isModerator(user)) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                "This slot is blocked by a guard rail. Only an administrator can override it.");
                    } else {
                        throw new GuardRailViolationException(gr.getHardBlocks());
                    }
                }
            }
            submission.setScheduledAt(dto.getScheduledAt());
            // reserve() releases any existing held slot and creates a new one
            slotReservationService.reserve(submissionId, submission.getInstitution().getId(), dto.getScheduledAt());
        }

        return submissionRepository.save(submission);
    }

    private static boolean isAdmin(JwtUserDetails user) {
        return user != null && "admin".equalsIgnoreCase(user.role());
    }

    private static boolean isModerator(JwtUserDetails user) {
        return user != null && "moderator".equalsIgnoreCase(user.role());
    }

    private static boolean isNetworkRole(JwtUserDetails user) {
        return isAdmin(user) || isModerator(user);
    }

    /**
     * Deletes a DRAFT or REJECTED submission and removes its slot reservations.
     * Only the owning contributor may delete. Media that was uploaded solely for
     * this draft and is now orphaned is permanently purged (row + storage
     * object); assets picked from the library or ever used beyond draft status
     * stay put.
     */
    public void delete(UUID submissionId, JwtUserDetails user) {
        Submission submission = loadOwnedSubmission(submissionId, user);
        if (submission.getStatus() != SubmissionStatus.draft
                && submission.getStatus() != SubmissionStatus.rejected) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only DRAFT or REJECTED submissions can be deleted. Current status: " + submission.getStatus());
        }
        List<MediaAsset> attached
                = submissionMediaAssetRepository.findMediaAssetsBySubmissionId(submissionId);
        submissionMediaAssetRepository.deleteBySubmissionId(submissionId);
        submissionMediaAssetRepository.flush();
        for (MediaAsset asset : attached) {
            purgeOrphanedDraftUpload(asset.getId(), "draft " + submissionId + " deleted");
        }
        slotReservationService.deleteAllForSubmission(submissionId);
        submissionRepository.delete(submission);
        log.info("Submission {} deleted by user {} ({} orphan-checked media)",
                submissionId, user.userId(), attached.size());
    }

    /**
     * Transitions DRAFT → PENDING (initial submission) or NEEDS_REVISION →
     * PENDING (re-submission after revision request). Re-validates guard rails
     * before accepting.
     */
    public SubmissionResponseDto submit(UUID submissionId, JwtUserDetails user) {
        Submission submission = loadOwnedSubmission(submissionId, user);

        if (submission.getStatus() != SubmissionStatus.draft
                && submission.getStatus() != SubmissionStatus.needs_revision
                && submission.getStatus() != SubmissionStatus.rejected) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only DRAFT, NEEDS_REVISION, or REJECTED submissions can be submitted. Current status: "
                    + submission.getStatus());
        }

        boolean wasRejected = submission.getStatus() == SubmissionStatus.rejected;
        boolean wasRevision = submission.getStatus() == SubmissionStatus.needs_revision;
        boolean fastTrack = submission.isFastTrack();

        // A Standard post always needs a scheduled time — the guard-rail switch
        // only governs the *rules* on that time (spacing, daily cap, lead time,
        // publish window), not whether one is picked at all.
        if (!fastTrack && submission.getScheduledAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A scheduled time must be selected before submitting.");
        }

        // Re-run guard rails — slot may have been taken since draft was saved
        if (!fastTrack && guardRailSettings.enforced() && submission.getScheduledAt() != null) {
            GuardRailResult result = guardRailService.validate(submission.getInstitution().getId(), submission.getScheduledAt(), submission.getId());
            if (result.isBlocked()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Guard rail violation: " + result.getHardBlocks().get(0).getMessage());
            }
        } else if (!fastTrack) {
            log.info("Guard rail enforcement disabled; submitting {} without blocking slot validation.",
                    submissionId);
        }

        // Content completeness is a data-integrity invariant, not a scheduling
        // guard rail — enforce it on every submit regardless of guardRailsEnforced.
        assertContentComplete(submission);
        if (submissionMediaAssetRepository.countBySubmissionId(submissionId) == 0) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(422),
                    "At least one valid media attachment is required before submitting.");
        }

        // Reconcile every attached asset with the finalised submission:
        //  - a NEW upload (STAGED, no institution/album) is bound to the
        //    submission's institution, filed into the album the contributor
        //    assigned on the post, and handed to the AI pipeline (PROCESSING);
        //  - an EXISTING library asset keeps its original album — it is only
        //    being linked to the post;
        //  - BOTH get the post's media tags (manual, deduped by label).
        List<MediaAsset> attachedAssets = submissionMediaAssetRepository
                .findMediaAssetsBySubmissionId(submissionId);
        List<MediaAsset> dirty = new java.util.ArrayList<>();
        // Resolved (and, if needed, created) lazily — an all-library-picks post
        // must not spawn an empty album.
        MediaAlbum submissionAlbum = null;
        boolean albumResolved = false;
        for (MediaAsset asset : attachedAssets) {
            boolean changed = false;
            if (asset.getStatus() == MediaAssetStatus.STAGED) {
                asset.setInstitution(submission.getInstitution());
                asset.setStatus(MediaAssetStatus.PROCESSING);
                changed = true;
            }
            // A real library pick always has an album (UC-2.1 invariant), so an
            // album-less attached asset is a fresh upload for this post — file it
            // into the album the contributor assigned. Library picks keep theirs.
            if (asset.getMediaAlbum() == null) {
                if (!albumResolved) {
                    submissionAlbum = resolveSubmissionAlbum(submission, user);
                    albumResolved = true;
                }
                if (submissionAlbum != null) {
                    asset.setMediaAlbum(submissionAlbum);
                    changed = true;
                }
            }
            if (changed) {
                dirty.add(asset);
            }
            applySubmissionMediaTags(asset, submission.getMediaTags());
        }
        if (!dirty.isEmpty()) {
            mediaAssetRepository.saveAll(dirty);
        }

        refreshManualPublishingFlag(submission);

        submission.setStatus(SubmissionStatus.pending);
        submission.setSubmittedAt(Instant.now());
        if (wasRejected) {
            submission.setRejectionReason(null);
        }
        submission = submissionRepository.save(submission);

        java.util.Map<String, String> auditDetails = new java.util.HashMap<>();
        if (fastTrack) {
            auditDetails.put("fastTrack", "true");
        } else if (submission.getScheduledAt() != null) {
            auditDetails.put("scheduledAt", submission.getScheduledAt().toString());
        }
        if (wasRejected) {
            auditDetails.put("resubmittedFrom", "rejected");
        } else if (wasRevision) {
            auditDetails.put("resubmittedFrom", "needs_revision");
        }

        auditLogService.record(
                entityManager.getReference(User.class, user.userId()),
                "SUBMISSION_SUBMITTED", null, null,
                submissionId,
                auditDetails);

        // T-01 / T-11 — notify institution moderators via domain events
        if (eventPublisher != null) {
            if (fastTrack) {
                eventPublisher.publishEvent(new FastTrackSubmissionEvent(submission));
            } else {
                eventPublisher.publishEvent(new SubmissionPendingEvent(submission));
            }
        }

        log.info("Submission {} → PENDING by user {}", submissionId, user.userId());
        return buildResponse(submission);
    }

    /**
     * Rejects a submit when the post is missing fields required for a
     * publishable post: an event title, an event date, a caption, and at least
     * one media asset. Throws 422 listing everything that is still missing.
     */
    void assertContentComplete(Submission submission) {
        List<String> missing = new java.util.ArrayList<>();
        if (submission.getEventTitle() == null || submission.getEventTitle().isBlank()) {
            missing.add("an event title");
        }
        if (submission.getEventDate() == null) {
            missing.add("an event date");
        }
        if (submission.getCaption() == null || submission.getCaption().isBlank()) {
            missing.add("a caption");
        } else {
            validateCaptionCharLimit(submission.getCaption(), HttpStatus.UNPROCESSABLE_ENTITY);
        }
        long attachmentCount = submissionMediaAssetRepository.countBySubmissionId(submission.getId());
        if (attachmentCount < 1) {
            missing.add("at least one media attachment");
        } else {
            boolean hasUsableAttachment = submissionMediaAssetRepository
                    .findMediaAssetsBySubmissionId(submission.getId())
                    .stream()
                    .anyMatch(asset -> asset != null
                    && asset.getDeletedAt() == null
                    && asset.getStatus() != MediaAssetStatus.DELETED);
            if (!hasUsableAttachment) {
                missing.add("at least one non-deleted media attachment");
            }
        }
        if (submission.getAlbumName() == null || submission.getAlbumName().isBlank()) {
            missing.add("an album assignment");
        }
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(422),
                    "Submission is incomplete — add " + String.join(", ", missing) + " before submitting.");
        }
    }

    /**
     * Evaluates guard rails for a proposed slot without creating a reservation.
     * Called by the SlotPicker in real time as the contributor selects a time.
     */
    @Transactional(readOnly = true)
    public GuardRailResult evaluateSlot(UUID submissionId, SlotEvaluateRequestDto dto, JwtUserDetails user) {
        Submission submission = loadOwnedSubmission(submissionId, user);
        return guardRailService.validate(submission.getInstitution().getId(), dto.getScheduledAt(), submission.getId());
    }

    /**
     * Lists submissions filtered by the caller's role: - CONTRIBUTOR: only
     * their own submissions for their institution - VALIDATOR: all submissions
     * for their institution - MODERATOR: own editable drafts plus submitted
     * network records for monitoring/approval handoff
     */
    @Transactional(readOnly = true)
    public List<SubmissionSummaryDto> list(JwtUserDetails user) {
        List<Submission> submissions = submissionRepository.findByContributorIdOrderByCreatedAtDesc(user.userId());
        return buildSubmissionSummaries(submissions);
    }

    /**
     * Returns one bounded page for My Submissions while preserving the existing
     * status buckets and search fields used by the frontend.
     */
    @Transactional(readOnly = true)
    public SubmissionPageDto listPage(
            JwtUserDetails user,
            int page,
            int pageSize,
            String bucket,
            String search) {
        int safePage = Math.max(page, 0);
        int safePageSize = pageSize <= 0
                ? DEFAULT_SUBMISSION_PAGE_SIZE
                : Math.min(pageSize, MAX_SUBMISSION_PAGE_SIZE);
        List<SubmissionStatus> statuses = statusesForBucket(bucket);
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase();
        List<SubmissionStatus> matchingStatuses = statusesMatchingSearch(normalizedSearch);

        Page<Submission> result = submissionRepository.findSubmissionPage(
                user.userId(),
                statuses,
                normalizedSearch,
                !matchingStatuses.isEmpty(),
                matchingStatuses.isEmpty() ? List.of(SubmissionStatus.draft) : matchingStatuses,
                PageRequest.of(safePage, safePageSize));

        List<SubmissionSummaryDto> items = buildSubmissionSummaries(result.getContent());
        SubmissionBucketCountsDto counts = buildBucketCounts(
                submissionRepository.countStatusesByContributorId(user.userId()));

        return new SubmissionPageDto(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext(),
                counts);
    }

    private List<SubmissionSummaryDto> buildSubmissionSummaries(List<Submission> submissions) {

        if (submissions.isEmpty()) {
            return List.of();
        }

        Map<UUID, Long> mediaCounts = new HashMap<>();
        Map<UUID, SubmissionMediaPreviewDto> previews = new HashMap<>();
        List<UUID> submissionIds = submissions.stream().map(Submission::getId).toList();
        for (SubmissionMediaAsset link
                : submissionMediaAssetRepository.findListPreviewMediaBySubmissionIds(submissionIds)) {
            UUID submissionId = link.getSubmission().getId();
            mediaCounts.merge(submissionId, 1L, Long::sum);
            previews.putIfAbsent(
                    submissionId,
                    SubmissionMediaPreviewDto.from(link.getMediaAsset()));
        }

        return submissions.stream()
                .map(s -> SubmissionSummaryDto.from(
                        s,
                        mediaCounts.getOrDefault(s.getId(), 0L),
                        previews.get(s.getId())))
                .toList();
    }

    private List<SubmissionStatus> statusesForBucket(String bucket) {
        String normalized = bucket == null ? "all" : bucket.trim().toLowerCase();
        return switch (normalized) {
            case "all" -> List.of(SubmissionStatus.values());
            case "drafts" -> List.of(SubmissionStatus.draft);
            case "action-needed" -> List.of(SubmissionStatus.needs_revision, SubmissionStatus.rejected);
            case "submitted" -> List.of(
                    SubmissionStatus.pending,
                    SubmissionStatus.in_review,
                    SubmissionStatus.missed_review,
                    SubmissionStatus.scheduled,
                    SubmissionStatus.publishing,
                    SubmissionStatus.direct_post_scheduled,
                    SubmissionStatus.direct_post_publishing);
            case "published" -> List.of(
                    SubmissionStatus.published,
                    SubmissionStatus.published_manual,
                    SubmissionStatus.admin_direct_post);
            case "failed" -> List.of(
                    SubmissionStatus.publish_failed,
                    SubmissionStatus.direct_post_failed);
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported submission bucket: " + bucket);
        };
    }

    private List<SubmissionStatus> statusesMatchingSearch(String search) {
        if (search.isEmpty()) {
            return List.of();
        }
        return List.of(SubmissionStatus.values()).stream()
                .filter(status -> submissionStatusLabel(status).contains(search))
                .toList();
    }

    private String submissionStatusLabel(SubmissionStatus status) {
        return switch (status) {
            case draft -> "draft";
            case pending -> "pending approval";
            case in_review -> "under review";
            case needs_revision -> "needs revision";
            case missed_review -> "missed review";
            case scheduled -> "scheduled";
            case publishing -> "publishing";
            case publish_failed -> "publish failed";
            case published, published_manual -> "published";
            case admin_direct_post -> "direct post";
            case direct_post_scheduled -> "direct post scheduled";
            case direct_post_publishing -> "direct post publishing";
            case direct_post_failed -> "direct post failed";
            case rejected -> "rejected";
        };
    }

    private SubmissionBucketCountsDto buildBucketCounts(
            List<SubmissionRepository.SubmissionStatusCount> statusCounts) {
        long drafts = 0;
        long actionNeeded = 0;
        long submitted = 0;
        long published = 0;
        long failed = 0;

        for (SubmissionRepository.SubmissionStatusCount row : statusCounts) {
            long count = row.getCount();
            switch (row.getStatus()) {
                case draft -> drafts += count;
                case needs_revision, rejected -> actionNeeded += count;
                case published, published_manual, admin_direct_post -> published += count;
                case publish_failed, direct_post_failed -> failed += count;
                default -> submitted += count;
            }
        }

        return new SubmissionBucketCountsDto(
                drafts + actionNeeded + submitted + published + failed,
                drafts,
                actionNeeded,
                submitted,
                published,
                failed);
    }

    /**
     * Returns full submission detail. Accessible by the owning contributor, any
     * validator of the same institution, or any moderator.
     */
    @Transactional(readOnly = true)
    public SubmissionResponseDto get(UUID submissionId, JwtUserDetails user) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new SubmissionNotFoundException(submissionId));
        assertReadAccess(submission, user);
        return buildResponse(submission);
    }

    /**
     * Attaches a new media file to a submission. The frontend uploads the file
     * directly to Supabase Storage and passes the resulting URL here. A
     * MediaAsset record is created and linked.
     *
     * <p>
     * While the submission is a DRAFT the asset is <b>staged</b>: no
     * institution and {@code status = STAGED}, so it stays out of the Media
     * Repository and is not bound to the draft's (still tentative) institution.
     * It is bound to the final institution — and flipped to {@code PROCESSING}
     * — in {@link #submit}. Uploads during NEEDS_REVISION go straight to the
     * already-committed institution, as before.
     */
    public SubmissionResponseDto attachMedia(UUID submissionId, AttachMediaDto dto, JwtUserDetails user) {
        Submission submission = loadOwnedSubmission(submissionId, user);
        assertEditableStatus(submission);
        return attachUploadedMediaTo(submission, dto, user);
    }

    /**
     * attach-uploaded-media core — caller owns the auth/status checks. Reused
     * by ValidationService.
     */
    SubmissionResponseDto attachUploadedMediaTo(Submission submission, AttachMediaDto dto, JwtUserDetails user) {
        UUID submissionId = submission.getId();
        long currentCount = submissionMediaAssetRepository.countBySubmissionId(submissionId);
        if (currentCount >= MAX_MEDIA_PER_SUBMISSION) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(422),
                    "Maximum of " + MAX_MEDIA_PER_SUBMISSION + " media assets per submission.");
        }

        MediaFileType fileType = validateMediaFile(dto.getFileType(), dto.getFileSizeBytes());
        boolean stage = submission.getStatus() == SubmissionStatus.draft;

        MediaAsset asset = new MediaAsset();
        if (stage) {
            asset.setStatus(MediaAssetStatus.STAGED);
        } else {
            asset.setInstitution(entityManager.getReference(Institution.class, submission.getInstitution().getId()));
            // Every non-draft upload must land in an album so it surfaces in the
            // Media Library. Honour an explicit target (moderator review upload);
            // otherwise file it into the submission's own album.
            asset.setMediaAlbum(resolveUploadAlbum(submission, dto.getAlbumName(), user));
        }
        asset.setUploader(entityManager.getReference(User.class, user.userId()));
        asset.setAssetCode(generateAssetCode());
        asset.setStorageUrl(dto.getStorageUrl());
        asset.setFileName(dto.getFileName());
        asset.setFileType(fileType);
        asset.setFileSizeBytes(dto.getFileSizeBytes());
        asset = mediaAssetRepository.save(asset);
        applySubmissionMediaTags(asset, submission.getMediaTags());

        linkAssetToSubmission(submission, asset, (int) currentCount);
        refreshManualPublishingFlag(submission);

        log.info("Media asset {} attached to submission {} by user {}", asset.getId(), submissionId, user.userId());
        return buildResponse(submission);
    }

    /**
     * Copy the media tags the contributor entered on the Submit-Content upload
     * step onto the new asset as {@code manual} {@code asset_tags}, so those
     * tags show up in the Media Library alongside library-uploaded assets'
     * tags.
     */
    private void applySubmissionMediaTags(MediaAsset asset, String joinedTags) {
        if (joinedTags == null || joinedTags.isBlank()) {
            return;
        }
        for (String raw : joinedTags.split(",")) {
            String label = raw.trim();
            if (label.isEmpty() || assetTagRepository.existsByMediaAssetIdAndLabel(asset.getId(), label)) {
                continue;
            }
            AssetTag tag = new AssetTag();
            tag.setMediaAsset(asset);
            tag.setLabel(label);
            tag.setSource("manual");
            assetTagRepository.save(tag);
        }
    }

    /**
     * The album a submission's brand-new uploads are filed into on submit: an
     * existing <b>root</b> album of the submission's institution whose name
     * matches {@code submission.albumName} (case-insensitive), otherwise a new
     * root album created with that name. Returns {@code null} only when no
     * album name is set — {@link #assertContentComplete} already rejects that
     * for a normal submit, so in practice this is always non-null when there
     * are staged uploads to file.
     */
    private MediaAlbum resolveSubmissionAlbum(Submission submission, JwtUserDetails user) {
        return resolveAlbumByName(submission.getInstitution(), submission.getAlbumName(), user.userId());
    }

    /**
     * Finds the root album of {@code institution} whose name matches
     * {@code name} (case-insensitive), creating it if absent. Shared by the
     * contributor submit path and the reviewer upload path. Returns
     * {@code null} when the name is blank or the institution is unknown.
     */
    private MediaAlbum resolveAlbumByName(Institution institution, String rawName, UUID createdBy) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty() || institution == null) {
            return null;
        }
        return mediaAlbumRepository
                .findByParentAndNameIgnoreCase(institution.getId(), null, name)
                .orElseGet(() -> {
                    MediaAlbum album = new MediaAlbum();
                    album.setInstitution(institution);
                    album.setName(name);
                    album.setParentAlbum(null);
                    album.setCreatedBy(createdBy);
                    return mediaAlbumRepository.save(album);
                });
    }

    /**
     * Chooses the album for a non-draft upload: the reviewer's requested album
     * name (resolved / created in the submission's institution, same as the
     * contributor's album combobox), otherwise the submission's own album.
     */
    private MediaAlbum resolveUploadAlbum(Submission submission, String requestedAlbumName, JwtUserDetails user) {
        String requested = requestedAlbumName == null ? "" : requestedAlbumName.trim();
        if (!requested.isEmpty()) {
            return resolveAlbumByName(submission.getInstitution(), requested, user.userId());
        }
        return resolveSubmissionAlbum(submission, user);
    }

    /**
     * Attaches an existing media library asset to a submission. Used by the
     * media recommendation panel and AssetPickerModal.
     */
    public SubmissionResponseDto attachAsset(UUID submissionId, AttachAssetDto dto, JwtUserDetails user) {
        Submission submission = loadOwnedSubmission(submissionId, user);
        assertEditableStatus(submission);
        return attachLibraryAssetTo(submission, dto.getMediaAssetId(), user);
    }

    /**
     * attach-library-asset core — caller owns the auth/status checks. Reused by
     * ValidationService.
     */
    SubmissionResponseDto attachLibraryAssetTo(Submission submission, UUID mediaAssetId, JwtUserDetails user) {
        UUID submissionId = submission.getId();
        MediaAsset asset = mediaAssetRepository.findActiveById(mediaAssetId)
                .orElseThrow(() -> new MediaAssetNotFoundException(mediaAssetId));

        // A STAGED upload is not a library asset and cannot be picked this way.
        if (asset.getInstitution() == null) {
            throw new MediaAssetNotFoundException(mediaAssetId);
        }
        // Contributors may use assets from their own institution or from the
        // shared default library. Network-wide reviewers/admins may reuse vetted
        // library assets across institutions.
        UUID assetInstitutionId = asset.getInstitution().getId();
        UUID submissionInstitutionId = submission.getInstitution().getId();
        if (!isNetworkRole(user)
                && !assetInstitutionId.equals(submissionInstitutionId)
                && !assetInstitutionId.equals(sharedInstitutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Media asset does not belong to this submission's institution.");
        }

        if (submissionMediaAssetRepository.existsBySubmissionIdAndMediaAssetId(submissionId, mediaAssetId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Asset is already attached to this submission.");
        }

        long currentCount = submissionMediaAssetRepository.countBySubmissionId(submissionId);
        if (currentCount >= MAX_MEDIA_PER_SUBMISSION) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(422),
                    "Maximum of " + MAX_MEDIA_PER_SUBMISSION + " media assets per submission.");
        }

        linkAssetToSubmission(submission, asset, (int) currentCount);
        refreshManualPublishingFlag(submission);

        Map<String, Object> reuseMetadata = new LinkedHashMap<>();
        reuseMetadata.put("submissionId", submissionId.toString());
        reuseMetadata.put("submissionTitle", submission.getEventTitle());
        reuseMetadata.put("submissionStatus", submission.getStatus().name());
        auditLogService.record(
                userRepository.getReferenceById(user.userId()),
                "MEDIA_ASSET_REUSED", null, null, mediaAssetId, reuseMetadata);

        log.info("Existing asset {} attached to submission {}", asset.getId(), submissionId);
        return buildResponse(submissionRepository.findById(submissionId).orElseThrow());
    }

    public void detachAsset(UUID submissionId, UUID mediaAssetId, JwtUserDetails user) {
        Submission submission = loadOwnedSubmission(submissionId, user);
        assertEditableStatus(submission);
        detachAssetFrom(submission, mediaAssetId);
    }

    /**
     * detach-asset core — caller owns the auth/status checks. Reused by
     * ValidationService.
     */
    void detachAssetFrom(Submission submission, UUID mediaAssetId) {
        UUID submissionId = submission.getId();
        SubmissionMediaAsset link = submissionMediaAssetRepository
                .findBySubmissionIdAndMediaAssetId(submissionId, mediaAssetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Media asset is not attached to this submission."));

        submissionMediaAssetRepository.delete(link);
        submissionMediaAssetRepository.flush();
        refreshManualPublishingFlag(submission);
        log.info("Asset {} detached from submission {}", mediaAssetId, submissionId);

        purgeOrphanedDraftUpload(mediaAssetId, "detach from submission " + submissionId);
    }

    /**
     * Permanently deletes a media asset (row + storage object) when it exists
     * only to back a draft that no longer references it. A draft-only upload
     * attached to nothing served no purpose beyond that draft, so it is removed
     * outright rather than soft-deleted into the 30-day retention queue like a
     * deliberate library deletion. Assets that were ever used in a submission
     * beyond draft status, or that are still attached elsewhere, are left
     * alone. {@code asset_tags} and {@code media_asset_embeddings}
     * cascade-delete at the database level (ON DELETE CASCADE). Safe to call
     * for any asset id — it is a no-op unless the asset is a genuine orphaned
     * draft-only upload. Staged uploads ({@code status = STAGED}) are always
     * caught here: they are never "used beyond draft", so an abandoned draft
     * leaves no library asset behind.
     */
    private void purgeOrphanedDraftUpload(UUID mediaAssetId, String context) {
        boolean everUsedBeyondDraft = !submissionMediaAssetRepository
                .findAssetIdsUsedBeyondDraft(List.of(mediaAssetId)).isEmpty();
        if (everUsedBeyondDraft || submissionMediaAssetRepository.existsByMediaAssetId(mediaAssetId)) {
            return;
        }
        mediaAssetRepository.findActiveById(mediaAssetId).ifPresent(orphan -> {
            String storageUrl = orphan.getStorageUrl();
            mediaAssetRepository.delete(orphan);
            boolean storageDeleted = mediaStorage.deletePublicObject(storageUrl);
            log.info("Orphaned draft-only media asset {} permanently deleted ({}, storageDeleted={})",
                    mediaAssetId, context, storageDeleted);
        });
    }

    /**
     * Updates the posting sequence for media already attached to an editable
     * submission. The request must include every attached media asset exactly
     * once so reordering cannot accidentally drop an asset.
     */
    public SubmissionResponseDto reorderMedia(UUID submissionId, SubmissionMediaOrderDto dto, JwtUserDetails user) {
        Submission submission = loadOwnedSubmission(submissionId, user);
        assertEditableStatus(submission);
        return reorderMediaOf(submission, dto);
    }

    /**
     * True when the reorder request would change nothing — same asset order,
     * same captions, same skip-watermark flags. Lets the review flow skip an
     * audit-log entry for a no-op "Save Changes". Malformed requests (wrong
     * size / unknown ids) return {@code false} and are left for
     * {@link #reorderMediaOf} to reject.
     */
    boolean isNoOpMediaOrder(Submission submission, SubmissionMediaOrderDto dto) {
        List<SubmissionMediaAsset> links
                = submissionMediaAssetRepository.findBySubmissionIdOrderByDisplayOrderAsc(submission.getId());
        List<UUID> requested = dto.getMediaAssetIds();
        if (requested == null || links.size() != requested.size()) {
            return false;
        }
        for (int index = 0; index < requested.size(); index++) {
            UUID assetId = requested.get(index);
            SubmissionMediaAsset link = links.get(index);
            if (!link.getMediaAsset().getId().equals(assetId)) {
                return false;
            }
            if (dto.getMediaCaptions() != null && dto.getMediaCaptions().containsKey(assetId)
                    && !Objects.equals(normalizeOptional(dto.getMediaCaptions().get(assetId)), link.getCaption())) {
                return false;
            }
            if (dto.getSkipWatermarks() != null && dto.getSkipWatermarks().containsKey(assetId)) {
                boolean canSkip = link.getMediaAsset().getFileType().isImage();
                boolean requestedSkip = canSkip && Boolean.TRUE.equals(dto.getSkipWatermarks().get(assetId));
                if (requestedSkip != link.isSkipWatermark()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * reorder + per-asset caption/skip-watermark core — caller owns the
     * auth/status checks. Reused by ValidationService.
     */
    SubmissionResponseDto reorderMediaOf(Submission submission, SubmissionMediaOrderDto dto) {
        UUID submissionId = submission.getId();
        List<SubmissionMediaAsset> links
                = submissionMediaAssetRepository.findBySubmissionIdOrderByDisplayOrderAsc(submissionId);
        if (links.size() != dto.getMediaAssetIds().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "mediaAssetIds must include every attached media asset exactly once.");
        }

        HashSet<UUID> requestedIds = new HashSet<>(dto.getMediaAssetIds());
        if (requestedIds.size() != dto.getMediaAssetIds().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "mediaAssetIds must not contain duplicates.");
        }

        Map<UUID, SubmissionMediaAsset> linksByAssetId = links.stream()
                .collect(Collectors.toMap(link -> link.getMediaAsset().getId(), Function.identity()));
        if (!linksByAssetId.keySet().equals(requestedIds)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "mediaAssetIds must match the media currently attached to this submission.");
        }

        for (int index = 0; index < dto.getMediaAssetIds().size(); index++) {
            SubmissionMediaAsset link = linksByAssetId.get(dto.getMediaAssetIds().get(index));
            link.setDisplayOrder(index);
            if (dto.getMediaCaptions() != null && dto.getMediaCaptions().containsKey(dto.getMediaAssetIds().get(index))) {
                link.setCaption(normalizeOptional(dto.getMediaCaptions().get(dto.getMediaAssetIds().get(index))));
            }
            if (dto.getSkipWatermarks() != null && dto.getSkipWatermarks().containsKey(dto.getMediaAssetIds().get(index))) {
                boolean canSkipWatermark = link.getMediaAsset().getFileType().isImage();
                link.setSkipWatermark(canSkipWatermark && Boolean.TRUE.equals(dto.getSkipWatermarks().get(dto.getMediaAssetIds().get(index))));
            }
        }
        submissionMediaAssetRepository.saveAll(links);

        log.info("Reordered media for submission {}", submissionId);
        return buildResponse(submission);
    }

    // ── UC-3.1 Admin Reschedule ───────────────────────────────────────────────
    /**
     * Allows an Moderator to move a SCHEDULED submission to a new slot.
     *
     * Guard rails are re-evaluated. Hard violations block the move unless the
     * admin supplies an overrideReason, which is then written to the audit log.
     *
     * A Moderator (not Admin — unrestricted, final override authority already)
     * is additionally capped: at most {@link #MODERATOR_MAX_RESCHEDULES} moves
     * per submission — not per Moderator, it doesn't matter how many different
     * ones did it — and never further than {@link #MODERATOR_RESCHEDULE_WINDOW}
     * from the slot the submission was originally approved at
     * ({@code originalScheduledAt}, snapshotted once in
     * {@code ValidationService.approve()}), not from wherever it most recently
     * landed — otherwise repeated small moves could drift it arbitrarily far
     * from what was actually reviewed. Hitting either limit is a hard stop for
     * a Moderator, with no override path of its own: they ask an Admin, who
     * can just reschedule directly since Admin isn't capped.
     */
    public SubmissionResponseDto reschedule(UUID submissionId, RescheduleRequestDto dto, JwtUserDetails user) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new SubmissionNotFoundException(submissionId));

        if (submission.getStatus() != SubmissionStatus.scheduled) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only SCHEDULED submissions can be rescheduled. Current status: " + submission.getStatus());
        }

        Instant originalSlot = submission.getScheduledAt();
        Instant newSlot = dto.getScheduledAt();
        boolean callerIsAdmin = isAdmin(user);
        // Captured now, while submission is still attached -- the atomic claim
        // below clears the persistence context, so submission.getInstitution()
        // (a lazy proxy) is not safe to dereference again after that point.
        UUID institutionId = submission.getInstitution().getId();

        if (!callerIsAdmin) {
            if (submission.getModeratorRescheduleCount() >= MODERATOR_MAX_RESCHEDULES) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "This post has already been rescheduled " + MODERATOR_MAX_RESCHEDULES
                                + " times by a Moderator. Ask an Administrator to reschedule it further.");
            }
            Instant anchor = submission.getOriginalScheduledAt() != null
                    ? submission.getOriginalScheduledAt() : originalSlot;
            Duration drift = Duration.between(anchor, newSlot).abs();
            if (drift.compareTo(MODERATOR_RESCHEDULE_WINDOW) > 0) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Moderators may only reschedule within " + MODERATOR_RESCHEDULE_WINDOW.toDays()
                                + " day of the originally approved time. Ask an Administrator for a larger change.");
            }
        }

        GuardRailResult guardRailResult = guardRailService.validate(institutionId, newSlot, submissionId);
        boolean adminOverride = false;
        if (guardRailResult.isBlocked()) {
            if (dto.getOverrideReason() == null || dto.getOverrideReason().isBlank()) {
                throw new GuardRailViolationException(guardRailResult.getHardBlocks());
            }
            if (!callerIsAdmin) {
                // Moderators cannot bypass a guard rail — they raise an override
                // request for an administrator to decide.
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Only an administrator can bypass a guard rail. Submit an override request instead.");
            }
            auditLogService.record(
                    entityManager.getReference(User.class, user.userId()),
                    "ADMIN_RESCHEDULE_OVERRIDE",
                    null, null,
                    submissionId,
                    Map.of(
                            "originalSlot", originalSlot.toString(),
                            "newSlot", newSlot.toString(),
                            "overrideReason", dto.getOverrideReason(),
                            "violations", guardRailResult.getHardBlocks().toString()
                    )
            );
            // Marks the reservation exempt from the V95/V96 network-wide
            // exclusion constraint below -- otherwise the DB would reject the
            // very slot the Administrator just chose to override into.
            adminOverride = true;
        }

        // Atomic claim (see SubmissionRepository.claimModeratorReschedule/claimAdminReschedule):
        // the earlier cap/window check above is a normal entity read, which is fine
        // for fast, cheap rejection, but Submission has no @Version/optimistic
        // locking, so that read-check-write pattern alone would let two concurrent
        // reschedule requests for the same submission both pass the check before
        // either writes. The actual write is a single conditional UPDATE guarded by
        // the same WHERE clause, mirroring PublishingQueryService.claimForPublishing's
        // idiom for exactly this class of race. A 0-row result here means someone
        // else's request won the race (or changed the submission's status) between
        // our read above and now — never silently proceed in that case.
        int claimed = callerIsAdmin
                ? submissionRepository.claimAdminReschedule(submissionId, newSlot)
                : submissionRepository.claimModeratorReschedule(submissionId, newSlot, MODERATOR_MAX_RESCHEDULES);
        if (claimed != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This post was just changed by someone else. Please refresh and try again.");
        }

        slotReservationService.reserveLockedSlot(submissionId, institutionId, newSlot, adminOverride);

        // The claim above was a bulk update (clearAutomatically = true), which
        // detaches whatever was loaded earlier in this persistence context — reload
        // fresh rather than risk building the response off now-stale in-memory state.
        Submission updated = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new SubmissionNotFoundException(submissionId));

        log.info("Admin {} rescheduled submission {} from {} to {}", user.userId(), submissionId, originalSlot, newSlot);
        eventPublisher.publishEvent(new SubmissionRescheduledEvent(updated, originalSlot, newSlot));

        return buildResponse(updated);
    }

    // ── Private Helpers ──────────────────────────────────────────────────────
    private Submission loadOwnedSubmission(UUID submissionId, JwtUserDetails user) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new SubmissionNotFoundException(submissionId));
        if (!submission.getContributor().getId().equals(user.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You do not own this submission.");
        }
        return submission;
    }

    private UUID resolveSubmissionInstitutionId(UUID requestedInstitutionId, JwtUserDetails user) {
        if ("moderator".equalsIgnoreCase(user.role()) || "admin".equalsIgnoreCase(user.role())) {
            if (requestedInstitutionId != null) {
                return requestedInstitutionId;
            }
            return institutionRepository.findByNameIgnoreCase("DASIG Central Visayas")
                    .map(Institution::getId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Default institution 'DASIG Central Visayas' not found."));
        }
        if (user.institutionId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Your account is not scoped to an institution.");
        }
        return user.institutionId();
    }

    private UUID sharedInstitutionId() {
        return institutionRepository.findFirstByIsProtectedTrueOrderByCreatedAtAsc()
                .map(Institution::getId)
                .orElse(null);
    }

    private void assertEditableStatus(Submission submission) {
        if (!isEditableStatus(submission)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Submission cannot be edited in status: " + submission.getStatus());
        }
    }

    private boolean isEditableStatus(Submission submission) {
        return submission.getStatus() == SubmissionStatus.draft
                || submission.getStatus() == SubmissionStatus.needs_revision
                || submission.getStatus() == SubmissionStatus.rejected;
    }

    private boolean isPrivateDraftStatus(Submission submission) {
        return submission.getStatus() == SubmissionStatus.draft
                || submission.getStatus() == SubmissionStatus.needs_revision;
    }

    private void assertReadAccess(Submission submission, JwtUserDetails user) {
        switch (user.role().toLowerCase()) {
            case "moderator", "admin" -> {
                if (isPrivateDraftStatus(submission)
                        && !submission.getContributor().getId().equals(user.userId())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied.");
                }
            }
            case "contributor" -> {
                if (!submission.getContributor().getId().equals(user.userId())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied.");
                }
            }
            default ->
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unknown role.");
        }
    }

    private void linkAssetToSubmission(Submission submission, MediaAsset asset, int currentCount) {
        SubmissionMediaAsset link = new SubmissionMediaAsset();
        link.setSubmission(submission);
        link.setMediaAsset(asset);
        link.setDisplayOrder(currentCount);
        submissionMediaAssetRepository.save(link);
    }

    private MediaFileType validateMediaFile(String rawFileType, Long fileSizeBytes) {
        MediaFileType fileType;
        try {
            fileType = MediaFileType.valueOf(rawFileType == null ? "" : rawFileType.toLowerCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported file type. Accepted types: JPEG, PNG, WebP, GIF, MP4, MOV, WebM.");
        }
        if (fileSizeBytes == null || fileSizeBytes <= 0 || fileSizeBytes > MAX_FILE_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(422),
                    "File size must be greater than 0 and no larger than 50 MB.");
        }
        return fileType;
    }

    private void refreshManualPublishingFlag(Submission submission) {
        List<MediaAsset> assets = submissionMediaAssetRepository
                .findMediaAssetsBySubmissionId(submission.getId());
        boolean hasImage = assets.stream().anyMatch(asset -> asset.getFileType().isImage());
        boolean hasVideo = assets.stream().anyMatch(asset -> asset.getFileType().isVideo());
        submission.setRequiresManualPublishing(hasImage && hasVideo);
        submissionRepository.save(submission);
    }

    private SubmissionResponseDto buildResponse(Submission submission) {
        List<MediaAssetSummaryDto> mediaAssets = submissionMediaAssetRepository
                .findBySubmissionIdOrderByDisplayOrderAsc(submission.getId())
                .stream()
                .map(MediaAssetSummaryDto::from)
                .toList();
        return SubmissionResponseDto.from(submission, mediaAssets);
    }

    private String generateAssetCode() {
        return "ASSET-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void validateCaptionCharLimit(String caption, HttpStatus status) {
        if (captionLength(caption) <= MAX_CAPTION_CHARS) {
            return;
        }
        throw new ResponseStatusException(
                status,
                "Caption must not exceed " + MAX_CAPTION_CHARS + " characters.");
    }

    private static int captionLength(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        return value.codePointCount(0, value.length());
    }

    private static String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        String joined = tags.stream()
                .map(SubmissionService::normalizeOptional)
                .filter(tag -> tag != null && !tag.isBlank())
                .distinct()
                .collect(Collectors.joining(","));
        return joined.isBlank() ? null : joined;
    }

    private static String formatInstant(Instant instant) {
        return java.time.ZonedDateTime.ofInstant(instant, java.time.ZoneOffset.UTC)
                .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm 'UTC'"));
    }
}
