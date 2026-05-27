package com.dasigconnect.backend.model.dto.exception;

import com.dasigconnect.backend.model.entity.Submission;
import java.time.Instant;
import java.util.UUID;

public class TimeoutEscalationDto {

    private UUID submissionId;
    private String eventTitle;
    private String institutionName;
    private String contributorFirstName;
    private String contributorLastName;
    private String contributorEmail;
    private Instant submittedAt;
    private Instant scheduledAt;
    private String status;

    public static TimeoutEscalationDto from(Submission s) {
        TimeoutEscalationDto dto = new TimeoutEscalationDto();
        dto.submissionId = s.getId();
        dto.eventTitle = s.getEventTitle();
        dto.institutionName = s.getInstitution().getName();
        dto.contributorFirstName = s.getContributor().getFirstName();
        dto.contributorLastName = s.getContributor().getLastName();
        dto.contributorEmail = s.getContributor().getEmail();
        dto.submittedAt = s.getSubmittedAt();
        dto.scheduledAt = s.getScheduledAt();
        dto.status = s.getStatus().name();
        return dto;
    }

    public UUID getSubmissionId() { return submissionId; }
    public String getEventTitle() { return eventTitle; }
    public String getInstitutionName() { return institutionName; }
    public String getContributorFirstName() { return contributorFirstName; }
    public String getContributorLastName() { return contributorLastName; }
    public String getContributorEmail() { return contributorEmail; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getScheduledAt() { return scheduledAt; }
    public String getStatus() { return status; }
}
