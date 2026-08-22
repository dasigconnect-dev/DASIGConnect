package com.dasigconnect.backend.model.dto.settings;

import com.dasigconnect.backend.model.entity.PageSettings;
import java.time.Instant;
import java.util.UUID;

public record PageSettingsDto(UUID institutionId, boolean watermarkEnabled,
        String watermarkText, String facebookPageId, Instant updatedAt) {
    public static PageSettingsDto from(PageSettings value) {
        return new PageSettingsDto(value.getInstitution() == null ? null : value.getInstitution().getId(),
                value.isWatermarkEnabled(), value.getWatermarkText(), value.getFacebookPageId(), value.getUpdatedAt());
    }
}
