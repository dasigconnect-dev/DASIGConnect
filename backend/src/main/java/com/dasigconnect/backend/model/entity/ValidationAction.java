package com.dasigconnect.backend.model.entity;

public enum ValidationAction {
    approved,
    edited,
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
