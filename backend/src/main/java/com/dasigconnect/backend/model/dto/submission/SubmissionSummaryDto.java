package com.dasigconnect.backend.model.dto.submission;

import com.dasigconnect.backend.model.entity.Submission;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class SubmissionSummaryDto {

    private UUID id;
    private String eventTitle;
    private LocalDate eventDate;
    private String status;
    private Instant scheduledAt;
    private Instant submittedAt;
    private Instant createdAt;
    private UUID institutionId;
    private String contributorEmail;
    private long mediaCount;
    private String category;
    private List<String> tags;

    public static SubmissionSummaryDto from(Submission s, long mediaCount) {
        SubmissionSummaryDto dto = new SubmissionSummaryDto();
        dto.id = s.getId();
        dto.eventTitle = s.getEventTitle();
        dto.eventDate = s.getEventDate();
        dto.status = s.getStatus().name();
        dto.scheduledAt = s.getScheduledAt();
        dto.submittedAt = s.getSubmittedAt();
        dto.createdAt = s.getCreatedAt();
        dto.institutionId = s.getInstitution().getId();
        dto.contributorEmail = s.getContributor().getEmail();
        dto.mediaCount = mediaCount;
        dto.category = s.getCategory();
        dto.tags = (s.getTags() == null || s.getTags().isBlank())
                ? List.of()
                : Arrays.stream(s.getTags().split(",")).map(String::trim).filter(t -> !t.isEmpty()).toList();
        return dto;
    }

    public UUID getId() { return id; }
    public String getEventTitle() { return eventTitle; }
    public LocalDate getEventDate() { return eventDate; }
    public String getStatus() { return status; }
    public Instant getScheduledAt() { return scheduledAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getInstitutionId() { return institutionId; }
    public String getContributorEmail() { return contributorEmail; }
    public long getMediaCount() { return mediaCount; }
    public String getCategory() { return category; }
    public List<String> getTags() { return tags; }
}
