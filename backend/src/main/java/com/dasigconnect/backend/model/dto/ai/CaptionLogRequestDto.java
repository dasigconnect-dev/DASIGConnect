package com.dasigconnect.backend.model.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class CaptionLogRequestDto {
    @NotNull
    private UUID submissionId;

    @NotBlank
    private String actionTaken;

    private String toneSelected;

    public UUID getSubmissionId() { return submissionId; }
    public void setSubmissionId(UUID submissionId) { this.submissionId = submissionId; }
    public String getActionTaken() { return actionTaken; }
    public void setActionTaken(String actionTaken) { this.actionTaken = actionTaken; }
    public String getToneSelected() { return toneSelected; }
    public void setToneSelected(String toneSelected) { this.toneSelected = toneSelected; }
}
