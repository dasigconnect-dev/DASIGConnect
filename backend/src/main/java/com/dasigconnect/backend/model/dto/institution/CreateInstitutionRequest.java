package com.dasigconnect.backend.model.dto.institution;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/admin/institutions.
 *
 * Only Administrators may call this endpoint (enforced via @PreAuthorize in
 * InstitutionController). Spring's @Valid annotation triggers the constraint
 * checks below before the request reaches the service layer.
 *
 * IMPORTANT — field mapping to M1's Institution entity: institutionCode (this
 * DTO) → Institution.code (entity field / DB column "code") Call
 * institution.setCode(request.getInstitutionCode()) in InstitutionService.
 *
 * Example JSON body:
 * <pre>
 * {
 *   "name": "Cebu Institute of Technology - University",
 *   "institutionCode": "CIT-U"
 * }
 * </pre>
 */
public class CreateInstitutionRequest {

    /**
     * Full display name of the institution. Stored in Institution.name (VARCHAR
     * 255).
     */
    @NotBlank(message = "Institution name is required.")
    @Size(min = 2, max = 255, message = "Institution name must be between 2 and 255 characters.")
    private String name;

    /**
     * Short, unique identifier for the institution. Maps to Institution.code
     * (VARCHAR 50, UNIQUE). Used in workspace routing:
     * /workspace/{institutionCode}/dashboard. Allowed characters: uppercase
     * letters, digits, and hyphens only. Examples: "CIT-U", "SU", "ADDU"
     */
    @NotBlank(message = "Institution code is required.")
    @Size(min = 2, max = 50, message = "Institution code must be between 2 and 50 characters.")
    @Pattern(
            regexp = "^[A-Z0-9\\-]+$",
            message = "Institution code may only contain uppercase letters, digits, and hyphens (e.g. CIT-U)."
    )
    private String institutionCode;

    // ── Constructors ──────────────────────────────────────────────────────────
    public CreateInstitutionRequest() {
    }

    public CreateInstitutionRequest(String name, String institutionCode) {
        this.name = name;
        this.institutionCode = institutionCode;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInstitutionCode() {
        return institutionCode;
    }

    public void setInstitutionCode(String institutionCode) {
        this.institutionCode = institutionCode;
    }

    @Override
    public String toString() {
        return "CreateInstitutionRequest{"
                + "name='" + name + '\''
                + ", institutionCode='" + institutionCode + '\''
                + '}';
    }
}
