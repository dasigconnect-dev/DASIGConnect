package com.dasigconnect.backend.event;

import java.time.Instant;

import com.dasigconnect.backend.model.entity.Submission;

/** T16 — Administrator rescheduled a contributor's SCHEDULED post via the Master Calendar. */
public record SubmissionRescheduledEvent(Submission submission, Instant originalSlot, Instant newSlot) {}
