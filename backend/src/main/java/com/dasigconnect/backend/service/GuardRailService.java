package com.dasigconnect.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dasigconnect.backend.model.dto.guardrail.GuardRailResult;
import com.dasigconnect.backend.model.dto.guardrail.GuardRailViolation;
import com.dasigconnect.backend.model.entity.SlotReservation;
import com.dasigconnect.backend.repository.SlotReservationRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;

/**
 * Evaluates all five guard rail rules for a requested scheduling slot.
 *
 * Hard rules (block selection): GR-H1 — No two posts within ±30 minutes of each
 * other (network-wide) GR-H2 — Scheduled time must be ≥2 hours in the future
 * GR-H3 — Scheduled time must be ≤30 days in the future
 *
 * Soft rules (warning, Contributor can proceed): GR-S1 — Institution has ≤3
 * scheduled-but-unpublished posts GR-S2 — ≤6 posts per calendar day
 * network-wide
 *
 * Called by: - POST /api/guardrails/validate (slot check before reserving) -
 * SlotReservationService.reserve() (re-validated at reservation time)
 */
@Service
@Transactional(readOnly = true)
public class GuardRailService {

    // Thresholds — match SRS Section 3.x guard rail definitions
    private static final Duration GR_H1_WINDOW = Duration.ofMinutes(30);
    private static final Duration GR_H2_MIN_FUTURE = Duration.ofHours(2);
    private static final Duration GR_H3_MAX_FUTURE = Duration.ofDays(30);
    private static final long GR_S1_MAX_PENDING = 3L;
    private static final long GR_S2_MAX_PER_DAY = 6L;

    // Search window for suggesting alternative slots on GR-H1 violations
    private static final Duration SUGGESTION_STEP = Duration.ofMinutes(31);
    private static final int SUGGESTION_COUNT = 3;
    private static final Duration SUGGESTION_SEARCH = Duration.ofHours(2);

    private final SlotReservationRepository slotReservationRepository;
    private final SubmissionRepository submissionRepository;

    public GuardRailService(
            SlotReservationRepository slotReservationRepository,
            SubmissionRepository submissionRepository) {
        this.slotReservationRepository = slotReservationRepository;
        this.submissionRepository = submissionRepository;
    }

    /**
     * Validates a requested slot against all guard rail rules.
     *
     * @param institutionId the institution making the request (for GR-S1)
     * @param requestedSlot the proposed scheduled_at time
     * @return GuardRailResult containing any hard blocks and/or soft warnings
     */
    public GuardRailResult validate(UUID institutionId, Instant requestedSlot) {
        Instant now = Instant.now();
        List<GuardRailViolation> hardBlocks = new ArrayList<>();
        List<GuardRailViolation> softWarnings = new ArrayList<>();

        // ── Hard Rules ────────────────────────────────────────────────────────
        // GR-H1: No two posts within ±30 minutes (network-wide)
        Instant windowStart = requestedSlot.minus(GR_H1_WINDOW);
        Instant windowEnd = requestedSlot.plus(GR_H1_WINDOW);
        boolean hasConflict = slotReservationRepository.existsActiveWithin30Minutes(windowStart, windowEnd);
        if (hasConflict) {
            hardBlocks.add(new GuardRailViolation(
                    "GR-H1",
                    "A post is already scheduled within 30 minutes of this slot. "
                    + "Please choose one of the suggested times.",
                    suggestAlternativeSlots(requestedSlot, now)
            ));
        }

        // GR-H2: Scheduled time must be ≥2 hours in the future
        if (requestedSlot.isBefore(now.plus(GR_H2_MIN_FUTURE))) {
            hardBlocks.add(new GuardRailViolation(
                    "GR-H2",
                    "Scheduled time must be at least 2 hours from now."
            ));
        }

        // GR-H3: Scheduled time must be ≤30 days in the future
        if (requestedSlot.isAfter(now.plus(GR_H3_MAX_FUTURE))) {
            hardBlocks.add(new GuardRailViolation(
                    "GR-H3",
                    "Scheduled time cannot be more than 30 days in the future."
            ));
        }

        // ── Soft Rules ────────────────────────────────────────────────────────
        // GR-S1: ≤3 scheduled-but-unpublished posts per institution
        long unpublishedCount = submissionRepository.countUnpublishedByInstitution(institutionId);
        if (unpublishedCount >= GR_S1_MAX_PENDING) {
            softWarnings.add(new GuardRailViolation(
                    "GR-S1",
                    "Your institution already has " + unpublishedCount
                    + " posts awaiting publication. You can still proceed, "
                    + "but consider reducing your queue first."
            ));
        }

        // GR-S2: ≤6 posts per calendar day network-wide
        Instant dayStart = requestedSlot.truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        Instant dayEnd = dayStart.plus(Duration.ofDays(1));
        long dailyCount = slotReservationRepository.countActiveOnDay(dayStart, dayEnd);
        if (dailyCount >= GR_S2_MAX_PER_DAY) {
            softWarnings.add(new GuardRailViolation(
                    "GR-S2",
                    dailyCount + " posts are already scheduled on this day across the network. "
                    + "You can still proceed, but the feed may be crowded."
            ));
        }

        return new GuardRailResult(hardBlocks, softWarnings);
    }

    // ── Private Helpers ───────────────────────────────────────────────────────
    /**
     * Suggests up to SUGGESTION_COUNT alternative slot times near the
     * conflicted slot. Walks forward from (requestedSlot + 31 min) in 31-minute
     * increments, skipping any time that would itself trigger GR-H1. Also
     * enforces GR-H2 (must be ≥2 hours from now).
     */
    private List<Instant> suggestAlternativeSlots(Instant conflictedSlot, Instant now) {
        List<Instant> suggestions = new ArrayList<>();
        Instant minAllowed = now.plus(GR_H2_MIN_FUTURE);

        // Gather existing reservations within ±2 hours to avoid during suggestion
        List<SlotReservation> nearby = slotReservationRepository.findActiveInWindow(
                conflictedSlot.minus(SUGGESTION_SEARCH),
                conflictedSlot.plus(SUGGESTION_SEARCH)
        );

        Instant candidate = conflictedSlot.plus(SUGGESTION_STEP);
        int attempts = 0;

        while (suggestions.size() < SUGGESTION_COUNT && attempts < 20) {
            attempts++;
            // Skip if in the past or too soon
            if (candidate.isBefore(minAllowed)) {
                candidate = candidate.plus(SUGGESTION_STEP);
                continue;
            }
            // Skip if too far in the future (GR-H3)
            if (candidate.isAfter(now.plus(GR_H3_MAX_FUTURE))) {
                break;
            }
            // Check against nearby reserved slots
            Instant finalCandidate = candidate;
            boolean blocked = nearby.stream().anyMatch(r
                    -> Duration.between(r.getScheduledAt(), finalCandidate).abs().compareTo(GR_H1_WINDOW) <= 0
            );
            if (!blocked) {
                suggestions.add(candidate);
            }
            candidate = candidate.plus(SUGGESTION_STEP);
        }

        return suggestions;
    }
}
