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

    public List<String> getAllowedFileTypes() { return allowedFileTypes; }
    public List<String> getAllowedImageTypes() { return allowedImageTypes; }
    public List<String> getAllowedVideoTypes() { return allowedVideoTypes; }
    public int getMaxFileSizeMb() { return maxFileSizeMb; }
    public int getMaxMediaAssetsPerSubmission() { return maxMediaAssetsPerSubmission; }
    public int getMaxTitleLength() { return maxTitleLength; }
    public long getMinScheduleLeadTimeHours() { return minScheduleLeadTimeHours; }
    public long getMaxScheduleDaysAhead() { return maxScheduleDaysAhead; }
}
