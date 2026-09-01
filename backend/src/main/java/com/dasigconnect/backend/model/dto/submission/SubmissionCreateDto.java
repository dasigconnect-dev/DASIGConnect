package com.dasigconnect.backend.model.dto.submission;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class SubmissionCreateDto {

    private UUID institutionId;

    @NotBlank(message = "Event title is required")
    @Size(max = 255, message = "Event title must not exceed 255 characters")
    private String eventTitle;

    @NotNull(message = "Event date is required")
    private LocalDate eventDate;

    private String caption;

    private String description;

    private Instant scheduledAt;

    @Size(max = 100)
    private String category;

    @Size(max = 100)
    private String templateId;

    @Size(max = 255)
    private String albumName;

    private List<String> mediaTags;

    private boolean fastTrack;

    @Size(max = 255)
    private String liveEventName;

    private List<String> tags;

    public UUID getInstitutionId() {
        return institutionId;
    }

    public void setInstitutionId(UUID institutionId) {
        this.institutionId = institutionId;
    }

    public String getEventTitle() {
        return eventTitle;
    }

    public void setEventTitle(String eventTitle) {
        this.eventTitle = eventTitle;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public String getAlbumName() {
        return albumName;
    }

    public void setAlbumName(String albumName) {
        this.albumName = albumName;
    }

    public List<String> getMediaTags() {
        return mediaTags;
    }

    public void setMediaTags(List<String> mediaTags) {
        this.mediaTags = mediaTags;
    }

    public boolean isFastTrack() {
        return fastTrack;
    }

    public void setFastTrack(boolean fastTrack) {
        this.fastTrack = fastTrack;
    }

    public String getLiveEventName() {
        return liveEventName;
    }

    public void setLiveEventName(String liveEventName) {
        this.liveEventName = liveEventName;
    }
}
