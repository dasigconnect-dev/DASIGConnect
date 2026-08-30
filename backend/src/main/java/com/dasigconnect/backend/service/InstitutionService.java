package com.dasigconnect.backend.service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.exception.InstitutionNotFoundException;
import com.dasigconnect.backend.model.dto.institution.CreateInstitutionRequest;
import com.dasigconnect.backend.model.dto.institution.InstitutionDto;
import com.dasigconnect.backend.model.dto.institution.UpdateInstitutionRequest;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.InstitutionStatus;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.model.entity.UserStatus;
import com.dasigconnect.backend.repository.InstitutionRepository;
import com.dasigconnect.backend.repository.InvitationTokenRepository;
import com.dasigconnect.backend.repository.MediaAlbumRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.PageSettingsRepository;
import com.dasigconnect.backend.repository.SlotReservationRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.repository.WatermarkConfigurationRepository;

/**
 * Manages the institution lifecycle.
 *
 * State machine: INACTIVE → (admin sends moderator invitation) → PENDING
 * PENDING → (moderator activates account) → ACTIVE ACTIVE → (all moderators
 * deactivated) → INACTIVE PENDING → (last moderator invitation cancelled, no
 * active moderators) → INACTIVE
 *
 * Invalid transitions are rejected with IllegalStateException → HTTP 409.
 */
@Service
@Transactional
public class InstitutionService {

    private static final Logger log = LoggerFactory.getLogger(InstitutionService.class);
    private static final long MAX_LOGO_BYTES = 2L * 1024 * 1024;

    private final InstitutionRepository institutionRepository;
    private final UserRepository userRepository;
    private final SubmissionRepository submissionRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final MediaAlbumRepository mediaAlbumRepository;
    private final InvitationTokenRepository invitationTokenRepository;
    private final SlotReservationRepository slotReservationRepository;
    private final PageSettingsRepository pageSettingsRepository;
    private final WatermarkConfigurationRepository watermarkConfigurationRepository;
    /** Media object store — the concrete host is abstracted behind this service. */
    private final MediaStorageService mediaStorage;
    private final WorkspaceProvisionerService workspaceProvisioner;
    private final AuditLogService auditLogService;

    public InstitutionService(
            InstitutionRepository institutionRepository,
            UserRepository userRepository,
            SubmissionRepository submissionRepository,
            MediaAssetRepository mediaAssetRepository,
            MediaAlbumRepository mediaAlbumRepository,
            InvitationTokenRepository invitationTokenRepository,
            SlotReservationRepository slotReservationRepository,
            PageSettingsRepository pageSettingsRepository,
            WatermarkConfigurationRepository watermarkConfigurationRepository,
            MediaStorageService mediaStorage,
            WorkspaceProvisionerService workspaceProvisioner,
            AuditLogService auditLogService) {
        this.institutionRepository = institutionRepository;
        this.userRepository = userRepository;
        this.submissionRepository = submissionRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.mediaAlbumRepository = mediaAlbumRepository;
        this.invitationTokenRepository = invitationTokenRepository;
        this.slotReservationRepository = slotReservationRepository;
        this.pageSettingsRepository = pageSettingsRepository;
        this.watermarkConfigurationRepository = watermarkConfigurationRepository;
        this.mediaStorage = mediaStorage;
        this.workspaceProvisioner = workspaceProvisioner;
        this.auditLogService = auditLogService;
    }

    /**
     * Creates a new institution with status INACTIVE and provisions its RLS
     * workspace.
     */
    public InstitutionDto createInstitution(CreateInstitutionRequest request) {
        if (institutionRepository.existsByCode(request.getInstitutionCode())) {
            throw new IllegalArgumentException(
                    "Institution code '" + request.getInstitutionCode() + "' is already in use.");
        }

        String emailDomain = request.getEmailDomain().trim().toLowerCase();
        if (institutionRepository.existsByEmailDomain(emailDomain)) {
            throw new IllegalArgumentException(
                    "Email domain '" + emailDomain + "' is already in use.");
        }

        Institution institution = new Institution();
        institution.setName(request.getName());
        institution.setCode(request.getInstitutionCode());
        institution.setEmailDomain(emailDomain);
        institution.setStatus(InstitutionStatus.active);

        institution = institutionRepository.save(institution);

        workspaceProvisioner.provision(institution);

        auditLogService.recordSystemAction(
                "INSTITUTION_CREATED",
                institution.getId(),
                Map.of("name", institution.getName(), "code", institution.getCode())
        );

        log.info("Institution created: {} ({}), status=INACTIVE", institution.getName(), institution.getId());
        return InstitutionDto.from(institution);
    }

