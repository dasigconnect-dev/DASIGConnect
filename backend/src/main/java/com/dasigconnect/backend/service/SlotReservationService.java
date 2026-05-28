package com.dasigconnect.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dasigconnect.backend.exception.GuardRailViolationException;
import com.dasigconnect.backend.exception.SlotAlreadyTakenException;
import com.dasigconnect.backend.model.dto.guardrail.GuardRailResult;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.SlotReservation;
import com.dasigconnect.backend.model.entity.SlotReservationStatus;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.repository.InstitutionRepository;
import com.dasigconnect.backend.repository.SlotReservationRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;

/**
 * Manages the full lifecycle of slot reservations.
 *
 * Slot states (SlotReservationStatus): held — tentative hold during DRAFT and
 * PENDING submission states locked — confirmed after submission is APPROVED
 * (permanent) released — slot is free again (rejection / revision / stale draft
 * expiry)
 *
 * Atomicity: The DB has a partial unique index on (scheduled_at,
 * institution_id) WHERE status != 'released'. On concurrent reservation of the
 * same slot, one transaction gets a DataIntegrityViolationException which we
 * catch and rethrow as SlotAlreadyTakenException → HTTP 409.
 *
 * Called by: - M5's SubmissionController (reserve on slot selection, release on
 * rejection/revision) - GR-T2 cron job (release stale draft holds after 7 days
 * of inactivity)
 */
@Service
public class SlotReservationService {

    private static final Logger log = LoggerFactory.getLogger(SlotReservationService.class);

    private static final Duration STALE_DRAFT_THRESHOLD = Duration.ofDays(7);

    private final SlotReservationRepository slotReservationRepository;
    private final SubmissionRepository submissionRepository;
    private final InstitutionRepository institutionRepository;
    private final GuardRailService guardRailService;

    @Value("${app.guardrails.enforced:true}")
    private boolean guardRailsEnforced = true;

    public SlotReservationService(
            SlotReservationRepository slotReservationRepository,
            SubmissionRepository submissionRepository,
            InstitutionRepository institutionRepository,
            GuardRailService guardRailService) {
        this.slotReservationRepository = slotReservationRepository;
        this.submissionRepository = submissionRepository;
        this.institutionRepository = institutionRepository;
        this.guardRailService = guardRailService;
    }

    /**
     * Validates guard rails and reserves a slot for a submission.
     *
     * Flow: 1. Run GuardRailService — throw GuardRailViolationException on hard
     * blocks 2. Persist SlotReservation with status = held 3. If DB unique
     * constraint fires → throw SlotAlreadyTakenException
     *
     * @param submissionId the submission claiming this slot
     * @param institutionId the institution the submission belongs to
     * @param requestedSlot the desired scheduled_at time
     * @return the persisted SlotReservation
     */
    @Transactional
    public SlotReservation reserve(UUID submissionId, UUID institutionId, Instant requestedSlot) {
        SlotReservation existingReservation = slotReservationRepository.findActiveBySubmissionId(submissionId)
                .orElse(null);
        if (existingReservation != null && existingReservation.getScheduledAt().compareTo(requestedSlot) == 0) {
            log.debug("Submission {} already owns slot {}; skipping duplicate reservation.",
                    submissionId, requestedSlot);
            return existingReservation;
        }

        if (guardRailsEnforced) {
            // Step 1: Guard rail validation
            GuardRailResult result = guardRailService.validate(institutionId, requestedSlot);
            if (result.isBlocked()) {
                log.info("Slot reservation rejected for submission {} institution {} slot {}: {}",
                        submissionId, institutionId, requestedSlot, result.getHardBlocks());
                throw new GuardRailViolationException(result.getHardBlocks());
            }
            // Soft warnings are logged but do not block (frontend handles the warning UI)
            if (result.hasWarnings()) {
                log.info("Slot reservation proceeding with soft warnings for submission {}: {}",
                        submissionId, result.getSoftWarnings());
            }
        } else {
            log.info("Guard rail enforcement disabled; reserving requested slot {} for submission {}.",
                    requestedSlot, submissionId);
        }

        // Step 2: Load entities
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found: " + submissionId));
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new IllegalArgumentException("Institution not found: " + institutionId));

        // Step 3: Release any existing held slot for this submission (re-selection case)
        slotReservationRepository.releaseBySubmissionId(submissionId);

