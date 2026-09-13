package com.dasigconnect.backend.model.entity;

/**
 * Governance tier for a moderator edit made during review (A9/A10).
 *
 * <p>Persisted lowercase into {@code validation_logs.edit_severity} and mirrored
 * into the audit-log metadata as {@code editSeverity}. Drives how prominently the
 * edit is surfaced — quietly logged vs. flagged to the contributor and to admins.
 */
public enum ReviewEditSeverity {

    /** Minor, freely-editable change — logged, not flagged (title, date, tags, album, small caption tweak). */
    QUIET,

    /** Substantive change — audit trail plus prominent contributor notification (big caption reword, schedule, asset swap/removal). */
    FLAGGED,

    /** Moderator attached a Library asset the contributor did not originally submit — its own distinct audit event. */
    ADDED_MEDIA;

    /** DB/JSON form. */
    public String toDbValue() {
        return name().toLowerCase();
    }

    /** Highest severity of the two (null-safe; null counts as lowest). */
    public static ReviewEditSeverity max(ReviewEditSeverity a, ReviewEditSeverity b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
