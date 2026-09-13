package com.dasigconnect.backend.model.dto.settings;

import com.dasigconnect.backend.model.entity.PageSettings;
import java.time.Instant;
import java.util.UUID;

/**
 * Page Settings response — the network-wide scheduling guard-rail switch.
 * Watermark on/off + layout live in {@code WatermarkConfiguration} (see
 * {@code /api/v1/settings/watermark}); the Facebook Page ID actually used for
 * publishing lives in {@code FacebookPageToken} (see System Health -> Tokens),
 * not here.
 */
public record PageSettingsDto(
        UUID institutionId,
        boolean guardrailsEnforced,
        Instant updatedAt) {
    public static PageSettingsDto from(PageSettings value) {
        return new PageSettingsDto(
                value.getInstitution() == null ? null : value.getInstitution().getId(),
                value.isGuardrailsEnforced(),
                value.getUpdatedAt());
    }
}
