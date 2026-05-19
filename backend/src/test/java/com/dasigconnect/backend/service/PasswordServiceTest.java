package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.password.ForgotPasswordRequestDto;
import com.dasigconnect.backend.model.dto.password.ResetPasswordRequestDto;
import com.dasigconnect.backend.model.entity.PasswordResetToken;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.repository.PasswordResetTokenRepository;
import com.dasigconnect.backend.repository.UserRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EmailService emailService;
    @Mock AuditLogService auditLogService;
    @InjectMocks PasswordService passwordService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setRole(UserRole.contributor);
        user.setPasswordHash("$old_hash");
    }

    private PasswordResetToken buildResetToken(boolean used, boolean expired) {
        PasswordResetToken token = new PasswordResetToken();
        token.setId(UUID.randomUUID());
        token.setUser(user);
        token.setTokenHash("anyhash");
        token.setExpiresAt(expired ? Instant.now().minusSeconds(1) : Instant.now().plusSeconds(3600));
        if (used) token.setUsedAt(Instant.now().minusSeconds(60));
        return token;
    }

    // ── requestReset ──────────────────────────────────────────────────────

    @Test
    void requestReset_unknownEmail_returnsSilentlyWithoutSaving() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        passwordService.requestReset(new ForgotPasswordRequestDto("unknown@example.com"));
        verify(passwordResetTokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    void requestReset_knownEmail_savesTokenAndSendsEmail() {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        passwordService.requestReset(new ForgotPasswordRequestDto(user.getEmail()));

        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
        verify(emailService).sendPasswordResetEmail(eq(user.getEmail()), any());
    }

    @Test
    void requestReset_tokenExpiresInOneHour() {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        passwordService.requestReset(new ForgotPasswordRequestDto(user.getEmail()));

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(captor.capture());
        Instant expiresAt = captor.getValue().getExpiresAt();
        assertThat(expiresAt).isAfter(Instant.now().plusSeconds(3500));
        assertThat(expiresAt).isBefore(Instant.now().plusSeconds(3700));
    }

    // ── resetPassword ─────────────────────────────────────────────────────

    @Test
    void resetPassword_unknownToken_throws400() {
        when(passwordResetTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> passwordService.resetPassword(
                new ResetPasswordRequestDto("badtoken", "newpassword")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(400);
    }

    @Test
    void resetPassword_alreadyUsedToken_throws400() {
        when(passwordResetTokenRepository.findByTokenHash(any()))
                .thenReturn(Optional.of(buildResetToken(true, false)));
        assertThatThrownBy(() -> passwordService.resetPassword(
                new ResetPasswordRequestDto("usedtoken", "newpassword")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(400);
    }

    @Test
    void resetPassword_expiredToken_throws400() {
        when(passwordResetTokenRepository.findByTokenHash(any()))
                .thenReturn(Optional.of(buildResetToken(false, true)));
        assertThatThrownBy(() -> passwordService.resetPassword(
                new ResetPasswordRequestDto("expiredtoken", "newpassword")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(400);
    }

    @Test
    void resetPassword_validToken_updatesPasswordAndMarksUsed() {
        PasswordResetToken token = buildResetToken(false, false);
        when(passwordResetTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("newpassword")).thenReturn("$new_hash");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(passwordResetTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        passwordService.resetPassword(new ResetPasswordRequestDto("validtoken", "newpassword"));

        assertThat(user.getPasswordHash()).isEqualTo("$new_hash");
        assertThat(token.getUsedAt()).isNotNull();
        verify(auditLogService).record(eq(user), eq("PASSWORD_RESET"), any(), any(), any(), any());
    }
}
