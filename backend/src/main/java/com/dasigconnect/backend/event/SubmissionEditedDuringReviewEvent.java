package com.dasigconnect.backend.event;

import com.dasigconnect.backend.model.entity.ReviewEditSeverity;
import com.dasigconnect.backend.model.entity.Submission;

/**
 * A10 — a moderator changed a contributor's submission during review. Fired once
 * per review outcome (approve / request-revision / reject) when any edit happened
 * this session, and on explicit lock release when edits were made but no terminal
 * action was taken. Drives the contributor "your submission was edited"
 * notification with a link to the before/after diff.
 *
 * @param severity the highest severity across every edit made this review session
 * @param editDiff combined before/after JSON for the session, or {@code null}
 */
public record SubmissionEditedDuringReviewEvent(
        Submission submission,
        ReviewEditSeverity severity,
        String editDiff) {}
