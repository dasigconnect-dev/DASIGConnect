package com.dasigconnect.backend.model.dto.media;

import java.time.Instant;
import java.util.UUID;

import com.dasigconnect.backend.model.entity.SubmissionMediaAsset;

public record MediaAssetUsageDto(
        UUID submissionId,
        String eventTitle,
        Instant submittedAt,
        String status,
        String deepLink) {

    public static MediaAssetUsageDto from(SubmissionMediaAsset sma) {
        var s = sma.getSubmission();
        return new MediaAssetUsageDto(
                s.getId(),
                s.getEventTitle(),
                s.getSubmittedAt(),
                s.getStatus().name(),
                "/submissions/" + s.getId());
    }
}
