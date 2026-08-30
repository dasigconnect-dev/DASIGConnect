package com.dasigconnect.backend.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.config.CacheConfig;
import com.dasigconnect.backend.model.dto.analytics.AiPerformanceDto;
import com.dasigconnect.backend.model.dto.analytics.AdminAnalyticsDto;
import com.dasigconnect.backend.model.dto.analytics.AnalyticsReportDto;
import com.dasigconnect.backend.model.dto.analytics.AnalyticsSummaryDto;
import com.dasigconnect.backend.model.dto.analytics.ContributorBreakdownDto;
import com.dasigconnect.backend.model.dto.analytics.ContributorAnalyticsDto;
import com.dasigconnect.backend.model.dto.analytics.FacebookEngagementSummaryDto;
import com.dasigconnect.backend.model.dto.analytics.KpiMetricDto;
import com.dasigconnect.backend.model.dto.analytics.OperationalHealthDto;
import com.dasigconnect.backend.model.dto.analytics.ValidatorAnalyticsDto;
import com.dasigconnect.backend.repository.AnalyticsRepository;
import com.dasigconnect.backend.repository.AnalyticsRepository.AiStats;
import com.dasigconnect.backend.repository.AnalyticsRepository.AnalyticsScope;
import com.dasigconnect.backend.repository.AnalyticsRepository.CompletenessStats;
import com.dasigconnect.backend.repository.AnalyticsRepository.ContributorStats;
import com.dasigconnect.backend.repository.AnalyticsRepository.FacebookEngagementStats;
import com.dasigconnect.backend.repository.AnalyticsRepository.OperationalStats;
import com.dasigconnect.backend.repository.AnalyticsRepository.PostingDelayStats;
import com.dasigconnect.backend.repository.AnalyticsRepository.PublishedPostStats;
import com.dasigconnect.backend.repository.AnalyticsRepository.ValidatorStats;
import com.dasigconnect.backend.security.JwtUserDetails;

@Service
@Transactional(readOnly = true)
public class MetricsAggregatorService {

    private static final double COMPLETENESS_TARGET = 95.0;
    private static final double POSTS_PER_MONTH_TARGET = 4.0;

    private final AnalyticsRepository analyticsRepository;

    public MetricsAggregatorService(AnalyticsRepository analyticsRepository) {
        this.analyticsRepository = analyticsRepository;
    }

