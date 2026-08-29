package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.auth.LoginRequestDto;
import com.dasigconnect.backend.model.dto.auth.LoginResponseDto;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserStatus;
import com.dasigconnect.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountLockoutService accountLockoutService;
    private final JWTService jwtService;
    private final AuditLogService auditLogService;
    private final TenantScopeService tenantScopeService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AccountLockoutService accountLockoutService,
            JWTService jwtService,
            AuditLogService auditLogService,
            TenantScopeService tenantScopeService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accountLockoutService = accountLockoutService;
        this.jwtService = jwtService;
        this.auditLogService = auditLogService;
        this.tenantScopeService = tenantScopeService;
    }

    @Transactional
    public LoginResponseDto login(LoginRequestDto dto, HttpServletRequest request) {
        // Temporarily elevate scope to moderator to bypass RLS during authentication lookup
        tenantScopeService.bindTenantScope(null, null, "admin");

        String email = dto.email() != null ? dto.email().trim() : "";
        User user = userRepository.findByEmailIgnoreCase(email)
                .or(() -> userRepository.findByEmail(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (accountLockoutService.isLocked(user.getId())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account temporarily locked. Try again later.");
        }

        if (!passwordEncoder.matches(dto.password(), user.getPasswordHash())) {
            accountLockoutService.recordFailedAttempt(user);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        if (user.getAccountState() != UserStatus.active) {
            String message = switch (user.getAccountState()) {
                case pending, pending_email_undelivered -> "Account pending activation. Check your invitation email.";
                case expired -> "Account invitation expired. Contact an Moderator for reissue.";
                case inactive -> "Account has been deactivated. Contact an Moderator.";
                default -> "Account is not active";
            };
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }

        accountLockoutService.clearLockout(user);

        String token = jwtService.generateAccessToken(user);

        auditLogService.record(
                user,
                "LOGIN_SUCCESS",
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                user.getId(),
                Map.of());

        UUID institutionId = user.getInstitution() != null ? user.getInstitution().getId() : null;
        return new LoginResponseDto(token, user.getRole().name(), institutionId);
    }

    public void logout(String token) {
        if (jwtService.validateToken(token)) {
            jwtService.invalidateToken(token);
        }
    }

    @Transactional(readOnly = true)
    public LoginResponseDto refresh(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));
        if (user.getAccountState() != UserStatus.active) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is not active");
        }
        UUID institutionId = user.getInstitution() != null ? user.getInstitution().getId() : null;
        return new LoginResponseDto(jwtService.generateAccessToken(user), user.getRole().name(), institutionId);
    }
}
