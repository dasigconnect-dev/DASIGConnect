package com.dasigconnect.backend.event;

/** T12 — Facebook Page Access Token expires within 7 days (GR-T3). */
public record TokenExpiryWarningEvent(int daysUntilExpiry) {}
