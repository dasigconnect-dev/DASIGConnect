package com.dasigconnect.backend.model.dto.ai;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public class MediaSuggestRequestDto {

    private String eventTitle;
    private String caption;
    private String category;
    private List<String> tags;

    @Size(max = 10, message = "selectedAssetIds cannot contain more than 10 assets")
    private List<UUID> selectedAssetIds;

    public String getEventTitle() { return eventTitle; }
    public void setEventTitle(String eventTitle) { this.eventTitle = eventTitle; }

    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public List<UUID> getSelectedAssetIds() { return selectedAssetIds; }
    public void setSelectedAssetIds(List<UUID> selectedAssetIds) { this.selectedAssetIds = selectedAssetIds; }
}
