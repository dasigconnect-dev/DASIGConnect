package com.dasigconnect.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dasigconnect.backend.model.dto.guardrail.GuardRailResult;
import com.dasigconnect.backend.model.dto.submission.AttachAssetDto;
import com.dasigconnect.backend.model.dto.submission.AttachMediaDto;
import com.dasigconnect.backend.model.dto.submission.SignedUploadUrlRequest;
import com.dasigconnect.backend.model.dto.submission.SignedUploadUrlResponse;
import com.dasigconnect.backend.model.dto.submission.SlotEvaluateRequestDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionCreateDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionLookupsDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionResponseDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionSummaryDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionUpdateDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.SubmissionService;

import jakarta.validation.Valid;

/**
 * REST endpoints for UC-1.3: Content Submission and Self-Service Scheduling.
 * Base path: /api/v1/submissions
 */
@RestController
@RequestMapping("/api/v1/submissions")
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    /**
     * GET /api/v1/submissions/lookups Returns form reference data (allowed file
     * types, size limits, guard rail thresholds). Must be declared before /{id}
     * mappings to avoid path ambiguity.
     */
    @GetMapping("/lookups")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SubmissionLookupsDto> lookups() {
        return ResponseEntity.ok(new SubmissionLookupsDto());
    }

    /**
     * GET /api/v1/submissions Lists submissions filtered by the caller's role.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SubmissionSummaryDto>> list(
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(submissionService.list(user));
    }

    /**
     * POST /api/v1/submissions Creates a new DRAFT submission. If scheduledAt
     * is provided, reserves the slot.
     */
    @PostMapping
    @PreAuthorize("hasRole('CONTRIBUTOR')")
    public ResponseEntity<SubmissionResponseDto> create(
            @Valid @RequestBody SubmissionCreateDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(submissionService.create(dto, user));
    }

    /**
     * GET /api/v1/submissions/{id} Returns full submission detail. Accessible
     * by owner, institution validator, or admin.
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SubmissionResponseDto> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(submissionService.get(id, user));
    }

    /**
     * PATCH /api/v1/submissions/{id} Partial update of a DRAFT or
     * NEEDS_REVISION submission. Supports 60-second auto-save.
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('CONTRIBUTOR')")
    public ResponseEntity<SubmissionResponseDto> update(
            @PathVariable UUID id,
            @Valid @RequestBody SubmissionUpdateDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(submissionService.update(id, dto, user));
    }

    /**
     * DELETE /api/v1/submissions/{id} Deletes a DRAFT submission and releases
     * its slot reservation.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CONTRIBUTOR')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails user) {
        submissionService.delete(id, user);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/submissions/{id}/submit Transitions DRAFT → PENDING or
     * NEEDS_REVISION → PENDING. Re-validates guard rails before accepting.
     */
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('CONTRIBUTOR')")
    public ResponseEntity<SubmissionResponseDto> submit(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(submissionService.submit(id, user));
    }

    /**
     * POST /api/v1/submissions/{id}/evaluate-slot Evaluates guard rails for a
     * proposed slot without creating a reservation. Called in real time by the
     * SlotPicker component.
     */
    @PostMapping("/{id}/evaluate-slot")
    @PreAuthorize("hasRole('CONTRIBUTOR')")
    public ResponseEntity<GuardRailResult> evaluateSlot(
            @PathVariable UUID id,
            @Valid @RequestBody SlotEvaluateRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(submissionService.evaluateSlot(dto, user));
    }

    /**
     * POST /api/v1/submissions/{id}/media/upload-url Returns a Supabase signed
     * upload URL so the browser can upload directly without needing the anon
     * key or storage RLS policies.
     */
    @PostMapping("/{id}/media/upload-url")
    @PreAuthorize("hasRole('CONTRIBUTOR')")
    public ResponseEntity<SignedUploadUrlResponse> getSignedUploadUrl(
            @PathVariable UUID id,
            @Valid @RequestBody SignedUploadUrlRequest dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(submissionService.createSignedUploadUrl(id, dto, user));
    }

    /**
     * POST /api/v1/submissions/{id}/media Attaches a new media file to a
     * submission. The frontend uploads the file directly to Supabase Storage
     * and passes the resulting storageUrl, fileName, fileType, and
     * fileSizeBytes here.
     */
    @PostMapping("/{id}/media")
    @PreAuthorize("hasRole('CONTRIBUTOR')")
    public ResponseEntity<SubmissionResponseDto> attachMedia(
            @PathVariable UUID id,
            @Valid @RequestBody AttachMediaDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(submissionService.attachMedia(id, dto, user));
    }

    /**
     * POST /api/v1/submissions/{id}/assets Attaches an existing media library
     * asset to a submission. Used by the media recommendation panel and
     * AssetPickerModal.
     */
    @PostMapping("/{id}/assets")
    @PreAuthorize("hasRole('CONTRIBUTOR')")
    public ResponseEntity<SubmissionResponseDto> attachAsset(
            @PathVariable UUID id,
            @Valid @RequestBody AttachAssetDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(submissionService.attachAsset(id, dto, user));
    }
}
