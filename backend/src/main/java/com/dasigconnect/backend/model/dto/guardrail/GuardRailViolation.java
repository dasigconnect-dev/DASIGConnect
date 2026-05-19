package com.dasigconnect.backend.model.dto.guardrail;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Represents a single guard rail rule violation.
 *
 * Hard violations (GR-H1, GR-H2, GR-H3) block submission entirely. Soft
 * violations (GR-S1, GR-S2) produce dismissible warnings.
 *
 * suggestedSlots is only populated for GR-H1 (slot conflict), where the system
 * proposes the nearest available alternative times.
 *
 * NOTE: Uses Instant to match SlotReservation.scheduledAt (Instant), as defined
 * in M1's entity layer.
 */
public class GuardRailViolation {

    /**
     * Guard rail rule code, e.g. "GR-H1", "GR-S2".
     */
    private String code;

    /**
     * Human-readable explanation shown to the Contributor in the UI.
     */
    private String message;

    /**
     * Nearest available alternative slots suggested by the system. Only
     * populated when code == "GR-H1". Empty list otherwise. Type is Instant to
     * align with SlotReservation.scheduledAt in the entity layer.
     */
    private List<Instant> suggestedSlots;

    // ── Constructors ──────────────────────────────────────────────────────────
    public GuardRailViolation() {
    }

    /**
     * Use this constructor for violations that carry slot suggestions (GR-H1).
     */
    public GuardRailViolation(String code, String message, List<Instant> suggestedSlots) {
        this.code = code;
        this.message = message;
        this.suggestedSlots = (suggestedSlots != null) ? suggestedSlots : Collections.emptyList();
    }

    /**
     * Convenience constructor for violations with no slot suggestions (GR-H2,
     * GR-H3, GR-S1, GR-S2).
     */
    public GuardRailViolation(String code, String message) {
        this(code, message, Collections.emptyList());
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<Instant> getSuggestedSlots() {
        return suggestedSlots;
    }

    public void setSuggestedSlots(List<Instant> suggestedSlots) {
        this.suggestedSlots = (suggestedSlots != null) ? suggestedSlots : Collections.emptyList();
    }

    @Override
    public String toString() {
        return "GuardRailViolation{"
                + "code='" + code + '\''
                + ", message='" + message + '\''
                + ", suggestedSlots=" + suggestedSlots
                + '}';
    }
}