    @Cacheable(
            cacheNames = CacheConfig.ANALYTICS_SUMMARY_CACHE,
            key = "#range + ':' + #institutionId + ':' + #category + ':' + #user.role() + ':' + #user.userId() + ':' + #user.institutionId()")
    public AnalyticsSummaryDto summary(String range, UUID institutionId, String category, JwtUserDetails user) {
        ReportingPeriod period = resolvePeriod(range);
        ReportingPeriod previousPeriod = previousPeriod(period);
        AnalyticsScope scope = scopeFor(user, institutionId, category);
        boolean adminView = isAdmin(scope.role());
        boolean contributorView = "contributor".equals(scope.role());
        // Admins and (network-wide) moderators both see cross-institution data;
        // only the admin-only operational blocks below stay gated on adminView.
        boolean networkView = adminView || "moderator".equals(scope.role());
        boolean institutionDrilldown = adminView && scope.institutionId() != null;

        PostingDelayStats delay = analyticsRepository.averagePostingDelay(period.start(), period.end(), scope);
        PostingDelayStats previousDelay = analyticsRepository.averagePostingDelay(
                previousPeriod.start(), previousPeriod.end(), scope);
        CompletenessStats completeness = analyticsRepository.contentCompleteness(period.start(), period.end(), scope);
        CompletenessStats previousCompleteness = analyticsRepository.contentCompleteness(
                previousPeriod.start(), previousPeriod.end(), scope);
        PublishedPostStats posts = analyticsRepository.publishedPostStats(period.start(), period.end(), scope);
        PublishedPostStats previousPosts = analyticsRepository.publishedPostStats(
                previousPeriod.start(), previousPeriod.end(), scope);

        double completenessRate = percent(completeness.completeCount(), completeness.totalCount());
        double previousCompletenessRate = percent(
                previousCompleteness.completeCount(), previousCompleteness.totalCount());
        double postsPerMonth = postsPerMonth(posts.totalCount(), period.days());
        List<ContributorBreakdownDto> contributorBreakdown = institutionDrilldown
                ? analyticsRepository.contributorBreakdown(period.start(), period.end(), scope)
                : List.of();
        ContributorAnalyticsDto contributorAnalytics = null;
        ValidatorAnalyticsDto validatorAnalytics = null;
        AdminAnalyticsDto adminAnalytics = null;
        OperationalHealthDto operationalHealth = null;
        AiPerformanceDto aiPerformanceDto = null;
        if (contributorView) {
            ContributorStats contributor = analyticsRepository.contributorStats(period.start(), period.end(), scope);
            contributorAnalytics = new ContributorAnalyticsDto(
                    contributor.submittedCount(),
                    contributor.publishedCount(),
                    contributor.revisionRequestCount(),
                    contributor.rejectedOrRevisionCount(),
                    round(percent(contributor.rejectedOrRevisionCount(), contributor.submittedCount())));
        }
        if (institutionDrilldown) {
            ValidatorStats validator = analyticsRepository.validatorStats(
                    period.start(), period.end(), Instant.now(), scope);
            validatorAnalytics = new ValidatorAnalyticsDto(
                    validator.submissionVolume(),
                    validator.pendingCount(),
                    validator.inReviewCount(),
                    validator.averageTurnaroundDays(),
                    validator.queueAgingCount());
        }
        if (adminView) {
            AiStats ai = analyticsRepository.aiPerformance(period.start(), period.end(), scope);
            aiPerformanceDto = aiPerformance(ai);

            OperationalStats operational = analyticsRepository.operationalHealth(
                    period.start(), period.end(), Instant.now(), scope);
            double publishingSuccessRate = percent(operational.successCount(), operational.attemptCount());
            operationalHealth = new OperationalHealthDto(
                    operational.workflowCount(),
                    operational.deadlineRiskCount(),
                    round(percent(operational.deadlineRiskCount(), operational.workflowCount())),
                    operational.overrideCount(),
                    round(percent(operational.overrideCount(), operational.workflowCount())),
                    operational.attemptCount(),
                    operational.successCount(),
                    round(publishingSuccessRate),
                    operational.onTimeCount(),
                    round(percent(operational.onTimeCount(), operational.successCount())),
                    operational.adminActionCount());
            adminAnalytics = new AdminAnalyticsDto(
                    Math.max(0, operational.attemptCount() - operational.successCount()),
                    operational.adminActionCount(),
                    posts.adminDirectCount());
        }

        FacebookEngagementStats engagement = analyticsRepository.facebookEngagement(period.start(), period.end(), scope);
        FacebookEngagementSummaryDto facebookEngagement = new FacebookEngagementSummaryDto(
                round(engagement.averageReach()),
                engagement.totalReactions(),
                engagement.totalComments(),
                engagement.totalShares(),
                engagement.sampleSize(),
                engagement.pendingCount());

        return new AnalyticsSummaryDto(
                period.label(),
                period.start(),
                period.end(),
                Instant.now(),
                scope.role(),
                adminView,
                scope.institutionId(),
                adminView ? analyticsRepository.institutionFilterOptions() : List.of(),
                new KpiMetricDto(
                        "averagePostingDelay",
                        "AVG Posting Delay",
                        round(delay.averageDays()),
                        "days",
                        delay.sampleSize(),
                        null,
                        true,
                        deltaPercent(delay.averageDays(), previousDelay.averageDays()),
                        analyticsRepository.postingDelaySparkline(period.start(), period.end(), scope),
                        null,
                        null),
                new KpiMetricDto(
                        "contentCompleteness",
                        "Content Completeness",
                        round(completenessRate),
                        "percent",
                        completeness.totalCount(),
                        COMPLETENESS_TARGET,
                        completenessRate >= COMPLETENESS_TARGET,
                        deltaPercent(completenessRate, previousCompletenessRate),
                        analyticsRepository.completenessSparkline(period.start(), period.end(), scope),
                        null,
                        null),
                new KpiMetricDto(
                        "totalPostsPublished",
                        "Total Posts Published",
                        posts.totalCount(),
                        "posts",
                        posts.totalCount(),
                        POSTS_PER_MONTH_TARGET,
                        postsPerMonth >= POSTS_PER_MONTH_TARGET,
                        deltaPercent(posts.totalCount(), previousPosts.totalCount()),
                        analyticsRepository.publishedPostsSparkline(period.start(), period.end(), scope),
                        "Admin direct posts",
                        posts.adminDirectCount()),
                networkView ? analyticsRepository.postsByInstitution(period.start(), period.end(), scope) : List.of(),
                contributorBreakdown,
                analyticsRepository.statusBreakdown(scope),
                analyticsRepository.contentIssues(period.start(), period.end(), scope),
                analyticsRepository.topCategories(period.start(), period.end(), scope),
                contributorAnalytics,
                validatorAnalytics,
                aiPerformanceDto,
                adminAnalytics,
                operationalHealth,
                facebookEngagement);
    }

