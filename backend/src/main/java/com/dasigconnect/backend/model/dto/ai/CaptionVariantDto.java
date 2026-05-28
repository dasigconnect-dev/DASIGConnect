package com.dasigconnect.backend.model.dto.ai;

public class CaptionVariantDto {
    private String tone;
    private String caption;

    public CaptionVariantDto() {}

    public CaptionVariantDto(String tone, String caption) {
        this.tone = tone;
        this.caption = caption;
    }

    public String getTone() { return tone; }
    public void setTone(String tone) { this.tone = tone; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
}
