package com.dasigconnect.backend.model.dto.media;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public class MediaAssetAddToDraftRequestDto {

    @NotNull(message = "submissionId is required")
    private UUID submissionId;

    public UUID getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(UUID submissionId) {
        this.submissionId = submissionId;
    }
}
