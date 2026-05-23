package com.dasigconnect.backend.event;

import com.dasigconnect.backend.model.entity.Submission;

/** T5 — Post published automatically; transitions to PUBLISHED. */
public record PostPublishedEvent(Submission submission, String platformPostUrl) {}
