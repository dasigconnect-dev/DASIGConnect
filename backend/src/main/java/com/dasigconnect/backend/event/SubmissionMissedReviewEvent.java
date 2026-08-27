package com.dasigconnect.backend.event;

import com.dasigconnect.backend.model.entity.Submission;

/**
 * UC-2.4 A6 / GR-T9 — a submission's scheduled publication time passed while it
 * was still PENDING/IN_REVIEW. It has been moved to MISSED_REVIEW and its slot
 * released; a network administrator must assign a new schedule.
 */
public record SubmissionMissedReviewEvent(Submission submission) {
}
