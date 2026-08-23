package com.dasigconnect.backend.model.dto.calendar;

import java.time.Instant;
import java.util.UUID;

import com.dasigconnect.backend.model.entity.Submission;

public class CalendarEventDto {

    private UUID id;
    /** Null when the viewer is not from the same institution and is not an admin. */
    private String title;
    private UUID institutionId;
    private String institutionName;
    private String institutionCode;
    private String status;
    private Instant scheduledAt;
    private Instant publishedAt;
    private String caption;
    private String description;
    private String contributorName;

    public static CalendarEventDto full(Submission s) {
        CalendarEventDto dto = new CalendarEventDto();
        dto.id = s.getId();
        dto.title = s.getEventTitle();
        dto.institutionId = s.getInstitution() != null ? s.getInstitution().getId() : null;
        dto.institutionName = s.getInstitution() != null ? s.getInstitution().getName() : null;
        dto.institutionCode = s.getInstitution() != null ? s.getInstitution().getCode() : null;
        dto.status = s.getStatus() != null ? s.getStatus().name() : null;
        dto.scheduledAt = s.getScheduledAt();
        dto.publishedAt = s.getPublishedAt();
        dto.caption = s.getCaption();
        dto.description = s.getDescription();
        if (s.getContributor() != null) {
            String firstName = s.getContributor().getFirstName() != null ? s.getContributor().getFirstName() : "";
            String lastName = s.getContributor().getLastName() != null ? s.getContributor().getLastName() : "";
            String fullName = (firstName + " " + lastName).trim();
            dto.contributorName = fullName.isEmpty() ? s.getContributor().getEmail() : fullName;
        }
        return dto;
    }

    /** For cross-institution slots visible to contributors/validators: timing only, content masked. */
    public static CalendarEventDto masked(Submission s) {
        CalendarEventDto dto = new CalendarEventDto();
        dto.id = s.getId();
        dto.title = null;
        dto.institutionId = s.getInstitution() != null ? s.getInstitution().getId() : null;
        dto.institutionName = s.getInstitution() != null ? s.getInstitution().getName() : null;
        dto.institutionCode = s.getInstitution() != null ? s.getInstitution().getCode() : null;
        dto.status = s.getStatus() != null ? s.getStatus().name() : null;
        dto.scheduledAt = s.getScheduledAt();
        dto.publishedAt = s.getPublishedAt();
        dto.caption = null;
        dto.description = null;
        dto.contributorName = null;
        return dto;
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public UUID getInstitutionId() { return institutionId; }
    public String getInstitutionName() { return institutionName; }
    public String getInstitutionCode() { return institutionCode; }
    public String getStatus() { return status; }
    public Instant getScheduledAt() { return scheduledAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getCaption() { return caption; }
    public String getDescription() { return description; }
    public String getContributorName() { return contributorName; }
}
