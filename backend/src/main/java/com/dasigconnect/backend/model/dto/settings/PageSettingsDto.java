package com.dasigconnect.backend.model.dto.settings;

import com.dasigconnect.backend.model.entity.PageSettings;
import java.time.Instant;
import java.util.UUID;

/**
 * Page Settings response — the Facebook Page ID only. Watermark on/off + layout
 * live in {@code WatermarkConfiguration} (see {@code /api/v1/settings/watermark}).
 */
public record PageSettingsDto(UUID institutionId, String facebookPageId, Instant updatedAt) {
    public static PageSettingsDto from(PageSettings value) {
        return new PageSettingsDto(
                value.getInstitution() == null ? null : value.getInstitution().getId(),
                value.getFacebookPageId(),
                value.getUpdatedAt());
    }
}
