package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.password.ForgotPasswordRequestDto;
import com.dasigconnect.backend.model.dto.password.ResetPasswordRequestDto;
import com.dasigconnect.backend.model.dto.password.ChangePasswordRequestDto;
import com.dasigconnect.backend.model.entity.PasswordResetToken;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.PasswordResetTokenRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.TokenHashUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class PasswordService {

    private static final Logger log = LoggerFactory.getLogger(PasswordService.class);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditLogService auditLogService;

    public PasswordService(
            UserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.auditLogService = auditLogService;
    }

    public void requestReset(ForgotPasswordRequestDto dto) {
        Optional<User> userOpt = userRepository.findByEmail(dto.email());
        // Anti-enumeration: return silently if email not found
        if (userOpt.isEmpty()) {
            return;
        }

        User user = userOpt.get();
        String rawToken = TokenHashUtils.generateRawToken();
        String tokenHash = TokenHashUtils.sha256Hex(rawToken);

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setTokenHash(tokenHash);
        resetToken.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
        passwordResetTokenRepository.save(resetToken);

        // A mail-server failure must not roll back the saved token or turn the
        // response into a 5xx — that would both lose a usable link and leak
        // (via the status code) which addresses exist. The user can retry.
        try {
            emailService.sendPasswordResetEmail(user.getEmail(), rawToken);
        } catch (RuntimeException ex) {
            log.warn("Password reset email delivery failed for user {}: {}", user.getId(), ex.getMessage());
        }
    }

    public void resetPassword(ResetPasswordRequestDto dto) {
        String rawToken = dto.token() == null ? "" : dto.token().trim();
        if (rawToken.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset token is required");
        }
        String tokenHash = TokenHashUtils.sha256Hex(rawToken);
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token"));

        if (resetToken.getUsedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token has already been used");
        }
        if (resetToken.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token has expired");
        }

        User user = resetToken.getUser();
        PasswordPolicy.validate(dto.newPassword(), user.getEmail(), user.getFirstName(), user.getLastName());
        user.setPasswordHash(passwordEncoder.encode(dto.newPassword()));
        user.setSessionVersion(user.getSessionVersion() + 1);
        userRepository.save(user);

        resetToken.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(resetToken);

        auditLogService.record(user, "PASSWORD_RESET", null, null, user.getId(), Map.of());
    }

    public void changePassword(UUID userId, ChangePasswordRequestDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!passwordEncoder.matches(dto.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        if (passwordEncoder.matches(dto.newPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be different");
        }
        PasswordPolicy.validate(dto.newPassword(), user.getEmail(), user.getFirstName(), user.getLastName());
        user.setPasswordHash(passwordEncoder.encode(dto.newPassword()));
        userRepository.save(user);
        auditLogService.record(user, "PASSWORD_CHANGED", null, null, user.getId(), Map.of());
    }
}
