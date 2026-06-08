package com.dasigconnect.backend.model.entity;

public enum ValidationAction {
    approved,
    self_approved,
    needs_revision,
    rejected,
    lock_acquired,
    lock_released,
    lock_expired
}
