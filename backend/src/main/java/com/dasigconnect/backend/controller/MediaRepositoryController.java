package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.model.dto.media.RepositoryHealthDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.RepositoryHealthService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC-4.12 Phase 7C: repository-level reporting. Base path: /api/v1/media-repository.
 */
@RestController
@RequestMapping("/api/v1/media-repository")
public class MediaRepositoryController {

    private final RepositoryHealthService repositoryHealthService;

    public MediaRepositoryController(RepositoryHealthService repositoryHealthService) {
        this.repositoryHealthService = repositoryHealthService;
    }

    /**
     * Tenant-scoped Repository Health aggregates. Validators see their own institution;
     * administrators see the whole network, or a single institution when {@code institutionId}
     * is supplied.
     */
    @GetMapping("/health")
    @PreAuthorize("hasAnyRole('VALIDATOR', 'ADMINISTRATOR')")
    public ResponseEntity<RepositoryHealthDto> health(
            @RequestParam(required = false) UUID institutionId,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(repositoryHealthService.health(institutionId, user));
    }
}
