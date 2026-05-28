package com.dasigconnect.backend.model.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class AiInteractionLogRequestDto {

    @NotNull
    private UUID submissionId;

    @NotBlank
    private String type;

    @NotBlank
    private String actionTaken;

    public UUID getSubmissionId() { return submissionId; }
    public void setSubmissionId(UUID submissionId) { this.submissionId = submissionId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getActionTaken() { return actionTaken; }
    public void setActionTaken(String actionTaken) { this.actionTaken = actionTaken; }
}
