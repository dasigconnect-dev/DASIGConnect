package com.dasigconnect.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dasigconnect.backend.model.dto.common.ApiResponse;
import com.dasigconnect.backend.model.dto.exception.OverrideDenyRequestDto;
import com.dasigconnect.backend.model.dto.exception.OverrideRequestCreateDto;
import com.dasigconnect.backend.model.dto.exception.OverrideRequestDto;
import com.dasigconnect.backend.model.dto.exception.OverrideSuggestRequestDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.OverrideRequestService;

import jakarta.validation.Valid;

/**
 * Guard-rail override request triage. A contributor who is hard-blocked while
 * scheduling submits an override request (see the submission flow); an
 * administrator resolves it here — approve (bypass the rule, reserve the slot),
 * suggest a compliant alternative, or deny.
 *
 * <p>Was previously served by the (removed) Resolution Center's
 * {@code /admin/resolution/overrides/*}.
 */
@RestController
@RequestMapping("/api/v1/override-requests")
@PreAuthorize("hasRole('ADMIN')")
public class OverrideRequestController {

    private final OverrideRequestService overrideRequestService;

    public OverrideRequestController(OverrideRequestService overrideRequestService) {
        this.overrideRequestService = overrideRequestService;
    }

    /** Contributor asks for an override on a hard-blocked slot. */
    @PostMapping
    @PreAuthorize("hasRole('CONTRIBUTOR')")
    public ResponseEntity<ApiResponse<OverrideRequestDto>> create(
            @Valid @RequestBody OverrideRequestCreateDto dto,
            @AuthenticationPrincipal JwtUserDetails caller) {
        return ResponseEntity.status(201)
                .body(ApiResponse.success(overrideRequestService.create(dto, caller)));
    }

    /** The submission's pending override request, if any — drives the schedule-step status chip. */
    @GetMapping("/for-submission/{submissionId}")
    @PreAuthorize("hasAnyRole('CONTRIBUTOR', 'MODERATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<OverrideRequestDto>>> forSubmission(@PathVariable UUID submissionId) {
        return ResponseEntity.ok(ApiResponse.success(overrideRequestService.forSubmission(submissionId)));
    }

    /** Pending override requests across the network, soonest requested slot first. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<OverrideRequestDto>>> listPending() {
        return ResponseEntity.ok(ApiResponse.success(overrideRequestService.getPendingRequests()));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails admin) {
        overrideRequestService.approve(id, admin);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/suggest")
    public ResponseEntity<Void> suggest(
            @PathVariable UUID id,
            @RequestBody OverrideSuggestRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails admin) {
        overrideRequestService.suggest(id, dto.getSuggestedSlot(), admin);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/deny")
    public ResponseEntity<Void> deny(
            @PathVariable UUID id,
            @RequestBody OverrideDenyRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails admin) {
        overrideRequestService.deny(id, dto.getReason(), admin);
        return ResponseEntity.noContent().build();
    }
}
