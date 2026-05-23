package com.dasigconnect.backend.event;

import com.dasigconnect.backend.model.entity.Submission;

/** T4 — Validator rejects submission; transitions to REJECTED. */
public record SubmissionRejectedEvent(Submission submission, String reason) {}
