package com.dasigconnect.backend.event;

import com.dasigconnect.backend.model.entity.Submission;

/** T3 — Validator requests revision; transitions to NEEDS_REVISION. */
public record RevisionRequestedEvent(Submission submission, String remarks) {}
