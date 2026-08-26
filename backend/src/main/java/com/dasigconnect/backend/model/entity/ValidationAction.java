package com.dasigconnect.backend.model.entity;

public enum ValidationAction {
    approved,
    edited,
    edited_and_approved,
    needs_revision,
    rejected,
    lock_acquired,
    lock_released,
    lock_expired
}
