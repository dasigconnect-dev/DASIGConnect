package com.dasigconnect.backend.event;

import com.dasigconnect.backend.model.entity.Submission;
import java.util.UUID;

/**
 * UC-2.5 A4: watermark rendering failed for one photo during publish.
 * Non-blocking by design — the post still publishes with the original,
 * unwatermarked image (see WatermarkApplicationService.resolvePublishUrl) — but
 * an Admin needs to know a branded watermark silently didn't make it onto a
 * live post.
 */
public record WatermarkApplicationFailedEvent(Submission submission, UUID mediaAssetId, String errorDetail) {}
