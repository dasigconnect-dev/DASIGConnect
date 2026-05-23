package com.dasigconnect.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dasigconnect.backend.model.dto.submission.SubmissionSummaryDto;
import com.dasigconnect.backend.model.dto.validation.RejectionRequestDto;
import com.dasigconnect.backend.model.dto.validation.ReviewLockDto;
import com.dasigconnect.backend.model.dto.validation.RevisionRequestDto;
import com.dasigconnect.backend.model.dto.validation.ValidationLogDto;
import com.dasigconnect.backend.model.entity.ReviewLock;
import com.dasigconnect.backend.repository.ValidationLogRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.ReviewLockService;
import com.dasigconnect.backend.service.ValidationService;

import jakarta.validation.Valid;

/**
 * REST endpoints for UC-2.1: Content Validation and Approval.
 * Base path: /api/v1/validation
 */
@RestController
@RequestMapping("/api/v1/validation")
public class ValidationController {

    private final ValidationService validationService;
    private final ReviewLockService reviewLockService;
    private final ValidationLogRepository validationLogRepository;

    public ValidationController(
            ValidationService validationService,
            ReviewLockService reviewLockService,
            ValidationLogRepository validationLogRepository) {
        this.validationService = validationService;
        this.reviewLockService = reviewLockService;
        this.validationLogRepository = validationLogRepository;
    }

    /**
     * GET /api/v1/validation/queue
     * Returns all PENDING + IN_REVIEW submissions for the caller's institution,
     * sorted by scheduledAt ASC.
     */
    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('VALIDATOR', 'ADMINISTRATOR')")
    public ResponseEntity<List<SubmissionSummaryDto>> getQueue(
            @AuthenticationPrincipal JwtUserDetails caller) {
        return ResponseEntity.ok(validationService.getQueue(caller));
    }

    /**
     * POST /api/v1/validation/{id}/lock
     * Acquires a review lock for a submission. Idempotent if caller already holds it.
     * Returns 409 if another validator holds an active lock.
     */
    @PostMapping("/{id}/lock")
    @PreAuthorize("hasAnyRole('VALIDATOR', 'ADMINISTRATOR')")
    public ResponseEntity<ReviewLockDto> acquireLock(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails caller) {
        ReviewLock lock = reviewLockService.acquire(id, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(ReviewLockDto.from(lock));
    }

    /**
     * DELETE /api/v1/validation/{id}/lock
     * Releases the review lock. Reverts IN_REVIEW → PENDING if no action was taken.
     */
    @DeleteMapping("/{id}/lock")
    @PreAuthorize("hasAnyRole('VALIDATOR', 'ADMINISTRATOR')")
    public ResponseEntity<Void> releaseLock(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails caller) {
        reviewLockService.release(id, caller);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/validation/{id}/approve
     * Approves a submission: transitions to SCHEDULED and confirms slot reservation.
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('VALIDATOR', 'ADMINISTRATOR')")
    public ResponseEntity<Void> approve(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails caller) {
        validationService.approve(id, caller);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/validation/{id}/revise
     * Requests revision: transitions to NEEDS_REVISION and releases slot.
     * Body: { remarks: string (10–1000 chars, BR-VAL-02) }
     */
    @PostMapping("/{id}/revise")
    @PreAuthorize("hasAnyRole('VALIDATOR', 'ADMINISTRATOR')")
    public ResponseEntity<Void> requestRevision(
            @PathVariable UUID id,
            @Valid @RequestBody RevisionRequestDto body,
            @AuthenticationPrincipal JwtUserDetails caller) {
        validationService.requestRevision(id, body.getRemarks(), caller);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/validation/{id}/reject
     * Rejects a submission: transitions to REJECTED and releases slot.
     * Body: { reasonCode: string (BR-VAL-03), notes: string? (required if OTHER) }
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('VALIDATOR', 'ADMINISTRATOR')")
    public ResponseEntity<Void> reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectionRequestDto body,
            @AuthenticationPrincipal JwtUserDetails caller) {
        validationService.reject(id, body.getReasonCode(), body.getNotes(), caller);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/v1/validation/{id}/log
     * Returns the validation audit log for a submission, newest first.
     */
    @GetMapping("/{id}/log")
    @PreAuthorize("hasAnyRole('VALIDATOR', 'ADMINISTRATOR')")
    public ResponseEntity<List<ValidationLogDto>> getLog(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails caller) {
        List<ValidationLogDto> log = validationLogRepository
                .findBySubmissionIdOrderByCreatedAtDesc(id)
                .stream()
                .map(ValidationLogDto::from)
                .toList();
        return ResponseEntity.ok(log);
    }
}
