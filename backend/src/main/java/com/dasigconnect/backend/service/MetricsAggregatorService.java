package com.dasigconnect.backend.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.config.CacheConfig;
import com.dasigconnect.backend.model.dto.analytics.AiPerformanceDto;
import com.dasigconnect.backend.model.dto.analytics.AnalyticsSummaryDto;
import com.dasigconnect.backend.model.dto.analytics.KpiMetricDto;
import com.dasigconnect.backend.model.dto.analytics.OperationalHealthDto;
import com.dasigconnect.backend.repository.AnalyticsRepository;
import com.dasigconnect.backend.repository.AnalyticsRepository.AiStats;
import com.dasigconnect.backend.repository.AnalyticsRepository.AnalyticsScope;
import com.dasigconnect.backend.repository.AnalyticsRepository.CompletenessStats;
import com.dasigconnect.backend.repository.AnalyticsRepository.OperationalStats;
import com.dasigconnect.backend.repository.AnalyticsRepository.PostingDelayStats;
import com.dasigconnect.backend.repository.AnalyticsRepository.PublishedPostStats;
import com.dasigconnect.backend.security.JwtUserDetails;

@Service
@Transactional(readOnly = true)
public class MetricsAggregatorService {

    private static final double COMPLETENESS_TARGET = 95.0;
    private static final double POSTS_PER_MONTH_TARGET = 4.0;
    private static final double PUBLISHING_SUCCESS_TARGET = 95.0;

    private final AnalyticsRepository analyticsRepository;

    public MetricsAggregatorService(AnalyticsRepository analyticsRepository) {
        this.analyticsRepository = analyticsRepository;
    }

    @Cacheable(
            cacheNames = CacheConfig.ANALYTICS_SUMMARY_CACHE,
            key = "#range + ':' + #user.role() + ':' + #user.userId() + ':' + #user.institutionId()")
    public AnalyticsSummaryDto summary(String range, JwtUserDetails user) {
        ReportingPeriod period = resolvePeriod(range);
        AnalyticsScope scope = scopeFor(user);

        PostingDelayStats delay = analyticsRepository.averagePostingDelay(period.start(), period.end(), scope);
        CompletenessStats completeness = analyticsRepository.contentCompleteness(period.start(), period.end(), scope);
        PublishedPostStats posts = analyticsRepository.publishedPostStats(period.start(), period.end(), scope);
        AiStats ai = analyticsRepository.aiPerformance(period.start(), period.end(), scope);
        OperationalStats operational = analyticsRepository.operationalHealth(
                period.start(), period.end(), Instant.now(), scope);

        double completenessRate = percent(completeness.completeCount(), completeness.totalCount());
        double postsPerMonth = postsPerMonth(posts.totalCount(), period.days());
        double publishingSuccessRate = percent(operational.successCount(), operational.attemptCount());

        return new AnalyticsSummaryDto(
                period.label(),
                period.start(),
                period.end(),
                new KpiMetricDto(
                        "averagePostingDelay",
                        "AVG Posting Delay",
                        round(delay.averageDays()),
                        "days",
                        delay.sampleSize(),
                        null,
                        true),
                new KpiMetricDto(
                        "contentCompleteness",
                        "Content Completeness",
                        round(completenessRate),
                        "percent",
                        completeness.totalCount(),
                        COMPLETENESS_TARGET,
                        completenessRate >= COMPLETENESS_TARGET),
                new KpiMetricDto(
                        "totalPostsPublished",
                        "Total Posts Published",
                        posts.totalCount(),
                        "posts",
                        posts.totalCount(),
                        POSTS_PER_MONTH_TARGET,
                        postsPerMonth >= POSTS_PER_MONTH_TARGET),
                analyticsRepository.postsByInstitution(period.start(), period.end(), scope),
                aiPerformance(ai),
                new OperationalHealthDto(
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
                        operational.adminActionCount()));
    }

    public CsvExport export(String metric, String range, JwtUserDetails user) {
        ReportingPeriod period = resolvePeriod(range);
        List<Map<String, Object>> rows = analyticsRepository.exportRows(
                normalizeMetric(metric),
                period.start(),
                period.end(),
                scopeFor(user));
        return new CsvExport("dasigconnect-" + metric + "-" + period.label() + ".csv", toCsv(rows));
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

    private AnalyticsScope scopeFor(JwtUserDetails user) {
        String role = user.role() == null ? "" : user.role().toLowerCase(Locale.ROOT);
        return switch (role) {
            case "administrator" -> new AnalyticsScope("administrator", null, null);
            case "validator" -> new AnalyticsScope("validator", user.institutionId(), null);
            case "contributor" -> new AnalyticsScope("contributor", user.institutionId(), user.userId());
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

    private String normalizeMetric(String metric) {
        String normalized = metric == null ? "" : metric.toLowerCase(Locale.ROOT);
        if (List.of(
                "posting-delay",
                "content-completeness",
                "posts-by-institution",
                "ai-performance",
                "operational-health").contains(normalized)) {
            return normalized;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Unsupported export metric. Use posting-delay, content-completeness, posts-by-institution, ai-performance, or operational-health.");
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

    private double postsPerMonth(long posts, long days) {
        if (days <= 0) {
            return 0;
        }
        return posts / (days / 30.0);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record CsvExport(String filename, String content) {}

    private record ReportingPeriod(String label, Instant start, Instant end) {
        long days() {
            return Math.max(1, java.time.Duration.between(start, end).toDays());
        }
    }
}
