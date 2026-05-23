package com.dasigconnect.backend.model.dto.submission;

import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

public class SubmissionUpdateDto {

    @Size(max = 255, message = "Event title must not exceed 255 characters")
    private String eventTitle;

    private LocalDate eventDate;

    private String caption;

    private String description;

    private Instant scheduledAt;

    public String getEventTitle() { return eventTitle; }
    public void setEventTitle(String eventTitle) { this.eventTitle = eventTitle; }

    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }

    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
}
