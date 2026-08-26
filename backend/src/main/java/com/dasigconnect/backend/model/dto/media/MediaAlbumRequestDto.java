package com.dasigconnect.backend.model.dto.media;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class MediaAlbumRequestDto {

    private UUID institutionId;

    @NotBlank
    @Size(max = 255)
    private String name;

    public UUID getInstitutionId() { return institutionId; }
    public void setInstitutionId(UUID institutionId) { this.institutionId = institutionId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
