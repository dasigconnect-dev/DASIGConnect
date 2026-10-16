package com.dasigconnect.backend.model.entity;

public enum FacebookImportStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    FAILED,
    CANCELLED
}
