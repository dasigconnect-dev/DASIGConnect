package com.dasigconnect.backend.model.dto.submission;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

public class RescheduleRequestDto {

    @NotNull
    private Instant scheduledAt;

    /** Required only when the new slot violates a hard guard rail. Admin must provide a reason to override. */
    private String overrideReason;

    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }

    public String getOverrideReason() { return overrideReason; }
    public void setOverrideReason(String overrideReason) { this.overrideReason = overrideReason; }
}
