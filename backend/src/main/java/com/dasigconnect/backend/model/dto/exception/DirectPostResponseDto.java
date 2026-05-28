package com.dasigconnect.backend.model.dto.exception;

import java.util.UUID;

public class DirectPostResponseDto {

    private UUID submissionId;
    private String status;
    private boolean grH1ConflictWarning;

    public DirectPostResponseDto(UUID submissionId, String status, boolean grH1ConflictWarning) {
        this.submissionId = submissionId;
        this.status = status;
        this.grH1ConflictWarning = grH1ConflictWarning;
    }

    public UUID getSubmissionId() { return submissionId; }
    public String getStatus() { return status; }
    public boolean isGrH1ConflictWarning() { return grH1ConflictWarning; }
}
