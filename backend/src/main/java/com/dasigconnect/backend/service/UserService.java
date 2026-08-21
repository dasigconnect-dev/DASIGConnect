package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.user.SuperAdministratorTransferResponseDto;
import com.dasigconnect.backend.model.dto.user.UserDto;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.model.entity.UserStatus;
import com.dasigconnect.backend.repository.AccountLockoutRepository;
import com.dasigconnect.backend.repository.EmailDeliveryLogRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.NotificationRepository;
import com.dasigconnect.backend.repository.ReviewLockRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.repository.ValidationLogRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class UserService {

    private static final Duration SUPER_ADMIN_TRANSFER_TTL = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final EmailDeliveryLogRepository emailDeliveryLogRepository;
    private final AccountLockoutRepository accountLockoutRepository;
    private final ReviewLockRepository reviewLockRepository;
    private final SubmissionRepository submissionRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final ValidationLogRepository validationLogRepository;
    private final JWTService jwtService;
    private final AuditLogService auditLogService;

    public UserService(
            UserRepository userRepository,
            NotificationRepository notificationRepository,
            EmailDeliveryLogRepository emailDeliveryLogRepository,
            AccountLockoutRepository accountLockoutRepository,
            ReviewLockRepository reviewLockRepository,
            SubmissionRepository submissionRepository,
            MediaAssetRepository mediaAssetRepository,
            ValidationLogRepository validationLogRepository,
            JWTService jwtService,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.emailDeliveryLogRepository = emailDeliveryLogRepository;
        this.accountLockoutRepository = accountLockoutRepository;
        this.reviewLockRepository = reviewLockRepository;
        this.submissionRepository = submissionRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.validationLogRepository = validationLogRepository;
        this.jwtService = jwtService;
        this.auditLogService = auditLogService;
    }

    /**
     * Returns the profile of the authenticated user.
     * Used by GET /api/v1/me so the frontend has reliable identity data.
     */
    public UserDto getProfile(JwtUserDetails principal) {
        return userRepository.findById(principal.userId())
                .map(UserDto::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    /**
     * Lists all users for a given institution.
     * - ADMINISTRATOR: may query any institution
     * - VALIDATOR: may only query their own institution
     * - CONTRIBUTOR: access denied
     */
    public List<UserDto> listByInstitution(UUID institutionId, JwtUserDetails requester) {
        validateInstitutionScope(institutionId, requester);

        return userRepository.findByInstitutionIdOrderByCreatedAtDesc(institutionId)
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

    /**
     * Removes a user. Two outcomes based on whether the user has business data:
     *
     * - Has submissions, media assets, or validation history → auto-deactivate
     *   (soft delete: account disabled, all related records preserved).
     * - No related records → permanent delete after cleaning up owned records.
     *
     * Returns "deactivated" or "deleted" so the caller can surface the right message.
     */
    @Transactional
    public String removeUser(UUID id, JwtUserDetails requester) {
        var user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        validateCanManageUser(user, requester);

        boolean hasData = submissionRepository.existsByContributorId(id)
                || mediaAssetRepository.existsByUploaderId(id)
                || validationLogRepository.existsByValidatorId(id);

        if (hasData) {
            // Preserve data integrity — just disable the account
            user.setAccountState(UserStatus.inactive);
            userRepository.save(user);
            jwtService.invalidateUserTokens(user.getId());
            auditLogService.record(
                    findRequesterForAudit(requester),
                    "USER_DEACTIVATED",
                    null, null,
                    user.getId(),
                    java.util.Map.of("email", user.getEmail(), "role", user.getRole().name()));
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

    /**
     * Returns counts of contributors and validators for an institution.
     * Used for dashboard summary tiles.
     */
    public java.util.Map<String, Long> countByRole(UUID institutionId, JwtUserDetails requester) {
        validateInstitutionScope(institutionId, requester);
        return java.util.Map.of(
                "contributors", userRepository.countByInstitutionIdAndRole(institutionId, UserRole.contributor),
                "validators", userRepository.countByInstitutionIdAndRole(institutionId, UserRole.validator)
        );
    }

    @Transactional
    public SuperAdministratorTransferResponseDto requestSuperAdministratorTransfer(
            UUID targetUserId,
            JwtUserDetails requester) {
        User requesterAccount = requireActiveSuperAdministrator(requester);
        if (requesterAccount.getId().equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Super Administrator cannot transfer status to the same account");
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (target.getRole() != UserRole.administrator || target.getAccountState() != UserStatus.active) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Super Administrator status can only be transferred to an active Administrator");
        }
        if (target.isSuperAdministrator()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Target account is already the Super Administrator");
        }

        Instant expiresAt = Instant.now().plus(SUPER_ADMIN_TRANSFER_TTL);
        target.setSuperAdminTransferRequestedBy(requesterAccount.getId());
        target.setSuperAdminTransferExpiresAt(expiresAt);
        userRepository.save(target);

        auditLogService.record(
                requesterAccount,
                "SUPER_ADMINISTRATOR_TRANSFER_REQUESTED",
                null, null,
                target.getId(),
                java.util.Map.of("targetEmail", target.getEmail(), "expiresAt", expiresAt.toString()));

        return new SuperAdministratorTransferResponseDto(
                target.getId(),
                requesterAccount.getId(),
                expiresAt,
                "pending_confirmation");
    }

    @Transactional
    public UserDto confirmSuperAdministratorTransfer(JwtUserDetails requester) {
        User incoming = userRepository.findById(requester.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (incoming.getRole() != UserRole.administrator || incoming.getAccountState() != UserStatus.active) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only an active Administrator can confirm Super Administrator transfer");
        }
        if (incoming.getSuperAdminTransferRequestedBy() == null
                || incoming.getSuperAdminTransferExpiresAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No pending Super Administrator transfer exists for this account");
        }
        if (incoming.getSuperAdminTransferExpiresAt().isBefore(Instant.now())) {
            incoming.setSuperAdminTransferRequestedBy(null);
            incoming.setSuperAdminTransferExpiresAt(null);
            userRepository.save(incoming);
            throw new ResponseStatusException(HttpStatus.GONE,
                    "Pending Super Administrator transfer has expired");
        }

        User outgoing = userRepository.findById(incoming.getSuperAdminTransferRequestedBy())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Requesting Super Administrator account no longer exists"));
        if (outgoing.getRole() != UserRole.administrator
                || outgoing.getAccountState() != UserStatus.active
                || !outgoing.isSuperAdministrator()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Requesting account is no longer the active Super Administrator");
        }

        incoming.setSuperAdministrator(true);
        incoming.setSuperAdminTransferRequestedBy(null);
        incoming.setSuperAdminTransferExpiresAt(null);
        outgoing.setSuperAdministrator(false);
        outgoing.setSuperAdminTransferRequestedBy(null);
        outgoing.setSuperAdminTransferExpiresAt(null);

        userRepository.save(outgoing);
        User savedIncoming = userRepository.save(incoming);
        jwtService.invalidateUserTokens(outgoing.getId());

        auditLogService.record(
                incoming,
                "SUPER_ADMINISTRATOR_TRANSFER_CONFIRMED",
                null, null,
                incoming.getId(),
                java.util.Map.of("previousSuperAdministratorId", outgoing.getId().toString()));

        return UserDto.from(savedIncoming);
    }

    private void validateInstitutionScope(UUID institutionId, JwtUserDetails requester) {
        switch (requester.role().toLowerCase()) {
            case "administrator" -> { /* access allowed */ }
            case "validator" -> {
                if (institutionId == null || !institutionId.equals(requester.institutionId())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Validators can only access users in their own institution.");
                }
            }
            default -> throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only administrators and validators can access users.");
        }
    }

    private void validateCanManageUser(User target, JwtUserDetails requester) {
        UUID institutionId = target.getInstitution() != null ? target.getInstitution().getId() : null;
        UserRole targetRole = target.getRole();
        validateInstitutionScope(institutionId, requester);
        if ("validator".equalsIgnoreCase(requester.role()) && targetRole != UserRole.contributor) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Validators can only manage contributors");
        }
        if (targetRole == UserRole.administrator) {
            User requesterAccount = requireActiveSuperAdministrator(requester);
            if (requesterAccount.getId().equals(target.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Super Administrator cannot remove or deactivate their own account");
            }
        }
    }

    private User requireActiveSuperAdministrator(JwtUserDetails requester) {
        if (requester == null || requester.userId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the Super Administrator can manage Administrator accounts");
        }
        User requesterAccount = userRepository.findById(requester.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Only the Super Administrator can manage Administrator accounts"));
        if (requesterAccount.getRole() != UserRole.administrator
                || requesterAccount.getAccountState() != UserStatus.active
                || !requesterAccount.isSuperAdministrator()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the Super Administrator can manage Administrator accounts");
        }
        return requesterAccount;
    }

    private User findRequesterForAudit(JwtUserDetails requester) {
        if (requester == null || requester.userId() == null) {
            return null;
        }
        return userRepository.findById(requester.userId()).orElse(null);
    }
}
