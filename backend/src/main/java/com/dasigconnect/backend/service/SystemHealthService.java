package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.exception.TokenStatusDto;
import com.dasigconnect.backend.model.dto.systemhealth.BackgroundJobHealthDto;
import com.dasigconnect.backend.model.dto.systemhealth.ExternalServiceHealthDto;
import com.dasigconnect.backend.model.dto.systemhealth.HealthStatus;
import com.dasigconnect.backend.model.dto.systemhealth.OperationalMetricDto;
import com.dasigconnect.backend.model.dto.systemhealth.StorageMetricDto;
import com.dasigconnect.backend.model.dto.systemhealth.SystemHealthSummaryDto;
import com.dasigconnect.backend.model.entity.ScheduledJobRun;
import com.dasigconnect.backend.repository.ScheduledJobRunRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemHealthService {

    private static final Logger log = LoggerFactory.getLogger(SystemHealthService.class);

    private static final List<String> EXPECTED_JOBS = List.of(
            "PublishingSchedulerJob",
            "StaleSubmissionDetectorJob",
            "EmbeddingReconciliationJob",
            "MediaAssetRetentionPurgeJob",
            "StaleDraftSlotReleaseJob",
            "TokenHealthCheckJob",
            "TokenPublishingEscalationJob",
            "SocialEngagementSyncJob",
            "AbandonmentDetectorJob",
            "ExpiredOverrideCleanupJob");

    private final JdbcTemplate jdbcTemplate;
    private final ScheduledJobRunRepository scheduledJobRunRepository;
    private final TokenManagementService tokenManagementService;
    private final JavaMailSender mailSender;
    private final HttpClient httpClient;
    private final long databaseLimitBytes;
    private final long mediaLimitBytes;
    private final double storageWarningThreshold;
    private final String anthropicApiKey;
    private final String voyageApiKey;

    public SystemHealthService(
            JdbcTemplate jdbcTemplate,
            ScheduledJobRunRepository scheduledJobRunRepository,
            TokenManagementService tokenManagementService,
            JavaMailSender mailSender,
            @Value("${app.system-health.database-limit-bytes:1073741824}") long databaseLimitBytes,
            @Value("${app.system-health.media-limit-bytes:1073741824}") long mediaLimitBytes,
            @Value("${app.system-health.storage-warning-threshold-percent:80}") double storageWarningThreshold,
            @Value("${anthropic.api.key:}") String anthropicApiKey,
            @Value("${voyage.api.key:}") String voyageApiKey) {
        this.jdbcTemplate = jdbcTemplate;
        this.scheduledJobRunRepository = scheduledJobRunRepository;
        this.tokenManagementService = tokenManagementService;
        this.mailSender = mailSender;
        this.databaseLimitBytes = databaseLimitBytes;
        this.mediaLimitBytes = mediaLimitBytes;
        this.storageWarningThreshold = storageWarningThreshold;
        this.anthropicApiKey = anthropicApiKey;
        this.voyageApiKey = voyageApiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Transactional(readOnly = true)
    public SystemHealthSummaryDto summary() {
        List<StorageMetricDto> storage = storage();
        List<ExternalServiceHealthDto> services = externalServices();
        List<BackgroundJobHealthDto> jobs = backgroundJobs();
        List<OperationalMetricDto> metrics = operationalMetrics();
        List<HealthStatus> statuses = new ArrayList<>();
        storage.forEach(item -> statuses.add(item.status()));
        services.forEach(item -> statuses.add(item.status()));
        jobs.forEach(item -> statuses.add(item.status()));
        metrics.forEach(item -> statuses.add(item.status()));

        int unhealthy = count(statuses, HealthStatus.UNHEALTHY);
        int unavailable = count(statuses, HealthStatus.UNAVAILABLE);
        int warnings = count(statuses, HealthStatus.WARNING);
        HealthStatus overall = unhealthy > 0 ? HealthStatus.UNHEALTHY
                : warnings > 0 ? HealthStatus.WARNING
                : unavailable > 0 ? HealthStatus.UNAVAILABLE
                : HealthStatus.HEALTHY;

        return new SystemHealthSummaryDto(
                Instant.now(),
                overall,
                storage,
                services,
                jobs,
                metrics,
                warnings,
                unhealthy,
                unavailable);
    }

    @Transactional(readOnly = true)
    public List<StorageMetricDto> storage() {
        return List.of(databaseStorage(), mediaStorage());
    }

    @Transactional(readOnly = true)
    public List<ExternalServiceHealthDto> externalServices() {
        List<ExternalServiceHealthDto> services = new ArrayList<>();
        services.add(facebookTokenHealth());
        services.add(httpReachability("Anthropic Claude Vision API", "https://api.anthropic.com/v1/messages", anthropicApiKey));
        services.add(httpReachability("Voyage AI API", "https://api.voyageai.com/v1/embeddings", voyageApiKey));
        services.add(emailHealth());
        return services;
    }

    @Transactional(readOnly = true)
    public List<BackgroundJobHealthDto> backgroundJobs() {
        Map<String, ScheduledJobRun> latestByName = new LinkedHashMap<>();
        for (ScheduledJobRun run : scheduledJobRunRepository.findLatestRunsByJobName()) {
            latestByName.put(run.getJobName(), run);
        }
        for (String expected : EXPECTED_JOBS) {
            latestByName.putIfAbsent(expected, null);
        }
        return latestByName.entrySet().stream()
                .map(entry -> toJobDto(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(BackgroundJobHealthDto::jobName))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OperationalMetricDto> operationalMetrics() {
        Instant start = Instant.now().minus(30, ChronoUnit.DAYS);
        return List.of(
                approvalTurnaround(start),
                editAndApproveRate(start),
                manualFallbackResolutionRate(start),
                publishSuccessRate(start),
                liveEventFastTrackVolume(start));
    }

    public String exportSnapshotCsv() {
        SystemHealthSummaryDto dto = summary();
        StringBuilder csv = new StringBuilder("section,metric,status,value,unit,detail\n");
        for (StorageMetricDto item : dto.storage()) {
            row(csv, "storage", item.name(), item.status(), item.usedPercent(), "percent", item.detail());
        }
        for (ExternalServiceHealthDto item : dto.externalServices()) {
            row(csv, "external_service", item.service(), item.status(), "", "", item.detail());
        }
        for (BackgroundJobHealthDto item : dto.backgroundJobs()) {
            row(csv, "background_job", item.jobName(), item.status(), item.lastDurationMs(), "ms", item.detail());
        }
        for (OperationalMetricDto item : dto.operationalMetrics()) {
            row(csv, "operational_metric", item.label(), item.status(), item.value(), item.unit(), item.detail());
        }
        return csv.toString();
    }

    private StorageMetricDto databaseStorage() {
        try {
            Long used = jdbcTemplate.queryForObject(
                    "SELECT pg_database_size(current_database())",
                    Long.class);
            return storageMetric("Database storage", used == null ? 0 : used, databaseLimitBytes,
                    "PostgreSQL database size relative to configured platform tier limit.");
        } catch (Exception ex) {
            return new StorageMetricDto("Database storage", HealthStatus.UNAVAILABLE, 0, databaseLimitBytes,
                    0, storageWarningThreshold, "Database storage metric could not be retrieved.");
        }
    }

    private StorageMetricDto mediaStorage() {
        try {
            Long used = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(file_size_bytes), 0) FROM media_assets WHERE purged_at IS NULL",
                    Long.class);
            return storageMetric("Media storage", used == null ? 0 : used, mediaLimitBytes,
                    "Tracked media asset bytes relative to configured platform tier limit.");
        } catch (Exception ex) {
            return new StorageMetricDto("Media storage", HealthStatus.UNAVAILABLE, 0, mediaLimitBytes,
                    0, storageWarningThreshold, "Media storage metric could not be retrieved.");
        }
    }

    private StorageMetricDto storageMetric(String name, long used, long limit, String detail) {
        double percent = limit > 0 ? round(used * 100.0 / limit) : 0;
        HealthStatus status = limit <= 0 ? HealthStatus.UNAVAILABLE
                : percent >= storageWarningThreshold ? HealthStatus.WARNING
                : HealthStatus.HEALTHY;
        return new StorageMetricDto(name, status, used, limit, percent, storageWarningThreshold, detail);
    }

    private ExternalServiceHealthDto facebookTokenHealth() {
        try {
            List<TokenStatusDto> tokens = tokenManagementService.getAllTokenStatuses();
            if (tokens.isEmpty()) {
                return service("Facebook Graph API", HealthStatus.UNAVAILABLE,
                        "No Facebook Page Access Token is configured.", null, null);
            }
            TokenStatusDto mostUrgent = tokens.stream()
                    .min(Comparator.comparingInt(token -> tokenPriority(token.getTokenStatus())))
                    .orElse(tokens.get(0));
            HealthStatus status = switch (mostUrgent.getTokenStatus()) {
                case "ACTIVE" -> HealthStatus.HEALTHY;
                case "EXPIRING" -> HealthStatus.WARNING;
                case "EXPIRED", "INVALID" -> HealthStatus.UNHEALTHY;
                default -> HealthStatus.UNAVAILABLE;
            };
            Long seconds = mostUrgent.getExpiresAt() == null ? null
                    : Duration.between(Instant.now(), mostUrgent.getExpiresAt()).getSeconds();
            return service("Facebook Graph API", status,
                    "Facebook Page token is " + tokenStatusLabel(mostUrgent.getTokenStatus()) + ".",
                    mostUrgent.getExpiresAt(), seconds);
        } catch (Exception ex) {
            return service("Facebook Graph API", HealthStatus.UNAVAILABLE,
                    "Facebook token status could not be retrieved.", null, null);
        }
    }

    private ExternalServiceHealthDto httpReachability(String service, String url, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return service(service, HealthStatus.UNAVAILABLE, "API key is not configured.", null, null);
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(3))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            HealthStatus status = response.statusCode() >= 500 ? HealthStatus.UNHEALTHY : HealthStatus.HEALTHY;
            String detail = status == HealthStatus.UNHEALTHY
                    ? "Service endpoint reported an unhealthy response."
                    : "Service endpoint responded to the reachability probe.";
            return service(service, status, detail, null, null);
        } catch (Exception ex) {
            return service(service, HealthStatus.UNAVAILABLE, "Reachability probe could not be completed.", null, null);
        }
    }

    private ExternalServiceHealthDto emailHealth() {
        if (mailSender instanceof JavaMailSenderImpl sender) {
            try {
                sender.testConnection();
                return service("Email Service Provider", HealthStatus.HEALTHY,
                        "SMTP connection succeeded.", null, null);
            } catch (Exception ex) {
                return service("Email Service Provider", HealthStatus.UNAVAILABLE,
                        "SMTP connection could not be verified.", null, null);
            }
        }
        return service("Email Service Provider", HealthStatus.UNAVAILABLE,
                "Mail sender does not expose a connection health probe.", null, null);
    }

    private BackgroundJobHealthDto toJobDto(String jobName, ScheduledJobRun run) {
        String displayName = jobDisplayName(jobName);
        if (run == null) {
            return new BackgroundJobHealthDto(displayName, HealthStatus.UNAVAILABLE,
                    null, null, null, null, null, "No recorded run yet.");
        }
        boolean failed = "FAILED".equalsIgnoreCase(run.getStatus());
        Instant staleCutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        HealthStatus status = failed ? HealthStatus.UNHEALTHY
                : run.getStartedAt().isBefore(staleCutoff) ? HealthStatus.WARNING
                : HealthStatus.HEALTHY;
        return new BackgroundJobHealthDto(
                displayName,
                status,
                run.getStartedAt(),
                failed ? null : run.getCompletedAt(),
                failed ? run.getCompletedAt() : null,
                run.getDurationMs(),
                run.getErrorMessage(),
                failed ? "Last run failed." : "Last run completed successfully.");
    }

    private OperationalMetricDto approvalTurnaround(Instant start) {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap("""
                    SELECT COALESCE(AVG(EXTRACT(EPOCH FROM (vl.created_at - COALESCE(s.submitted_at, s.created_at))) / 3600.0), 0) AS value,
                           COUNT(*) AS sample_size
                    FROM validation_logs vl
                    JOIN submissions s ON s.id = vl.submission_id
                    WHERE vl.created_at >= ?
                      AND vl.action IN ('approved', 'edited_and_approved')
                    """, start);
            double value = number(row.get("value"));
            long sample = longNumber(row.get("sample_size"));
            if (sample == 0) {
                return noSampleMetric("approval_turnaround_time", "Approval turnaround time", "hours",
                        "No approvals were recorded in the last 30 days.");
            }
            return metric("approval_turnaround_time", "Approval turnaround time", value, "hours", sample,
                    value > 24 ? HealthStatus.WARNING : HealthStatus.HEALTHY,
                    "Average time from submission to approval in the last 30 days.");
        } catch (Exception ex) {
            return unavailableMetric("approval_turnaround_time", "Approval turnaround time", "hours", ex);
        }
    }

    private OperationalMetricDto editAndApproveRate(Instant start) {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap("""
                    SELECT COUNT(*) AS approvals,
                           COUNT(CASE WHEN action = 'edited_and_approved' THEN 1 END) AS edited
                    FROM validation_logs
                    WHERE created_at >= ?
                      AND action IN ('approved', 'edited_and_approved')
                    """, start);
            long approvals = longNumber(row.get("approvals"));
            long edited = longNumber(row.get("edited"));
            if (approvals == 0) {
                return noSampleMetric("edit_approve_rate", "Edit & Approve rate", "percent",
                        "No approval decisions were recorded in the last 30 days.");
            }
            double rate = approvals == 0 ? 0 : round(edited * 100.0 / approvals);
            return metric("edit_approve_rate", "Edit & Approve rate", rate, "percent", approvals,
                    HealthStatus.HEALTHY, "Share of approvals completed through Edit & Approve in the last 30 days.");
        } catch (Exception ex) {
            return unavailableMetric("edit_approve_rate", "Edit & Approve rate", "percent", ex);
        }
    }

    private OperationalMetricDto manualFallbackResolutionRate(Instant start) {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap("""
                    SELECT COUNT(CASE WHEN action = 'MANUAL_PUBLISH_STARTED' THEN 1 END) AS started,
                           COUNT(CASE WHEN action = 'MANUAL_PUBLISH_COMPLETE' THEN 1 END) AS completed
                    FROM audit_log
                    WHERE created_at >= ?
                      AND action IN ('MANUAL_PUBLISH_STARTED', 'MANUAL_PUBLISH_COMPLETE')
                    """, start);
            long started = longNumber(row.get("started"));
            long completed = longNumber(row.get("completed"));
            if (started == 0) {
                return noSampleMetric("manual_fallback_resolution_rate", "Manual fallback resolution rate", "percent",
                        "No manual publishing fallback workflows were started in the last 30 days.");
            }
            double rate = started == 0 ? 100 : round(completed * 100.0 / started);
            return metric("manual_fallback_resolution_rate", "Manual fallback resolution rate", rate, "percent", started,
                    rate < 80 ? HealthStatus.WARNING : HealthStatus.HEALTHY,
                    "Completed manual publishing workflows divided by started workflows in the last 30 days.");
        } catch (Exception ex) {
            return unavailableMetric("manual_fallback_resolution_rate", "Manual fallback resolution rate", "percent", ex);
        }
    }

    private OperationalMetricDto publishSuccessRate(Instant start) {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap("""
                    SELECT COUNT(*) AS attempts,
                           COUNT(CASE WHEN result = 'success' THEN 1 END) AS successes
                    FROM publication_attempts
                    WHERE attempted_at >= ?
                    """, start);
            long attempts = longNumber(row.get("attempts"));
            long successes = longNumber(row.get("successes"));
            if (attempts == 0) {
                return noSampleMetric("publish_success_rate", "Publish success rate", "percent",
                        "No publishing attempts were recorded in the last 30 days.");
            }
            double rate = attempts == 0 ? 100 : round(successes * 100.0 / attempts);
            return metric("publish_success_rate", "Publish success rate", rate, "percent", attempts,
                    rate < 95 ? HealthStatus.WARNING : HealthStatus.HEALTHY,
                    "Successful Facebook publication attempts divided by total attempts in the last 30 days.");
        } catch (Exception ex) {
            return unavailableMetric("publish_success_rate", "Publish success rate", "percent", ex);
        }
    }

    private OperationalMetricDto liveEventFastTrackVolume(Instant start) {
        try {
            Long count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM submissions
                    WHERE fast_track = true
                      AND created_at >= ?
                    """, Long.class, start);
            long value = count == null ? 0 : count;
            return metric("live_event_fast_track_volume", "Live Event Fast-Track volume", value, "count", value,
                    HealthStatus.HEALTHY, "Fast-track live event submissions created in the last 30 days.");
        } catch (Exception ex) {
            return unavailableMetric("live_event_fast_track_volume", "Live Event Fast-Track volume", "count", ex);
        }
    }

    private ExternalServiceHealthDto service(String service, HealthStatus status, String detail, Instant expiresAt, Long secondsUntilExpiry) {
        return new ExternalServiceHealthDto(service, status, detail, Instant.now(), expiresAt, secondsUntilExpiry);
    }

    private OperationalMetricDto metric(String key, String label, double value, String unit, long sample, HealthStatus status, String detail) {
        return new OperationalMetricDto(key, label, status, round(value), unit, sample, detail);
    }

    private OperationalMetricDto unavailableMetric(String key, String label, String unit, Exception ex) {
        log.warn("System health operational metric '{}' could not be retrieved: {}", key, ex.getMessage());
        return metric(key, label, 0, unit, 0, HealthStatus.UNAVAILABLE, "Metric could not be retrieved.");
    }

    private OperationalMetricDto noSampleMetric(String key, String label, String unit, String detail) {
        return metric(key, label, 0, unit, 0, HealthStatus.HEALTHY, detail);
    }

    private static int count(List<HealthStatus> statuses, HealthStatus status) {
        return (int) statuses.stream().filter(status::equals).count();
    }

    private static int tokenPriority(String status) {
        return switch (status) {
            case "EXPIRED", "INVALID" -> 0;
            case "EXPIRING" -> 1;
            case "ACTIVE" -> 2;
            default -> 3;
        };
    }

    private static String tokenStatusLabel(String status) {
        return switch (status) {
            case "ACTIVE" -> "active";
            case "EXPIRING" -> "nearing expiry";
            case "EXPIRED" -> "expired";
            case "INVALID" -> "invalid";
            default -> "unavailable";
        };
    }

    private static String jobDisplayName(String jobName) {
        return switch (jobName) {
            case "PublishingSchedulerJob" -> "Publishing Scheduler";
            case "StaleSubmissionDetectorJob" -> "Stale Submission Detector";
            case "EmbeddingReconciliationJob" -> "Embedding Reconciliation";
            case "MediaAssetRetentionPurgeJob" -> "Media Asset Retention Purge";
            case "StaleDraftSlotReleaseJob" -> "Stale Draft Slot Release";
            case "TokenHealthCheckJob" -> "Token Health Check";
            case "TokenPublishingEscalationJob" -> "Token Publishing Escalation";
            case "SocialEngagementSyncJob" -> "Social Engagement Sync";
            case "AbandonmentDetectorJob" -> "Abandonment Detector";
            case "ExpiredOverrideCleanupJob" -> "Expired Override Cleanup";
            default -> jobName.replaceAll("(?<=[a-z])(?=[A-Z])", " ").replace(" Job", "");
        };
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    private static long longNumber(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static void row(StringBuilder csv, String section, String metric, HealthStatus status, Object value, String unit, String detail) {
        csv.append(escape(section)).append(',')
                .append(escape(metric)).append(',')
                .append(status).append(',')
                .append(escape(value == null ? "" : String.valueOf(value))).append(',')
                .append(escape(unit)).append(',')
                .append(escape(detail)).append('\n');
    }

    private static String escape(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
