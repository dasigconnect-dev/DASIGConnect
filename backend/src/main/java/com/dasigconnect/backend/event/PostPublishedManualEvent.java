package com.dasigconnect.backend.event;

import com.dasigconnect.backend.model.entity.Submission;

/** T6 — Moderator manually published a post; transitions to PUBLISHED_MANUAL. */
public record PostPublishedManualEvent(Submission submission, String platformPostUrl) {}