    public CsvExport export(String metric, String range, UUID institutionId, String category, JwtUserDetails user) {
        ReportingPeriod period = resolvePeriod(range);
        AnalyticsScope scope = scopeFor(user, institutionId, category);
        String normalizedMetric = normalizeMetric(metric);
        assertMetricAllowed(normalizedMetric, scope);
        List<Map<String, Object>> rows = analyticsRepository.exportRows(
                normalizedMetric,
                period.start(),
                period.end(),
                scope);
        return new CsvExport(csvFilename(normalizedMetric, period, scope), toCsv(rows));
    }

    public AnalyticsReportDto report(String metric, String range, UUID institutionId, String category, JwtUserDetails user) {
        ReportingPeriod period = resolvePeriod(range);
        AnalyticsScope scope = scopeFor(user, institutionId, category);
        String normalizedMetric = normalizeMetric(metric);
        assertMetricAllowed(normalizedMetric, scope);
        return new AnalyticsReportDto(
                normalizedMetric,
                period.label(),
                period.start(),
                period.end(),
                analyticsRepository.dailyBreakdown(normalizedMetric, period.start(), period.end(), scope),
                analyticsRepository.submissionReportRows(period.start(), period.end(), scope),
                analyticsRepository.exportRows(normalizedMetric, period.start(), period.end(), scope));
    }

    private AiPerformanceDto aiPerformance(AiStats ai) {
        long totalEvents = ai.captionTotal() + ai.tagTotal() + ai.mediaTotal();
        return new AiPerformanceDto(
                ai.captionTotal(),
                ai.captionAccepted(),
                round(percent(ai.captionAccepted(), ai.captionTotal())),
                ai.tagTotal(),
                ai.tagCorrected(),
                round(percent(ai.tagCorrected(), ai.tagTotal())),
                ai.mediaTotal(),
                ai.mediaRelevant(),
                round(percent(ai.mediaRelevant(), ai.mediaTotal())),
                totalEvents < 20);
    }

    private boolean isAdmin(String role) {
        return "admin".equals(role);
    }

