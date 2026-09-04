package com.dasigconnect.backend.model.entity;

public enum ValidationAction {
    approved,
    edited,
    /**
     * A moderator attached a Media Library asset the contributor did not
     * originally submit. Distinct from {@link #edited} so it is its own audit
     * event ("Moderator added media not originally submitted by the Contributor").
     */
    media_added,
    /**
     * Legacy — approvals are now always logged as {@link #approved} with the
     * before/after {@code edit_diff} attached when the admin edited during review.
     * Retained so historical rows still deserialize.
     */
    edited_and_approved,
    needs_revision,
    rejected,
    lock_acquired,
    lock_released,
    lock_expired
}
