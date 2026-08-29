package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.user.UserDto;
import com.dasigconnect.backend.model.dto.user.AdminTransferResponseDto;
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
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

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
        adminPrincipal = principal(UUID.randomUUID(), "admin", null);
        org.springframework.test.util.ReflectionTestUtils.setField(userService, "maxAdmins", 3L);
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
    void listByInstitution_moderator_canListAnyInstitution() {
        UUID requestedInstitutionId = UUID.randomUUID();
        User moderator = user(UUID.randomUUID(), "moderator@cit.edu.ph", UserRole.moderator, institution);
        when(userRepository.findByInstitutionIdOrderByCreatedAtDesc(requestedInstitutionId))
                .thenReturn(List.of(moderator));

        List<UserDto> result = userService.listByInstitution(
                requestedInstitutionId,
                principal(UUID.randomUUID(), "admin", null));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo("moderator@cit.edu.ph");
    }

    @Test
    void listByInstitution_moderator_canListOwnInstitution() {
        when(userRepository.findByInstitutionIdOrderByCreatedAtDesc(institutionId))
                .thenReturn(List.of(contributor));

        List<UserDto> result = userService.listByInstitution(
                institutionId,
                principal(UUID.randomUUID(), "moderator", institutionId));

        assertThat(result).extracting(UserDto::getEmail).containsExactly("contributor@cit.edu.ph");
    }

    @Test
    void listByInstitution_moderatorCanListOtherInstitution() {
        // Moderators are network-wide now — no institution comparison applies.
        UUID otherInstitution = UUID.randomUUID();
        when(userRepository.findByInstitutionIdOrderByCreatedAtDesc(otherInstitution))
                .thenReturn(List.of(contributor));

        List<UserDto> result = userService.listByInstitution(
                otherInstitution,
                principal(UUID.randomUUID(), "moderator", null));

        assertThat(result).extracting(UserDto::getEmail).containsExactly("contributor@cit.edu.ph");
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
    void listAdmins_returnsNetworkAdminAccounts() {
        User superAdmin = user(UUID.randomUUID(), "super@dasigconnect.com", UserRole.admin, null);
        superAdmin.setAdminOwner(true);
        when(userRepository.findByRolesOrderByCreatedAtDesc(any())).thenReturn(List.of(superAdmin));

        List<UserDto> result = userService.listAdmins(
                principal(UUID.randomUUID(), "admin", null));

        assertThat(result).extracting(UserDto::getEmail)
                .containsExactly("super@dasigconnect.com");
    }

    @Test
    void listModerators_contributorIsForbidden() {
        assertThatThrownBy(() -> userService.listModerators(
                principal(userId, "contributor", institutionId)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void countByRole_returnsContributorAndmoderatorCounts() {
        when(userRepository.countByInstitutionIdAndRoleAndAccountState(institutionId, UserRole.contributor, UserStatus.active)).thenReturn(12L);
        when(userRepository.countByInstitutionIdAndRoleAndAccountState(institutionId, UserRole.moderator, UserStatus.active)).thenReturn(2L);

        Map<String, Long> result = userService.countByRole(
                institutionId,
                principal(UUID.randomUUID(), "moderator", institutionId));

        assertThat(result).containsEntry("contributors", 12L).containsEntry("moderators", 2L);
        verify(userRepository).countByInstitutionIdAndRoleAndAccountState(institutionId, UserRole.contributor, UserStatus.active);
        verify(userRepository).countByInstitutionIdAndRoleAndAccountState(institutionId, UserRole.moderator, UserStatus.active);
    }

    @Test
    void countByRole_moderatorCanCountOtherInstitution() {
        // Moderators are network-wide now — no institution comparison applies.
        UUID otherInstitution = UUID.randomUUID();
        when(userRepository.countByInstitutionIdAndRoleAndAccountState(otherInstitution, UserRole.contributor, UserStatus.active)).thenReturn(3L);
        when(userRepository.countByInstitutionIdAndRoleAndAccountState(otherInstitution, UserRole.moderator, UserStatus.active)).thenReturn(0L);

        Map<String, Long> result = userService.countByRole(
                otherInstitution,
                principal(UUID.randomUUID(), "moderator", null));

        assertThat(result).containsEntry("contributors", 3L);
    }

    @Test
    void getById_moderatorCanViewOwnInstitutionUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(contributor));

        UserDto result = userService.getById(userId, principal(UUID.randomUUID(), "moderator", institutionId));

        assertThat(result.getEmail()).isEqualTo("contributor@cit.edu.ph");
    }

    @Test
    void getById_moderatorCanViewOtherInstitutionUser() {
        // Moderators are network-wide now — no institution comparison applies.
        when(userRepository.findById(userId)).thenReturn(Optional.of(contributor));

        UserDto result = userService.getById(userId,
                principal(UUID.randomUUID(), "moderator", UUID.randomUUID()));

        assertThat(result.getEmail()).isEqualTo("contributor@cit.edu.ph");
    }

    @Test
    void updateStatus_moderatorCanDeactivateUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(contributor));
        when(userRepository.save(contributor)).thenReturn(contributor);

        UserDto result = userService.updateStatus(userId, UserStatus.inactive,
                principal(UUID.randomUUID(), "admin", null));

        assertThat(result.getAccountState()).isEqualTo("inactive");
        verify(userRepository).save(contributor);
    }

    @Test
    void updateStatus_rejectsPendingStatusChange() {
        assertThatThrownBy(() -> userService.updateStatus(userId, UserStatus.pending,
                principal(UUID.randomUUID(), "admin", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateStatus_standardModeratorCannotManageModerator() {
        User moderator = user(UUID.randomUUID(), "moderator@cit.edu.ph", UserRole.moderator, institution);
        when(userRepository.findById(moderator.getId())).thenReturn(Optional.of(moderator));

        assertThatThrownBy(() -> userService.updateStatus(moderator.getId(), UserStatus.inactive,
                principal(UUID.randomUUID(), "moderator", institutionId)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void removeUser_standardModeratorCannotRemoveModerator() {
        User targetAdmin = user(UUID.randomUUID(), "target@dasigconnect.com", UserRole.moderator, null);
        when(userRepository.findById(targetAdmin.getId())).thenReturn(Optional.of(targetAdmin));

        assertThatThrownBy(() -> userService.removeUser(targetAdmin.getId(),
                principal(UUID.randomUUID(), "moderator", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void removeUser_adminCanRemoveModerator() {
        User targetAdmin = user(UUID.randomUUID(), "target@dasigconnect.com", UserRole.moderator, null);
        targetAdmin.setAccountState(UserStatus.inactive);
        User superAdmin = user(UUID.randomUUID(), "super@dasigconnect.com", UserRole.admin, null);
        superAdmin.setAdminOwner(true);
        when(userRepository.findById(targetAdmin.getId())).thenReturn(Optional.of(targetAdmin));
        when(userRepository.findById(superAdmin.getId())).thenReturn(Optional.of(superAdmin));
        when(submissionRepository.existsByContributorId(targetAdmin.getId())).thenReturn(true);

        assertThat(userService.removeUser(targetAdmin.getId(),
                principal(superAdmin.getId(), "admin", null))).isEqualTo("deactivated");
    }

    @Test
    void updateAvatar_moderatorCanUploadValidPng() {
        byte[] png = new byte[]{
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00
        };
        MockMultipartFile file = new MockMultipartFile("file", "profile.png", "image/png", png);
        when(userRepository.findById(userId)).thenReturn(Optional.of(contributor));
        when(userRepository.save(contributor)).thenReturn(contributor);

        UserDto result = userService.updateAvatar(
                userId, file, principal(UUID.randomUUID(), "moderator", null));

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
                userId, file, principal(UUID.randomUUID(), "contributor", institutionId)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void updateStatus_adminCanDeactivateModeratorAndRevokesSessions() {
        User targetAdmin = user(UUID.randomUUID(), "target@dasigconnect.com", UserRole.moderator, null);
        User superAdmin = user(UUID.randomUUID(), "super@dasigconnect.com", UserRole.admin, null);
        superAdmin.setAdminOwner(true);
        when(userRepository.findById(targetAdmin.getId())).thenReturn(Optional.of(targetAdmin));
        when(userRepository.findById(superAdmin.getId())).thenReturn(Optional.of(superAdmin));
        when(userRepository.save(targetAdmin)).thenReturn(targetAdmin);

        UserDto result = userService.updateStatus(targetAdmin.getId(), UserStatus.inactive,
                principal(superAdmin.getId(), "admin", null));

        assertThat(result.getAccountState()).isEqualTo("inactive");
        verify(jwtService).invalidateUserTokens(targetAdmin.getId());
        verify(auditLogService).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateStatus_peerAdminCanDeactivateModerator() {
        User targetModerator = user(UUID.randomUUID(), "mod@dasigconnect.com", UserRole.moderator, null);
        User peerAdmin = user(UUID.randomUUID(), "peer@dasigconnect.com", UserRole.admin, null);
        when(userRepository.findById(targetModerator.getId())).thenReturn(Optional.of(targetModerator));
        when(userRepository.findById(peerAdmin.getId())).thenReturn(Optional.of(peerAdmin));
        when(userRepository.save(targetModerator)).thenReturn(targetModerator);

        UserDto result = userService.updateStatus(targetModerator.getId(), UserStatus.inactive,
                principal(peerAdmin.getId(), "admin", null));

        assertThat(result.getAccountState()).isEqualTo("inactive");
    }

    @Test
    void updateStatus_peerAdminCannotManageFellowAdmin() {
        User targetAdmin = user(UUID.randomUUID(), "other@dasigconnect.com", UserRole.admin, null);
        User peerAdmin = user(UUID.randomUUID(), "peer@dasigconnect.com", UserRole.admin, null);
        when(userRepository.findById(targetAdmin.getId())).thenReturn(Optional.of(targetAdmin));
        when(userRepository.findById(peerAdmin.getId())).thenReturn(Optional.of(peerAdmin));

        assertThatThrownBy(() -> userService.updateStatus(targetAdmin.getId(), UserStatus.inactive,
                principal(peerAdmin.getId(), "admin", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void requestAdminTransfer_setsPendingConfirmation() {
        User targetAdmin = user(UUID.randomUUID(), "target@dasigconnect.com", UserRole.moderator, null);
        User superAdmin = user(UUID.randomUUID(), "super@dasigconnect.com", UserRole.admin, null);
        superAdmin.setAdminOwner(true);
        when(userRepository.findById(superAdmin.getId())).thenReturn(Optional.of(superAdmin));
        when(userRepository.findById(targetAdmin.getId())).thenReturn(Optional.of(targetAdmin));
        when(userRepository.save(targetAdmin)).thenReturn(targetAdmin);

        AdminTransferResponseDto result = userService.requestAdminTransfer(
                targetAdmin.getId(),
                principal(superAdmin.getId(), "admin", null));

        assertThat(result.targetUserId()).isEqualTo(targetAdmin.getId());
        assertThat(result.requestedBy()).isEqualTo(superAdmin.getId());
        assertThat(result.status()).isEqualTo("pending_confirmation");
        assertThat(targetAdmin.getSuperAdminTransferRequestedBy()).isEqualTo(superAdmin.getId());
    }

    @Test
    void confirmAdminTransfer_promotesIncomingAndDemotesOutgoing() {
        User outgoing = user(UUID.randomUUID(), "super@dasigconnect.com", UserRole.admin, null);
        outgoing.setAdminOwner(true);
        User incoming = user(UUID.randomUUID(), "target@dasigconnect.com", UserRole.moderator, null);
        incoming.setSuperAdminTransferRequestedBy(outgoing.getId());
        incoming.setSuperAdminTransferExpiresAt(java.time.Instant.now().plusSeconds(3600));
        when(userRepository.findById(incoming.getId())).thenReturn(Optional.of(incoming));
        when(userRepository.findById(outgoing.getId())).thenReturn(Optional.of(outgoing));
        when(userRepository.save(incoming)).thenReturn(incoming);
        when(userRepository.save(outgoing)).thenReturn(outgoing);

        UserDto result = userService.confirmAdminTransfer(
                principal(incoming.getId(), "moderator", null));

        assertThat(result.isAdminOwner()).isTrue();
        assertThat(outgoing.isAdminOwner()).isFalse();
        assertThat(incoming.getSuperAdminTransferRequestedBy()).isNull();
        verify(jwtService).invalidateUserTokens(outgoing.getId());
        verify(auditLogService).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void confirmAdminTransfer_toExistingAdmin_keepsOutgoingAsAdmin() {
        User outgoing = user(UUID.randomUUID(), "owner@dasigconnect.com", UserRole.admin, null);
        outgoing.setAdminOwner(true);
        User incoming = user(UUID.randomUUID(), "peer@dasigconnect.com", UserRole.admin, null);
        incoming.setSuperAdminTransferRequestedBy(outgoing.getId());
        incoming.setSuperAdminTransferExpiresAt(java.time.Instant.now().plusSeconds(3600));
        when(userRepository.findById(incoming.getId())).thenReturn(Optional.of(incoming));
        when(userRepository.findById(outgoing.getId())).thenReturn(Optional.of(outgoing));
        when(userRepository.save(incoming)).thenReturn(incoming);
        when(userRepository.save(outgoing)).thenReturn(outgoing);

        UserDto result = userService.confirmAdminTransfer(
                principal(incoming.getId(), "admin", null));

        assertThat(result.isAdminOwner()).isTrue();
        assertThat(incoming.getRole()).isEqualTo(UserRole.admin);
        assertThat(outgoing.isAdminOwner()).isFalse();
        assertThat(outgoing.getRole()).isEqualTo(UserRole.admin);
    }

    // ── changeRole (promotion / demotion) ────────────────────────────────

    @Test
    void changeRole_promoteContributorToModerator_clearsInstitutionAndInvalidatesTokens() {
        User target = user(UUID.randomUUID(), "c@cit.edu.ph", UserRole.contributor, institution);
        User peerAdmin = user(UUID.randomUUID(), "peer@dasigconnect.com", UserRole.admin, null);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.findById(peerAdmin.getId())).thenReturn(Optional.of(peerAdmin));
        when(userRepository.save(target)).thenReturn(target);

        UserDto result = userService.changeRole(target.getId(), UserRole.moderator, null,
                principal(peerAdmin.getId(), "admin", null));

        assertThat(result.getRole()).isEqualTo("moderator");
        assertThat(target.getInstitution()).isNull();
        verify(jwtService).invalidateUserTokens(target.getId());
        verify(eventPublisher).publishEvent(
                org.mockito.ArgumentMatchers.any(com.dasigconnect.backend.event.UserRoleChangedEvent.class));
    }

    @Test
    void changeRole_demoteModeratorToContributor_withoutInstitution_returns400() {
        User target = user(UUID.randomUUID(), "m@dasigconnect.com", UserRole.moderator, null);
        User peerAdmin = user(UUID.randomUUID(), "peer@dasigconnect.com", UserRole.admin, null);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.findById(peerAdmin.getId())).thenReturn(Optional.of(peerAdmin));

        assertThatThrownBy(() -> userService.changeRole(target.getId(), UserRole.contributor, null,
                principal(peerAdmin.getId(), "admin", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void changeRole_demoteModeratorToContributor_setsInstitutionAndClearsReviewLocks() {
        User target = user(UUID.randomUUID(), "m@dasigconnect.com", UserRole.moderator, null);
        User peerAdmin = user(UUID.randomUUID(), "peer@dasigconnect.com", UserRole.admin, null);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.findById(peerAdmin.getId())).thenReturn(Optional.of(peerAdmin));
        when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(institution));
        when(userRepository.save(target)).thenReturn(target);

        UserDto result = userService.changeRole(target.getId(), UserRole.contributor, institutionId,
                principal(peerAdmin.getId(), "admin", null));

        assertThat(result.getRole()).isEqualTo("contributor");
        assertThat(target.getInstitution()).isEqualTo(institution);
        verify(reviewLockRepository).deleteByLockedById(target.getId());
        verify(jwtService).invalidateUserTokens(target.getId());
    }

    @Test
    void changeRole_peerAdminCannotPromoteToAdmin() {
        User target = user(UUID.randomUUID(), "m@dasigconnect.com", UserRole.moderator, null);
        User peerAdmin = user(UUID.randomUUID(), "peer@dasigconnect.com", UserRole.admin, null);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.findById(peerAdmin.getId())).thenReturn(Optional.of(peerAdmin));

        assertThatThrownBy(() -> userService.changeRole(target.getId(), UserRole.admin, null,
                principal(peerAdmin.getId(), "admin", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void changeRole_ownerPromoteToAdmin_blockedAtCap() {
        User target = user(UUID.randomUUID(), "m@dasigconnect.com", UserRole.moderator, null);
        User owner = user(UUID.randomUUID(), "owner@dasigconnect.com", UserRole.admin, null);
        owner.setAdminOwner(true);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(userRepository.countByRoleAndAccountState(UserRole.admin, UserStatus.active)).thenReturn(3L);

        assertThatThrownBy(() -> userService.changeRole(target.getId(), UserRole.admin, null,
                principal(owner.getId(), "admin", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void changeRole_ownerPromoteModeratorToAdmin_succeeds() {
        User target = user(UUID.randomUUID(), "m@dasigconnect.com", UserRole.moderator, null);
        User owner = user(UUID.randomUUID(), "owner@dasigconnect.com", UserRole.admin, null);
        owner.setAdminOwner(true);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(userRepository.countByRoleAndAccountState(UserRole.admin, UserStatus.active)).thenReturn(2L);
        when(userRepository.save(target)).thenReturn(target);

        UserDto result = userService.changeRole(target.getId(), UserRole.admin, null,
                principal(owner.getId(), "admin", null));

        assertThat(result.getRole()).isEqualTo("admin");
        assertThat(target.isAdminOwner()).isFalse();
        verify(jwtService).invalidateUserTokens(target.getId());
    }

    @Test
    void changeRole_cannotChangeOwnRole() {
        UUID sameId = UUID.randomUUID();
        assertThatThrownBy(() -> userService.changeRole(sameId, UserRole.moderator, null,
                principal(sameId, "admin", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void changeRole_cannotChangeTheAdminOwnersRole() {
        User targetOwner = user(UUID.randomUUID(), "owner@dasigconnect.com", UserRole.admin, null);
        targetOwner.setAdminOwner(true);
        User requester = user(UUID.randomUUID(), "req@dasigconnect.com", UserRole.admin, null);
        requester.setAdminOwner(true);
        when(userRepository.findById(targetOwner.getId())).thenReturn(Optional.of(targetOwner));
        when(userRepository.findById(requester.getId())).thenReturn(Optional.of(requester));

        assertThatThrownBy(() -> userService.changeRole(targetOwner.getId(), UserRole.moderator, null,
                principal(requester.getId(), "admin", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void changeRole_sameRole_returns400() {
        User target = user(UUID.randomUUID(), "c@cit.edu.ph", UserRole.contributor, institution);
        User peerAdmin = user(UUID.randomUUID(), "peer@dasigconnect.com", UserRole.admin, null);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.findById(peerAdmin.getId())).thenReturn(Optional.of(peerAdmin));

        assertThatThrownBy(() -> userService.changeRole(target.getId(), UserRole.contributor, null,
                principal(peerAdmin.getId(), "admin", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
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
                userId, targetInstId, principal(UUID.randomUUID(), "moderator", null));

        assertThat(contributor.getInstitution()).isEqualTo(targetInst);
        assertThat(result.getInstitutionId()).isEqualTo(targetInstId);
        verify(userRepository).save(contributor);
        verify(auditLogService).record(any(), eq("contributor.reassigned"), any(), any(), eq(userId), any());
    }

    @Test
    void reassignContributor_nonAdmin_throwsForbidden() {
        UUID targetInstId = UUID.randomUUID();

        assertThatThrownBy(() -> userService.reassignContributor(
                userId, targetInstId, principal(UUID.randomUUID(), "contributor", institutionId)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void reassignContributor_moderatorUser_throwsUnprocessableEntity() {
        User moderator = user(UUID.randomUUID(), "val@cit.edu.ph", UserRole.moderator, institution);
        when(userRepository.findById(moderator.getId())).thenReturn(Optional.of(moderator));

        assertThatThrownBy(() -> userService.reassignContributor(
                moderator.getId(), UUID.randomUUID(), principal(UUID.randomUUID(), "moderator", null)))
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
                userId, targetInstId, principal(UUID.randomUUID(), "moderator", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(422);
    }

    @Test
    void reassignContributor_sameInstitution_throwsUnprocessableEntity() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(contributor));
        when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(institution));

        assertThatThrownBy(() -> userService.reassignContributor(
                userId, institutionId, principal(UUID.randomUUID(), "moderator", null)))
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
