package com.dasigconnect.backend.model.dto.validation;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ValidationQueueCountsDto(
        long all,
        long pending,
        @JsonProperty("in_review") long inReview,
        @JsonProperty("needs_revision") long needsRevision,
        long scheduled,
        long published,
        long rejected) {
}
