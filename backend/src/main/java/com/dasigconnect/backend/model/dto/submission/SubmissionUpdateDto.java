package com.dasigconnect.backend.model.dto.submission;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Size;

public class SubmissionUpdateDto {

    @Size(max = 255, message = "Event title must not exceed 255 characters")
    private String eventTitle;

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

    private Boolean fastTrack;

    @Size(max = 255)
    private String liveEventName;

    private List<String> tags;

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

    public Boolean getFastTrack() {
        return fastTrack;
    }

    public void setFastTrack(Boolean fastTrack) {
        this.fastTrack = fastTrack;
    }

    public String getLiveEventName() {
        return liveEventName;
    }

    public void setLiveEventName(String liveEventName) {
        this.liveEventName = liveEventName;
    }
}
