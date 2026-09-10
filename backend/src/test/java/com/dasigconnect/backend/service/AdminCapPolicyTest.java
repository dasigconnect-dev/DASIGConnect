package com.dasigconnect.backend.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.model.entity.InvitationToken;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.model.entity.UserStatus;
import com.dasigconnect.backend.repository.InvitationTokenRepository;
import com.dasigconnect.backend.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * UC-1.1 — "confirmed admins + pending admin invites + pending promotions &lt;
 * app.admins.max", the invariant every admin-cap gate (invite, accept,
 * promote, reactivate) delegates to.
 */
@ExtendWith(MockitoExtension.class)
class AdminCapPolicyTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private InvitationTokenRepository invitationTokenRepository;

    @InjectMocks
    private AdminCapPolicy adminCapPolicy;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adminCapPolicy, "maxAdmins", 3L);
        // lenient: several tests override these with their own args/values.
        org.mockito.Mockito.lenient()
                .when(invitationTokenRepository.findPendingNetworkRoleInvitations(eq(UserRole.admin), any()))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient()
                .when(userRepository.countLivePendingAdminPromotions(any())).thenReturn(0L);
    }

    private InvitationToken pendingInvite(String email) {
        InvitationToken token = new InvitationToken();
        token.setRecipientEmail(email);
        token.setAssignedRole(UserRole.admin);
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        return token;
    }

    @Test
    void slotsInUse_sumsActiveAdminsPendingInvitesAndPendingPromotions() {
        when(userRepository.countByRoleAndAccountState(UserRole.admin, UserStatus.active)).thenReturn(1L);
        when(invitationTokenRepository.findPendingNetworkRoleInvitations(eq(UserRole.admin), any()))
                .thenReturn(List.of(pendingInvite("a@x.com")));
        when(userRepository.countLivePendingAdminPromotions(any())).thenReturn(1L);

        assertThat(adminCapPolicy.slotsInUse(null, null)).isEqualTo(3L);
    }

    @Test
    void slotsInUse_excludesTheInviteBeingAccepted() {
        when(userRepository.countByRoleAndAccountState(UserRole.admin, UserStatus.active)).thenReturn(2L);
        when(invitationTokenRepository.findPendingNetworkRoleInvitations(eq(UserRole.admin), any()))
                .thenReturn(List.of(pendingInvite("accepting@x.com")));

        // The invite converting to an active admin right now must not double-count.
        assertThat(adminCapPolicy.slotsInUse("accepting@x.com", null)).isEqualTo(2L);
    }

    @Test
    void slotsInUse_deduplicatesInvitesToTheSameEmail() {
        when(userRepository.countByRoleAndAccountState(UserRole.admin, UserStatus.active)).thenReturn(0L);
        when(invitationTokenRepository.findPendingNetworkRoleInvitations(eq(UserRole.admin), any()))
                .thenReturn(List.of(pendingInvite("dup@x.com"), pendingInvite("dup@x.com")));

        assertThat(adminCapPolicy.slotsInUse(null, null)).isEqualTo(1L);
    }

    @Test
    void slotsInUse_excludesTheTargetsOwnLivePendingPromotion() {
        UUID targetId = UUID.randomUUID();
        when(userRepository.countByRoleAndAccountState(UserRole.admin, UserStatus.active)).thenReturn(1L);
        when(userRepository.countLivePendingAdminPromotions(any())).thenReturn(2L);
        when(userRepository.hasLivePendingAdminPromotion(eq(targetId), any())).thenReturn(true);

        // 1 active admin + 2 pending promotions - 1 (the one converting now) = 2
        assertThat(adminCapPolicy.slotsInUse(null, targetId)).isEqualTo(2L);
    }

    @Test
    void assertHasFreeSlot_atCap_throws409WithUniformMessage() {
        when(userRepository.countByRoleAndAccountState(UserRole.admin, UserStatus.active)).thenReturn(3L);

        assertThatThrownBy(() -> adminCapPolicy.assertHasFreeSlot(null, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void assertHasFreeSlot_underCap_doesNotThrow() {
        when(userRepository.countByRoleAndAccountState(UserRole.admin, UserStatus.active)).thenReturn(2L);

        adminCapPolicy.assertHasFreeSlot(null, null);
    }
}
