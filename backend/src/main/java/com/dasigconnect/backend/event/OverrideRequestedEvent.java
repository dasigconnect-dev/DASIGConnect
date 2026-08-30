package com.dasigconnect.backend.event;

import java.time.Instant;

import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.User;

/**
 * A moderator asked for a guard-rail override so '{@code submission}' can be
 * (re)scheduled at '{@code requestedSlot}' despite the hard block '{@code violatedRule}'.
 * Consumed by {@code NotificationEventListener} to alert administrators, who decide it.
 */
public record OverrideRequestedEvent(
        Submission submission,
        User requestedBy,
        Instant requestedSlot,
        String violatedRule,
        String reason) {
}
