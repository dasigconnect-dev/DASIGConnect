package com.dasigconnect.backend.event;

import java.time.Instant;

import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.User;

/**
 * A contributor asked for a guard-rail override so they can schedule '{@code submission}'
 * at '{@code requestedSlot}' despite the hard block '{@code violatedRule}'. Consumed by
 * {@code NotificationEventListener} to alert administrators, who decide it.
 */
public record OverrideRequestedEvent(
        Submission submission,
        User contributor,
        Instant requestedSlot,
        String violatedRule,
        String reason) {
}
