package com.dasigconnect.backend.event;

import com.dasigconnect.backend.model.entity.Submission;

/**
 * A Live Event / Fast-Track submission whose automated publish failed is
 * being retried without a mode change (see ManualPublishingService.retry()).
 * Fast-Track publishing is normally triggered by SubmissionApprovedEvent, but
 * reusing that event here would also re-fire its approval notifications —
 * this is a distinct signal carrying no such side effects, just "attempt
 * publishing now" for FastTrackPublishingListener.
 */
public record SubmissionFastTrackRetryEvent(Submission submission) {}
