package com.dasigconnect.backend.model.dto.validation;

public record ValidationDashboardSummaryDto(
        long awaitingReview,
        long approvedThisMonth,
        long rejectedThisMonth,
        long contributorCount) {
}
