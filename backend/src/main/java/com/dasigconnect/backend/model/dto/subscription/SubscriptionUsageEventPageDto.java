package com.dasigconnect.backend.model.dto.subscription;

import java.util.List;

public record SubscriptionUsageEventPageDto(
        List<SubscriptionUsageEventDto> items,
        int page,
        int pageSize,
        long totalElements,
        int totalPages) {
}
