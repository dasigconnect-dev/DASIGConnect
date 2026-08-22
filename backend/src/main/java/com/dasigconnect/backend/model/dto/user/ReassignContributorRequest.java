package com.dasigconnect.backend.model.dto.user;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for PATCH /api/v1/users/{id}/institution (A4).
 *
 * Moves a contributor account to a different institution. Only the
 * target institution UUID is required; historical submissions remain
 * attributed to the original institution.
 */
public class ReassignContributorRequest {

    @NotNull(message = "Target institution ID is required.")
    private UUID targetInstitutionId;

    public ReassignContributorRequest() {}

    public UUID getTargetInstitutionId() {
        return targetInstitutionId;
    }

    public void setTargetInstitutionId(UUID targetInstitutionId) {
        this.targetInstitutionId = targetInstitutionId;
    }
}
