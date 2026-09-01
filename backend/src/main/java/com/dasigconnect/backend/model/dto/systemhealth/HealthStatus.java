package com.dasigconnect.backend.model.dto.systemhealth;

public enum HealthStatus {
    HEALTHY,
    WARNING,
    UNHEALTHY,
    UNAVAILABLE,
    /** A scheduled job that has not run yet but is not overdue (e.g. a daily job soon after a restart). */
    SCHEDULED
}
