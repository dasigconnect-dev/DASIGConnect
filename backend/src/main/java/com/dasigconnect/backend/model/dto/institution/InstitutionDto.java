package com.dasigconnect.backend.model.dto.institution;

import java.util.UUID;

import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.InstitutionStatus;

/**
 * Read-only response DTO for institution data.
 *
 * Returned by: - POST /api/admin/institutions (after creation) - GET
 * /api/admin/institutions/{id}
 *
 * Never expose the raw Institution entity directly from controllers — always
 * convert via InstitutionDto.from(institution).
 *
 * NOTE: id is UUID (not Long) to match M1's entity. NOTE: institutionCode maps
 * to Institution.getCode() — M1 named the column/field "code", but we expose it
 * as "institutionCode" in the API response for clarity to the frontend (M5).
 */
public class InstitutionDto {

    private UUID id;
    private String name;

    /**
     * Exposed as "institutionCode" in API; maps to Institution.code internally.
     */
    private String institutionCode;

    /**
     * Email domain associated with the institution (e.g., su.edu.ph).
     */
    private String emailDomain;

    /**
     * One of: onboarding, active, inactive_no_validator Matches the lowercase
     * InstitutionStatus enum values defined by M1.
     */
    private InstitutionStatus status;

    // ── Constructors ──────────────────────────────────────────────────────────
    public InstitutionDto() {
    }

    public InstitutionDto(UUID id, String name, String institutionCode, String emailDomain, InstitutionStatus status) {
        this.id = id;
        this.name = name;
        this.institutionCode = institutionCode;
        this.emailDomain = emailDomain;
        this.status = status;
    }

    // ── Static Factory ────────────────────────────────────────────────────────
    /**
     * Converts a JPA Institution entity to its DTO representation. Use this in
     * InstitutionController instead of returning entities directly.
     *
     * Maps Institution.getCode() → institutionCode (API-facing name).
     *
     * Example:
     * <pre>
     *   Institution saved = institutionService.createInstitution(req);
     *   return ResponseEntity.status(201).body(InstitutionDto.from(saved));
     * </pre>
     */
    public static InstitutionDto from(Institution institution) {
        return new InstitutionDto(
                institution.getId(),
                institution.getName(),
                institution.getCode(), // M1 field is getCode(), not getInstitutionCode()
                institution.getEmailDomain(),
                institution.getStatus()
        );
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

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

    public String getEmailDomain() {
        return emailDomain;
    }

    public void setEmailDomain(String emailDomain) {
        this.emailDomain = emailDomain;
    }

    public InstitutionStatus getStatus() {
        return status;
    }

    public void setStatus(InstitutionStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "InstitutionDto{"
                + "id=" + id
                + ", name='" + name + '\''
                + ", institutionCode='" + institutionCode + '\''
            + ", emailDomain='" + emailDomain + '\''
                + ", status=" + status
                + '}';
    }
}
