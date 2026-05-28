package com.dasigconnect.backend.model.dto.resolution;

public class ManualPublishCompleteDto {

    /** Optional — URL of the live Facebook post. Must match https://www.facebook.com/* if provided. */
    private String postUrl;

    /** Optional — admin notes about the manual publish. */
    private String notes;

    public String getPostUrl() { return postUrl; }
    public void setPostUrl(String postUrl) { this.postUrl = postUrl; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
