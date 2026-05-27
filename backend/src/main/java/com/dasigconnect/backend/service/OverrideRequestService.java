package com.dasigconnect.backend.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.event.OverrideApprovedEvent;
import com.dasigconnect.backend.event.OverrideDeniedEvent;
import com.dasigconnect.backend.event.OverrideSlotSuggestedEvent;
import com.dasigconnect.backend.model.dto.exception.OverrideRequestDto;
import com.dasigconnect.backend.model.entity.OverrideRequest;
import com.dasigconnect.backend.model.entity.OverrideRequestDecision;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.OverrideRequestRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.JwtUserDetails;

/**
 * UC-3.5 Category C — Guard Rail Override Requests.
 *
 * Handles the Administrator triage of pending override requests:
 * approve (bypasses the guard rail for this submission), suggest alternative slot,
 * or deny. Also auto-dismisses expired requests via {@link #dismissExpiredRequests()}.
 */
@Service
@Transactional
public class OverrideRequestService {

    private static final Logger log = LoggerFactory.getLogger(OverrideRequestService.class);

    // UC-1.3 limit: max 2 override requests per submission before auto-escalation flag
    private static final int MAX_OVERRIDE_REQUESTS = 2;

    private final OverrideRequestRepository overrideRequestRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final SlotReservationService slotReservationService;
    private final GuardRailService guardRailService;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;

    public OverrideRequestService(
            OverrideRequestRepository overrideRequestRepository,
            SubmissionRepository submissionRepository,
            UserRepository userRepository,
            SlotReservationService slotReservationService,
            GuardRailService guardRailService,
            AuditLogService auditLogService,
            ApplicationEventPublisher eventPublisher) {
        this.overrideRequestRepository = overrideRequestRepository;
        this.submissionRepository = submissionRepository;
        this.userRepository = userRepository;
        this.slotReservationService = slotReservationService;
        this.guardRailService = guardRailService;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<OverrideRequestDto> getPendingRequests() {
        return overrideRequestRepository.findPendingOrderBySlot()
                .stream()
                .map(r -> OverrideRequestDto.from(r,
                        (int) overrideRequestRepository.countNonExpiredBySubmissionId(r.getSubmission().getId())))
                .toList();
    }

    /**
     * C(i) — Approve Override: bypasses the violated guard rail and reserves the requested slot.
     */
    public void approve(UUID requestId, JwtUserDetails admin) {
        OverrideRequest r = loadPending(requestId);
        Submission s = r.getSubmission();

        // Reserve the slot, bypassing guard rails (by calling reserveLockedSlot)
        slotReservationService.reserveLockedSlot(s.getId(), s.getInstitution().getId(), r.getRequestedSlot());

        User adminUser = loadUser(admin.userId());
        r.setDecision(OverrideRequestDecision.approved);
        r.setDecidedBy(adminUser);
        r.setDecidedAt(Instant.now());
        overrideRequestRepository.save(r);

        auditLogService.record(adminUser, "OVERRIDE_APPROVED", null, null, r.getId(),
                Map.of("submissionId", s.getId().toString(),
                       "violatedRule", r.getViolatedRule(),
                       "requestedSlot", r.getRequestedSlot().toString(),
                       "contributorId", r.getContributor().getId().toString()));

        eventPublisher.publishEvent(new OverrideApprovedEvent(s, r.getContributor()));
        log.info("Admin {} approved override request {} for submission {}.",
                admin.userId(), requestId, s.getId());
    }

    /**
     * C(ii) — Suggest Alternative Slot.
     * Validates the suggested slot satisfies hard rules, then notifies the Contributor.
     */
    public void suggest(UUID requestId, Instant suggestedSlot, JwtUserDetails admin) {
        if (suggestedSlot == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Suggested slot is required.");
        }

        OverrideRequest r = loadPending(requestId);
        Submission s = r.getSubmission();

        // Validate the suggested slot against all hard rules (GR-H1, H2, H3)
        var result = guardRailService.validate(s.getInstitution().getId(), suggestedSlot);
        if (result.isBlocked()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Suggested slot violates guard rail rules: " + result.getHardBlocks().get(0).getMessage());
        }

        User adminUser = loadUser(admin.userId());
        r.setDecision(OverrideRequestDecision.suggested);
        r.setSuggestedSlot(suggestedSlot);
        r.setDecidedBy(adminUser);
        r.setDecidedAt(Instant.now());
        overrideRequestRepository.save(r);

        auditLogService.record(adminUser, "OVERRIDE_SLOT_SUGGESTED", null, null, r.getId(),
                Map.of("submissionId", s.getId().toString(),
                       "suggestedSlot", suggestedSlot.toString()));

        eventPublisher.publishEvent(new OverrideSlotSuggestedEvent(s, r.getContributor(), suggestedSlot));
        log.info("Admin {} suggested slot {} for override request {}.", admin.userId(), suggestedSlot, requestId);
    }

    /**
     * C(iii) — Deny Override. Submission remains in DRAFT; hard guard rail stays active.
     */
    public void deny(UUID requestId, String reason, JwtUserDetails admin) {
        OverrideRequest r = loadPending(requestId);
        Submission s = r.getSubmission();

        User adminUser = loadUser(admin.userId());
        r.setDecision(OverrideRequestDecision.denied);
        r.setDecisionReason(reason);
        r.setDecidedBy(adminUser);
        r.setDecidedAt(Instant.now());
        overrideRequestRepository.save(r);

        auditLogService.record(adminUser, "OVERRIDE_DENIED", null, null, r.getId(),
                Map.of("submissionId", s.getId().toString(),
                       "violatedRule", r.getViolatedRule(),
                       "reason", reason != null ? reason : ""));

        eventPublisher.publishEvent(new OverrideDeniedEvent(s, r.getContributor(), reason));
        log.info("Admin {} denied override request {} for submission {}.",
                admin.userId(), requestId, s.getId());
    }

    /**
     * Called by {@link com.dasigconnect.backend.schedule.ExpiredOverrideCleanupJob}.
     * Auto-dismisses pending requests whose requested slot has already passed.
     */
    public int dismissExpiredRequests() {
        List<OverrideRequest> expired = overrideRequestRepository.findExpiredRequests(Instant.now());
        if (expired.isEmpty()) return 0;

        for (OverrideRequest r : expired) {
            r.setDecision(OverrideRequestDecision.expired);
            r.setDecidedAt(Instant.now());
            overrideRequestRepository.save(r);

            eventPublisher.publishEvent(new OverrideDeniedEvent(
                    r.getSubmission(), r.getContributor(),
                    "Your override request for '" + r.getSubmission().getEventTitle()
                    + "' has expired — the requested slot (" + r.getRequestedSlot()
                    + ") is now in the past. Please select a new time slot."));
        }

        log.info("ExpiredOverrideCleanupJob: dismissed {} expired override request(s).", expired.size());
        return expired.size();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private OverrideRequest loadPending(UUID requestId) {
        OverrideRequest r = overrideRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Override request not found."));
        if (r.getDecision() != OverrideRequestDecision.pending) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This override request has already been resolved (decision: " + r.getDecision() + ").");
        }
        return r;
    }

    private User loadUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Authenticated user not found."));
    }
}
