package com.dasigconnect.backend.model.entity;

public enum NotificationEventType {
    submission_pending,
    submission_approved,
    submission_needs_revision,
    submission_rejected,
    submission_scheduled,
    submission_publish_failed,
    submission_published,
    submission_published_manual,
    validation_timeout,
    override_approved,
    override_denied,
    override_slot_suggested,
    admin_direct_post,
    institution_no_validator,
    institution_onboarded,
    submission_rescheduled,
    token_expiring,
    token_invalid,
    generic
}