    private AnalyticsScope scopeFor(JwtUserDetails user, UUID institutionId, String category) {
        String role = user.role() == null ? "" : user.role().toLowerCase(Locale.ROOT);
        String normalizedCategory = category == null || category.isBlank() ? null : category.trim();
        return switch (role) {
            case "admin" -> new AnalyticsScope(role, institutionId, null, normalizedCategory);
            case "moderator" -> {
                // Moderators are network-wide: they get the network engagement +
                // workflow view (no institution filter). The admin-only blocks in
                // summary()/assertMetricAllowed() (operational health, AI performance,
                // override rate, admin workload) stay gated on isAdmin().
                if (institutionId != null) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Institution analytics filters are available to admins only.");
                }
                yield new AnalyticsScope("moderator", null, null, normalizedCategory);
            }
            case "contributor" -> {
                if (institutionId != null) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Institution analytics filters are available to admins only.");
                }
                yield new AnalyticsScope("contributor", user.institutionId(), user.userId(), normalizedCategory);
            }
            default -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported analytics role.");
        };
    }

    private ReportingPeriod resolvePeriod(String rawRange) {
        String range = rawRange == null || rawRange.isBlank()
                ? "30d"
                : rawRange.toLowerCase(Locale.ROOT);
        Instant end = Instant.now();
        return switch (range) {
            case "7d" -> new ReportingPeriod("7d", end.minusSeconds(7L * 86_400), end);
            case "30d" -> new ReportingPeriod("30d", end.minusSeconds(30L * 86_400), end);
            case "90d" -> new ReportingPeriod("90d", end.minusSeconds(90L * 86_400), end);
            case "ytd" -> {
                LocalDate today = LocalDate.now(ZoneOffset.UTC);
                Instant start = today.withDayOfYear(1).atStartOfDay().toInstant(ZoneOffset.UTC);
                yield new ReportingPeriod("ytd", start, end);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported analytics range. Use 7d, 30d, 90d, or ytd.");
        };
    }

    private ReportingPeriod previousPeriod(ReportingPeriod period) {
        long seconds = java.time.Duration.between(period.start(), period.end()).getSeconds();
        if ("ytd".equals(period.label())) {
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            LocalDate previousStart = today.minusYears(1).withDayOfYear(1);
            LocalDate previousEnd = today.minusYears(1);
            return new ReportingPeriod(
                    "previous-ytd",
                    previousStart.atStartOfDay().toInstant(ZoneOffset.UTC),
                    previousEnd.atStartOfDay().toInstant(ZoneOffset.UTC));
        }
        return new ReportingPeriod("previous-" + period.label(), period.start().minusSeconds(seconds), period.start());
    }

    private String normalizeMetric(String metric) {
        String normalized = metric == null ? "" : metric.toLowerCase(Locale.ROOT);
        if (List.of(
                "posting-delay",
                "content-completeness",
                "posts-by-institution",
                "ai-performance",
                "operational-health",
                "facebook-engagement").contains(normalized)) {
            return normalized;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Unsupported export metric. Use posting-delay, content-completeness, posts-by-institution, "
                        + "ai-performance, operational-health, or facebook-engagement.");
    }

    private String toCsv(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return "message\r\n\"No data for selected period\"\r\n";
        }
        List<String> headers = rows.getFirst().keySet().stream().sorted().toList();
        StringBuilder csv = new StringBuilder();
        csv.append(headers.stream().map(this::escapeCsv).collect(Collectors.joining(","))).append("\r\n");
        for (Map<String, Object> row : rows) {
            csv.append(headers.stream()
                    .map(header -> escapeCsv(String.valueOf(row.getOrDefault(header, ""))))
                    .collect(Collectors.joining(",")))
                    .append("\r\n");
        }
        return csv.toString();
    }

    private String escapeCsv(String value) {
        String escaped = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private double percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return (numerator * 100.0) / denominator;
    }

    private void assertMetricAllowed(String metric, AnalyticsScope scope) {
        if (("operational-health".equals(metric) || "ai-performance".equals(metric)) && !isAdmin(scope.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This analytics metric is available to admins only.");
        }
    }

    private Double deltaPercent(double current, double previous) {
        if (previous == 0) {
            return null;
        }
        return round(((current - previous) / previous) * 100.0);
    }

    private double postsPerMonth(long posts, long days) {
        if (days <= 0) {
            return 0;
        }
        return posts / (days / 30.0);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String csvFilename(String metric, ReportingPeriod period, AnalyticsScope scope) {
        String role = switch (scope.role()) {
            case "admin" -> "Admin";
            case "contributor" -> "Contributor";
            default -> "User";
        };
        String scopeLabel = scope.institutionId() == null && isAdmin(scope.role()) ? "Network" : "Institution";
        return "DASIGConnect_Analytics_%s_%s_%s_%s.csv".formatted(
                role,
                scopeLabel,
                metric.replace("-", "_"),
                period.label().toUpperCase(Locale.ROOT));
    }

    public record CsvExport(String filename, String content) {}

    private record ReportingPeriod(String label, Instant start, Instant end) {
        long days() {
            return Math.max(1, java.time.Duration.between(start, end).toDays());
        }
    }
}
