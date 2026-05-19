package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.auth.LoginRequestDto;
import com.dasigconnect.backend.model.dto.auth.LoginResponseDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.model.entity.UserStatus;
import com.dasigconnect.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AccountLockoutService accountLockoutService;
    @Mock JWTService jwtService;
    @Mock AuditLogService auditLogService;
    @InjectMocks AuthService authService;

    @Mock HttpServletRequest request;

    private User activeUser;
    private UUID userId;
    private static final String RAW_PASSWORD = "password123";
    private static final String HASHED_PASSWORD = "$2a$10$hashed";

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        activeUser = new User();
        activeUser.setId(userId);
        activeUser.setEmail("user@example.com");
        activeUser.setPasswordHash(HASHED_PASSWORD);
        activeUser.setRole(UserRole.contributor);
        activeUser.setAccountState(UserStatus.active);

        lenient().when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        lenient().when(request.getHeader("User-Agent")).thenReturn("Test");
    }

    @Test
    void login_unknownEmail_throws401() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.login(new LoginRequestDto("unknown@example.com", RAW_PASSWORD), request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(401);
    }

    @Test
    void login_lockedAccount_throws401() {
        when(userRepository.findByEmail(activeUser.getEmail())).thenReturn(Optional.of(activeUser));
        when(accountLockoutService.isLocked(userId)).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequestDto(activeUser.getEmail(), RAW_PASSWORD), request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(401);

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void login_wrongPassword_throws401AndRecordsFailedAttempt() {
        when(userRepository.findByEmail(activeUser.getEmail())).thenReturn(Optional.of(activeUser));
        when(accountLockoutService.isLocked(userId)).thenReturn(false);
        when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequestDto(activeUser.getEmail(), RAW_PASSWORD), request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(401);

        verify(accountLockoutService).recordFailedAttempt(activeUser);
    }

    @Test
    void login_inactiveAccount_throws403() {
        activeUser.setAccountState(UserStatus.inactive);
        when(userRepository.findByEmail(activeUser.getEmail())).thenReturn(Optional.of(activeUser));
        when(accountLockoutService.isLocked(userId)).thenReturn(false);
        when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequestDto(activeUser.getEmail(), RAW_PASSWORD), request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(403);
    }

    @Test
    void login_validCredentials_returnsTokenAndClearsLockout() {
        UUID institutionId = UUID.randomUUID();
        Institution inst = new Institution();
        inst.setId(institutionId);
        activeUser.setInstitution(inst);

        when(userRepository.findByEmail(activeUser.getEmail())).thenReturn(Optional.of(activeUser));
        when(accountLockoutService.isLocked(userId)).thenReturn(false);
        when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(true);
        when(jwtService.generateAccessToken(activeUser)).thenReturn("mocked.jwt.token");

        LoginResponseDto result = authService.login(new LoginRequestDto(activeUser.getEmail(), RAW_PASSWORD), request);

        assertThat(result.accessToken()).isEqualTo("mocked.jwt.token");
        assertThat(result.role()).isEqualTo("contributor");
        assertThat(result.institutionId()).isEqualTo(institutionId);
        verify(accountLockoutService).clearLockout(activeUser);
        verify(auditLogService).record(eq(activeUser), eq("LOGIN_SUCCESS"), any(), any(), any(), any());
    }

    @Test
    void login_administrator_returnsNullInstitutionId() {
        activeUser.setRole(UserRole.administrator);
        activeUser.setInstitution(null);

        when(userRepository.findByEmail(activeUser.getEmail())).thenReturn(Optional.of(activeUser));
        when(accountLockoutService.isLocked(userId)).thenReturn(false);
        when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(true);
        when(jwtService.generateAccessToken(activeUser)).thenReturn("admin.jwt.token");

        LoginResponseDto result = authService.login(new LoginRequestDto(activeUser.getEmail(), RAW_PASSWORD), request);

        assertThat(result.institutionId()).isNull();
        assertThat(result.role()).isEqualTo("administrator");
    }

    @Test
    void logout_delegatesToJwtService() {
        authService.logout("some.token");
        verify(jwtService).invalidateToken("some.token");
    }
}
