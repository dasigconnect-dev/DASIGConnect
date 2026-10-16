package com.dasigconnect.backend.model.dto.resolution;

import java.util.List;

public record FailedPublicationPageDto(
        List<FailedPublicationDto> items,
        int page,
        int pageSize,
        long totalCount,
        int totalPages,
        boolean hasNext,
        long failureCount) {
}
