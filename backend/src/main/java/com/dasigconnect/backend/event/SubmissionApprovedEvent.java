package com.dasigconnect.backend.event;

import com.dasigconnect.backend.model.entity.Submission;

/** T2 — Submission approved; transitions to SCHEDULED. */
public record SubmissionApprovedEvent(Submission submission) {}
