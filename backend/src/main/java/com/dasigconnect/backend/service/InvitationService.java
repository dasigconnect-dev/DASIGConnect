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

    /**
     * Administrative policy cap: maximum active admin accounts network-wide
     * (`app.admins.max`, default 3). Enforced when an admin invitation is
     * created and again when it is accepted, so a stale invite can never push
     * the network past the limit.
     */
    @org.springframework.beans.factory.annotation.Value("${app.admins.max:3}")
    private long maxAdmins;

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
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "An authenticated admin is required to create invitations");
    }

    public InvitationResponseDto createInvitation(CreateInvitationRequestDto dto, JwtUserDetails inviter) {
        String recipientEmail = dto.recipientEmail().trim().toLowerCase();
        Institution institution = resolveInvitationInstitution(dto);
        validateInviterScope(dto, inviter);

        if (dto.assignedRole() == UserRole.admin) {
            assertAdminCapAllows(recipientEmail);
        }

        // Reject invitation to inactive institution
        if (institution != null && institution.getStatus() == InstitutionStatus.inactive) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot invite contributors to an inactive institution. Reactivate the institution first.");
        }

        User invitedUser = userRepository.findByEmail(recipientEmail)
                .map(existing -> prepareExistingPendingUser(existing, dto.assignedRole(), institution))
                .orElseGet(() -> createPendingUser(recipientEmail, dto.assignedRole(), institution));
        invitedUser.setInvitedByUserId(inviter != null ? inviter.userId() : null);
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
        token.setCreatedByUserId(inviter != null ? inviter.userId() : null);
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
                institution != null ? institution.getId() : null,
                token.getExpiresAt(),
                token.getCreatedAt(),
                emailDelivered,
                emailService.buildInvitationLink(rawToken));
    }

    @Transactional(readOnly = true)
    public InvitationValidateResponseDto validateToken(String rawToken) {
        String tokenHash = TokenHashUtils.sha256Hex(normalizeRawToken(rawToken));
        InvitationToken token = invitationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid invitation token"));

        assertTokenUnused(token);

        return new InvitationValidateResponseDto(
                token.getRecipientEmail(),
                token.getAssignedRole(),
                token.getInstitution() != null ? token.getInstitution().getName() : null,
                token.getExpiresAt());
    }

    public LoginResponseDto acceptInvitation(AcceptInvitationRequestDto dto) {
        String tokenHash = TokenHashUtils.sha256Hex(normalizeRawToken(dto.token()));
        InvitationToken token = invitationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid invitation token"));

        assertTokenUnused(token);

        User user = userRepository.findByEmail(token.getRecipientEmail())
                .orElseGet(User::new);
        if (user.getAccountState() == UserStatus.active) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account is already active");
        }
        if (token.getAssignedRole() == UserRole.admin
                && userRepository.countByRoleAndAccountState(UserRole.admin, UserStatus.active) >= maxAdmins) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Admin limit reached (" + maxAdmins + "). This invitation can no longer be accepted.");
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
        UUID institutionId = user.getInstitution() != null ? user.getInstitution().getId() : null;
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

    private String normalizeRawToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation token is required");
        }
        return rawToken.trim();
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
        if ((user.getRole() == UserRole.moderator || user.getRole() == UserRole.admin)
                && user.getAccountState() == UserStatus.inactive) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A deactivated account must be reactivated by an admin");
        }
        user.setAdminOwner(false);
        user.setSuperAdminTransferRequestedBy(null);
        user.setSuperAdminTransferExpiresAt(null);
        user.setRole(role);
        user.setInstitution(institution);
        user.setAccountState(UserStatus.pending);
        return user;
    }

    private Institution resolveInvitationInstitution(CreateInvitationRequestDto dto) {
        if (dto.assignedRole() == UserRole.admin || dto.assignedRole() == UserRole.moderator) {
            if (dto.institutionId() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Admin and moderator invitations must not be assigned to an institution");
            }
            return null;
        }
        if (dto.institutionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Institution is required for contributor invitations");
        }
        Institution institution = entityManager.find(Institution.class, dto.institutionId());
        if (institution == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Institution not found");
        }
        return institution;
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
                original.getInstitution() != null ? original.getInstitution().getId() : null,
                original.getAssignedRole()), requester);
        assertMayManageInvitation(original, requester, "resend");

        userRepository.findByEmail(original.getRecipientEmail()).ifPresent(user -> {
            if (user.getAccountState() == UserStatus.pending_email_undelivered
                    || user.getAccountState() == UserStatus.expired
                    || user.getAccountState() == UserStatus.cancelled) {
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
        newToken.setCreatedByUserId(original.getCreatedByUserId() != null
                ? original.getCreatedByUserId()
                : (requester != null ? requester.userId() : null));
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
                newToken.getInstitution() != null ? newToken.getInstitution().getId() : null,
                newToken.getExpiresAt(),
                newToken.getCreatedAt(),
                emailDelivered,
                emailService.buildInvitationLink(rawToken));
    }

    public void resendExpiredToken(String rawToken, String email) {
        InvitationToken targetToken = null;
        if (rawToken != null && !rawToken.isBlank()) {
            String hash = TokenHashUtils.sha256Hex(normalizeRawToken(rawToken));
            targetToken = invitationTokenRepository.findByTokenHash(hash).orElse(null);
        }

        String targetEmail = targetToken != null ? targetToken.getRecipientEmail() : (email != null ? email.trim().toLowerCase() : null);
        if (targetEmail == null || targetEmail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email or valid token is required.");
        }

        User user = userRepository.findByEmail(targetEmail).orElse(null);
        if (user != null && user.getAccountState() == UserStatus.active) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account is already active. Please sign in.");
        }

        UserRole role = targetToken != null ? targetToken.getAssignedRole() : (user != null ? user.getRole() : UserRole.contributor);
        Institution institution = targetToken != null ? targetToken.getInstitution() : (user != null ? user.getInstitution() : null);

        if (user != null && (user.getAccountState() == UserStatus.pending_email_undelivered
                || user.getAccountState() == UserStatus.expired
                || user.getAccountState() == UserStatus.cancelled)) {
            user.setAccountState(UserStatus.pending);
            userRepository.save(user);
        }

        Instant now = Instant.now();
        invalidateOpenInvitations(targetEmail, now);

        String freshRawToken = TokenHashUtils.generateRawToken();
        String freshTokenHash = TokenHashUtils.sha256Hex(freshRawToken);

        InvitationToken newToken = new InvitationToken();
        newToken.setRecipientEmail(targetEmail);
        newToken.setAssignedRole(role);
        newToken.setInstitution(institution);
        newToken.setTokenHash(freshTokenHash);
        newToken.setExpiresAt(now.plus(Duration.ofHours(72)));
        invitationTokenRepository.save(newToken);

        try {
            emailService.sendInvitationEmail(targetEmail, freshRawToken);
        } catch (RuntimeException ex) {
            if (user != null) {
                user.setAccountState(UserStatus.pending_email_undelivered);
                userRepository.save(user);
            }
            log.warn("Resend expired invitation email failed for {}: {}", targetEmail, ex.getMessage());
        }
    }

    public void cancel(UUID tokenId, JwtUserDetails requester) {
        InvitationToken token = invitationTokenRepository.findById(tokenId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found."));

        assertMayManageInvitation(token, requester, "cancel");

        if (token.getUsedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This invitation has already been accepted.");
        }

        invitationTokenRepository.delete(token);
        log.info("Invitation {} cancelled by {}", tokenId, requester != null ? requester.userId() : "unknown");

        userRepository.findByEmail(token.getRecipientEmail()).ifPresent(user -> {
            if (user.getAccountState() == UserStatus.pending
                    || user.getAccountState() == UserStatus.pending_email_undelivered
                    || user.getAccountState() == UserStatus.expired) {
                user.setAccountState(UserStatus.cancelled);
                userRepository.save(user);
            } else {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Cancel invitation is only allowed for pending accounts.");
            }
        });
    }

    /**
     * Cancels a pending account by user id rather than token id. Works even when
     * the invitation token has expired or was cleaned up — the pending user row
     * is what the management screens actually show, and it must reliably move to
     * CANCELLED. Removes every token for the address and marks the account
     * cancelled. Admin-only.
     */
    public void cancelPendingUserInvitation(UUID userId, JwtUserDetails requester) {
        if (!isAdmin(requester)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can cancel invitations.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));

        UserStatus state = user.getAccountState();
        if (state != UserStatus.pending
                && state != UserStatus.pending_email_undelivered
                && state != UserStatus.expired) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only pending accounts can have their invitation cancelled.");
        }

        int removed = user.getEmail() != null
                ? invitationTokenRepository.deleteByRecipientEmailIgnoreCase(user.getEmail())
                : 0;
        user.setAccountState(UserStatus.cancelled);
        userRepository.save(user);
        log.info("Pending invitation for {} cancelled by {} ({} token(s) removed)",
                userId, requester != null ? requester.userId() : "unknown", removed);
    }

    @Transactional(readOnly = true)
    public List<PendingInvitationDto> listPending(UUID institutionId, JwtUserDetails requester) {
        validateInstitutionScope(institutionId, requester);
        return invitationTokenRepository
                .findByInstitutionIdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(institutionId, Instant.now())
                .stream()
                .map(token -> PendingInvitationDto.from(token, mayManageInvitation(token, requester)))
                .toList();
    }

    /** Non-throwing counterpart of {@link #assertMayManageInvitation} — used to flag rows in listings. */
    private boolean mayManageInvitation(InvitationToken token, JwtUserDetails requester) {
        if (isAdmin(requester)) {
            return true;
        }
        return requester != null
                && "moderator".equalsIgnoreCase(requester.role())
                && token.getAssignedRole() == UserRole.contributor
                && token.getCreatedByUserId() != null
                && token.getCreatedByUserId().equals(requester.userId());
    }

    @Transactional(readOnly = true)
    public List<PendingInvitationDto> listPendingAdmins(JwtUserDetails requester) {
        if (!isAdmin(requester)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only admins can view admin invitations");
        }
        return invitationTokenRepository
                .findPendingNetworkRoleInvitations(UserRole.admin, Instant.now())
                .stream()
                .map(PendingInvitationDto::from)
                .toList();
    }

    /**
     * Pending contributor/moderator invitations across every institution.
     * Admin-only — backs the network-wide User Management page.
     */
    @Transactional(readOnly = true)
    public List<PendingInvitationDto> listPendingNetwork(JwtUserDetails requester) {
        if (!isAdmin(requester)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only admins can view network-wide invitations");
        }
        return invitationTokenRepository
                .findPendingRoleInvitationsAcrossInstitutions(
                        java.util.EnumSet.of(UserRole.contributor, UserRole.moderator), Instant.now())
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

    /**
     * Enforces the three-admin policy cap. Counts active admins plus distinct
     * pending admin invitations (other than one already outstanding for this
     * same recipient, which a resend would simply replace).
     */
    private void assertAdminCapAllows(String recipientEmail) {
        long activeAdmins = userRepository.countByRoleAndAccountState(UserRole.admin, UserStatus.active);
        long pendingAdminInvites = invitationTokenRepository
                .findPendingNetworkRoleInvitations(UserRole.admin, Instant.now())
                .stream()
                .map(InvitationToken::getRecipientEmail)
                .filter(email -> !email.equalsIgnoreCase(recipientEmail))
                .distinct()
                .count();
        if (activeAdmins + pendingAdminInvites >= maxAdmins) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Admin limit reached (" + maxAdmins
                            + "). Remove or transfer an existing admin before inviting another.");
        }
    }

    private void validateInviterScope(CreateInvitationRequestDto dto, JwtUserDetails inviter) {
        if (isAdmin(inviter)) {
            return;
        }
        if (inviter != null && "moderator".equalsIgnoreCase(inviter.role())) {
            if (dto.assignedRole() != UserRole.contributor) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Only admins can invite moderators or admins");
            }
            // Moderators are network-wide (no owning institution) — they may invite
            // contributors to any institution, same as admins.
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Only admins can send invitations");
    }

    private void invalidateOpenInvitations(String recipientEmail, Instant now) {
        invitationTokenRepository
                .findByRecipientEmailAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(recipientEmail, now)
                .forEach(token -> {
                    token.setUsedAt(now);
                    invitationTokenRepository.save(token);
                });
    }

    private boolean isAdmin(JwtUserDetails inviter) {
        return inviter != null && "admin".equalsIgnoreCase(inviter.role());
    }

    /**
     * An admin may resend/cancel any invitation. A moderator may only manage a
     * {@code contributor} invitation that they issued themselves.
     */
    private void assertMayManageInvitation(InvitationToken token, JwtUserDetails requester, String action) {
        if (!mayManageInvitation(token, requester)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only " + action + " invitations you sent.");
        }
    }

    private void validateInstitutionScope(UUID institutionId, JwtUserDetails requester) {
        if (isAdmin(requester) || (requester != null && "moderator".equalsIgnoreCase(requester.role()))) {
            // Moderator and Admin are both network-wide roles — no institution comparison needed.
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Only admins and moderators can view invitations");
    }
}
