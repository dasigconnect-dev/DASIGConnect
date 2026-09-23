package com.dasigconnect.backend.model.dto.submission;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SubmissionBucketCountsDto(
        long all,
        long drafts,
        @JsonProperty("action-needed") long actionNeeded,
        long submitted,
        long published,
        long failed) {
}
