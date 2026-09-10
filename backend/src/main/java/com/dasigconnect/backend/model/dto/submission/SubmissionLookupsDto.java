package com.dasigconnect.backend.model.dto.submission;

import java.util.List;

public class SubmissionLookupsDto {

    private final List<String> allowedFileTypes = List.of("jpeg", "png", "webp", "gif", "mp4", "mov", "webm");
    private final List<String> allowedImageTypes = List.of("jpeg", "png", "webp", "gif");
    private final List<String> allowedVideoTypes = List.of("mp4", "mov", "webm");
    private final int maxFileSizeMb = 50;
    private final int maxMediaAssetsPerSubmission = 10;
    private final int maxTitleLength = 255;
    private final long minScheduleLeadTimeHours = 2;
    private final long maxScheduleDaysAhead = 30;
    private final List<String> categories = List.of(
            "Research and Development",
            "Innovation",
            "Seminar / Webinar",
            "Workshop",
            "Conference / Forum",
            "Community Outreach",
            "Awards and Recognition",
            "Partnership / Collaboration"
    );
    private final List<String> availableTags = List.of(
            "Science", "Technology", "Engineering", "Mathematics",
            "Innovation", "Research", "Community", "Outreach",
            "Students", "Faculty", "Partnership", "DOST", "DASIG"
    );

    /**
     * Network-wide scheduling guard-rail switch (Page Settings). When false the
     * composer treats a preferred schedule and the 8:00 AM–8:00 PM publish
     * window as non-blocking; a future date is still required if one is set.
     */
    private boolean guardrailsEnforced = true;

    public List<String> getAllowedFileTypes() {
        return allowedFileTypes;
    }

    public List<String> getAllowedImageTypes() {
        return allowedImageTypes;
    }

    public List<String> getAllowedVideoTypes() {
        return allowedVideoTypes;
    }

    public int getMaxFileSizeMb() {
        return maxFileSizeMb;
    }

    public int getMaxMediaAssetsPerSubmission() {
        return maxMediaAssetsPerSubmission;
    }

    public int getMaxTitleLength() {
        return maxTitleLength;
    }

    public long getMinScheduleLeadTimeHours() {
        return minScheduleLeadTimeHours;
    }

    public long getMaxScheduleDaysAhead() {
        return maxScheduleDaysAhead;
    }

    public List<String> getCategories() {
        return categories;
    }

    public List<String> getAvailableTags() {
        return availableTags;
    }

    public boolean isGuardrailsEnforced() {
        return guardrailsEnforced;
    }

    public void setGuardrailsEnforced(boolean guardrailsEnforced) {
        this.guardrailsEnforced = guardrailsEnforced;
    }
}
