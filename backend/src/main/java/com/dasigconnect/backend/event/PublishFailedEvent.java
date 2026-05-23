package com.dasigconnect.backend.event;

import com.dasigconnect.backend.model.entity.Submission;

/** T7 — Automated publishing failed; transitions to PUBLISH_FAILED. */
public record PublishFailedEvent(Submission submission, String errorDetail) {}
