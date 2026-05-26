package com.dasigconnect.backend.model.dto.ai;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class CaptionRequestDto {
    @NotNull
    private UUID submissionId;

    public UUID getSubmissionId() { return submissionId; }
    public void setSubmissionId(UUID submissionId) { this.submissionId = submissionId; }
}
