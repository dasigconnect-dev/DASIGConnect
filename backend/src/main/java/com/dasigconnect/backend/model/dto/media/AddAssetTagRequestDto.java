package com.dasigconnect.backend.model.dto.media;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AddAssetTagRequestDto {

    @NotBlank
    @Size(max = 50)
    private String label;

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
}
