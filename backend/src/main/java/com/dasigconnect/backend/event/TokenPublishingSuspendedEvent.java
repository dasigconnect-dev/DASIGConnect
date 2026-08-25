package com.dasigconnect.backend.event;

import com.dasigconnect.backend.model.entity.Submission;

public record TokenPublishingSuspendedEvent(
        Submission submission,
        Stage stage,
        String detail) {

    public enum Stage {
        FIRST_ALERT,
        ESCALATION_24H,
        FINAL_FAILURE
    }
}
