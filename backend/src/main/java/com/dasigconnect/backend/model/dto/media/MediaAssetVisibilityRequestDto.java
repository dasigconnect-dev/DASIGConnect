package com.dasigconnect.backend.model.dto.media;

import jakarta.validation.constraints.NotBlank;

/** UC-4.x: change a media asset's consent/visibility (internal_only | cleared_for_public). */
public class MediaAssetVisibilityRequestDto {

    @NotBlank
    private String visibility;

    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
}
