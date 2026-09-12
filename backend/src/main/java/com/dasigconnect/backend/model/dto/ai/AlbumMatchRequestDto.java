package com.dasigconnect.backend.model.dto.ai;

import java.util.List;

/**
 * Draft context sent for album Auto-Match (UC-1.7). Mirrors {@link MediaSuggestRequestDto} —
 * the composer's in-progress form state, not yet necessarily saved to the draft.
 */
public class AlbumMatchRequestDto {

    private String eventTitle;
    private String caption;
    private List<String> tags;

    public String getEventTitle() { return eventTitle; }
    public void setEventTitle(String eventTitle) { this.eventTitle = eventTitle; }

    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}
