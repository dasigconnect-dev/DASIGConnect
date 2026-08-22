package com.dasigconnect.backend.service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.model.dto.user.UserDto;
import com.dasigconnect.backend.model.entity.InstitutionStatus;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.model.entity.UserStatus;
import com.dasigconnect.backend.repository.AccountLockoutRepository;
import com.dasigconnect.backend.repository.EmailDeliveryLogRepository;
import com.dasigconnect.backend.repository.InstitutionRepository;
import com.dasigconnect.backend.repository.InvitationTokenRepository;
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

    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final EmailDeliveryLogRepository emailDeliveryLogRepository;
    private final AccountLockoutRepository accountLockoutRepository;
    private final ReviewLockRepository reviewLockRepository;
    private final SubmissionRepository submissionRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final ValidationLogRepository validationLogRepository;
    private final InstitutionRepository institutionRepository;
    private final InvitationTokenRepository invitationTokenRepository;
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
            InstitutionRepository institutionRepository,
            InvitationTokenRepository invitationTokenRepository,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.emailDeliveryLogRepository = emailDeliveryLogRepository;
        this.accountLockoutRepository = accountLockoutRepository;
        this.reviewLockRepository = reviewLockRepository;
        this.submissionRepository = submissionRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.validationLogRepository = validationLogRepository;
        this.institutionRepository = institutionRepository;
        this.invitationTokenRepository = invitationTokenRepository;
        this.auditLogService = auditLogService;
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

    /**
     * Lists all users for a given institution. - ADMINISTRATOR: may query any
     * institution - VALIDATOR: may only query their own institution -
     * CONTRIBUTOR: access denied
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
        validateCanManageUser(user.getInstitution() != null ? user.getInstitution().getId() : null, user.getRole(), requester);

        user.setAccountState(newStatus);
        return UserDto.from(userRepository.save(user));
    }

    @Transactional
    public UserDto updateAvatar(UUID id, MultipartFile file, JwtUserDetails requester) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a profile image to upload.");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile image must be 2 MB or smaller.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read the profile image.", ex);
        }

        String contentType = detectImageContentType(bytes);
        var target = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        boolean isAdministrator = "administrator".equalsIgnoreCase(requester.role());
        boolean isOwnProfile = id.equals(requester.userId());
        if (!isAdministrator && !isOwnProfile) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only administrators or the account owner can update this profile image.");
        }

        target.setAvatarData(bytes);
        target.setAvatarContentType(contentType);
        target.setAvatarUpdatedAt(Instant.now());
        return UserDto.from(userRepository.save(target));
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
     * Removes a user. Two outcomes based on whether the user has business data:
     *
     * - Has submissions, media assets, or validation history → auto-deactivate
     * (soft delete: account disabled, all related records preserved). - No
     * related records → permanent delete after cleaning up owned records.
     *
     * Returns "deactivated" or "deleted" so the caller can surface the right
     * message.
     */
    @Transactional
    public String removeUser(UUID id, JwtUserDetails requester) {
        var user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        validateCanManageUser(
                user.getInstitution() != null ? user.getInstitution().getId() : null,
                user.getRole(), requester);

        boolean hasData = submissionRepository.existsByContributorId(id)
                || mediaAssetRepository.existsByUploaderId(id)
                || validationLogRepository.existsByValidatorId(id);

        if (hasData) {
            // Preserve data integrity — just disable the account
            user.setAccountState(UserStatus.inactive);
            userRepository.save(user);
            return "deactivated";
        }

        // No related records — safe to permanently delete
        notificationRepository.deleteByRecipientId(id);
        emailDeliveryLogRepository.deleteByRecipientId(id);
        accountLockoutRepository.deleteByUserId(id);
        reviewLockRepository.deleteByLockedById(id);
        userRepository.delete(user);
        return "deleted";
    }

    /**
     * A4: Reassigns a contributor to a different institution.
     *
     * Rules: - Only ADMINISTRATOR may call this. - Target user must be a
     * CONTRIBUTOR (validators are managed via separate flows). - Target
     * institution must exist and be ACTIVE. - Cannot reassign to the user's
     * existing institution. - Historical submissions retain their original
     * institution_id for audit and RLS integrity.
     */
    @Transactional
    public UserDto reassignContributor(UUID userId, UUID targetInstitutionId, JwtUserDetails requester) {
        if (!"administrator".equalsIgnoreCase(requester.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only administrators can reassign contributors.");
        }

        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));

        if (user.getRole() != UserRole.contributor) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Only contributor accounts can be reassigned. Validators must be managed through institution settings.");
        }

        var targetInstitution = institutionRepository.findById(targetInstitutionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Target institution not found."));

        if (targetInstitution.getStatus() != InstitutionStatus.active) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Target institution is not active. Contributors can only be assigned to active institutions.");
        }

        UUID fromInstitutionId = user.getInstitution() != null ? user.getInstitution().getId() : null;
        String fromInstitutionName = user.getInstitution() != null ? user.getInstitution().getName() : "(none)";

        if (fromInstitutionId != null && fromInstitutionId.equals(targetInstitutionId)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
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
                "contributor.reassigned",
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
     * Returns counts of contributors and validators for an institution. Used
     * for dashboard summary tiles.
     */
    public Map<String, Long> countByRole(UUID institutionId, JwtUserDetails requester) {
        validateInstitutionScope(institutionId, requester);
        return Map.of(
                "contributors", userRepository.countByInstitutionIdAndRole(institutionId, UserRole.contributor),
                "validators", userRepository.countByInstitutionIdAndRole(institutionId, UserRole.validator)
        );
    }

    private void validateInstitutionScope(UUID institutionId, JwtUserDetails requester) {
        switch (requester.role().toLowerCase()) {
            case "administrator" -> {
                /* access allowed */ }
            case "validator" -> {
                if (institutionId == null || !institutionId.equals(requester.institutionId())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Validators can only access users in their own institution.");
                }
            }
            default ->
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Only administrators and validators can access users.");
        }
    }

    private void validateCanManageUser(UUID institutionId, UserRole targetRole, JwtUserDetails requester) {
        validateInstitutionScope(institutionId, requester);
        if ("validator".equalsIgnoreCase(requester.role()) && targetRole != UserRole.contributor) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Validators can only manage contributors");
        }
    }
}
