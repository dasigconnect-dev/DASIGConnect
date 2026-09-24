package com.dasigconnect.backend.event;

import com.dasigconnect.backend.model.entity.ReviewEditSeverity;
import com.dasigconnect.backend.model.entity.Submission;

import java.util.UUID;

/**
 * A10 — a moderator changed a contributor's submission during review. Fired once
 * per review outcome (approve / request-revision / reject) when any edit happened
 * this session, and on explicit lock release when edits were made but no terminal
 * action was taken. Drives the contributor "your submission was edited"
 * notification with a link to the before/after diff, and — for substantive
 * edits (FLAGGED / ADDED_MEDIA) — an in-app alert to every other Administrator.
 *
 * @param severity the highest severity across every edit made this review session
 * @param editDiff combined before/after JSON for the session, or {@code null}
 * @param editorId the reviewer who made the edits, or {@code null} if unknown
 */
public record SubmissionEditedDuringReviewEvent(
        Submission submission,
        ReviewEditSeverity severity,
        String editDiff,
        UUID editorId) {

    public SubmissionEditedDuringReviewEvent(Submission submission, ReviewEditSeverity severity, String editDiff) {
        this(submission, severity, editDiff, null);
    }
}
