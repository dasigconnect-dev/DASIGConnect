package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.user.UserDto;
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
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final EmailDeliveryLogRepository emailDeliveryLogRepository;
    private final AccountLockoutRepository accountLockoutRepository;
    private final ReviewLockRepository reviewLockRepository;
    private final SubmissionRepository submissionRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final ValidationLogRepository validationLogRepository;

    public UserService(
            UserRepository userRepository,
            NotificationRepository notificationRepository,
            EmailDeliveryLogRepository emailDeliveryLogRepository,
            AccountLockoutRepository accountLockoutRepository,
            ReviewLockRepository reviewLockRepository,
            SubmissionRepository submissionRepository,
            MediaAssetRepository mediaAssetRepository,
            ValidationLogRepository validationLogRepository) {
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.emailDeliveryLogRepository = emailDeliveryLogRepository;
        this.accountLockoutRepository = accountLockoutRepository;
        this.reviewLockRepository = reviewLockRepository;
        this.submissionRepository = submissionRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.validationLogRepository = validationLogRepository;
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
        validateCanManageUser(user.getInstitution() != null ? user.getInstitution().getId() : null, user.getRole(), requester);

        user.setAccountState(newStatus);
        return UserDto.from(userRepository.save(user));
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

    private void validateCanManageUser(UUID institutionId, UserRole targetRole, JwtUserDetails requester) {
        validateInstitutionScope(institutionId, requester);
        if ("validator".equalsIgnoreCase(requester.role()) && targetRole != UserRole.contributor) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Validators can only manage contributors");
        }
    }
}
