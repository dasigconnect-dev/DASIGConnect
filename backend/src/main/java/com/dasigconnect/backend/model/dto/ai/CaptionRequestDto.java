package com.dasigconnect.backend.model.dto.ai;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public class CaptionRequestDto {
    @NotNull
    private UUID submissionId;

    /** The contributor's current draft caption. Null or blank = generate from scratch. */
    @Size(max = 500)
    private String existingCaption;

    public UUID getSubmissionId() { return submissionId; }
    public void setSubmissionId(UUID submissionId) { this.submissionId = submissionId; }

    public String getExistingCaption() { return existingCaption; }
    public void setExistingCaption(String existingCaption) { this.existingCaption = existingCaption; }
}
