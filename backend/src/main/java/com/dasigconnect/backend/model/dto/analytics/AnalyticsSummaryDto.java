package com.dasigconnect.backend.model.dto.analytics;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnalyticsSummaryDto(
        String range,
        Instant periodStart,
        Instant periodEnd,
        Instant lastUpdated,
        String scopeRole,
        boolean adminView,
        UUID selectedInstitutionId,
        List<InstitutionFilterOptionDto> institutionFilterOptions,
        KpiMetricDto averagePostingDelay,
        KpiMetricDto contentCompleteness,
        KpiMetricDto totalPostsPublished,
        List<InstitutionPostsDto> postsByInstitution,
        List<ContributorBreakdownDto> contributorBreakdown,
        List<StatusBreakdownDto> statusBreakdown,
        List<ContentIssueDto> contentIssues,
        List<CategoryPerformanceDto> topCategories,
        ContributorAnalyticsDto contributorAnalytics,
        ValidatorAnalyticsDto validatorAnalytics,
        AiPerformanceDto aiPerformance,
        AdminAnalyticsDto adminAnalytics,
        OperationalHealthDto operationalHealth) {
}
