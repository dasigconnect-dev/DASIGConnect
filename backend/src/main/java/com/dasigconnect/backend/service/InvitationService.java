package com.dasigconnect.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
import com.dasigconnect.backend.security.TokenHashUtils;

import jakarta.persistence.EntityManager;

@Service
@Transactional
public class InvitationService {

    private static final Logger log = LoggerFactory.getLogger(InvitationService.class);

    private final InvitationTokenRepository invitationTokenRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;
    private final PasswordEncoder passwordEncoder;
    private final JWTService jwtService;
    private final EmailService emailService;
    private final AuditLogService auditLogService;

    public InvitationService(
            InvitationTokenRepository invitationTokenRepository,
            UserRepository userRepository,
            EntityManager entityManager,
            PasswordEncoder passwordEncoder,
            JWTService jwtService,
            EmailService emailService,
            AuditLogService auditLogService) {
        this.invitationTokenRepository = invitationTokenRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.auditLogService = auditLogService;
    }

    public InvitationResponseDto createInvitation(CreateInvitationRequestDto dto) {
        return createInvitation(dto, null);
    }

    public InvitationResponseDto createInvitation(CreateInvitationRequestDto dto, JwtUserDetails inviter) {
        if (dto.assignedRole() == UserRole.administrator) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot invite a user as administrator");
        }

        String recipientEmail = dto.recipientEmail().trim().toLowerCase();
        Institution institution = entityManager.find(Institution.class, dto.institutionId());
        if (institution == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Institution not found");
        }
        validateInviterScope(dto, inviter);
        if (isValidator(inviter)) {
            validateInstitutionEmailDomain(recipientEmail, institution);
        }

        User invitedUser = userRepository.findByEmail(recipientEmail)
                .map(existing -> prepareExistingPendingUser(existing, dto.assignedRole(), institution))
                .orElseGet(() -> createPendingUser(recipientEmail, dto.assignedRole(), institution));
        userRepository.save(invitedUser);

        String rawToken = TokenHashUtils.generateRawToken();
        String tokenHash = TokenHashUtils.sha256Hex(rawToken);

        InvitationToken token = new InvitationToken();
        token.setRecipientEmail(recipientEmail);
        token.setAssignedRole(dto.assignedRole());
        token.setInstitution(institution);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(Instant.now().plus(Duration.ofHours(72)));
        invitationTokenRepository.save(token);

        boolean emailDelivered = true;
        try {
            emailService.sendInvitationEmail(recipientEmail, rawToken);
        } catch (RuntimeException ex) {
            emailDelivered = false;
            invitedUser.setAccountState(UserStatus.pending_email_undelivered);
            userRepository.save(invitedUser);
            log.warn("Invitation email failed for {}: {}", recipientEmail, ex.getMessage());
        }

