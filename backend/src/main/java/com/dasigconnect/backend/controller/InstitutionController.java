package com.dasigconnect.backend.controller;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.dasigconnect.backend.model.dto.common.ApiResponse;
import com.dasigconnect.backend.model.dto.institution.CreateInstitutionRequest;
import com.dasigconnect.backend.model.dto.institution.InstitutionDto;
import com.dasigconnect.backend.model.dto.institution.UpdateInstitutionRequest;
import com.dasigconnect.backend.service.InstitutionService;

import jakarta.validation.Valid;

/**
 * REST endpoints for institution management (UC-1.2).
 *
 * All endpoints are restricted to Moderator roles via @PreAuthorize. The JWT
 * filter (M1's JwtAuthenticationFilter) populates the SecurityContext with
 * ROLE_ADMIN before these methods are reached.
 *
 * NOTE: M1's UserRole enum uses lowercase ("admin"), but Spring
 * Security expects "ROLE_ADMIN" (uppercase). The filter applies
 * .toUpperCase() when creating the GrantedAuthority, so
 * @PreAuthorize works with the uppercase authorities created from the role enum.
 *
 * Base path: /api/v1/institutions Legacy alias: /api/v1/admin/institutions
 */
@RestController
@RequestMapping({"/api/v1/institutions", "/api/v1/admin/institutions"})
@PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
public class InstitutionController {

    private final InstitutionService institutionService;

    public InstitutionController(InstitutionService institutionService) {
        this.institutionService = institutionService;
    }

    /**
     * POST /api/admin/institutions
     *
     * Creates a new institution and provisions its isolated workspace. Returns
     * 201 Created with the InstitutionDto in the response body.
     *
     * Request body (JSON): { "name": "Cebu Institute of Technology -
     * University", "institutionCode": "CIT-U" }
     *
     * Error responses: 400 Bad Request — validation failure (blank name,
     * invalid code format) 400 Bad Request — institution code already exists
     * 403 Forbidden — caller is not an ADMIN
     */
    @PostMapping
    public ResponseEntity<ApiResponse<InstitutionDto>> createInstitution(
            @Valid @RequestBody CreateInstitutionRequest request) {
        InstitutionDto created = institutionService.createInstitution(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    /**
     * GET /api/admin/institutions/{institutionId}
     *
     * Retrieves institution details by UUID. Returns 404 (not 403) for
     * missing/inaccessible institutions per SRS 3.4.6.4.
     *
     * Error responses: 404 Not Found — institution does not exist 403 Forbidden
     * — caller is not an ADMIN
     */
    @GetMapping("/{institutionId}")
    public ResponseEntity<ApiResponse<InstitutionDto>> getInstitution(
            @PathVariable UUID institutionId) {
        InstitutionDto dto = institutionService.getInstitution(institutionId);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    /**
     * GET /api/admin/institutions
     *
     * Returns all institutions for Moderator dropdowns and management.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<InstitutionDto>>> listInstitutions() {
        return ResponseEntity.ok(ApiResponse.success(institutionService.listInstitutions()));
    }

    /**
     * GET /api/v1/institutions/public
     *
     * Returns all institutions for public display on the login page.
     */
    @GetMapping("/public")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<List<InstitutionDto>>> listPublicInstitutions() {
        return ResponseEntity.ok(ApiResponse.success(institutionService.listInstitutions()));
    }

    @PutMapping(value = "/{institutionId}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<InstitutionDto>> updateLogo(
            @PathVariable UUID institutionId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(institutionService.updateLogo(institutionId, file)));
    }

    @GetMapping("/{institutionId}/logo")
    @PreAuthorize("permitAll()")
    public ResponseEntity<byte[]> getLogo(@PathVariable UUID institutionId) {
        InstitutionService.InstitutionLogo logo = institutionService.getLogo(institutionId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(logo.contentType()))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .body(logo.data());
    }

    /**
     * PUT /api/v1/institutions/{institutionId}
     *
     * A1: Updates the institution's name and email domain. Returns 400 if the
     * new name or domain conflicts with another institution.
     */
    @PutMapping("/{institutionId}")
    public ResponseEntity<ApiResponse<InstitutionDto>> updateInstitution(
            @PathVariable UUID institutionId,
            @Valid @RequestBody UpdateInstitutionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(institutionService.updateInstitution(institutionId, request)));
    }

    /**
     * PATCH /api/v1/institutions/{institutionId}/deactivate
     *
     * A2: Admin-initiated deactivation. Sets status to INACTIVE. Historical
     * data retained; new invitations blocked.
     */
    @PatchMapping("/{institutionId}/deactivate")
    public ResponseEntity<ApiResponse<InstitutionDto>> deactivateInstitution(@PathVariable UUID institutionId) {
        return ResponseEntity.ok(ApiResponse.success(institutionService.deactivateInstitution(institutionId)));
    }

    /**
     * PATCH /api/v1/institutions/{institutionId}/reactivate
     *
     * A3: Admin-initiated reactivation. Restores ACTIVE or PENDING status
     * depending on whether the institution has active moderators.
     */
    @PatchMapping("/{institutionId}/reactivate")
    public ResponseEntity<ApiResponse<InstitutionDto>> reactivateInstitution(@PathVariable UUID institutionId) {
        return ResponseEntity.ok(ApiResponse.success(institutionService.reactivateInstitution(institutionId)));
    }

    /**
     * DELETE /api/v1/institutions/{institutionId}
     *
     * Permanently removes an institution. Blocked with 400 if the institution
     * still has users or submissions.
     */
    @DeleteMapping("/{institutionId}")
    public ResponseEntity<Void> deleteInstitution(@PathVariable UUID institutionId) {
        institutionService.deleteInstitution(institutionId);
        return ResponseEntity.noContent().build();
    }
}
