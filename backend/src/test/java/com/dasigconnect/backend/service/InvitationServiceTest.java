package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.auth.LoginResponseDto;
import com.dasigconnect.backend.model.dto.invitation.AcceptInvitationRequestDto;
import com.dasigconnect.backend.model.dto.invitation.CreateInvitationRequestDto;
import com.dasigconnect.backend.model.dto.invitation.InvitationResponseDto;
import com.dasigconnect.backend.model.dto.invitation.InvitationValidateResponseDto;
import com.dasigconnect.backend.model.dto.invitation.PendingInvitationDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.InvitationToken;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.model.entity.UserStatus;
import com.dasigconnect.backend.repository.InvitationTokenRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock InvitationTokenRepository invitationTokenRepository;
    @Mock UserRepository userRepository;
    @Mock EntityManager entityManager;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JWTService jwtService;
    @Mock EmailService emailService;
    @Mock AuditLogService auditLogService;
    @InjectMocks InvitationService invitationService;

    private Institution institution;
    private UUID institutionId;
    private JwtUserDetails adminPrincipal;
    private JwtUserDetails validatorPrincipal;

    @BeforeEach
    void setUp() {
        institutionId = UUID.randomUUID();
        institution = new Institution();
        institution.setId(institutionId);
        institution.setName("CIT-U");
        institution.setCode("citu");
        institution.setEmailDomain("example.com");
        adminPrincipal = new JwtUserDetails(UUID.randomUUID(), "admin@dasigconnect.com", "administrator", null);
        validatorPrincipal = new JwtUserDetails(UUID.randomUUID(), "validator@example.com", "validator", institutionId);
    }

    private InvitationToken buildToken(boolean used, boolean expired) {
        InvitationToken token = new InvitationToken();
        token.setId(UUID.randomUUID());
        token.setRecipientEmail("invitee@example.com");
        token.setAssignedRole(UserRole.contributor);
        token.setInstitution(institution);
        token.setTokenHash("anyhash");
        token.setExpiresAt(expired ? Instant.now().minusSeconds(1) : Instant.now().plusSeconds(3600));
        if (used) token.setUsedAt(Instant.now().minusSeconds(60));
        return token;
    }

    // ── createInvitation ──────────────────────────────────────────────────

    @Test
    void createInvitation_withAdministratorRole_throws400() {
        CreateInvitationRequestDto dto = new CreateInvitationRequestDto(
                "admin@example.com", institutionId, UserRole.administrator);
        assertThatThrownBy(() -> invitationService.createInvitation(dto))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(400);
    }

    @Test
    void createInvitation_withUnknownInstitution_throws404() {
        when(entityManager.find(Institution.class, institutionId)).thenReturn(null);
        CreateInvitationRequestDto dto = new CreateInvitationRequestDto(
                "user@example.com", institutionId, UserRole.contributor);
        assertThatThrownBy(() -> invitationService.createInvitation(dto))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(404);
    }

    @Test
    void createInvitation_validRequest_savesTokenAndSendsEmail() {
        when(entityManager.find(Institution.class, institutionId)).thenReturn(institution);
        when(invitationTokenRepository.save(any())).thenAnswer(inv -> {
            InvitationToken t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        when(emailService.buildInvitationLink(any())).thenReturn("http://localhost:5173/invite?token=token");

        CreateInvitationRequestDto dto = new CreateInvitationRequestDto(
                "user@example.com", institutionId, UserRole.contributor);
        InvitationResponseDto result = invitationService.createInvitation(dto);

        assertThat(result.recipientEmail()).isEqualTo("user@example.com");
        assertThat(result.assignedRole()).isEqualTo(UserRole.contributor);
        assertThat(result.institutionId()).isEqualTo(institutionId);
        assertThat(result.emailDelivered()).isTrue();
        assertThat(result.invitationUrl()).contains("/invite?token=");
        verify(userRepository).save(argThat(user ->
                user.getEmail().equals("user@example.com")
                        && user.getRole() == UserRole.contributor
                        && user.getAccountState().name().equals("pending")));
        verify(emailService).sendInvitationEmail(eq("user@example.com"), any());
    }

    @Test
    void createInvitation_adminWithDifferentEmailDomain_savesTokenAndSendsEmail() {
        when(entityManager.find(Institution.class, institutionId)).thenReturn(institution);
        when(invitationTokenRepository.save(any())).thenAnswer(inv -> {
            InvitationToken t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        when(emailService.buildInvitationLink(any())).thenReturn("http://localhost:5173/invite?token=token");
        CreateInvitationRequestDto dto = new CreateInvitationRequestDto(
                "user@gmail.com", institutionId, UserRole.validator);

        InvitationResponseDto result = invitationService.createInvitation(dto, adminPrincipal);

        assertThat(result.recipientEmail()).isEqualTo("user@gmail.com");
        assertThat(result.assignedRole()).isEqualTo(UserRole.validator);
        verify(emailService).sendInvitationEmail(eq("user@gmail.com"), any());
    }

    @Test
    void createInvitation_validatorWithWrongInstitutionDomain_throws400() {
        when(entityManager.find(Institution.class, institutionId)).thenReturn(institution);
        CreateInvitationRequestDto dto = new CreateInvitationRequestDto(
                "user@other.edu.ph", institutionId, UserRole.contributor);

        assertThatThrownBy(() -> invitationService.createInvitation(dto, validatorPrincipal))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(400);
    }

    // ── validateToken ─────────────────────────────────────────────────────

    @Test
    void validateToken_unknownToken_throws404() {
        when(invitationTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> invitationService.validateToken("badtoken"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(404);
    }

    @Test
    void validateToken_alreadyUsed_throws410() {
        InvitationToken token = buildToken(true, false);
        when(invitationTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        assertThatThrownBy(() -> invitationService.validateToken("sometoken"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(410);
    }

    @Test
    void validateToken_expired_throws410() {
        InvitationToken token = buildToken(false, true);
        when(invitationTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        assertThatThrownBy(() -> invitationService.validateToken("sometoken"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(410);
    }

    @Test
    void validateToken_valid_returnsDetails() {
        InvitationToken token = buildToken(false, false);
        when(invitationTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        InvitationValidateResponseDto result = invitationService.validateToken("validtoken");

        assertThat(result.recipientEmail()).isEqualTo("invitee@example.com");
        assertThat(result.institutionName()).isEqualTo("CIT-U");
        assertThat(result.assignedRole()).isEqualTo(UserRole.contributor);
    }

    // ── acceptInvitation ──────────────────────────────────────────────────

    @Test
    void acceptInvitation_alreadyUsedToken_throws409() {
        InvitationToken token = buildToken(true, false);
        when(invitationTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> invitationService.acceptInvitation(
                new AcceptInvitationRequestDto("validrawtoken", "password1")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(410);
    }

    @Test
    void acceptInvitation_valid_createsUserAndReturnsJwt() {
        InvitationToken token = buildToken(false, false);
        when(invitationTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("password1")).thenReturn("$hashed");
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(jwtService.generateAccessToken(any())).thenReturn("new.jwt.token");
        when(userRepository.findByEmail("invitee@example.com")).thenReturn(Optional.of(new User()));

        LoginResponseDto result = invitationService.acceptInvitation(
                new AcceptInvitationRequestDto("validrawtoken", "password1"));

        assertThat(result.accessToken()).isEqualTo("new.jwt.token");
        assertThat(result.role()).isEqualTo("contributor");
        assertThat(result.institutionId()).isEqualTo(institutionId);

        ArgumentCaptor<InvitationToken> tokenCaptor = ArgumentCaptor.forClass(InvitationToken.class);
        verify(invitationTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getUsedAt()).isNotNull();
    }

    @Test
    void resend_validInvitation_savesFreshTokenAndSendsEmail() {
        InvitationToken original = buildToken(false, false);
        User pendingUser = new User();
        pendingUser.setEmail(original.getRecipientEmail());
        pendingUser.setRole(UserRole.contributor);
        pendingUser.setInstitution(institution);
        pendingUser.setAccountState(UserStatus.pending_email_undelivered);

        when(invitationTokenRepository.findById(original.getId())).thenReturn(Optional.of(original));
        when(userRepository.findByEmail(original.getRecipientEmail())).thenReturn(Optional.of(pendingUser));
        when(invitationTokenRepository.save(any())).thenAnswer(inv -> {
            InvitationToken token = inv.getArgument(0);
            token.setId(UUID.randomUUID());
            return token;
        });
        when(emailService.buildInvitationLink(anyString())).thenReturn("http://localhost/invite?token=new");

        InvitationResponseDto result = invitationService.resend(
                original.getId(),
                principal("administrator", null));

        assertThat(result.recipientEmail()).isEqualTo(original.getRecipientEmail());
        assertThat(result.emailDelivered()).isTrue();
        verify(emailService).sendInvitationEmail(eq(original.getRecipientEmail()), anyString());
        verify(invitationTokenRepository).save(argThat(token ->
                token.getRecipientEmail().equals(original.getRecipientEmail())
                        && token.getAssignedRole() == original.getAssignedRole()
                        && token.getUsedAt() == null));
    }

    @Test
    void resend_usedInvitation_throws409() {
        InvitationToken original = buildToken(true, false);
        when(invitationTokenRepository.findById(original.getId())).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> invitationService.resend(original.getId(), principal("administrator", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(409);
    }

    @Test
    void listPending_validatorCanListOwnInstitution() {
        InvitationToken token = buildToken(false, false);
        when(invitationTokenRepository.findByInstitutionIdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                eq(institutionId), any())).thenReturn(List.of(token));

        List<PendingInvitationDto> result = invitationService.listPending(
                institutionId,
                principal("validator", institutionId));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).recipientEmail()).isEqualTo("invitee@example.com");
    }

    @Test
    void countPending_validatorCannotCountOtherInstitution() {
        assertThatThrownBy(() -> invitationService.countPending(UUID.randomUUID(), principal("validator", institutionId)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(403);
    }

    @Test
    void countPending_administratorReturnsCount() {
        when(invitationTokenRepository.countByInstitutionIdAndUsedAtIsNullAndExpiresAtAfter(eq(institutionId), any()))
                .thenReturn(4L);

        Map<String, Long> result = invitationService.countPending(institutionId, principal("administrator", null));

        assertThat(result).containsEntry("pendingInvitations", 4L);
    }

    private static JwtUserDetails principal(String role, UUID institutionId) {
        return new JwtUserDetails(UUID.randomUUID(), role + "@example.com", role, institutionId);
    }
}
