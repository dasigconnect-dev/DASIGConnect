package com.dasigconnect.backend.event;

import com.dasigconnect.backend.model.entity.Submission;

/** T-11 — Fast-track live event submission; transitions DRAFT → PENDING. */
public record FastTrackSubmissionEvent(Submission submission) {}
