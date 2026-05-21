package com.dasigconnect.backend.model.dto.submission;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public class SlotEvaluateRequestDto {

    @NotNull(message = "scheduledAt is required")
    private Instant scheduledAt;

    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
}
