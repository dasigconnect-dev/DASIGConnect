package com.dasigconnect.backend.model.dto.exception;

public class ResolutionCountsDto {

    private long failures;
    private long timeouts;
    private long overrides;

    public ResolutionCountsDto(long failures, long timeouts, long overrides) {
        this.failures = failures;
        this.timeouts = timeouts;
        this.overrides = overrides;
    }

    public long getFailures() { return failures; }
    public long getTimeouts() { return timeouts; }
    public long getOverrides() { return overrides; }
}
