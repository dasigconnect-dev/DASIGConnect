package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.user.UserDto;
import com.dasigconnect.backend.model.dto.user.SuperAdministratorTransferResponseDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.InstitutionStatus;
import com.dasigconnect.backend.model.entity.User;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private JWTService jwtService;
    @Mock
    private AuditLogService auditLogService;

    @Mock
    private InstitutionRepository institutionRepository;

    @Mock
    private InvitationTokenRepository invitationTokenRepository;

    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private MediaAssetRepository mediaAssetRepository;
    @Mock
    private ValidationLogRepository validationLogRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private EmailDeliveryLogRepository emailDeliveryLogRepository;
    @Mock
    private AccountLockoutRepository accountLockoutRepository;
    @Mock
    private ReviewLockRepository reviewLockRepository;

    @InjectMocks
    private UserService userService;

    private UUID userId;
    private UUID institutionId;
    private Institution institution;
    private User contributor;
    private JwtUserDetails adminPrincipal;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        institutionId = UUID.randomUUID();

        institution = new Institution();
        institution.setId(institutionId);
        institution.setName("CIT-U");
        institution.setCode("CIT-U");
        institution.setEmailDomain("cit.edu.ph");
        institution.setStatus(InstitutionStatus.active);

        contributor = user(userId, "contributor@cit.edu.ph", UserRole.contributor, institution);
        adminPrincipal = principal(UUID.randomUUID(), "administrator", null);
    }

    @Test
    void getProfile_existingUser_returnsUserDtoWithoutPasswordHash() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(contributor));

        UserDto result = userService.getProfile(principal(userId, "contributor", institutionId));

        assertThat(result.getId()).isEqualTo(userId);
        assertThat(result.getEmail()).isEqualTo("contributor@cit.edu.ph");
        assertThat(result.getFirstName()).isEqualTo("Test");
        assertThat(result.getLastName()).isEqualTo("User");
        assertThat(result.getDisplayName()).isEqualTo("Test User");
        assertThat(result.getRole()).isEqualTo("contributor");
        assertThat(result.getInstitutionId()).isEqualTo(institutionId);
        assertThat(result.getInstitutionName()).isEqualTo("CIT-U");
    }

    @Test
    void getProfile_missingUser_throws404() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(principal(userId, "contributor", institutionId)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listByInstitution_administrator_canListAnyInstitution() {
        UUID requestedInstitutionId = UUID.randomUUID();
        User validator = user(UUID.randomUUID(), "validator@cit.edu.ph", UserRole.validator, institution);
        when(userRepository.findByInstitutionIdOrderByCreatedAtDesc(requestedInstitutionId))
                .thenReturn(List.of(validator));

        List<UserDto> result = userService.listByInstitution(
                requestedInstitutionId,
                principal(UUID.randomUUID(), "administrator", null));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo("validator@cit.edu.ph");
    }

    @Test
    void listByInstitution_validator_canListOwnInstitution() {
        when(userRepository.findByInstitutionIdOrderByCreatedAtDesc(institutionId))
                .thenReturn(List.of(contributor));

        List<UserDto> result = userService.listByInstitution(
                institutionId,
                principal(UUID.randomUUID(), "validator", institutionId));

        assertThat(result).extracting(UserDto::getEmail).containsExactly("contributor@cit.edu.ph");
    }

    @Test
    void listByInstitution_validatorCannotListOtherInstitution() {
        assertThatThrownBy(() -> userService.listByInstitution(
                UUID.randomUUID(),
                principal(UUID.randomUUID(), "validator", institutionId)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void listByInstitution_contributorIsForbidden() {
        assertThatThrownBy(() -> userService.listByInstitution(
                institutionId,
                principal(userId, "contributor", institutionId)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void countByRole_returnsContributorAndValidatorCounts() {
        when(userRepository.countByInstitutionIdAndRole(institutionId, UserRole.contributor)).thenReturn(12L);
        when(userRepository.countByInstitutionIdAndRole(institutionId, UserRole.validator)).thenReturn(2L);

        Map<String, Long> result = userService.countByRole(
                institutionId,
                principal(UUID.randomUUID(), "validator", institutionId));

        assertThat(result).containsEntry("contributors", 12L).containsEntry("validators", 2L);
        verify(userRepository).countByInstitutionIdAndRole(institutionId, UserRole.contributor);
        verify(userRepository).countByInstitutionIdAndRole(institutionId, UserRole.validator);
    }

    @Test
    void countByRole_validatorCannotCountOtherInstitution() {
        assertThatThrownBy(() -> userService.countByRole(
                UUID.randomUUID(),
                principal(UUID.randomUUID(), "validator", institutionId)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getById_validatorCanViewOwnInstitutionUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(contributor));

        UserDto result = userService.getById(userId, principal(UUID.randomUUID(), "validator", institutionId));

        assertThat(result.getEmail()).isEqualTo("contributor@cit.edu.ph");
    }

    @Test
    void getById_validatorCannotViewOtherInstitutionUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(contributor));

        assertThatThrownBy(() -> userService.getById(userId, principal(UUID.randomUUID(), "validator", UUID.randomUUID())))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void updateStatus_administratorCanDeactivateUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(contributor));
        when(userRepository.save(contributor)).thenReturn(contributor);

        UserDto result = userService.updateStatus(userId, UserStatus.inactive,
                principal(UUID.randomUUID(), "administrator", null));

        assertThat(result.getAccountState()).isEqualTo("inactive");
        verify(userRepository).save(contributor);
    }

    @Test
    void updateStatus_rejectsPendingStatusChange() {
        assertThatThrownBy(() -> userService.updateStatus(userId, UserStatus.pending,
                principal(UUID.randomUUID(), "administrator", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateStatus_validatorCannotManageValidator() {
        User validator = user(UUID.randomUUID(), "validator@cit.edu.ph", UserRole.validator, institution);
        when(userRepository.findById(validator.getId())).thenReturn(Optional.of(validator));

        assertThatThrownBy(() -> userService.updateStatus(validator.getId(), UserStatus.inactive,
                principal(UUID.randomUUID(), "validator", institutionId)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void updateStatus_standardAdministratorCannotDeactivateAdministrator() {
        User targetAdmin = user(UUID.randomUUID(), "target@dasigconnect.com", UserRole.administrator, null);
        User requesterAdmin = user(UUID.randomUUID(), "requester@dasigconnect.com", UserRole.administrator, null);
        when(userRepository.findById(targetAdmin.getId())).thenReturn(Optional.of(targetAdmin));
        when(userRepository.findById(requesterAdmin.getId())).thenReturn(Optional.of(requesterAdmin));

        assertThatThrownBy(() -> userService.updateStatus(targetAdmin.getId(), UserStatus.inactive,
                principal(requesterAdmin.getId(), "administrator", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void updateAvatar_administratorCanUploadValidPng() {
        byte[] png = new byte[]{
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00
        };
        MockMultipartFile file = new MockMultipartFile("file", "profile.png", "image/png", png);
        when(userRepository.findById(userId)).thenReturn(Optional.of(contributor));
        when(userRepository.save(contributor)).thenReturn(contributor);

        UserDto result = userService.updateAvatar(
                userId, file, principal(UUID.randomUUID(), "administrator", null));

        assertThat(result.isHasAvatar()).isTrue();
        assertThat(contributor.getAvatarData()).isEqualTo(png);
        assertThat(contributor.getAvatarContentType()).isEqualTo("image/png");
        assertThat(contributor.getAvatarUpdatedAt()).isNotNull();
        verify(userRepository).save(contributor);
    }

    @Test
    void updateAvatar_nonOwnerIsForbidden() {
        byte[] png = new byte[]{
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00
        };
        MockMultipartFile file = new MockMultipartFile("file", "profile.png", "image/png", png);
        when(userRepository.findById(userId)).thenReturn(Optional.of(contributor));

        assertThatThrownBy(() -> userService.updateAvatar(
                userId, file, principal(UUID.randomUUID(), "validator", institutionId)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void updateStatus_superAdministratorCanDeactivateAdministratorAndRevokesSessions() {
        User targetAdmin = user(UUID.randomUUID(), "target@dasigconnect.com", UserRole.administrator, null);
        User superAdmin = user(UUID.randomUUID(), "super@dasigconnect.com", UserRole.administrator, null);
        superAdmin.setSuperAdministrator(true);
        when(userRepository.findById(targetAdmin.getId())).thenReturn(Optional.of(targetAdmin));
        when(userRepository.findById(superAdmin.getId())).thenReturn(Optional.of(superAdmin));
        when(userRepository.save(targetAdmin)).thenReturn(targetAdmin);

        UserDto result = userService.updateStatus(targetAdmin.getId(), UserStatus.inactive,
                principal(superAdmin.getId(), "administrator", null));

        assertThat(result.getAccountState()).isEqualTo("inactive");
        verify(jwtService).invalidateUserTokens(targetAdmin.getId());
        verify(auditLogService).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void requestSuperAdministratorTransfer_setsPendingConfirmation() {
        User targetAdmin = user(UUID.randomUUID(), "target@dasigconnect.com", UserRole.administrator, null);
        User superAdmin = user(UUID.randomUUID(), "super@dasigconnect.com", UserRole.administrator, null);
        superAdmin.setSuperAdministrator(true);
        when(userRepository.findById(superAdmin.getId())).thenReturn(Optional.of(superAdmin));
        when(userRepository.findById(targetAdmin.getId())).thenReturn(Optional.of(targetAdmin));
        when(userRepository.save(targetAdmin)).thenReturn(targetAdmin);

        SuperAdministratorTransferResponseDto result = userService.requestSuperAdministratorTransfer(
                targetAdmin.getId(),
                principal(superAdmin.getId(), "administrator", null));

        assertThat(result.targetUserId()).isEqualTo(targetAdmin.getId());
        assertThat(result.requestedBy()).isEqualTo(superAdmin.getId());
        assertThat(result.status()).isEqualTo("pending_confirmation");
        assertThat(targetAdmin.getSuperAdminTransferRequestedBy()).isEqualTo(superAdmin.getId());
    }

    @Test
    void confirmSuperAdministratorTransfer_promotesIncomingAndDemotesOutgoing() {
        User outgoing = user(UUID.randomUUID(), "super@dasigconnect.com", UserRole.administrator, null);
        outgoing.setSuperAdministrator(true);
        User incoming = user(UUID.randomUUID(), "target@dasigconnect.com", UserRole.administrator, null);
        incoming.setSuperAdminTransferRequestedBy(outgoing.getId());
        incoming.setSuperAdminTransferExpiresAt(java.time.Instant.now().plusSeconds(3600));
        when(userRepository.findById(incoming.getId())).thenReturn(Optional.of(incoming));
        when(userRepository.findById(outgoing.getId())).thenReturn(Optional.of(outgoing));
        when(userRepository.save(incoming)).thenReturn(incoming);
        when(userRepository.save(outgoing)).thenReturn(outgoing);

        UserDto result = userService.confirmSuperAdministratorTransfer(
                principal(incoming.getId(), "administrator", null));

        assertThat(result.isSuperAdministrator()).isTrue();
        assertThat(outgoing.isSuperAdministrator()).isFalse();
        assertThat(incoming.getSuperAdminTransferRequestedBy()).isNull();
        verify(jwtService).invalidateUserTokens(outgoing.getId());
        verify(auditLogService).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void reassignContributor_success_transfersInstitutionAndSaves() {
        UUID targetInstId = UUID.randomUUID();
        Institution targetInst = new Institution();
        targetInst.setId(targetInstId);
        targetInst.setName("Silliman University");
        targetInst.setCode("SU");
        targetInst.setStatus(InstitutionStatus.active);

        when(userRepository.findById(userId)).thenReturn(Optional.of(contributor));
        when(institutionRepository.findById(targetInstId)).thenReturn(Optional.of(targetInst));
        when(userRepository.save(contributor)).thenReturn(contributor);
        when(invitationTokenRepository.findByRecipientEmailAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                eq(contributor.getEmail()), any())).thenReturn(Collections.emptyList());

        UserDto result = userService.reassignContributor(
                userId, targetInstId, principal(UUID.randomUUID(), "administrator", null));

        assertThat(contributor.getInstitution()).isEqualTo(targetInst);
        assertThat(result.getInstitutionId()).isEqualTo(targetInstId);
        verify(userRepository).save(contributor);
        verify(auditLogService).record(any(), eq("contributor.reassigned"), any(), any(), eq(userId), any());
    }

    @Test
    void reassignContributor_nonAdmin_throwsForbidden() {
        UUID targetInstId = UUID.randomUUID();

        assertThatThrownBy(() -> userService.reassignContributor(
                userId, targetInstId, principal(UUID.randomUUID(), "validator", institutionId)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void reassignContributor_validatorUser_throwsUnprocessableEntity() {
        User validator = user(UUID.randomUUID(), "val@cit.edu.ph", UserRole.validator, institution);
        when(userRepository.findById(validator.getId())).thenReturn(Optional.of(validator));

        assertThatThrownBy(() -> userService.reassignContributor(
                validator.getId(), UUID.randomUUID(), principal(UUID.randomUUID(), "administrator", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(422);
    }

    @Test
    void reassignContributor_targetInactive_throwsUnprocessableEntity() {
        UUID targetInstId = UUID.randomUUID();
        Institution targetInst = new Institution();
        targetInst.setId(targetInstId);
        targetInst.setStatus(InstitutionStatus.inactive);

        when(userRepository.findById(userId)).thenReturn(Optional.of(contributor));
        when(institutionRepository.findById(targetInstId)).thenReturn(Optional.of(targetInst));

        assertThatThrownBy(() -> userService.reassignContributor(
                userId, targetInstId, principal(UUID.randomUUID(), "administrator", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(422);
    }

    @Test
    void reassignContributor_sameInstitution_throwsUnprocessableEntity() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(contributor));
        when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(institution));

        assertThatThrownBy(() -> userService.reassignContributor(
                userId, institutionId, principal(UUID.randomUUID(), "administrator", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(422);
    }

    @Test
    void removeUser_activeUser_throws409Conflict() {
        contributor.setAccountState(UserStatus.active);
        when(userRepository.findById(userId)).thenReturn(Optional.of(contributor));

        assertThatThrownBy(() -> userService.removeUser(userId, adminPrincipal))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(409);
    }

    @Test
    void removeUser_inactiveUserWithoutData_deletesSuccessfully() {
        contributor.setAccountState(UserStatus.inactive);
        when(userRepository.findById(userId)).thenReturn(Optional.of(contributor));
        when(submissionRepository.existsByContributorId(userId)).thenReturn(false);
        when(mediaAssetRepository.existsByUploaderId(userId)).thenReturn(false);
        when(validationLogRepository.existsByValidatorId(userId)).thenReturn(false);

        String result = userService.removeUser(userId, adminPrincipal);

        assertThat(result).isEqualTo("deleted");
        verify(userRepository).delete(contributor);
    }

    @Test
    void removeUser_cancelledUserWithoutData_deletesSuccessfully() {
        contributor.setAccountState(UserStatus.cancelled);
        when(userRepository.findById(userId)).thenReturn(Optional.of(contributor));
        when(submissionRepository.existsByContributorId(userId)).thenReturn(false);
        when(mediaAssetRepository.existsByUploaderId(userId)).thenReturn(false);
        when(validationLogRepository.existsByValidatorId(userId)).thenReturn(false);

        String result = userService.removeUser(userId, adminPrincipal);

        assertThat(result).isEqualTo("deleted");
        verify(userRepository).delete(contributor);
    }

    @Test
    void updateStatus_deactivateNonActiveUser_throws400() {
        contributor.setAccountState(UserStatus.inactive);
        when(userRepository.findById(userId)).thenReturn(Optional.of(contributor));

        assertThatThrownBy(() -> userService.updateStatus(userId, UserStatus.inactive, adminPrincipal))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(400);
    }

    private static JwtUserDetails principal(UUID id, String role, UUID institutionId) {
        return new JwtUserDetails(id, role + "@example.com", role, institutionId);
    }

    private static User user(UUID id, String email, UserRole role, Institution institution) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setRole(role);
        user.setAccountState(UserStatus.active);
        user.setInstitution(institution);
        user.setPasswordHash("hash-not-exposed");
        return user;
    }
}
