package com.dasigconnect.backend.model.dto.media;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AlbumCreateRequestDto {

    @NotBlank
    @Size(max = 150)
    private String name;

    @Size(max = 1000)
    private String description;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
