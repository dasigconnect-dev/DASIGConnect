package com.dasigconnect.backend.model.dto.submission;

import java.util.List;

public record SubmissionPageDto(
        List<SubmissionSummaryDto> items,
        int page,
        int pageSize,
        long totalCount,
        int totalPages,
        boolean hasNext,
        SubmissionBucketCountsDto counts) {
}
