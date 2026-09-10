package com.dasigconnect.backend.service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.model.dto.user.AdminTransferResponseDto;
import com.dasigconnect.backend.model.dto.user.UserDto;
import com.dasigconnect.backend.model.dto.user.UpdateAccountSettingsRequestDto;
import com.dasigconnect.backend.model.entity.InstitutionStatus;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.model.entity.UserStatus;
import com.dasigconnect.backend.repository.AccountLockoutRepository;
import com.dasigconnect.backend.repository.AuditLogRepository;
import com.dasigconnect.backend.repository.EmailDeliveryLogRepository;
import com.dasigconnect.backend.repository.InstitutionRepository;
import com.dasigconnect.backend.repository.InvitationTokenRepository;
import com.dasigconnect.backend.repository.MediaAlbumRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.NotificationRepository;
import com.dasigconnect.backend.repository.ReviewLockRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.repository.ValidationLogRepository;
import com.dasigconnect.backend.security.JwtUserDetails;

@Service
@Transactional(readOnly = true)
public class UserService {

    private static final Duration SUPER_ADMIN_TRANSFER_TTL = Duration.ofHours(24);
    /** A proposed Administrator promotion lapses if the invitee does not confirm within this window. */
    private static final Duration ADMIN_PROMOTION_TTL = Duration.ofHours(72);
    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final EmailDeliveryLogRepository emailDeliveryLogRepository;
    private final AccountLockoutRepository accountLockoutRepository;
    private final ReviewLockRepository reviewLockRepository;
    private final SubmissionRepository submissionRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final MediaAlbumRepository mediaAlbumRepository;
    private final ValidationLogRepository validationLogRepository;
    private final AuditLogRepository auditLogRepository;
    private final JWTService jwtService;
    private final InstitutionRepository institutionRepository;
    private final InvitationTokenRepository invitationTokenRepository;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    /** Administrator headcount cap (`app.admins.max`, default 3) — see {@link AdminCapPolicy}. */
    private final AdminCapPolicy adminCapPolicy;

    public UserService(
            UserRepository userRepository,
            NotificationRepository notificationRepository,
            EmailDeliveryLogRepository emailDeliveryLogRepository,
            AccountLockoutRepository accountLockoutRepository,
            ReviewLockRepository reviewLockRepository,
            SubmissionRepository submissionRepository,
            MediaAssetRepository mediaAssetRepository,
            MediaAlbumRepository mediaAlbumRepository,
            ValidationLogRepository validationLogRepository,
            AuditLogRepository auditLogRepository,
            JWTService jwtService,
            InstitutionRepository institutionRepository,
            InvitationTokenRepository invitationTokenRepository,
            AuditLogService auditLogService,
            ApplicationEventPublisher eventPublisher,
            AdminCapPolicy adminCapPolicy) {
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.emailDeliveryLogRepository = emailDeliveryLogRepository;
        this.accountLockoutRepository = accountLockoutRepository;
        this.reviewLockRepository = reviewLockRepository;
        this.submissionRepository = submissionRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.mediaAlbumRepository = mediaAlbumRepository;
        this.validationLogRepository = validationLogRepository;
        this.auditLogRepository = auditLogRepository;
        this.jwtService = jwtService;
        this.institutionRepository = institutionRepository;
        this.invitationTokenRepository = invitationTokenRepository;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
        this.adminCapPolicy = adminCapPolicy;
    }

