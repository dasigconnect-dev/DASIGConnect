package com.dasigconnect.backend.model.dto.guardrail;

import java.util.Collections;
import java.util.List;

/**
 * The complete result of guard rail validation for a requested time slot.
 *
 * Returned by GuardRailService.validate() and by the POST
 * /api/guardrails/validate endpoint.
 *
 * The frontend uses this to: - Render hard block UI (prevents slot selection,
 * shows alternatives) - Render soft warning UI (dismissible, allows proceeding)
 *
 * Usage in GuardRailService:
 * <pre>
 *   GuardRailResult result = guardRailService.validate(institutionId, slot, pageId);
 *   if (result.isBlocked()) {
 *       throw new GuardRailViolationException(result.getHardBlocks());
 *   }
 * </pre>
 */
public class GuardRailResult {

    /**
     * Hard rule violations — submission is BLOCKED until all are resolved.
     * Possible codes: GR-H1, GR-H2, GR-H3
     */
    private List<GuardRailViolation> hardBlocks;

    /**
     * Soft rule violations — Contributor sees a warning but CAN proceed.
     * Possible codes: GR-S1, GR-S2
     */
    private List<GuardRailViolation> softWarnings;

    // ── Constructors ──────────────────────────────────────────────────────────
    public GuardRailResult() {
        this.hardBlocks = Collections.emptyList();
        this.softWarnings = Collections.emptyList();
    }

    public GuardRailResult(List<GuardRailViolation> hardBlocks, List<GuardRailViolation> softWarnings) {
        this.hardBlocks = (hardBlocks != null) ? hardBlocks : Collections.emptyList();
        this.softWarnings = (softWarnings != null) ? softWarnings : Collections.emptyList();
    }

    // ── Convenience Methods ───────────────────────────────────────────────────
    /**
     * Returns true if there is at least one hard block. SlotReservationService
     * should throw GuardRailViolationException when this is true.
     */
    public boolean isBlocked() {
        return !hardBlocks.isEmpty();
    }

    /**
     * Returns true if there are soft warnings but no hard blocks. The frontend
     * should show a dismissible warning panel.
     */
    public boolean hasWarnings() {
        return hardBlocks.isEmpty() && !softWarnings.isEmpty();
    }

    /**
     * Returns true if the slot passed all guard rail rules cleanly.
     */
    public boolean isClean() {
        return hardBlocks.isEmpty() && softWarnings.isEmpty();
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────
    public List<GuardRailViolation> getHardBlocks() {
        return hardBlocks;
    }

    public void setHardBlocks(List<GuardRailViolation> hardBlocks) {
        this.hardBlocks = (hardBlocks != null) ? hardBlocks : Collections.emptyList();
    }

    public List<GuardRailViolation> getSoftWarnings() {
        return softWarnings;
    }

    public void setSoftWarnings(List<GuardRailViolation> softWarnings) {
        this.softWarnings = (softWarnings != null) ? softWarnings : Collections.emptyList();
    }

    @Override
    public String toString() {
        return "GuardRailResult{"
                + "hardBlocks=" + hardBlocks
                + ", softWarnings=" + softWarnings
                + ", isBlocked=" + isBlocked()
                + '}';
    }
}