        // Step 4: Persist the new reservation
        SlotReservation reservation = new SlotReservation();
        reservation.setSubmission(submission);
        reservation.setInstitution(institution);
        reservation.setScheduledAt(requestedSlot);
        reservation.setStatus(SlotReservationStatus.held);

        try {
            return slotReservationRepository.save(reservation);
        } catch (DataIntegrityViolationException ex) {
            // DB unique index fired — another Contributor took this slot in a race
            throw new SlotAlreadyTakenException(
                    "This slot was just taken by another submission. Please choose a different time.");
        }
    }

    /**
     * Releases the active slot reservation for a submission.
     *
     * Called when: - Validator rejects a submission - Validator requests
     * revision (NEEDS_REVISION state) - GR-T2 cron job expires a stale draft
     *
     * @param submissionId the submission whose slot should be freed
     * @return number of rows updated (0 = no active reservation existed)
     */
    @Transactional
    public int release(UUID submissionId) {
        int updated = slotReservationRepository.releaseBySubmissionId(submissionId);
        if (updated > 0) {
            log.info("Released slot reservation for submission {}", submissionId);
        }
        return updated;
    }

    @Transactional
    public void deleteAllForSubmission(UUID submissionId) {
        slotReservationRepository.deleteBySubmissionId(submissionId);
    }

    /**
     * Confirms (locks) the slot reservation after a submission is approved.
     *
     * Transitions: held → locked Once locked, the slot is permanently occupied
     * until the post is published or the submission is cancelled by an
     * Administrator.
     *
     * @param submissionId the approved submission
     */
    @Transactional
    public void confirm(UUID submissionId) {
        SlotReservation reservation = slotReservationRepository
                .findActiveBySubmissionId(submissionId)
                .orElseThrow(() -> new IllegalStateException(
                "No active reservation found for submission: " + submissionId));

        reservation.setStatus(SlotReservationStatus.locked);
        slotReservationRepository.save(reservation);
        log.info("Confirmed (locked) slot reservation for submission {}", submissionId);
    }

    /**
     * Creates a permanently locked slot reservation for an admin reschedule.
     *
     * Unlike reserve(), this bypasses guard rail validation (caller is responsible)
     * and creates the reservation in LOCKED state directly — matching the post-approval
     * state a normal submission reaches after Validator approval.
     *
     * @param submissionId  the submission being rescheduled
     * @param institutionId the institution of the submission
     * @param newSlot       the new scheduled_at time
     */
    @Transactional
    public SlotReservation reserveLockedSlot(UUID submissionId, UUID institutionId, Instant newSlot) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found: " + submissionId));
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new IllegalArgumentException("Institution not found: " + institutionId));

        slotReservationRepository.releaseBySubmissionId(submissionId);

        SlotReservation reservation = new SlotReservation();
        reservation.setSubmission(submission);
        reservation.setInstitution(institution);
        reservation.setScheduledAt(newSlot);
        reservation.setStatus(SlotReservationStatus.locked);

        try {
            return slotReservationRepository.save(reservation);
        } catch (DataIntegrityViolationException ex) {
            throw new SlotAlreadyTakenException(
                    "This slot was just taken by another submission. Please choose a different time.");
        }
    }

    /**
     * GR-T2: Releases all stale held reservations.
     *
     * A reservation is stale when the linked submission: - Is still in DRAFT
     * state - Has not been updated in the last 7 days
     *
     * This method is called by the GR-T2 @Scheduled cron job (to be created in
     * the schedule package). It runs daily.
     *
     * @return list of submission IDs whose slots were released
     */
    @Transactional
    public List<UUID> releaseStaleHeldReservations() {
        Instant cutoff = Instant.now().minus(STALE_DRAFT_THRESHOLD);
        List<SlotReservation> stale = slotReservationRepository.findStaleHeldReservations(cutoff);

        List<UUID> released = stale.stream().map(r -> {
            r.setStatus(SlotReservationStatus.released);
            UUID submissionId = r.getSubmission().getId();
            log.info("GR-T2: Released stale slot for submission {} (last updated before {})",
                    submissionId, cutoff);
            return submissionId;
        }).toList();

        slotReservationRepository.saveAll(stale);
        return released;
    }
}
