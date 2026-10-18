package com.dasigconnect.backend.exception;

import java.time.Instant;
import java.util.Map;

public class SubscriptionLimitExceededException extends RuntimeException {

    private final Map<String, Object> details;

    public SubscriptionLimitExceededException(
            String resource,
            long used,
            long reserved,
            long requested,
            long limit,
            Instant resetsAt) {
        super("The organization's " + resource + " limit has been reached.");
        this.details = Map.of(
                "resource", resource,
                "used", used,
                "reserved", reserved,
                "requested", requested,
                "limit", limit,
                "resetsAt", resetsAt.toString());
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
