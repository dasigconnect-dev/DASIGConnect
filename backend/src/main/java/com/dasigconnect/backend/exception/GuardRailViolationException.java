package com.dasigconnect.backend.exception;

import java.util.List;

import com.dasigconnect.backend.model.dto.guardrail.GuardRailViolation;

/**
 * Thrown by SlotReservationService when one or more HARD guard rail rules
 * (GR-H1, GR-H2, GR-H3) block a slot reservation.
 *
 * Caught by ExceptionHandlingController (M1's global handler) and returned as
 * HTTP 422 Unprocessable Entity with the violations list in the body, so the
 * frontend (M5) can render the hard block UI with suggested slots.
 */
public class GuardRailViolationException extends RuntimeException {

    private final List<GuardRailViolation> violations;

    public GuardRailViolationException(List<GuardRailViolation> violations) {
        super("Slot rejected: " + violations.stream()
                .map(GuardRailViolation::getCode)
                .reduce((a, b) -> a + ", " + b)
                .orElse("unknown"));
        this.violations = violations;
    }

    public List<GuardRailViolation> getViolations() {
        return violations;
    }
}
