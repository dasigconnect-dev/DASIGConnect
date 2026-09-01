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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dasigconnect.backend.model.dto.common.ApiResponse;
import com.dasigconnect.backend.model.dto.submission.AttachAssetDto;
import com.dasigconnect.backend.model.dto.submission.AttachMediaDto;
import com.dasigconnect.backend.model.dto.submission.SignedUploadUrlRequest;
import com.dasigconnect.backend.model.dto.submission.SignedUploadUrlResponse;
import com.dasigconnect.backend.model.dto.submission.SubmissionMediaOrderDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionResponseDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionSummaryDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionUpdateDto;
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
     * Active queue (default): PENDING + IN_REVIEW sorted by scheduledAt ASC.
     * History (?history=true): all non-draft submissions outside the active queue,
     * sorted by scheduledAt DESC. Used by the History tab in the validation UI.
     */
    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<SubmissionSummaryDto>>> getQueue(
            @RequestParam(defaultValue = "false") boolean history,
            @AuthenticationPrincipal JwtUserDetails caller) {
        return ResponseEntity.ok(ApiResponse.success(
            history ? validationService.getHistory(caller) : validationService.getQueue(caller)
        ));
    }

    /**
     * GET /api/v1/validation/{id}/lock
     * Read-only lock status check — does not acquire or extend anything. Used by the
     * frontend to restore lock UI state after a page refresh. data is null if unlocked.
     */
    @GetMapping("/{id}/lock")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<ReviewLockDto>> getLockStatus(@PathVariable UUID id) {
        ReviewLockDto dto = reviewLockService.getActiveLock(id).map(ReviewLockDto::from).orElse(null);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    /**
     * POST /api/v1/validation/{id}/lock
     * Acquires a review lock for a submission. Idempotent if caller already holds it.
     * Returns 409 if another reviewer holds an active lock.
     */
    @PostMapping("/{id}/lock")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<ReviewLockDto>> acquireLock(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails caller) {
        ReviewLock lock = reviewLockService.acquire(id, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(ReviewLockDto.from(lock)));
    }

    /**
     * DELETE /api/v1/validation/{id}/lock
     * Releases the review lock. Reverts IN_REVIEW → PENDING if no action was taken.
     */
    @DeleteMapping("/{id}/lock")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
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
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
    public ResponseEntity<Void> approve(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails caller) {
        validationService.approve(id, caller);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/validation/{id}/edit
     * A9: applies a direct inline edit to any editable field. The submission stays
     * IN_REVIEW — the Moderator must still choose a terminal action afterwards.
     */
    @PostMapping("/{id}/edit")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
    public ResponseEntity<Void> edit(
            @PathVariable UUID id,
            @Valid @RequestBody SubmissionUpdateDto dto,
            @AuthenticationPrincipal JwtUserDetails caller) {
        validationService.edit(id, dto, caller);
        return ResponseEntity.noContent().build();
    }

    // ── A9: media edits during review (admin only) ──────────────────────────

    @PostMapping("/{id}/media/upload-url")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<SignedUploadUrlResponse>> reviewMediaUploadUrl(
            @PathVariable UUID id,
            @Valid @RequestBody SignedUploadUrlRequest dto,
            @AuthenticationPrincipal JwtUserDetails caller) {
        return ResponseEntity.ok(ApiResponse.success(validationService.reviewMediaUploadUrl(id, dto, caller)));
    }

    @PostMapping("/{id}/media")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<SubmissionResponseDto>> attachReviewMedia(
            @PathVariable UUID id,
            @Valid @RequestBody AttachMediaDto dto,
            @AuthenticationPrincipal JwtUserDetails caller) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(validationService.attachReviewMedia(id, dto, caller)));
    }

    @PostMapping("/{id}/assets")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<SubmissionResponseDto>> attachReviewLibraryAsset(
            @PathVariable UUID id,
            @Valid @RequestBody AttachAssetDto dto,
            @AuthenticationPrincipal JwtUserDetails caller) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(validationService.attachReviewLibraryAsset(id, dto.getMediaAssetId(), caller)));
    }

    @DeleteMapping("/{id}/assets/{assetId}")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
    public ResponseEntity<Void> detachReviewMedia(
            @PathVariable UUID id,
            @PathVariable UUID assetId,
            @AuthenticationPrincipal JwtUserDetails caller) {
        validationService.detachReviewMedia(id, assetId, caller);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/media/order")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<SubmissionResponseDto>> reorderReviewMedia(
            @PathVariable UUID id,
            @Valid @RequestBody SubmissionMediaOrderDto dto,
            @AuthenticationPrincipal JwtUserDetails caller) {
        return ResponseEntity.ok(ApiResponse.success(validationService.reorderReviewMedia(id, dto, caller)));
    }

    /**
     * POST /api/v1/validation/{id}/revise
     * Requests revision: transitions to NEEDS_REVISION and releases slot.
     * Body: { remarks: string (10–1000 chars, BR-VAL-02) }
     */
    @PostMapping("/{id}/revise")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
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
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
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
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<ValidationLogDto>>> getLog(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails caller) {
        List<ValidationLogDto> log = validationLogRepository
                .findBySubmissionIdOrderByCreatedAtDesc(id)
                .stream()
                .map(ValidationLogDto::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(log));
    }
}
