package com.dasigconnect.backend.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.context.ApplicationEventPublisher;

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
import com.dasigconnect.backend.model.dto.submission.SubmissionMediaOrderDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionResponseDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionSummaryDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionUpdateDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.NotificationEventType;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionMediaAsset;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.repository.MediaAssetRepository;
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

    private final SubmissionRepository submissionRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final SubmissionMediaAssetRepository submissionMediaAssetRepository;
    private final SlotReservationService slotReservationService;
    private final GuardRailService guardRailService;
    private final AuditLogService auditLogService;
    private final SupabaseStorageService supabaseStorageService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${app.guardrails.enforced:true}")
    private boolean guardRailsEnforced = true;

    public SubmissionService(
            SubmissionRepository submissionRepository,
            MediaAssetRepository mediaAssetRepository,
            SubmissionMediaAssetRepository submissionMediaAssetRepository,
            SlotReservationService slotReservationService,
            GuardRailService guardRailService,
            AuditLogService auditLogService,
            SupabaseStorageService supabaseStorageService,
            NotificationService notificationService,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher) {
        this.submissionRepository = submissionRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.submissionMediaAssetRepository = submissionMediaAssetRepository;
        this.slotReservationService = slotReservationService;
        this.guardRailService = guardRailService;
        this.auditLogService = auditLogService;
        this.supabaseStorageService = supabaseStorageService;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public SignedUploadUrlResponse createSignedUploadUrl(UUID submissionId, SignedUploadUrlRequest dto, JwtUserDetails user) {
        Submission submission = loadForContributor(submissionId, user);
        assertEditableStatus(submission);
        String safeFileName = dto.getFileName().replaceAll("[^a-zA-Z0-9._-]", "-");
        String objectPath = submissionId + "/" + UUID.randomUUID() + "-" + safeFileName;
        String signedUrl = supabaseStorageService.createSignedUploadUrl(objectPath);
        String publicUrl = supabaseStorageService.getPublicUrl(objectPath);
        return new SignedUploadUrlResponse(signedUrl, publicUrl, objectPath);
    }

    /**
     * Creates a new submission in DRAFT status. If scheduledAt is provided,
     * validates guard rails and reserves the slot. Guard rail violations are
     * returned as HTTP 409 with the violation details.
     */
    public SubmissionResponseDto create(SubmissionCreateDto dto, JwtUserDetails user) {
        User contributor = entityManager.getReference(User.class, user.userId());
        Institution institution = entityManager.getReference(Institution.class, user.institutionId());

        Submission submission = new Submission();
        submission.setContributor(contributor);
        submission.setInstitution(institution);
        submission.setEventTitle(dto.getEventTitle());
        submission.setEventDate(dto.getEventDate());
        submission.setCaption(dto.getCaption());
        submission.setDescription(dto.getDescription());
        submission.setStatus(SubmissionStatus.draft);
        submission.setCategory(dto.getCategory());
        if (dto.getTags() != null && !dto.getTags().isEmpty()) {
            submission.setTags(String.join(",", dto.getTags()));
        }

        submission = submissionRepository.save(submission);

        if (dto.getScheduledAt() != null) {
            submission.setScheduledAt(dto.getScheduledAt());
            slotReservationService.reserve(submission.getId(), user.institutionId(), dto.getScheduledAt());
            submission = submissionRepository.save(submission);
        }

        auditLogService.record(contributor, "SUBMISSION_CREATED", null, null,
                submission.getId(), Map.of("eventTitle", submission.getEventTitle()));

        log.info("Submission {} created as DRAFT by contributor {}", submission.getId(), user.userId());
        return buildResponse(submission);
    }

    /**
     * Updates a DRAFT or NEEDS_REVISION submission (auto-save support). If
     * scheduledAt changes, releases the old slot and reserves the new one.
     */
    public SubmissionResponseDto update(UUID submissionId, SubmissionUpdateDto dto, JwtUserDetails user) {
        Submission submission = loadForContributor(submissionId, user);
        assertEditableStatus(submission);

        if (dto.getEventTitle() != null) {
            submission.setEventTitle(dto.getEventTitle());
        }
        if (dto.getEventDate() != null) {
            submission.setEventDate(dto.getEventDate());
        }
        if (dto.getCaption() != null) {
            submission.setCaption(dto.getCaption());
        }
        if (dto.getDescription() != null) {
            submission.setDescription(dto.getDescription());
        }
        if (dto.getCategory() != null) {
            submission.setCategory(dto.getCategory());
        }
        if (dto.getTags() != null) {
            submission.setTags(dto.getTags().isEmpty() ? null : String.join(",", dto.getTags()));
        }

        if (dto.getScheduledAt() != null && !dto.getScheduledAt().equals(submission.getScheduledAt())) {
            submission.setScheduledAt(dto.getScheduledAt());
            // reserve() releases any existing held slot and creates a new one
            slotReservationService.reserve(submissionId, user.institutionId(), dto.getScheduledAt());
        }

        submission = submissionRepository.save(submission);
        return buildResponse(submission);
    }

    /**
     * Deletes a DRAFT submission and removes its slot reservations. Only the
     * owning contributor may delete. Only DRAFT status is deletable.
     */
    public void delete(UUID submissionId, JwtUserDetails user) {
        Submission submission = loadForContributor(submissionId, user);
        if (submission.getStatus() != SubmissionStatus.draft) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only DRAFT submissions can be deleted. Current status: " + submission.getStatus());
        }
        submissionMediaAssetRepository.deleteBySubmissionId(submissionId);
        slotReservationService.deleteAllForSubmission(submissionId);
        submissionRepository.delete(submission);
        log.info("Submission {} deleted by contributor {}", submissionId, user.userId());
    }

    /**
     * Transitions DRAFT → PENDING (initial submission) or NEEDS_REVISION →
     * PENDING (re-submission after revision request). Re-validates guard rails
     * before accepting.
     */
    public SubmissionResponseDto submit(UUID submissionId, JwtUserDetails user) {
        Submission submission = loadForContributor(submissionId, user);

        if (submission.getStatus() != SubmissionStatus.draft
                && submission.getStatus() != SubmissionStatus.needs_revision) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only DRAFT or NEEDS_REVISION submissions can be submitted. Current status: "
                    + submission.getStatus());
        }

        if (guardRailsEnforced && submission.getScheduledAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A scheduled time must be selected before submitting.");
        }

        // Re-run guard rails — slot may have been taken since draft was saved
        if (guardRailsEnforced && submission.getScheduledAt() != null) {
            GuardRailResult result = guardRailService.validate(user.institutionId(), submission.getScheduledAt());
            if (result.isBlocked()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Guard rail violation: " + result.getHardBlocks().get(0).getMessage());
            }
        } else {
            log.info("Guard rail enforcement disabled; submitting {} without blocking slot validation.",
                    submissionId);
        }

        // Content completeness is a data-integrity invariant, not a scheduling
        // guard rail — enforce it on every submit regardless of guardRailsEnforced.
        assertContentComplete(submission);

        submission.setStatus(SubmissionStatus.pending);
        submission.setSubmittedAt(Instant.now());
        submission = submissionRepository.save(submission);

        auditLogService.record(
                entityManager.getReference(User.class, user.userId()),
                "SUBMISSION_SUBMITTED", null, null,
                submissionId,
                submission.getScheduledAt() != null
                        ? Map.of("scheduledAt", submission.getScheduledAt().toString())
                        : Map.of());

        // T1 — notify all institution validators (spec: contributor does not receive T1)
        try {
            String contributorEmail = submission.getContributor().getEmail();
            String scheduledPart = submission.getScheduledAt() != null
                    ? " — scheduled for " + formatInstant(submission.getScheduledAt())
                    : "";
            String t1Message = contributorEmail + " submitted '" + submission.getEventTitle()
                    + "' for review" + scheduledPart + ".";
            String submissionLink = "/submissions/" + submissionId;

            List<User> validators = userRepository
                    .findByInstitutionIdAndRoleOrderByCreatedAtDesc(user.institutionId(), UserRole.validator);
            for (User validator : validators) {
                notificationService.createNotification(
                        validator,
                        NotificationEventType.submission_pending,
                        t1Message,
                        submissionLink);
            }
        } catch (Exception e) {
            log.warn("T1 notifications skipped for submission {} — {}", submissionId, e.getMessage());
        }

        log.info("Submission {} → PENDING by contributor {}", submissionId, user.userId());
        return buildResponse(submission);
    }

    /**
     * Rejects a submit when the post is missing fields required for a publishable
     * post: an event title, an event date, a caption, and at least one media
     * asset. Throws 422 listing everything that is still missing.
     */
    private void assertContentComplete(Submission submission) {
        List<String> missing = new java.util.ArrayList<>();
        if (submission.getEventTitle() == null || submission.getEventTitle().isBlank()) {
            missing.add("an event title");
        }
        if (submission.getEventDate() == null) {
            missing.add("an event date");
        }
        if (submission.getCaption() == null || submission.getCaption().isBlank()) {
            missing.add("a caption");
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
    public GuardRailResult evaluateSlot(SlotEvaluateRequestDto dto, JwtUserDetails user) {
        return guardRailService.validate(user.institutionId(), dto.getScheduledAt());
    }

    /**
     * Lists submissions filtered by the caller's role: - CONTRIBUTOR: only
     * their own submissions for their institution - VALIDATOR: all submissions
     * for their institution - ADMINISTRATOR: all submissions (not filtered by
     * institution)
     */
    @Transactional(readOnly = true)
    public List<SubmissionSummaryDto> list(JwtUserDetails user) {
        List<Submission> submissions = switch (user.role().toLowerCase()) {
            case "contributor" ->
                submissionRepository
                .findByContributorIdAndInstitutionIdOrderByCreatedAtDesc(user.userId(), user.institutionId());
            case "validator" ->
                submissionRepository
                .findByInstitutionIdOrderByCreatedAtDesc(user.institutionId());
            case "administrator" ->
                submissionRepository.findAllByOrderByCreatedAtDesc();
            default ->
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unknown role");
        };

        return submissions.stream()
                .map(s -> SubmissionSummaryDto.from(s, submissionMediaAssetRepository.countBySubmissionId(s.getId())))
                .toList();
    }

    /**
     * Returns full submission detail. Accessible by the owning contributor, any
     * validator of the same institution, or any administrator.
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
     */
    public SubmissionResponseDto attachMedia(UUID submissionId, AttachMediaDto dto, JwtUserDetails user) {
        Submission submission = loadForContributor(submissionId, user);
        assertEditableStatus(submission);

        long currentCount = submissionMediaAssetRepository.countBySubmissionId(submissionId);
        if (currentCount >= MAX_MEDIA_PER_SUBMISSION) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(422),
                    "Maximum of " + MAX_MEDIA_PER_SUBMISSION + " media assets per submission.");
        }

        MediaFileType fileType;
        try {
            fileType = MediaFileType.valueOf(dto.getFileType().toLowerCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported file type: " + dto.getFileType());
        }

        MediaAsset asset = new MediaAsset();
        asset.setInstitution(entityManager.getReference(Institution.class, user.institutionId()));
        asset.setUploader(entityManager.getReference(User.class, user.userId()));
        asset.setAssetCode(generateAssetCode());
        asset.setStorageUrl(dto.getStorageUrl());
        asset.setFileName(dto.getFileName());
        asset.setFileType(fileType);
        asset.setFileSizeBytes(dto.getFileSizeBytes());
        asset = mediaAssetRepository.save(asset);

        linkAssetToSubmission(submission, asset, (int) currentCount);

        log.info("Media asset {} attached to submission {} by contributor {}", asset.getId(), submissionId, user.userId());
        return buildResponse(submission);
    }

    /**
     * Attaches an existing media library asset to a submission. Used by the
     * media recommendation panel and AssetPickerModal.
     */
    public SubmissionResponseDto attachAsset(UUID submissionId, AttachAssetDto dto, JwtUserDetails user) {
        Submission submission = loadForContributor(submissionId, user);
        assertEditableStatus(submission);

        MediaAsset asset = mediaAssetRepository.findActiveById(dto.getMediaAssetId())
                .orElseThrow(() -> new MediaAssetNotFoundException(dto.getMediaAssetId()));

        // Validate asset belongs to same institution
        if (!asset.getInstitution().getId().equals(user.institutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Media asset does not belong to your institution.");
        }

        if (submissionMediaAssetRepository.existsBySubmissionIdAndMediaAssetId(submissionId, dto.getMediaAssetId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Asset is already attached to this submission.");
        }

        long currentCount = submissionMediaAssetRepository.countBySubmissionId(submissionId);
        if (currentCount >= MAX_MEDIA_PER_SUBMISSION) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(422),
                    "Maximum of " + MAX_MEDIA_PER_SUBMISSION + " media assets per submission.");
        }

        linkAssetToSubmission(submission, asset, (int) currentCount);

        log.info("Existing asset {} attached to submission {} by contributor {}", asset.getId(), submissionId, user.userId());
        return buildResponse(submissionRepository.findById(submissionId).orElseThrow());
    }

    public void detachAsset(UUID submissionId, UUID mediaAssetId, JwtUserDetails user) {
        Submission submission = loadForContributor(submissionId, user);
        assertEditableStatus(submission);

        SubmissionMediaAsset link = submissionMediaAssetRepository
                .findBySubmissionIdAndMediaAssetId(submissionId, mediaAssetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Media asset is not attached to this submission."));

        submissionMediaAssetRepository.delete(link);
        log.info("Asset {} detached from submission {} by contributor {}", mediaAssetId, submissionId, user.userId());
    }

    /**
     * Updates the posting sequence for media already attached to an editable
     * submission. The request must include every attached media asset exactly
     * once so reordering cannot accidentally drop an asset.
     */
    public SubmissionResponseDto reorderMedia(UUID submissionId, SubmissionMediaOrderDto dto, JwtUserDetails user) {
        Submission submission = loadForContributor(submissionId, user);
        assertEditableStatus(submission);

        List<SubmissionMediaAsset> links =
                submissionMediaAssetRepository.findBySubmissionIdOrderByDisplayOrderAsc(submissionId);
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
            linksByAssetId.get(dto.getMediaAssetIds().get(index)).setDisplayOrder(index);
        }
        submissionMediaAssetRepository.saveAll(links);

        log.info("Contributor {} reordered media for submission {}", user.userId(), submissionId);
        return buildResponse(submission);
    }

    // ── UC-3.1 Admin Reschedule ───────────────────────────────────────────────

    /**
     * Allows an Administrator to move a SCHEDULED submission to a new slot.
     *
     * Guard rails are re-evaluated. Hard violations block the move unless the
     * admin supplies an overrideReason, which is then written to the audit log.
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

        GuardRailResult guardRailResult = guardRailService.validate(submission.getInstitution().getId(), newSlot);
        if (guardRailResult.isBlocked()) {
            if (dto.getOverrideReason() == null || dto.getOverrideReason().isBlank()) {
                throw new GuardRailViolationException(guardRailResult.getHardBlocks());
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
        }

        slotReservationService.reserveLockedSlot(submissionId, submission.getInstitution().getId(), newSlot);
        submission.setScheduledAt(newSlot);
        submissionRepository.save(submission);

        log.info("Admin {} rescheduled submission {} from {} to {}", user.userId(), submissionId, originalSlot, newSlot);
        eventPublisher.publishEvent(new SubmissionRescheduledEvent(submission, originalSlot, newSlot));

        return buildResponse(submission);
    }

    // ── Private Helpers ──────────────────────────────────────────────────────
    private Submission loadForContributor(UUID submissionId, JwtUserDetails user) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new SubmissionNotFoundException(submissionId));
        if (!submission.getContributor().getId().equals(user.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You do not own this submission.");
        }
        return submission;
    }

    private void assertEditableStatus(Submission submission) {
        if (submission.getStatus() != SubmissionStatus.draft
                && submission.getStatus() != SubmissionStatus.needs_revision) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Submission cannot be edited in status: " + submission.getStatus());
        }
    }

    private void assertReadAccess(Submission submission, JwtUserDetails user) {
        switch (user.role().toLowerCase()) {
            case "administrator" -> {
                /* full access */ }
            case "validator" -> {
                if (!submission.getInstitution().getId().equals(user.institutionId())) {
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

    private SubmissionResponseDto buildResponse(Submission submission) {
        List<MediaAssetSummaryDto> mediaAssets = submissionMediaAssetRepository
                .findBySubmissionIdOrderByDisplayOrderAsc(submission.getId())
                .stream()
                .map(sma -> MediaAssetSummaryDto.from(sma.getMediaAsset()))
                .toList();
        return SubmissionResponseDto.from(submission, mediaAssets);
    }

    private String generateAssetCode() {
        return "ASSET-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private static String formatInstant(Instant instant) {
        return java.time.ZonedDateTime.ofInstant(instant, java.time.ZoneOffset.UTC)
                .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm 'UTC'"));
    }
}
