package com.dasigconnect.backend.model.dto.validation;

import java.util.List;

import com.dasigconnect.backend.model.dto.submission.SubmissionSummaryDto;

public record ValidationQueuePageDto(
        List<SubmissionSummaryDto> items,
        int page,
        int pageSize,
        long totalCount,
        int totalPages,
        boolean hasNext,
        ValidationQueueCountsDto counts) {
}
