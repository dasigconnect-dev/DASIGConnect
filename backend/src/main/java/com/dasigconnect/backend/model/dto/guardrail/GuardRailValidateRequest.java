package com.dasigconnect.backend.model.dto.guardrail;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public class GuardRailValidateRequest {

    @NotNull(message = "scheduledAt is required")
    private Instant scheduledAt;

    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
}
