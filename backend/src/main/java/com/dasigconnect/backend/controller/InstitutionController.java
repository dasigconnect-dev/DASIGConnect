package com.dasigconnect.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dasigconnect.backend.model.dto.institution.CreateInstitutionRequest;
import com.dasigconnect.backend.model.dto.institution.InstitutionDto;
import com.dasigconnect.backend.service.InstitutionService;

import jakarta.validation.Valid;

/**
 * REST endpoints for institution management (UC-1.2).
 *
 * All endpoints are restricted to ADMINISTRATOR role via @PreAuthorize. The JWT
 * filter (M1's JwtAuthenticationFilter) populates the SecurityContext with
 * ROLE_ADMINISTRATOR before these methods are reached.
 *
 * NOTE: M1's UserRole enum uses lowercase ("administrator"), but Spring
 * Security expects "ROLE_ADMINISTRATOR" (uppercase). The filter applies
 * .toUpperCase() when creating the GrantedAuthority, so
 * @PreAuthorize("hasRole('ADMINISTRATOR')") works correctly with M1's enum.
 *
 * Base path: /api/v1/institutions
 * Legacy alias: /api/v1/admin/institutions
 */
@RestController
@RequestMapping({"/api/v1/institutions", "/api/v1/admin/institutions"})
@PreAuthorize("hasRole('ADMINISTRATOR')")
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
     * 403 Forbidden — caller is not an ADMINISTRATOR
     */
    @PostMapping
    public ResponseEntity<InstitutionDto> createInstitution(
            @Valid @RequestBody CreateInstitutionRequest request) {
        InstitutionDto created = institutionService.createInstitution(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * GET /api/admin/institutions/{institutionId}
     *
     * Retrieves institution details by UUID. Returns 404 (not 403) for
     * missing/inaccessible institutions per SRS 3.4.6.4.
     *
     * Error responses: 404 Not Found — institution does not exist 403 Forbidden
     * — caller is not an ADMINISTRATOR
     */
    @GetMapping("/{institutionId}")
    public ResponseEntity<InstitutionDto> getInstitution(
            @PathVariable UUID institutionId) {
        InstitutionDto dto = institutionService.getInstitution(institutionId);
        return ResponseEntity.ok(dto);
    }

    /**
     * GET /api/admin/institutions
     *
     * Returns all institutions for Administrator dropdowns and management.
     */
    @GetMapping
    public ResponseEntity<List<InstitutionDto>> listInstitutions() {
        return ResponseEntity.ok(institutionService.listInstitutions());
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