        return new InvitationResponseDto(
                token.getId(),
                token.getRecipientEmail(),
                token.getAssignedRole(),
                institution.getId(),
                token.getExpiresAt(),
                token.getCreatedAt(),
                emailDelivered,
                emailService.buildInvitationLink(rawToken));
    }

    @Transactional(readOnly = true)
    public InvitationValidateResponseDto validateToken(String rawToken) {
        String tokenHash = TokenHashUtils.sha256Hex(rawToken);
        InvitationToken token = invitationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid invitation token"));

        assertTokenUnused(token);

        return new InvitationValidateResponseDto(
                token.getRecipientEmail(),
                token.getAssignedRole(),
                token.getInstitution().getName(),
                token.getExpiresAt());
    }

    public LoginResponseDto acceptInvitation(AcceptInvitationRequestDto dto) {
        String tokenHash = TokenHashUtils.sha256Hex(dto.token());
        InvitationToken token = invitationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid invitation token"));

        assertTokenUnused(token);

        User user = userRepository.findByEmail(token.getRecipientEmail())
                .orElseGet(User::new);
        if (user.getAccountState() == UserStatus.active) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account is already active");
        }
        user.setEmail(token.getRecipientEmail());
        user.setRole(token.getAssignedRole());
        user.setInstitution(token.getInstitution());
        user.setPasswordHash(passwordEncoder.encode(dto.password()));
        user.setAccountState(UserStatus.active);
        userRepository.save(user);

        token.setUsedAt(Instant.now());
        invitationTokenRepository.save(token);

        auditLogService.record(
                user,
                "INVITATION_ACCEPTED",
                null, null,
                user.getId(),
                Map.of("email", user.getEmail(), "role", user.getRole().name()));

        String jwt = jwtService.generateAccessToken(user);
        UUID institutionId = user.getInstitution().getId();
        return new LoginResponseDto(jwt, user.getRole().name(), institutionId);
    }

    private void assertTokenUnused(InvitationToken token) {
        if (token.getUsedAt() != null) {
            throw new ResponseStatusException(HttpStatus.GONE, "Invitation has already been used");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Invitation has expired");
        }
    }

    private User createPendingUser(String email, UserRole role, Institution institution) {
        User user = new User();
        user.setEmail(email);
        user.setRole(role);
        user.setInstitution(institution);
        user.setAccountState(UserStatus.pending);
        return user;
    }

    private User prepareExistingPendingUser(User user, UserRole role, Institution institution) {
        if (user.getAccountState() == UserStatus.active) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An active account already exists for this email");
        }
        user.setRole(role);
        user.setInstitution(institution);
        user.setAccountState(UserStatus.pending);
        return user;
    }

    private void validateInstitutionEmailDomain(String email, Institution institution) {
        String domain = institution.getEmailDomain();
        if (domain == null || domain.isBlank()) {
            return;
        }
        String normalizedDomain = domain.trim().toLowerCase();
        if (!email.endsWith("@" + normalizedDomain)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Recipient email must use the institution domain: " + normalizedDomain);
        }
    }

    /**
     * Resends an invitation by generating a new token for the same recipient.
     * Used when the original email was undelivered (pending_email_undelivered state).
     * The original token record remains but is superseded by the new one.
     */
    public InvitationResponseDto resend(UUID tokenId, JwtUserDetails requester) {
        InvitationToken original = invitationTokenRepository.findById(tokenId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found"));

        if (original.getUsedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invitation has already been accepted");
        }

        validateInviterScope(new CreateInvitationRequestDto(
                original.getRecipientEmail(),
                original.getInstitution().getId(),
                original.getAssignedRole()), requester);

        // Reset user state to pending so they can accept the new link
        userRepository.findByEmail(original.getRecipientEmail()).ifPresent(user -> {
            if (user.getAccountState() == UserStatus.pending_email_undelivered) {
                user.setAccountState(UserStatus.pending);
                userRepository.save(user);
            }
        });

        String rawToken = TokenHashUtils.generateRawToken();
        String tokenHash = TokenHashUtils.sha256Hex(rawToken);

        InvitationToken newToken = new InvitationToken();
        newToken.setRecipientEmail(original.getRecipientEmail());
        newToken.setAssignedRole(original.getAssignedRole());
        newToken.setInstitution(original.getInstitution());
        newToken.setTokenHash(tokenHash);
        newToken.setExpiresAt(Instant.now().plus(Duration.ofHours(72)));
        invitationTokenRepository.save(newToken);

        boolean emailDelivered = true;
        try {
            emailService.sendInvitationEmail(original.getRecipientEmail(), rawToken);
        } catch (RuntimeException ex) {
            emailDelivered = false;
            userRepository.findByEmail(original.getRecipientEmail()).ifPresent(user -> {
                user.setAccountState(UserStatus.pending_email_undelivered);
                userRepository.save(user);
            });
            log.warn("Resend invitation email failed for {}: {}", original.getRecipientEmail(), ex.getMessage());
        }

        return new InvitationResponseDto(
                newToken.getId(),
                newToken.getRecipientEmail(),
                newToken.getAssignedRole(),
                newToken.getInstitution().getId(),
                newToken.getExpiresAt(),
                newToken.getCreatedAt(),
                emailDelivered,
                emailService.buildInvitationLink(rawToken));
    }

    @Transactional(readOnly = true)
    public List<PendingInvitationDto> listPending(UUID institutionId, JwtUserDetails requester) {
        validateInstitutionScope(institutionId, requester);
        return invitationTokenRepository
                .findByInstitutionIdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(institutionId, Instant.now())
                .stream()
                .map(PendingInvitationDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> countPending(UUID institutionId, JwtUserDetails requester) {
        validateInstitutionScope(institutionId, requester);
        return Map.of(
                "pendingInvitations",
                invitationTokenRepository.countByInstitutionIdAndUsedAtIsNullAndExpiresAtAfter(institutionId, Instant.now()));
    }

    private void validateInviterScope(CreateInvitationRequestDto dto, JwtUserDetails inviter) {
        if (inviter == null || isAdministrator(inviter)) {
            return;
        }
        if (!isValidator(inviter)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only administrators and validators can send invitations");
        }
        if (dto.assignedRole() != UserRole.contributor) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Validators can only invite contributors");
        }
        if (inviter.institutionId() == null || !inviter.institutionId().equals(dto.institutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Validators can only invite users to their own institution");
        }
    }

    private boolean isAdministrator(JwtUserDetails inviter) {
        return inviter != null && "administrator".equalsIgnoreCase(inviter.role());
    }

    private boolean isValidator(JwtUserDetails inviter) {
        return inviter != null && "validator".equalsIgnoreCase(inviter.role());
    }

    private void validateInstitutionScope(UUID institutionId, JwtUserDetails requester) {
        if (requester == null || isAdministrator(requester)) {
            return;
        }
        if (!isValidator(requester)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only administrators and validators can view invitations");
        }
        if (requester.institutionId() == null || !requester.institutionId().equals(institutionId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Validators can only view invitations for their own institution");
        }
    }
}
