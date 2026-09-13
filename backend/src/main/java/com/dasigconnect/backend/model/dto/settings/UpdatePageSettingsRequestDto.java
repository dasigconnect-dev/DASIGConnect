package com.dasigconnect.backend.model.dto.settings;

/**
 * Page Settings now carries only the network-wide scheduling guard-rail
 * switch. Whether watermarking is on, and its layout, live entirely in
 * {@code WatermarkConfiguration} (the source {@code WatermarkApplicationService}
 * actually reads) — see {@code /api/v1/settings/watermark}. The Facebook Page
 * ID/token actually used for publishing is managed in System Health -> Tokens
 * ({@code FacebookPageToken}), not here.
 */
public record UpdatePageSettingsRequestDto(
        /** Network-wide scheduling guard-rail switch; null = leave unchanged. Only applied on the no-institution row. */
        Boolean guardrailsEnforced) {}
