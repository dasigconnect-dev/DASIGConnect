package com.dasigconnect.backend.model.dto.submission;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SubmissionBucketCountsDto(
        long all,
        long drafts,
        /** Needs revision only; rejected posts have their own bucket. */
        @JsonProperty("action-needed") long actionNeeded,
        long rejected,
        long submitted,
        long published,
        long failed) {
}