    @Transactional(readOnly = true)
    public java.util.List<InstitutionDto> listInstitutions() {
        return institutionRepository.findAll().stream()
                .map(InstitutionDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public InstitutionDto getInstitution(UUID institutionId) {
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new InstitutionNotFoundException(institutionId));
        return InstitutionDto.from(institution);
    }

    public InstitutionDto updateLogo(UUID institutionId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a logo image to upload.");
        }
        if (file.getSize() > MAX_LOGO_BYTES) {
            throw new IllegalArgumentException("Institution logo must be 2 MB or smaller.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to read the uploaded logo.", ex);
        }

        String contentType = detectLogoContentType(bytes);
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new InstitutionNotFoundException(institutionId));
        institution.setLogoData(bytes);
        institution.setLogoContentType(contentType);
        institution.setLogoUpdatedAt(Instant.now());
        Institution saved = institutionRepository.save(institution);

        auditLogService.recordSystemAction(
                "INSTITUTION_LOGO_UPDATED",
                institutionId,
                Map.of("contentType", contentType, "sizeBytes", bytes.length));
        return InstitutionDto.from(saved);
    }

    @Transactional(readOnly = true)
    public InstitutionLogo getLogo(UUID institutionId) {
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new InstitutionNotFoundException(institutionId));
        byte[] data = institution.getLogoData();
        if (data == null || data.length == 0 || institution.getLogoContentType() == null) {
            throw new InstitutionNotFoundException(institutionId);
        }
        return new InstitutionLogo(data, institution.getLogoContentType());
    }

    private String detectLogoContentType(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4e
                && bytes[3] == 0x47
                && bytes[4] == 0x0d
                && bytes[5] == 0x0a
                && bytes[6] == 0x1a
                && bytes[7] == 0x0a) {
            return "image/png";
        }
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        throw new IllegalArgumentException("Logo must be a valid JPEG, PNG, or WebP image.");
    }

    public record InstitutionLogo(byte[] data, String contentType) {

    }

    // ── A1: Edit Institution Details ──────────────────────────────────────────
    /**
     * Updates an institution's name and/or email domain. Validates uniqueness
     * of name and domain across all other institutions.
     */
    public InstitutionDto updateInstitution(UUID institutionId, UpdateInstitutionRequest request) {
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new InstitutionNotFoundException(institutionId));

        String newName = request.getName().trim();
        String newDomain = request.getEmailDomain().trim().toLowerCase();

        // A5: reject duplicate name (exclude self)
        if (institutionRepository.existsByNameIgnoreCaseAndIdNot(newName, institutionId)) {
            throw new IllegalArgumentException(
                    "An institution named '" + newName + "' already exists.");
        }

        // reject duplicate email domain (exclude self)
        if (institutionRepository.existsByEmailDomainAndIdNot(newDomain, institutionId)) {
            throw new IllegalArgumentException(
                    "Email domain '" + newDomain + "' is already in use by another institution.");
        }

        String previousName = institution.getName();
        String previousDomain = institution.getEmailDomain();
        institution.setName(newName);
        institution.setEmailDomain(newDomain);
        Institution saved = institutionRepository.save(institution);

        auditLogService.recordSystemAction(
                "INSTITUTION_UPDATED",
                institutionId,
                Map.of(
                        "previousName", previousName, "newName", newName,
                        "previousDomain", previousDomain, "newDomain", newDomain
                )
        );

        log.info("Institution {} updated: name='{}', domain='{}'", institutionId, newName, newDomain);
        return InstitutionDto.from(saved);
    }

    // ── A2: Deactivate Institution ────────────────────────────────────────────
    /**
     * Admin-initiated deactivation of an institution (A2). Sets status to
     * INACTIVE regardless of current status (active or pending). Historical
     * data is retained; new invitations will be blocked by status checks.
     */
    public InstitutionDto deactivateInstitution(UUID institutionId) {
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new InstitutionNotFoundException(institutionId));

        if (institution.isProtected()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This institution cannot be deactivated");
        }

        if (institution.getStatus() == InstitutionStatus.inactive) {
            throw new IllegalStateException(
                    "Institution '" + institution.getName() + "' is already inactive.");
        }

        boolean hasBlockingContributors = hasBlockingContributorAccounts(institutionId);
        long pendingInviteCount = invitationTokenRepository
                .countByInstitutionIdAndAssignedRoleAndUsedAtIsNullAndExpiresAtAfter(
                        institutionId,
                        UserRole.contributor,
                        Instant.now());
        if (hasBlockingContributors || pendingInviteCount > 0) {
            throw new IllegalStateException(
                    "Cannot deactivate institution '" + institution.getName()
                    + "' while active contributors or pending contributor invitations exist. Please transfer or remove accounts first.");
        }

        String previousStatus = institution.getStatus().name();
        institution.setStatus(InstitutionStatus.inactive);
        Institution saved = institutionRepository.save(institution);

        auditLogService.recordSystemAction(
                "INSTITUTION_DEACTIVATED",
                institutionId,
                Map.of("previousStatus", previousStatus)
        );

        log.info("Institution {} deactivated by admin ({} → inactive)", institutionId, previousStatus);
        return InstitutionDto.from(saved);
    }

    // ── A3: Reactivate Institution ────────────────────────────────────────────
    /**
     * Admin-initiated reactivation of a deactivated institution (A3). Sets
     * status back to ACTIVE.
     */
    public InstitutionDto reactivateInstitution(UUID institutionId) {
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new InstitutionNotFoundException(institutionId));

        if (institution.getStatus() != InstitutionStatus.inactive) {
            throw new IllegalStateException(
                    "Institution '" + institution.getName() + "' is not inactive and cannot be reactivated.");
        }

        String previousStatus = institution.getStatus().name();
        institution.setStatus(InstitutionStatus.active);
        Institution saved = institutionRepository.save(institution);

        auditLogService.recordSystemAction(
                "INSTITUTION_REACTIVATED",
                institutionId,
                Map.of("previousStatus", previousStatus, "newStatus", InstitutionStatus.active.name())
        );

        log.info("Institution {} reactivated by admin (inactive → ACTIVE)", institutionId);
        return InstitutionDto.from(saved);
    }

    /**
     * INACTIVE → PENDING. Called when an admin sends a moderator invitation.
     */
    public void transitionToPending(UUID institutionId) {
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new InstitutionNotFoundException(institutionId));

        if (institution.getStatus() != InstitutionStatus.inactive) {
            throw new IllegalStateException(
                    "Cannot transition institution " + institutionId + " to PENDING: "
                    + "current status is " + institution.getStatus()
                    + " (expected: inactive)");
        }

        institution.setStatus(InstitutionStatus.pending);
        institutionRepository.save(institution);

        auditLogService.recordSystemAction(
                "INSTITUTION_PENDING",
                institutionId,
                Map.of("previousStatus", "inactive")
        );

        log.info("Institution {} transitioned INACTIVE → PENDING", institutionId);
    }

    /**
     * PENDING → ACTIVE. Called when the first moderator activates their
     * account. Also accepts INACTIVE as a precondition to handle edge cases.
     */
    public void transitionToActive(UUID institutionId) {
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new InstitutionNotFoundException(institutionId));

        if (institution.getStatus() != InstitutionStatus.pending
                && institution.getStatus() != InstitutionStatus.inactive) {
            throw new IllegalStateException(
                    "Cannot transition institution " + institutionId + " to ACTIVE: "
                    + "current status is " + institution.getStatus()
                    + " (expected: pending)");
        }

        String previousStatus = institution.getStatus().name();
        institution.setStatus(InstitutionStatus.active);
        institutionRepository.save(institution);

        auditLogService.recordSystemAction(
                "INSTITUTION_ACTIVATED",
                institutionId,
                Map.of("previousStatus", previousStatus)
        );

        log.info("Institution {} transitioned {} → ACTIVE", institutionId, previousStatus.toUpperCase());
    }

    /**
     * ACTIVE or PENDING → INACTIVE. Called when all moderators are
     * deactivated/removed, or when the last pending moderator invitation is
     * cancelled.
     */
    public void transitionToInactive(UUID institutionId) {
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new InstitutionNotFoundException(institutionId));

        if (institution.getStatus() != InstitutionStatus.active
                && institution.getStatus() != InstitutionStatus.pending) {
            throw new IllegalStateException(
                    "Cannot transition institution " + institutionId + " to INACTIVE: "
                    + "current status is " + institution.getStatus()
                    + " (expected: active or pending)");
        }

        String previousStatus = institution.getStatus().name();
        institution.setStatus(InstitutionStatus.inactive);
        institutionRepository.save(institution);

        auditLogService.recordSystemAction(
                "INSTITUTION_INACTIVE",
                institutionId,
                Map.of("previousStatus", previousStatus)
        );

        log.info("Institution {} transitioned {} → INACTIVE", institutionId, previousStatus.toUpperCase());
    }

    /**
     * Permanently removes an institution.
     *
     * Blocked with 400 if the institution still has users, submissions, or
     * active media assets — the admin must clear those first. Invitation
     * tokens, slot reservations, and override requests are cleaned up
     * automatically since they are ephemeral administrative records that are
     * meaningless without the owning institution.
     */
    public void deleteInstitution(UUID institutionId) {
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new InstitutionNotFoundException(institutionId));

        if (hasBlockingContributorAccounts(institutionId)) {
            throw new IllegalArgumentException(
                    "\"" + institution.getName() + "\" still has contributors. Transfer or remove all contributors before deleting.");
        }

        if (submissionRepository.existsByInstitutionId(institutionId)) {
            throw new IllegalArgumentException(
                    "\"" + institution.getName() + "\" has existing submissions. Remove all submissions before deleting.");
        }

        if (mediaAssetRepository.existsActiveByInstitutionId(institutionId)) {
            throw new IllegalArgumentException(
                    "\"" + institution.getName() + "\" has media assets in its library. Delete all media assets before deleting the institution.");
        }

        // Stored objects for any lingering soft-deleted assets — the retention
        // purge job iterates DB rows, so once those rows are gone below the
        // objects would be orphaned in the media store. Purge them best-effort
        // after the delete transaction commits (no DB connection held during the
        // storage I/O).
        List<String> orphanStorageUrls = mediaAssetRepository.findStorageUrlsByInstitutionId(institutionId);

        // Clean up FK-constrained administrative records before the hard delete.
        invitationTokenRepository.deleteByInstitutionId(institutionId);
        slotReservationRepository.deleteByInstitutionId(institutionId);
        pageSettingsRepository.deleteByInstitutionId(institutionId);
        watermarkConfigurationRepository.deleteByInstitutionId(institutionId);
        // Assets first: soft-deleted rows still point at institution_id and
        // media_album_id, so they must go before the albums (and the
        // institution) can be removed.
        mediaAssetRepository.deleteByInstitutionId(institutionId);
        mediaAlbumRepository.deleteByInstitutionId(institutionId);

        String name = institution.getName();
        String code = institution.getCode();
        institutionRepository.delete(institution);

        auditLogService.recordSystemAction(
                "INSTITUTION_DELETED",
                institutionId,
                Map.of("name", name, "code", code)
        );

        log.info("Institution deleted: {} ({})", name, institutionId);

        purgeStorageObjectsAfterCommit(name, orphanStorageUrls);
    }

    /**
     * Best-effort removal of the stored objects whose {@code media_assets} rows
     * were just hard-deleted with the institution. Runs after the transaction
     * commits so no pooled DB connection is held during the storage calls;
     * failures are logged and never fail the delete (the rows are already gone
     * regardless). Storage host is abstracted behind the injected storage service.
     */
    private void purgeStorageObjectsAfterCommit(String institutionName, List<String> storageUrls) {
        if (storageUrls == null || storageUrls.isEmpty()) {
            return;
        }
        Runnable sweep = () -> {
            int purged = 0;
            for (String url : storageUrls) {
                if (url != null && !url.isBlank() && mediaStorage.deletePublicObject(url)) {
                    purged++;
                }
            }
            log.info("Purged {}/{} orphaned storage objects for deleted institution {}",
                    purged, storageUrls.size(), institutionName);
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sweep.run();
                }
            });
        } else {
            sweep.run();
        }
    }

    private boolean hasBlockingContributorAccounts(UUID institutionId) {
        return userRepository.existsByInstitutionIdAndRoleAndAccountStateIn(
                institutionId,
                UserRole.contributor,
                List.of(UserStatus.pending, UserStatus.pending_email_undelivered, UserStatus.active));
    }
}
