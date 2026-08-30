package com.dasigconnect.backend.model.entity;

public enum NotificationEventType {
    submission_pending,
    submission_approved,
    submission_needs_revision,
    submission_rejected,
    submission_scheduled,
    submission_publish_failed,
    submission_missed_review,
    submission_published,
    submission_published_manual,
    validation_timeout,
    override_requested,
    override_approved,
    override_denied,
    override_slot_suggested,
    admin_direct_post,
    institution_no_moderator,
    institution_onboarded,
    submission_rescheduled,
    token_expiring,
    token_invalid,
    empty_schedule_warning,
    fast_track_submission,
    embedding_failure_digest,
    user_role_changed,
    generic
}
