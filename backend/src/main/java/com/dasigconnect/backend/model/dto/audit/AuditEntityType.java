package com.dasigconnect.backend.model.dto.audit;

public enum AuditEntityType {
    SUBMISSION("Submission"),
    MEDIA_ASSET("Media Asset"),
    MEDIA_ALBUM("Media Album"),
    USER("User Account"),
    INSTITUTION("Institution"),
    FACEBOOK_TOKEN("Facebook Token"),
    WATERMARK_CONFIG("Watermark Configuration"),
    SYSTEM("System");

    private final String label;

    AuditEntityType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static AuditEntityType fromAction(String action) {
        if (action == null) return SYSTEM;
        String a = action.toUpperCase();

        if (a.startsWith("SUBMISSION") || a.startsWith("TIMEOUT") || a.startsWith("OVERRIDE") || a.startsWith("DIRECT_POST") || a.startsWith("MANUAL_PUBLISH") || a.startsWith("MISSED_REVIEW") || a.startsWith("PUBLISH")) {
            return SUBMISSION;
        }
        if (a.startsWith("MEDIA_ALBUM")) {
            return MEDIA_ALBUM;
        }
        if (a.startsWith("MEDIA_") || a.startsWith("ASSET_")) {
            return MEDIA_ASSET;
        }
        if (a.startsWith("USER_") || a.contains("SUPER_ADMIN") || a.contains("ADMIN_TRANSFER") || a.contains("ADMIN_OWNER")
                || a.startsWith("CONTRIBUTOR_") || a.contains("INVITATION") || a.contains("PASSWORD")
                || a.startsWith("LOGIN_") || a.equals("LOGOUT") || a.contains("ACCOUNT_LOCKED")) {
            return USER;
        }
        if (a.startsWith("INSTITUTION_")) {
            return INSTITUTION;
        }
        if (a.contains("TOKEN")) {
            return FACEBOOK_TOKEN;
        }
        if (a.contains("WATERMARK")) {
            return WATERMARK_CONFIG;
        }
        return SYSTEM;
    }
}
