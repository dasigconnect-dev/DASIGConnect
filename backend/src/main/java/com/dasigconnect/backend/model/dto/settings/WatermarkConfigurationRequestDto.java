package com.dasigconnect.backend.model.dto.settings;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record WatermarkConfigurationRequestDto(
        boolean enabled,
        @NotNull @Size(max = 3, message = "Watermark configuration can contain at most 3 elements")
        List<WatermarkElementDto> elements
) {}
