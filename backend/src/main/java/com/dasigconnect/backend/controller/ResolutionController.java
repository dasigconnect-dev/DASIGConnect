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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.model.dto.resolution.FailedPublicationDto;
import com.dasigconnect.backend.model.dto.resolution.ManualPublishCompleteDto;
import com.dasigconnect.backend.model.dto.resolution.ManualPublishDetailDto;
import com.dasigconnect.backend.model.entity.PublicationAttempt;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionMediaAsset;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.repository.PublicationAttemptRepository;
import com.dasigconnect.backend.repository.SubmissionMediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.ManualPublishingService;

/**
 * UC-3.4 Resolution Center — administrator-only endpoints for handling
 * PUBLISH_FAILED and (during token failure) SCHEDULED submissions.
 * Base path: /api/v1/resolution
 */
@RestController
@RequestMapping("/api/v1/resolution")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class ResolutionController {

    private final SubmissionRepository submissionRepository;
    private final PublicationAttemptRepository publicationAttemptRepository;
    private final SubmissionMediaAssetRepository submissionMediaAssetRepository;
    private final ManualPublishingService manualPublishingService;

    public ResolutionController(
            SubmissionRepository submissionRepository,
            PublicationAttemptRepository publicationAttemptRepository,
            SubmissionMediaAssetRepository submissionMediaAssetRepository,
            ManualPublishingService manualPublishingService) {
        this.submissionRepository = submissionRepository;
        this.publicationAttemptRepository = publicationAttemptRepository;
        this.submissionMediaAssetRepository = submissionMediaAssetRepository;
        this.manualPublishingService = manualPublishingService;
    }

    @GetMapping("/failures")
    public ResponseEntity<List<FailedPublicationDto>> listFailures() {
        List<Submission> failures = submissionRepository.findPublishFailures();
        List<FailedPublicationDto> dtos = failures.stream()
                .map(s -> {
                    PublicationAttempt last = publicationAttemptRepository
                            .findTopBySubmissionIdOrderByAttemptedAtDesc(s.getId())
                            .orElse(null);
                    return FailedPublicationDto.from(s, last);
                })
                .toList();
        return ResponseEntity.ok(dtos);
    }

    /** Returns the full post-content detail needed for the manual publishing panel. */
    @GetMapping("/{id}")
    public ResponseEntity<ManualPublishDetailDto> getDetail(@PathVariable UUID id) {
        Submission s = submissionRepository.findByIdWithInstitution(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found."));

        if (s.getStatus() != SubmissionStatus.publish_failed
                && s.getStatus() != SubmissionStatus.scheduled) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Submission is not eligible for manual publishing.");
        }

        List<SubmissionMediaAsset> media =
                submissionMediaAssetRepository.findBySubmissionIdWithMediaAsset(id);
        return ResponseEntity.ok(ManualPublishDetailDto.from(s, media));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<Void> retry(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails admin) {
        manualPublishingService.retry(id, admin);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/manual-publish/start")
    public ResponseEntity<Void> startManualPublish(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails admin) {
        manualPublishingService.start(id, admin);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/manual-publish/complete")
    public ResponseEntity<Void> completeManualPublish(
            @PathVariable UUID id,
            @RequestBody ManualPublishCompleteDto dto,
            @AuthenticationPrincipal JwtUserDetails admin) {
        manualPublishingService.complete(id, dto, admin);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/manual-publish/cancel")
    public ResponseEntity<Void> cancelManualPublish(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails admin) {
        manualPublishingService.cancel(id, admin);
        return ResponseEntity.noContent().build();
    }
}
