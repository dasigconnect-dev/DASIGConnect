package com.dasigconnect.backend.model.dto.validation;

import java.time.Instant;
import java.util.UUID;

import com.dasigconnect.backend.model.entity.ValidationLog;

public class ValidationLogDto {

    private UUID id;
    private UUID submissionId;
    private UUID validatorId;
    private String validatorEmail;
    private String action;
    private String remarks;
    private String rejectionReason;
    private boolean selfReview;
    private boolean fastTrack;
    private String editDiff;
    private Instant createdAt;

    public static ValidationLogDto from(ValidationLog entry) {
        ValidationLogDto dto = new ValidationLogDto();
        dto.id = entry.getId();
        dto.submissionId = entry.getSubmission().getId();
        dto.validatorId = entry.getValidator().getId();
        dto.validatorEmail = entry.getValidator().getEmail();
        dto.action = entry.getAction().name();
        dto.remarks = entry.getRemarks();
        dto.rejectionReason = entry.getRejectionReason();
        dto.selfReview = entry.isSelfReview();
        dto.fastTrack = entry.isFastTrack();
        dto.editDiff = entry.getEditDiff();
        dto.createdAt = entry.getCreatedAt();
        return dto;
    }

    public UUID getId() { return id; }
    public UUID getSubmissionId() { return submissionId; }
    public UUID getValidatorId() { return validatorId; }
    public String getValidatorEmail() { return validatorEmail; }
    public String getAction() { return action; }
    public String getRemarks() { return remarks; }
    public String getRejectionReason() { return rejectionReason; }
    public boolean isSelfReview() { return selfReview; }
    public boolean isFastTrack() { return fastTrack; }
    public String getEditDiff() { return editDiff; }
    public Instant getCreatedAt() { return createdAt; }
}
