package com.dasigconnect.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dasigconnect.backend.model.dto.exception.DirectPostRequestDto;
import com.dasigconnect.backend.model.dto.exception.DirectPostResponseDto;
import com.dasigconnect.backend.model.dto.exception.OAuthInitResponseDto;
import com.dasigconnect.backend.model.dto.exception.OverrideDenyRequestDto;
import com.dasigconnect.backend.model.dto.exception.OverrideRequestDto;
import com.dasigconnect.backend.model.dto.exception.OverrideSuggestRequestDto;
import com.dasigconnect.backend.model.dto.exception.ResolutionCountsDto;
import com.dasigconnect.backend.model.dto.exception.TimeoutEscalationDto;
import com.dasigconnect.backend.model.dto.exception.TimeoutRejectRequestDto;
import com.dasigconnect.backend.model.dto.exception.TokenStatusDto;
import com.dasigconnect.backend.model.entity.OverrideRequestDecision;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.repository.OverrideRequestRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.DirectPostService;
import com.dasigconnect.backend.service.OverrideRequestService;
import com.dasigconnect.backend.service.TokenManagementService;
import com.dasigconnect.backend.service.ValidationTimeoutService;

/**
 * UC-3.5 Administrator Exception Handling — umbrella controller for all
 * Resolution Center exception-handling endpoints.
 * Base path: /api/admin/resolution
 */
@RestController
@RequestMapping("/api/admin/resolution")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class ExceptionHandlingController {

    private final SubmissionRepository submissionRepository;
    private final OverrideRequestRepository overrideRequestRepository;
    private final ValidationTimeoutService validationTimeoutService;
    private final OverrideRequestService overrideRequestService;
    private final DirectPostService directPostService;
    private final TokenManagementService tokenManagementService;

    public ExceptionHandlingController(
            SubmissionRepository submissionRepository,
            OverrideRequestRepository overrideRequestRepository,
            ValidationTimeoutService validationTimeoutService,
            OverrideRequestService overrideRequestService,
            DirectPostService directPostService,
            TokenManagementService tokenManagementService) {
        this.submissionRepository = submissionRepository;
        this.overrideRequestRepository = overrideRequestRepository;
        this.validationTimeoutService = validationTimeoutService;
        this.overrideRequestService = overrideRequestService;
        this.directPostService = directPostService;
        this.tokenManagementService = tokenManagementService;
    }

    // ── Tab badge counts ──────────────────────────────────────────────────────

    @GetMapping("/counts")
    public ResponseEntity<ResolutionCountsDto> getCounts() {
        long failures = submissionRepository.findPublishFailures().size();
        long timeouts = validationTimeoutService.getEscalated().size();
        long overrides = overrideRequestRepository.countByDecision(OverrideRequestDecision.pending);
        return ResponseEntity.ok(new ResolutionCountsDto(failures, timeouts, overrides));
    }

    // ── Category A — API Failures (retry handled by existing ResolutionController) ──

    // Note: Category A retry and manual-publish actions are already handled by
    // the existing ResolutionController at /api/v1/resolution. This controller
    // adds the failure list via the counts endpoint above.

    // ── Category B — Validation Timeouts ─────────────────────────────────────

    @GetMapping("/timeouts")
    public ResponseEntity<List<TimeoutEscalationDto>> listTimeouts() {
        return ResponseEntity.ok(validationTimeoutService.getEscalated());
    }

    @PostMapping("/timeouts/{id}/approve")
    public ResponseEntity<Void> approveTimeout(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails admin) {
        validationTimeoutService.approve(id, admin);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/timeouts/{id}/defer")
    public ResponseEntity<Void> deferTimeout(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails admin) {
        validationTimeoutService.defer(id, admin);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/timeouts/{id}/reject")
    public ResponseEntity<Void> rejectTimeout(
            @PathVariable UUID id,
            @RequestBody TimeoutRejectRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails admin) {
        validationTimeoutService.reject(id, dto.getReasonCode(), dto.getNotes(), admin);
        return ResponseEntity.noContent().build();
    }

    // ── Category C — Override Requests ───────────────────────────────────────

    @GetMapping("/overrides")
    public ResponseEntity<List<OverrideRequestDto>> listOverrides() {
        return ResponseEntity.ok(overrideRequestService.getPendingRequests());
    }

    @PostMapping("/overrides/{id}/approve")
    public ResponseEntity<Void> approveOverride(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails admin) {
        overrideRequestService.approve(id, admin);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/overrides/{id}/suggest")
    public ResponseEntity<Void> suggestOverride(
            @PathVariable UUID id,
            @RequestBody OverrideSuggestRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails admin) {
        overrideRequestService.suggest(id, dto.getSuggestedSlot(), admin);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/overrides/{id}/deny")
    public ResponseEntity<Void> denyOverride(
            @PathVariable UUID id,
            @RequestBody OverrideDenyRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails admin) {
        overrideRequestService.deny(id, dto.getReason(), admin);
        return ResponseEntity.noContent().build();
    }

    // ── Category D — Direct Post ──────────────────────────────────────────────

    @PostMapping("/direct-post")
    public ResponseEntity<DirectPostResponseDto> createDirectPost(
            @RequestBody DirectPostRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails admin) {

        Submission s = directPostService.createDirectPost(dto, admin);
        boolean hadGrH1Warning = directPostService.hasGrH1Conflict(dto.getInstitutionId(),
                dto.isPublishImmediately() ? java.time.Instant.now() : dto.getScheduledAt());

        // For immediate posts, trigger Facebook publish asynchronously after the transaction commits.
        // publishImmediately() uses Propagation.NOT_SUPPORTED so it runs outside any transaction.
        if (dto.isPublishImmediately()) {
            directPostService.publishImmediately(s.getId());
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new DirectPostResponseDto(s.getId(), s.getStatus().name(), hadGrH1Warning));
    }

    // ── Category E — Token Management ────────────────────────────────────────

    @GetMapping("/tokens")
    public ResponseEntity<List<TokenStatusDto>> listTokens() {
        return ResponseEntity.ok(tokenManagementService.getAllTokenStatuses());
    }

    @GetMapping("/tokens/{tokenId}/oauth-init")
    public ResponseEntity<OAuthInitResponseDto> oauthInit(
            @PathVariable UUID tokenId,
            @AuthenticationPrincipal JwtUserDetails admin) {
        return ResponseEntity.ok(tokenManagementService.initOAuth(tokenId, admin));
    }

    @GetMapping("/tokens/oauth-callback")
    public ResponseEntity<String> oauthCallback(
            @RequestParam String code,
            @RequestParam String state) {
        String message = tokenManagementService.handleCallback(code, state);
        return ResponseEntity.ok(message);
    }
}
