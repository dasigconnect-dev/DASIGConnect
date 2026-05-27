package com.dasigconnect.backend.model.dto.exception;

import com.dasigconnect.backend.model.entity.OverrideRequest;
import java.time.Instant;
import java.util.UUID;

public class OverrideRequestDto {

    private UUID id;
    private UUID submissionId;
    private String eventTitle;
    private String contributorFirstName;
    private String contributorLastName;
    private String contributorEmail;
    private String institutionName;
    private Instant requestedSlot;
    private String violatedRule;
    private String overrideReason;
    private String decision;
    private Instant createdAt;
    private int overrideRequestCount;

    public static OverrideRequestDto from(OverrideRequest r, int totalRequestCount) {
        OverrideRequestDto dto = new OverrideRequestDto();
        dto.id = r.getId();
        dto.submissionId = r.getSubmission().getId();
        dto.eventTitle = r.getSubmission().getEventTitle();
        dto.contributorFirstName = r.getContributor().getFirstName();
        dto.contributorLastName = r.getContributor().getLastName();
        dto.contributorEmail = r.getContributor().getEmail();
        dto.institutionName = r.getInstitution().getName();
        dto.requestedSlot = r.getRequestedSlot();
        dto.violatedRule = r.getViolatedRule();
        dto.overrideReason = r.getOverrideReason();
        dto.decision = r.getDecision().name();
        dto.createdAt = r.getCreatedAt();
        dto.overrideRequestCount = totalRequestCount;
        return dto;
    }

    public UUID getId() { return id; }
    public UUID getSubmissionId() { return submissionId; }
    public String getEventTitle() { return eventTitle; }
    public String getContributorFirstName() { return contributorFirstName; }
    public String getContributorLastName() { return contributorLastName; }
    public String getContributorEmail() { return contributorEmail; }
    public String getInstitutionName() { return institutionName; }
    public Instant getRequestedSlot() { return requestedSlot; }
    public String getViolatedRule() { return violatedRule; }
    public String getOverrideReason() { return overrideReason; }
    public String getDecision() { return decision; }
    public Instant getCreatedAt() { return createdAt; }
    public int getOverrideRequestCount() { return overrideRequestCount; }
}
