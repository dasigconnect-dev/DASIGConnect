package com.dasigconnect.backend.model.dto.exception;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Contributor's request for a guard-rail override on a hard-blocked slot. */
public class OverrideRequestCreateDto {

    @NotNull
    private UUID submissionId;

    @NotNull
    private Instant requestedSlot;

    @NotNull
    @Size(min = 10, max = 1000, message = "Give a reason of at least 10 characters.")
    private String reason;

    public UUID getSubmissionId() { return submissionId; }
    public void setSubmissionId(UUID submissionId) { this.submissionId = submissionId; }

    public Instant getRequestedSlot() { return requestedSlot; }
    public void setRequestedSlot(Instant requestedSlot) { this.requestedSlot = requestedSlot; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
