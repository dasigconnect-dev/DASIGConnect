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
import com.dasigconnect.backend.model.entity.InstitutionStatus;
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
    private final InstitutionService institutionService;

    public InvitationService(
            InvitationTokenRepository invitationTokenRepository,
            UserRepository userRepository,
            EntityManager entityManager,
            PasswordEncoder passwordEncoder,
            JWTService jwtService,
            EmailService emailService,
            AuditLogService auditLogService,
            InstitutionService institutionService) {
        this.invitationTokenRepository = invitationTokenRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.auditLogService = auditLogService;
        this.institutionService = institutionService;
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

        // Enforce provisioning rules based on institution status
        if (dto.assignedRole() == UserRole.contributor) {
            if (institution.getStatus() != InstitutionStatus.active) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Contributors can only be invited to active institutions. "
                        + "Invite a validator first to activate this institution.");
            }
        } else if (dto.assignedRole() == UserRole.validator) {
            // Transition inactive → pending when the first validator invitation is sent
            if (institution.getStatus() == InstitutionStatus.inactive) {
                institutionService.transitionToPending(institution.getId());
            }
            // If already pending or active, the invitation proceeds without a status change
        }

        User invitedUser = userRepository.findByEmail(recipientEmail)
                .map(existing -> prepareExistingPendingUser(existing, dto.assignedRole(), institution))
                .orElseGet(() -> createPendingUser(recipientEmail, dto.assignedRole(), institution));
        userRepository.save(invitedUser);

        Instant now = Instant.now();
        invalidateOpenInvitations(recipientEmail, now);

        String rawToken = TokenHashUtils.generateRawToken();
        String tokenHash = TokenHashUtils.sha256Hex(rawToken);

        InvitationToken token = new InvitationToken();
        token.setRecipientEmail(recipientEmail);
        token.setAssignedRole(dto.assignedRole());
        token.setInstitution(institution);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(now.plus(Duration.ofHours(72)));
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
        user.setFirstName(normalizeName(dto.firstName()));
        user.setLastName(normalizeName(dto.lastName()));
        user.setPasswordHash(passwordEncoder.encode(dto.password()));
        user.setAccountState(UserStatus.active);
        userRepository.save(user);

        token.setUsedAt(Instant.now());
        invitationTokenRepository.save(token);

        // Transition institution PENDING → ACTIVE when the first validator activates
        if (token.getAssignedRole() == UserRole.validator) {
            Institution institution = token.getInstitution();
            InstitutionStatus status = institution.getStatus();
            if (status == InstitutionStatus.pending || status == InstitutionStatus.inactive) {
                institutionService.transitionToActive(institution.getId());
            }
        }

        auditLogService.record(
                user,
                "INVITATION_ACCEPTED",
                null, null,
                user.getId(),
                Map.of(
                        "email", user.getEmail(),
                        "role", user.getRole().name(),
                        "firstName", user.getFirstName(),
                        "lastName", user.getLastName()));

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

    private String normalizeName(String value) {
        return value == null ? null : value.trim().replaceAll("\\s+", " ");
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

        userRepository.findByEmail(original.getRecipientEmail()).ifPresent(user -> {
            if (user.getAccountState() == UserStatus.pending_email_undelivered) {
                user.setAccountState(UserStatus.pending);
                userRepository.save(user);
            }
        });

        Instant now = Instant.now();
        invalidateOpenInvitations(original.getRecipientEmail(), now);

        String rawToken = TokenHashUtils.generateRawToken();
        String tokenHash = TokenHashUtils.sha256Hex(rawToken);

        InvitationToken newToken = new InvitationToken();
        newToken.setRecipientEmail(original.getRecipientEmail());
        newToken.setAssignedRole(original.getAssignedRole());
        newToken.setInstitution(original.getInstitution());
        newToken.setTokenHash(tokenHash);
        newToken.setExpiresAt(now.plus(Duration.ofHours(72)));
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

    public void cancel(UUID tokenId, JwtUserDetails requester) {
        InvitationToken token = invitationTokenRepository.findById(tokenId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found."));

        if (!isAdministrator(requester)) {
            if (requester.institutionId() == null
                    || !token.getInstitution().getId().equals(requester.institutionId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "You can only cancel invitations for your own institution.");
            }
        }

        if (token.getUsedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This invitation has already been accepted.");
        }

        Institution institution = token.getInstitution();
        UserRole cancelledRole = token.getAssignedRole();
        invitationTokenRepository.delete(token);
        log.info("Invitation {} cancelled by {}", tokenId, requester != null ? requester.userId() : "unknown");

        // If the cancelled invitation was for a validator and the institution is PENDING,
        // revert to INACTIVE if no other pending validator invitations and no active validators remain.
        if (cancelledRole == UserRole.validator
                && institution.getStatus() == InstitutionStatus.pending) {
            long pendingValidatorInvites = invitationTokenRepository
                    .countByInstitutionIdAndAssignedRoleAndUsedAtIsNullAndExpiresAtAfter(
                            institution.getId(), UserRole.validator, Instant.now());
            long activeValidators = userRepository
                    .countByInstitutionIdAndRoleAndAccountState(
                            institution.getId(), UserRole.validator, UserStatus.active);
            if (pendingValidatorInvites == 0 && activeValidators == 0) {
                institutionService.transitionToInactive(institution.getId());
            }
        }
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

    private void invalidateOpenInvitations(String recipientEmail, Instant now) {
        invitationTokenRepository
                .findByRecipientEmailAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(recipientEmail, now)
                .forEach(token -> {
                    token.setUsedAt(now);
                    invitationTokenRepository.save(token);
                });
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
