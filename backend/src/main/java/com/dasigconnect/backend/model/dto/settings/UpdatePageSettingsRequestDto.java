package com.dasigconnect.backend.model.dto.settings;

import jakarta.validation.constraints.Size;

/**
 * Page Settings now carries only the Facebook Page ID. Whether watermarking is
 * on, and its layout, live entirely in {@code WatermarkConfiguration} (the
 * source {@code WatermarkApplicationService} actually reads) — see
 * {@code /api/v1/settings/watermark}.
 */
public record UpdatePageSettingsRequestDto(@Size(max = 255) String facebookPageId) {}