    /**
     * Returns the profile of the authenticated user. Used by GET /api/v1/me so
     * the frontend has reliable identity data.
     */
    public UserDto getProfile(JwtUserDetails principal) {
        return userRepository.findById(principal.userId())
                .map(UserDto::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    @Transactional
    public UserDto updateSettings(JwtUserDetails principal, UpdateAccountSettingsRequestDto request) {
        var user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        String displayName = request.displayName() == null ? null : request.displayName().trim();
        user.setDisplayName(displayName == null || displayName.isBlank() ? null : displayName);
        user.setNotifyInApp(request.notifyInApp());
        user.setNotifyEmail(request.notifyEmail());
        var saved = userRepository.save(user);
        auditLogService.record(
                saved,
                "USER_SETTINGS_UPDATED",
                null,
                null,
                saved.getId(),
                Map.of(
                        "notifyInApp", saved.isNotifyInApp(),
                        "notifyEmail", saved.isNotifyEmail()));
        return UserDto.from(saved);
    }

    /**
     * Both Moderator roles may list users in any institution.
     */
    public List<UserDto> listByInstitution(UUID institutionId, JwtUserDetails requester) {
        validateInstitutionScope(institutionId, requester);

        return userRepository.findByInstitutionIdOrderByCreatedAtDesc(institutionId)
                .stream()
                .map(u -> UserDto.from(u, requester))
                .toList();
    }

    /**
     * Lists network admin accounts. Admin accounts are network-scoped, not
     * institution-scoped, so they need a dedicated query.
     */
    public List<UserDto> listAdmins(JwtUserDetails requester) {
        if (!isAdminRole(requester)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only admins can access admin accounts.");
        }
        return userRepository.findByRolesOrderByCreatedAtDesc(
                        EnumSet.of(UserRole.admin))
                .stream()
                .map(UserDto::from)
                .toList();
    }

    public List<UserDto> listModerators(JwtUserDetails requester) {
        if (!isAdminRole(requester)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only admins can access moderator accounts.");
        }
        return userRepository.findByRole(UserRole.moderator)
                .stream()
                .map(UserDto::from)
                .toList();
    }

    /**
     * Network-wide roster of contributor and moderator accounts across every
     * institution. Admin-only — used by the User Management page so admins
     * don't have to open each institution individually.
     */
    public List<UserDto> listNetworkUsers(JwtUserDetails requester) {
        if (!isAdminRole(requester)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only admins can access the network-wide user directory.");
        }
        return userRepository.findByRolesWithInstitutionOrderByCreatedAtDesc(
                        EnumSet.of(UserRole.contributor, UserRole.moderator))
                .stream()
                .map(UserDto::from)
                .toList();
    }

    public UserDto getById(UUID id, JwtUserDetails requester) {
        var user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        validateInstitutionScope(user.getInstitution() != null ? user.getInstitution().getId() : null, requester);
        return UserDto.from(user);
    }

    @Transactional
    public UserDto updateStatus(UUID id, UserStatus newStatus, JwtUserDetails requester) {
        if (newStatus != UserStatus.active && newStatus != UserStatus.inactive) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User status can only be changed to active or inactive");
        }

        var user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        validateCanManageUser(user, requester);

        if (newStatus == UserStatus.inactive) {
            if (user.getAccountState() != UserStatus.active) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only active users can be deactivated.");
            }
        } else if (newStatus == UserStatus.active) {
            if (user.getAccountState() != UserStatus.inactive) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only inactive users can be reactivated.");
            }
            // Reactivating a deactivated admin re-adds an Administrator — it must
            // pass the same headcount cap as an invite or a promotion.
            if (user.getRole() == UserRole.admin) {
                adminCapPolicy.assertHasFreeSlot(null, null);
            }
        } else if (newStatus == UserStatus.cancelled) {
            if (user.getAccountState() != UserStatus.pending
                    && user.getAccountState() != UserStatus.pending_email_undelivered
                    && user.getAccountState() != UserStatus.expired) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only pending accounts can have invitations cancelled.");
            }
        }

        user.setAccountState(newStatus);
        User saved = userRepository.save(user);
        if (newStatus == UserStatus.inactive) {
            jwtService.invalidateUserTokens(saved.getId());
        }
        auditLogService.record(
                findRequesterForAudit(requester),
                "USER_STATUS_UPDATED",
                null, null,
                saved.getId(),
                java.util.Map.of(
                        "email", saved.getEmail(),
                        "role", saved.getRole().name(),
                        "accountState", saved.getAccountState().name()));
        return UserDto.from(saved);
    }

    @Transactional
    public UserDto updateAvatar(UUID id, MultipartFile file, JwtUserDetails requester) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid image file is required.");
        }
        if (file.getSize() > 2 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile image must be 2 MB or smaller.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (java.io.IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read profile image.");
        }

        String contentType = detectImageContentType(bytes);
        var user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        boolean isModerator = isModeratorRole(requester);
        boolean isOwnProfile = id.equals(requester.userId());
        if (!isModerator && !isOwnProfile) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only moderators or the account owner can update this profile image.");
        }

        user.setAvatarData(bytes);
        user.setAvatarContentType(contentType);
        user.setAvatarUpdatedAt(Instant.now());
        User saved = userRepository.save(user);

        auditLogService.record(
                findRequesterForAudit(requester),
                "USER_AVATAR_UPDATED",
                null, null,
                saved.getId(),
                java.util.Map.of("contentType", contentType, "sizeBytes", bytes.length));
        return UserDto.from(saved);
    }

    public UserAvatar getAvatar(UUID id) {
        var user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile image not found"));
        byte[] data = user.getAvatarData();
        if (data == null || data.length == 0 || user.getAvatarContentType() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile image not found");
        }
        return new UserAvatar(data, user.getAvatarContentType());
    }

    private String detectImageContentType(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47
                && bytes[4] == 0x0d && bytes[5] == 0x0a
                && bytes[6] == 0x1a && bytes[7] == 0x0a) {
            return "image/png";
        }
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Profile image must be a valid JPEG, PNG, or WebP image.");
    }

    public record UserAvatar(byte[] data, String contentType) {

    }

    /**
     * Removes a deactivated user.
     *
     * Only works on users that are currently in INACTIVE state. Active or pending
     * users cannot be removed without deactivating or cancelling first.
     */
    @Transactional
    public String removeUser(UUID id, JwtUserDetails requester) {
        var user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        validateCanRemoveUser(user, requester);

        if (user.getAccountState() != UserStatus.inactive
                && user.getAccountState() != UserStatus.cancelled
                && user.getAccountState() != UserStatus.expired) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only deactivated or cancelled users can be removed. Please deactivate or cancel the invitation first.");
        }

        // Any FK reference from a RESTRICT table blocks a hard user-row delete.
        // audit_log.actor_id in particular is set for anyone who has ever acted
        // (submitted, uploaded, created an album, accepted an invite), and
        // audit rows are append-only — so an erased account almost always lands
        // here and is retained as an anonymised inactive row.
        boolean hasData = submissionRepository.existsByContributorId(id)
                || mediaAssetRepository.existsByUploaderId(id)
                || validationLogRepository.existsByValidatorId(id)
                || mediaAlbumRepository.existsByCreatedBy(id)
                || auditLogRepository.existsByActorId(id);

        if (hasData) {
            // The account can't be row-deleted (RESTRICT FKs) and is already at
            // rest, so "remove" here is a no-op that just confirms it stays as an
            // inactive/anonymised row. Record it once — an already-erased account,
            // or one already marked USER_REMOVED, needs no further audit noise.
            boolean alreadyTerminal = user.getPurgedAt() != null
                    || auditLogRepository.existsByActionAndResourceId("USER_REMOVED", id);
            if (!alreadyTerminal) {
                jwtService.invalidateUserTokens(user.getId());
                auditLogService.record(
                        findRequesterForAudit(requester),
                        "USER_REMOVED",
                        null, null,
                        user.getId(),
                        java.util.Map.of("email", user.getEmail(), "role", user.getRole().name()));
            }
            return "deactivated";
        }

        // No related records — safe to permanently delete
        jwtService.invalidateUserTokens(user.getId());
        notificationRepository.deleteByRecipientId(id);
        emailDeliveryLogRepository.deleteByRecipientId(id);
        accountLockoutRepository.deleteByUserId(id);
        reviewLockRepository.deleteByLockedById(id);
        userRepository.delete(user);
        auditLogService.record(
                findRequesterForAudit(requester),
                "USER_DELETED",
                null, null,
                id,
                java.util.Map.of("email", user.getEmail(), "role", user.getRole().name()));
        return "deleted";
    }

    /** Summary returned after a personal-data erasure. */
    public record ErasureResult(String anonymizedEmail, int mediaAssetsPurged) {
    }

    /**
     * Erase an account's personal data ("right to be forgotten"). The users row
     * is anonymised in place — name, email, avatar, and credentials are
     * scrubbed — because every FK to users(id) is RESTRICT and audit_log is
     * append-only, so the row itself cannot be deleted once the account has
     * acted. Their unattached media uploads are soft-deleted (the retention job
     * purges the storage objects); submissions, validation logs, override
     * requests, and prior audit entries are kept but now reference a scrubbed
     * account. Admin Owner only.
     */
    @Transactional
    public ErasureResult erasePersonalData(UUID id, JwtUserDetails requester) {
        User actor = requireActiveAdminOwner(requester);

        User target = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (actor.getId().equals(target.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot erase your own account.");
        }
        if (target.isAdminOwner()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Transfer admin ownership before erasing this account.");
        }
        if (target.getPurgedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This account has already been erased.");
        }
        if (target.getAccountState() != UserStatus.inactive && target.getAccountState() != UserStatus.cancelled) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Deactivate or cancel the account before erasing it.");
        }

        String originalEmail = target.getEmail();
        jwtService.invalidateUserTokens(target.getId());

        int mediaPurged = mediaAssetRepository.softDeleteUnattachedAssetsByUploader(target.getId(), actor.getId());

        notificationRepository.deleteByRecipientId(target.getId());
        emailDeliveryLogRepository.deleteByRecipientId(target.getId());
        accountLockoutRepository.deleteByUserId(target.getId());
        reviewLockRepository.deleteByLockedById(target.getId());
        userRepository.deletePasswordResetTokensByUserId(target.getId());
        userRepository.deleteMessengerConnectionByUserId(target.getId());
        if (originalEmail != null) {
            userRepository.deleteInvitationTokensByRecipientEmail(originalEmail);
        }

        String tombstoneEmail = "deleted+" + target.getId() + "@deleted.invalid";
        target.setFirstName(null);
        target.setLastName(null);
        target.setDisplayName("Removed user");
        target.setEmail(tombstoneEmail);
        target.setPasswordHash("purged:" + UUID.randomUUID());
        target.setAvatarData(null);
        target.setAvatarContentType(null);
        target.setAvatarUpdatedAt(null);
        target.setNotifyInApp(false);
        target.setNotifyEmail(false);
        target.setAdminOwner(false);
        target.setSuperAdminTransferRequestedBy(null);
        target.setSuperAdminTransferExpiresAt(null);
        target.setAccountState(UserStatus.inactive);
        target.setPurgedAt(Instant.now());
        target.setPurgedByUserId(actor.getId());
        userRepository.save(target);

        auditLogService.record(
                actor,
                "USER_ANONYMIZED",
                null, null,
                target.getId(),
                Map.of(
                        "role", target.getRole().name(),
                        "mediaAssetsPurged", String.valueOf(mediaPurged)));

        // Right-to-be-forgotten also covers PII copied into prior audit metadata
        // (e.g. the "email" key written on LOGIN_FAILED / ACCOUNT_LOCKED rows).
        // The audit rows themselves are kept; only the personal fields are dropped.
        auditLogRepository.scrubPersonalMetadataForUser(target.getId());

        return new ErasureResult(tombstoneEmail, mediaPurged);
    }

    /**
     * Promote or demote an account between contributor, moderator, and admin.
     *
     * <ul>
     *   <li>Any active admin may move an account between contributor and
     *       moderator, and may propose promoting a contributor/moderator to
     *       admin — the target still has to confirm (see below), so this
     *       carries no more unilateral risk than sending an admin invitation.
     *       Only the Admin Owner may change an <em>existing</em> admin's role
     *       (demotion).</li>
     *   <li>Proposing a promotion does NOT apply the role change. It reserves
     *       an Administrator slot and records
     *       {@code adminPromotionRequestedBy}/{@code adminPromotionExpiresAt}
     *       on the target (72h TTL). The role only becomes {@code admin} once
     *       the target calls {@link #confirmAdminPromotion}; they may instead
     *       {@link #declineAdminPromotion}, or the proposer can
     *       {@link #cancelAdminPromotion} before either happens. Every demotion
     *       and every contributor/moderator move, by contrast, still applies
     *       immediately.</li>
     *   <li>{@code contributor} requires an active {@code institutionId}; the
     *       network-wide roles clear the institution.</li>
     *   <li>Promoting to admin is blocked when
     *       {@link AdminCapPolicy#assertHasFreeSlot} finds no free slot —
     *       confirmed admins + pending admin invites + pending promotions
     *       already at {@code app.admins.max}.</li>
     *   <li>The Admin Owner's own role cannot be changed here — transfer
     *       ownership first. Nobody can change their own role.</li>
     * </ul>
     *
     * For every transition applied immediately here (demotion, and
     * contributor/moderator moves), the account's tokens are invalidated (role
     * and institution live in the JWT), so the person must sign in again.
     * Historical submissions, media, and validation logs keep their original
     * institution and authorship.
     */
    @Transactional
    public UserDto changeRole(UUID userId, UserRole newRole, UUID institutionId, JwtUserDetails requester) {
        if (newRole == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target role is required.");
        }
        if (requester != null && requester.userId() != null && requester.userId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot change your own role.");
        }

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        UserRole fromRole = target.getRole();
        // Only demoting/otherwise changing an EXISTING admin's role is Owner-only.
        // Proposing a promotion to admin (fromRole != admin, newRole == admin) is
        // open to any active admin, same as inviting a new admin — the target
        // still has to confirm before anything takes effect, so this carries no
        // more unilateral risk than sending an invitation.
        boolean targetIsCurrentlyAdmin = fromRole == UserRole.admin;
        if (targetIsCurrentlyAdmin) {
            requireActiveAdminOwner(requester);
        } else {
            requireActiveAdmin(requester);
        }

        if (fromRole == UserRole.admin && target.isAdminOwner()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Transfer admin ownership before changing this account's role.");
        }
        if (newRole == fromRole) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This account already has that role.");
        }
        if (target.getAccountState() != UserStatus.active) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only active accounts can be reassigned. Reactivate or re-invite the account first.");
        }
        // Promotion to admin is NOT applied here — it is proposed. The target
        // keeps their current role until they confirm (UC-1.1: "the promoted
        // person must accept"). An Administrator slot is reserved immediately so
        // the cap can never be exceeded by concurrent invites/promotions.
        if (newRole == UserRole.admin) {
            if (userRepository.hasLivePendingAdminPromotion(userId, Instant.now())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This account already has an Administrator promotion awaiting confirmation.");
            }
            adminCapPolicy.assertHasFreeSlot(null, userId);

            Instant expiresAt = Instant.now().plus(ADMIN_PROMOTION_TTL);
            target.setAdminPromotionRequestedBy(requester != null ? requester.userId() : null);
            target.setAdminPromotionExpiresAt(expiresAt);
            User pending = userRepository.save(target);

            auditLogService.record(
                    findRequesterForAudit(requester),
                    "ADMIN_PROMOTION_REQUESTED",
                    null, null,
                    pending.getId(),
                    Map.of(
                            "email", pending.getEmail(),
                            "fromRole", fromRole.name(),
                            "expiresAt", expiresAt.toString()));

            eventPublisher.publishEvent(new com.dasigconnect.backend.event.AdminPromotionRequestedEvent(
                    pending, requester != null ? requester.email() : null, expiresAt));

            return UserDto.from(pending);
        }

        UUID fromInstitutionId = target.getInstitution() != null ? target.getInstitution().getId() : null;
        String fromInstitutionName = target.getInstitution() != null ? target.getInstitution().getName() : "(none)";
        String toInstitutionName = "(none)";

        if (newRole == UserRole.contributor) {
            if (institutionId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "An institution is required when demoting an account to contributor.");
            }
            var institution = institutionRepository.findById(institutionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target institution not found."));
            if (institution.getStatus() != InstitutionStatus.active) {
                throw new ResponseStatusException(org.springframework.http.HttpStatusCode.valueOf(422),
                        "Target institution is not active. Contributors can only be assigned to active institutions.");
            }
            target.setInstitution(institution);
            toInstitutionName = institution.getName();
        } else {
            // moderator and admin are network-wide.
            target.setInstitution(null);
        }

        if (newRole != UserRole.admin) {
            target.setAdminOwner(false);
        }
        if (fromRole == UserRole.moderator && newRole != UserRole.moderator) {
            // Drop any review claims the account holds as a moderator.
            reviewLockRepository.deleteByLockedById(userId);
        }

        target.setRole(newRole);
        User saved = userRepository.save(target);
        jwtService.invalidateUserTokens(saved.getId());

        auditLogService.record(
                findRequesterForAudit(requester),
                "USER_ROLE_CHANGED",
                null, null,
                saved.getId(),
                Map.of(
                        "email", saved.getEmail(),
                        "fromRole", fromRole.name(),
                        "toRole", newRole.name(),
                        "fromInstitution", fromInstitutionName,
                        "toInstitution", toInstitutionName));

        String actorEmail = requester != null ? requester.email() : null;
        eventPublisher.publishEvent(new com.dasigconnect.backend.event.UserRoleChangedEvent(
                saved, fromRole, newRole, actorEmail));

        return UserDto.from(saved);
    }

    /**
     * The invitee accepts a pending Administrator promotion (UC-1.1). Only the
     * account itself may confirm its own promotion. Re-checks the cap, since
     * slots may have filled up while the promotion sat pending; a lapsed
     * promotion (past {@code adminPromotionExpiresAt}) is cleared and reported
     * as expired rather than confirmed.
     */
    @Transactional
    public UserDto confirmAdminPromotion(JwtUserDetails requester) {
        User self = requireSelf(requester);

        if (self.getAdminPromotionRequestedBy() == null || self.getAdminPromotionExpiresAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No pending Administrator promotion exists for this account.");
        }
        if (self.getAdminPromotionExpiresAt().isBefore(Instant.now())) {
            clearAdminPromotion(self);
            userRepository.save(self);
            throw new ResponseStatusException(HttpStatus.GONE, "This Administrator promotion has expired.");
        }
        if (self.getAccountState() != UserStatus.active) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only an active account can confirm an Administrator promotion.");
        }

        // Exclude this account's own reserved slot — it is converting from
        // "pending promotion" to "confirmed admin", net zero.
        adminCapPolicy.assertHasFreeSlot(null, self.getId());

        UserRole fromRole = self.getRole();
        self.setRole(UserRole.admin);
        self.setInstitution(null);
        self.setAdminOwner(false);
        clearAdminPromotion(self);
        if (fromRole == UserRole.moderator) {
            reviewLockRepository.deleteByLockedById(self.getId());
        }

        User saved = userRepository.save(self);
        jwtService.invalidateUserTokens(saved.getId());

        auditLogService.record(
                saved,
                "ADMIN_PROMOTION_CONFIRMED",
                null, null,
                saved.getId(),
                Map.of("email", saved.getEmail(), "fromRole", fromRole.name()));

        eventPublisher.publishEvent(new com.dasigconnect.backend.event.UserRoleChangedEvent(
                saved, fromRole, UserRole.admin, saved.getEmail()));

        return UserDto.from(saved);
    }

    /**
     * The invitee declines a pending Administrator promotion. Releases the
     * reserved slot immediately and notifies whoever proposed it.
     */
    @Transactional
    public UserDto declineAdminPromotion(JwtUserDetails requester) {
        User self = requireSelf(requester);

        if (self.getAdminPromotionRequestedBy() == null || self.getAdminPromotionExpiresAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No pending Administrator promotion exists for this account.");
        }

        UUID requestedBy = self.getAdminPromotionRequestedBy();
        clearAdminPromotion(self);
        User saved = userRepository.save(self);

        auditLogService.record(
                saved,
                "ADMIN_PROMOTION_DECLINED",
                null, null,
                saved.getId(),
                Map.of("email", saved.getEmail()));

        eventPublisher.publishEvent(
                new com.dasigconnect.backend.event.AdminPromotionDeclinedEvent(saved, requestedBy));

        return UserDto.from(saved);
    }

    /**
     * The Admin Owner rescinds a pending Administrator promotion before the
     * invitee has responded, freeing the reserved slot right away.
     */
    @Transactional
    public UserDto cancelAdminPromotion(UUID userId, JwtUserDetails requester) {
        requireActiveAdminOwner(requester);

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (target.getAdminPromotionRequestedBy() == null || target.getAdminPromotionExpiresAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No pending Administrator promotion exists for this account.");
        }

        clearAdminPromotion(target);
        User saved = userRepository.save(target);

        auditLogService.record(
                findRequesterForAudit(requester),
                "ADMIN_PROMOTION_CANCELLED",
                null, null,
                saved.getId(),
                Map.of("email", saved.getEmail()));

        return UserDto.from(saved);
    }

    private void clearAdminPromotion(User user) {
        user.setAdminPromotionRequestedBy(null);
        user.setAdminPromotionExpiresAt(null);
    }

    private User requireSelf(JwtUserDetails requester) {
        if (requester == null || requester.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        }
        return userRepository.findById(requester.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    /**
     * A4: Reassigns a contributor to a different institution.
     *
     * Rules: - Either Moderator role may call this. - Target user must be a
     * CONTRIBUTOR (moderators are managed via separate flows). - Target
     * institution must exist and be ACTIVE. - Cannot reassign to the user's
     * existing institution. - Historical submissions retain their original
     * institution_id for audit and RLS integrity.
     */
    @Transactional
    public UserDto reassignContributor(UUID userId, UUID targetInstitutionId, JwtUserDetails requester) {
        if (!isModeratorRole(requester)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only moderators can reassign contributors.");
        }

        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));

        if (user.getRole() != UserRole.contributor) {
            throw new ResponseStatusException(org.springframework.http.HttpStatusCode.valueOf(422),
                    "Only contributor accounts can be reassigned. Moderators must be managed through institution settings.");
        }

        var targetInstitution = institutionRepository.findById(targetInstitutionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Target institution not found."));

        if (targetInstitution.getStatus() != InstitutionStatus.active) {
            throw new ResponseStatusException(org.springframework.http.HttpStatusCode.valueOf(422),
                    "Target institution is not active. Contributors can only be assigned to active institutions.");
        }

        UUID fromInstitutionId = user.getInstitution() != null ? user.getInstitution().getId() : null;
        String fromInstitutionName = user.getInstitution() != null ? user.getInstitution().getName() : "(none)";

        if (fromInstitutionId != null && fromInstitutionId.equals(targetInstitutionId)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatusCode.valueOf(422),
                    "Contributor is already assigned to this institution.");
        }

        // RLS scope update — the single source-of-truth for the contributor's workspace
        user.setInstitution(targetInstitution);
        UserDto saved = UserDto.from(userRepository.save(user));

        // Update any open, unexpired invitation tokens so accepting aligns with target institution
        if (invitationTokenRepository != null && user.getEmail() != null) {
            invitationTokenRepository
                    .findByRecipientEmailAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(user.getEmail(), Instant.now())
                    .forEach(token -> {
                        token.setInstitution(targetInstitution);
                        invitationTokenRepository.save(token);
                    });
        }

        // Audit the transfer with full context
        var actor = userRepository.findById(requester.userId()).orElse(null);
        auditLogService.record(
                actor,
                "CONTRIBUTOR_REASSIGNED",
                null, null,
                userId,
                Map.of(
                        "fromInstitutionId", fromInstitutionId != null ? fromInstitutionId.toString() : "",
                        "fromInstitutionName", fromInstitutionName,
                        "toInstitutionId", targetInstitutionId.toString(),
                        "toInstitutionName", targetInstitution.getName()
                ));

        return saved;
    }

    /**
     * Returns counts of contributors and moderators for an institution. Used
     * for dashboard summary tiles.
     */
    public Map<String, Long> countByRole(UUID institutionId, JwtUserDetails requester) {
        validateInstitutionScope(institutionId, requester);
        return Map.of(
                "contributors", userRepository.countByInstitutionIdAndRoleAndAccountState(institutionId, UserRole.contributor, UserStatus.active),
                "moderators", userRepository.countByInstitutionIdAndRoleAndAccountState(institutionId, UserRole.moderator, UserStatus.active)
        );
    }

    @Transactional
    public AdminTransferResponseDto requestAdminTransfer(
            UUID targetUserId,
            JwtUserDetails requester) {
        User requesterAccount = requireActiveAdminOwner(requester);
        if (requesterAccount.getId().equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Admin cannot transfer status to the same account");
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if ((target.getRole() != UserRole.moderator && target.getRole() != UserRole.admin)
                || target.getAccountState() != UserStatus.active) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Admin ownership can only be transferred to an active moderator or admin");
        }
        if (target.isAdminOwner()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Target account is already the Admin");
        }

        Instant expiresAt = Instant.now().plus(SUPER_ADMIN_TRANSFER_TTL);
        target.setSuperAdminTransferRequestedBy(requesterAccount.getId());
        target.setSuperAdminTransferExpiresAt(expiresAt);
        userRepository.save(target);

        auditLogService.record(
                requesterAccount,
                "ADMIN_TRANSFER_REQUESTED",
                null, null,
                target.getId(),
                java.util.Map.of("targetEmail", target.getEmail(), "expiresAt", expiresAt.toString()));

        return new AdminTransferResponseDto(
                target.getId(),
                requesterAccount.getId(),
                expiresAt,
                "pending_confirmation");
    }

    @Transactional
    public UserDto confirmAdminTransfer(JwtUserDetails requester) {
        User incoming = userRepository.findById(requester.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if ((incoming.getRole() != UserRole.moderator && incoming.getRole() != UserRole.admin)
                || incoming.getAccountState() != UserStatus.active) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only an active moderator or admin can confirm an Admin ownership transfer");
        }
        boolean incomingWasModerator = incoming.getRole() == UserRole.moderator;
        if (incoming.getSuperAdminTransferRequestedBy() == null
                || incoming.getSuperAdminTransferExpiresAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No pending Admin transfer exists for this account");
        }
        if (incoming.getSuperAdminTransferExpiresAt().isBefore(Instant.now())) {
            incoming.setSuperAdminTransferRequestedBy(null);
            incoming.setSuperAdminTransferExpiresAt(null);
            userRepository.save(incoming);
            throw new ResponseStatusException(HttpStatus.GONE,
                    "Pending Admin transfer has expired");
        }

        User outgoing = userRepository.findById(incoming.getSuperAdminTransferRequestedBy())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                "Requesting Admin account no longer exists"));
        if (outgoing.getRole() != UserRole.admin
                || outgoing.getAccountState() != UserStatus.active
                || !outgoing.isAdminOwner()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Requesting account is no longer the active Admin");
        }

        incoming.setRole(UserRole.admin);
        incoming.setAdminOwner(true);
        incoming.setSuperAdminTransferRequestedBy(null);
        incoming.setSuperAdminTransferExpiresAt(null);
        outgoing.setAdminOwner(false);
        if (incomingWasModerator) {
            // Owner-to-moderator handoff is 1-for-1: the outgoing owner steps
            // down to moderator so the admin headcount stays the same. Handing
            // ownership to an existing admin leaves both as admins.
            outgoing.setRole(UserRole.moderator);
        }
        outgoing.setSuperAdminTransferRequestedBy(null);
        outgoing.setSuperAdminTransferExpiresAt(null);

        userRepository.save(outgoing);
        User savedIncoming = userRepository.save(incoming);
        jwtService.invalidateUserTokens(outgoing.getId());

        auditLogService.record(
                incoming,
                "ADMIN_TRANSFER_CONFIRMED",
                null, null,
                incoming.getId(),
                java.util.Map.of("previousAdminOwnerId", outgoing.getId().toString()));

        return UserDto.from(savedIncoming);
    }

    private void validateInstitutionScope(UUID institutionId, JwtUserDetails requester) {
        if (isModeratorRole(requester)) {
            // Moderator and Admin are both network-wide roles — no institution comparison needed.
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Only admins or moderators can access users.");
    }

    private void validateCanManageUser(User target, JwtUserDetails requester) {
        UUID institutionId = target.getInstitution() != null ? target.getInstitution().getId() : null;
        validateInstitutionScope(institutionId, requester);
        if (target.getRole() == UserRole.admin) {
            // Admin-on-admin actions stay restricted to the Admin Owner.
            User requesterAccount = requireActiveAdminOwner(requester);
            if (requesterAccount.getId().equals(target.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Admins cannot manage their own account status");
            }
        } else if (target.getRole() == UserRole.moderator) {
            // Any active admin (Owner or peer) may manage moderator accounts.
            requireActiveAdmin(requester);
        }
    }

    private void validateCanRemoveUser(User target, JwtUserDetails requester) {
        validateCanManageUser(target, requester);
        if (target.getRole() == UserRole.admin) {
            User requesterAccount = requireActiveAdminOwner(requester);
            if (requesterAccount.getId().equals(target.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Admins cannot remove their own account");
            }
            return;
        }
        // A non-admin (moderator) may remove only a contributor they invited
        // whose invitation was cancelled/expired — never an activated account.
        if (!"admin".equalsIgnoreCase(requester != null ? requester.role() : null)) {
            boolean ownCancelledInvitee = target.getRole() == UserRole.contributor
                    && (target.getAccountState() == UserStatus.cancelled
                            || target.getAccountState() == UserStatus.expired)
                    && target.getInvitedByUserId() != null
                    && requester != null
                    && target.getInvitedByUserId().equals(requester.userId());
            if (!ownCancelledInvitee) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "You can only remove a contributor you invited whose invitation was cancelled.");
            }
        }
    }

    /**
     * Requires the caller to be any active admin (Owner or peer). Used for
     * actions a peer admin is allowed to perform, such as managing moderator
     * accounts.
     */
    private User requireActiveAdmin(JwtUserDetails requester) {
        if (requester == null || requester.userId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only an admin can manage this account");
        }
        User requesterAccount = userRepository.findById(requester.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Only an admin can manage this account"));
        if (requesterAccount.getRole() != UserRole.admin
                || requesterAccount.getAccountState() != UserStatus.active) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only an admin can manage this account");
        }
        return requesterAccount;
    }

    private User requireActiveAdminOwner(JwtUserDetails requester) {
        if (requester == null || requester.userId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the Admin can manage Moderator accounts");
        }
        User requesterAccount = userRepository.findById(requester.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Only the Admin can manage Moderator accounts"));
        if (requesterAccount.getRole() != UserRole.admin
                || requesterAccount.getAccountState() != UserStatus.active
                || !requesterAccount.isAdminOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the Admin can manage Moderator accounts");
        }
        return requesterAccount;
    }

    private boolean isModeratorRole(JwtUserDetails requester) {
        return requester != null && ("moderator".equalsIgnoreCase(requester.role())
                || "admin".equalsIgnoreCase(requester.role()));
    }

    private boolean isAdminRole(JwtUserDetails requester) {
        return requester != null && "admin".equalsIgnoreCase(requester.role());
    }

    private User findRequesterForAudit(JwtUserDetails requester) {
        if (requester == null || requester.userId() == null) {
            return null;
        }
        return userRepository.findById(requester.userId()).orElse(null);
    }
}
