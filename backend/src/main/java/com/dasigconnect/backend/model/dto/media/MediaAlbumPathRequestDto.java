package com.dasigconnect.backend.model.dto.media;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Body for POST /media-assets/albums/ensure-path — walks/creates a folder path and returns its leaf. */
public class MediaAlbumPathRequestDto {

    private UUID institutionId;

    @NotEmpty
    @Size(max = 24)
    private List<@Size(max = 255) String> segments;

    public UUID getInstitutionId() { return institutionId; }
    public void setInstitutionId(UUID institutionId) { this.institutionId = institutionId; }

    public List<String> getSegments() { return segments; }
    public void setSegments(List<String> segments) { this.segments = segments; }
}
