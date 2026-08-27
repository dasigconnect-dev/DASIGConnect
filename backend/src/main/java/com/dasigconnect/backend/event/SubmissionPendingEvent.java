package com.dasigconnect.backend.event;

import com.dasigconnect.backend.model.entity.Submission;

/** T-01 — New draft submitted; transitions DRAFT → PENDING. */
public record SubmissionPendingEvent(Submission submission) {}
