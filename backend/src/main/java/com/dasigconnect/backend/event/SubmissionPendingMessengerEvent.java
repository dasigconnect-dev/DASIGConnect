package com.dasigconnect.backend.event;

import java.util.List;
import java.util.UUID;

public record SubmissionPendingMessengerEvent(
        UUID submissionId, String eventTitle, List<UUID> validatorIds) {}
