package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.model.dto.media.DuplicateCandidatePairDto;
import com.dasigconnect.backend.model.dto.media.DuplicateDecisionRequestDto;
import com.dasigconnect.backend.model.dto.media.DuplicateDecisionResultDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.MediaDuplicateReviewService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC-4.12 Phase 7F: human duplicate-review workspace. Base path: /api/v1/media-duplicates.
 */
@RestController
@RequestMapping("/api/v1/media-duplicates")
public class MediaDuplicateController {

    private final MediaDuplicateReviewService duplicateReviewService;

    public MediaDuplicateController(MediaDuplicateReviewService duplicateReviewService) {
        this.duplicateReviewService = duplicateReviewService;
    }

    /** Pending (default) or reviewed duplicate candidate pairs, tenant-scoped. */
    @GetMapping
    @PreAuthorize("hasAnyRole('VALIDATOR', 'ADMINISTRATOR')")
    public ResponseEntity<List<DuplicateCandidatePairDto>> list(
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(defaultValue = "pending") String status,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(duplicateReviewService.listCandidates(institutionId, status, user));
    }

    /** Record a duplicate adjudication (append-only; never deletes an asset). */
    @PostMapping("/{candidateId:[0-9a-fA-F\\-]{36}}/decision")
    @PreAuthorize("hasAnyRole('VALIDATOR', 'ADMINISTRATOR')")
    public ResponseEntity<DuplicateDecisionResultDto> decide(
            @PathVariable UUID candidateId,
            @Valid @RequestBody DuplicateDecisionRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.status(201).body(duplicateReviewService.decide(candidateId, dto, user));
    }
}
