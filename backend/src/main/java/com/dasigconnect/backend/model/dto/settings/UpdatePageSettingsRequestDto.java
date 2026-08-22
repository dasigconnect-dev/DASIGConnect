package com.dasigconnect.backend.model.dto.settings;

import jakarta.validation.constraints.Size;

public record UpdatePageSettingsRequestDto(boolean watermarkEnabled,
        @Size(max = 150) String watermarkText,
        @Size(max = 255) String facebookPageId) {}
