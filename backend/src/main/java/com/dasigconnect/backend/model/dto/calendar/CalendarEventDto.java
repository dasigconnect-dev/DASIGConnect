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

    public static CalendarEventDto full(Submission s) {
        CalendarEventDto dto = new CalendarEventDto();
        dto.id = s.getId();
        dto.title = s.getEventTitle();
        dto.institutionId = s.getInstitution().getId();
        dto.institutionName = s.getInstitution().getName();
        dto.institutionCode = s.getInstitution().getCode();
        dto.status = s.getStatus().name();
        dto.scheduledAt = s.getScheduledAt();
        dto.publishedAt = s.getPublishedAt();
        return dto;
    }

    /** For cross-institution slots visible to contributors/validators: timing only, content masked. */
    public static CalendarEventDto masked(Submission s) {
        CalendarEventDto dto = new CalendarEventDto();
        dto.id = s.getId();
        dto.title = null;
        dto.institutionId = s.getInstitution().getId();
        dto.institutionName = s.getInstitution().getName();
        dto.institutionCode = s.getInstitution().getCode();
        dto.status = s.getStatus().name();
        dto.scheduledAt = s.getScheduledAt();
        dto.publishedAt = s.getPublishedAt();
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
}
