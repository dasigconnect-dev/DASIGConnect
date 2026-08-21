package com.dasigconnect.backend.model.dto.guardrail;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public class GuardRailValidateRequest {

    @NotNull(message = "scheduledAt is required")
    private Instant scheduledAt;
    private UUID institutionId;

    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }

    public UUID getInstitutionId() { return institutionId; }
    public void setInstitutionId(UUID institutionId) { this.institutionId = institutionId; }
}
