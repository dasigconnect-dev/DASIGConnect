package com.dasigconnect.backend.model.dto.audit;

public enum AuditLogCategory {
    APPROVAL("Approvals & Direct Posts"),
    REJECTION("Rejections"),
    EDIT_AND_REVISION("Edits & Revisions"),
    RESCHEDULE_AND_OVERRIDE("Reschedules & Overrides"),
    PUBLISHING("Publishing & Social"),
    ACCOUNT_MANAGEMENT("Account Management"),
    INSTITUTION_MANAGEMENT("Institution Management"),
    MEDIA_LIFECYCLE("Media Assets & Albums"),
    CONFIGURATION("Settings & Configuration"),
    SECURITY("Security & Tokens"),
    OTHER("Other System Actions");

    private final String label;

    AuditLogCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static AuditLogCategory fromAction(String action) {
        if (action == null) return OTHER;
        String a = action.toUpperCase();

        if (a.contains("EDIT") || a.contains("REVISION") || a.contains("SUBMISSION_UPDATED")) {
            return EDIT_AND_REVISION;
        }
        if (a.contains("APPROVED") || a.contains("DIRECT_POST")) {
            return APPROVAL;
        }
        if (a.contains("REJECT")) {
            return REJECTION;
        }
        if (a.contains("RESCHEDULE") || a.contains("OVERRIDE") || a.contains("TIMEOUT_DEFERRED") || a.contains("SLOT")) {
            return RESCHEDULE_AND_OVERRIDE;
        }
        if (a.contains("MANUAL_PUBLISH") || a.contains("PUBLISH") || a.contains("MISSED_REVIEW")) {
            return PUBLISHING;
        }
        if (a.startsWith("USER_") || a.contains("SUPER_ADMIN") || a.contains("ADMIN_TRANSFER") || a.contains("ADMIN_OWNER")
                || a.startsWith("CONTRIBUTOR_") || a.contains("INVITATION") || a.contains("PASSWORD")
                || a.startsWith("LOGIN_") || a.equals("LOGOUT")) {
            return ACCOUNT_MANAGEMENT;
        }
        if (a.startsWith("INSTITUTION_")) {
            return INSTITUTION_MANAGEMENT;
        }
        if (a.startsWith("MEDIA_") || a.startsWith("ASSET_") || a.contains("ALBUM")) {
            return MEDIA_LIFECYCLE;
        }
        if (a.contains("WATERMARK") || a.contains("GUARD_RAIL") || a.contains("PAGE_SETTINGS") || a.contains("CONFIG")
                || a.contains("JOB_RUN")) {
            return CONFIGURATION;
        }
        if (a.contains("TOKEN") || a.contains("AUTH") || a.contains("ACCOUNT_LOCKED") || a.contains("EXPORT")) {
            return SECURITY;
        }
        return OTHER;
    }
}
