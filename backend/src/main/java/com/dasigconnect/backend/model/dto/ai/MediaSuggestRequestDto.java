package com.dasigconnect.backend.model.dto.ai;

import java.util.List;

public class MediaSuggestRequestDto {

    private String eventTitle;
    private String caption;
    private String category;
    private List<String> tags;

    public String getEventTitle() { return eventTitle; }
    public void setEventTitle(String eventTitle) { this.eventTitle = eventTitle; }

    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}
