package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.model.dto.systemhealth.BackgroundJobHealthDto;
import com.dasigconnect.backend.model.dto.systemhealth.HealthStatus;
import com.dasigconnect.backend.model.dto.systemhealth.OperationalMetricDto;
import com.dasigconnect.backend.model.entity.ScheduledJobRun;
import com.dasigconnect.backend.repository.ScheduledJobRunRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;

class SystemHealthServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ScheduledJobRunRepository scheduledJobRunRepository = mock(ScheduledJobRunRepository.class);
    private final MediaStorageService mediaStorage = mock(MediaStorageService.class);

    private final SystemHealthService service = new SystemHealthService(
            jdbcTemplate,
            scheduledJobRunRepository,
            mock(TokenManagementService.class),
            mock(JavaMailSender.class),
            mediaStorage,
            1_000_000,
            1_000_000,
            80,
            95,
            "",
            "");

    private static ScheduledJobRun run(String jobName, String status, Instant startedAt) {
        ScheduledJobRun run = new ScheduledJobRun();
        run.setJobName(jobName);
        run.setStatus(status);
        run.setStartedAt(startedAt);
        run.setCompletedAt(startedAt.plusSeconds(1));
        run.setDurationMs(1000L);
        return run;
    }

    private BackgroundJobHealthDto jobDto(String displayName) {
        return service.backgroundJobs().stream()
                .filter(j -> j.jobName().equals(displayName))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void operationalMetrics_whenNoRecentActivity_returnsHealthyNoActivityMetrics() {
        when(jdbcTemplate.queryForMap(anyString(), any()))
                .thenReturn(Map.of("value", 0, "sample_size", 0))
                .thenReturn(Map.of("approvals", 0, "edited", 0))
                .thenReturn(Map.of("started", 0, "completed", 0))
                .thenReturn(Map.of("attempts", 0, "successes", 0));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any()))
                .thenReturn(0L);

        List<OperationalMetricDto> metrics = service.operationalMetrics();

        assertThat(metrics).hasSize(5);
        assertThat(metrics)
                .filteredOn(metric -> !"live_event_fast_track_volume".equals(metric.key()))
                .allSatisfy(metric -> {
                    assertThat(metric.status()).isEqualTo(HealthStatus.HEALTHY);
                    assertThat(metric.sampleSize()).isZero();
                    assertThat(metric.detail()).doesNotContain("could not be retrieved");
                });
        assertThat(metrics)
                .extracting(OperationalMetricDto::key)
                .containsExactly(
                        "approval_turnaround_time",
                        "edit_approve_rate",
                        "manual_fallback_resolution_rate",
                        "publish_success_rate",
                        "live_event_fast_track_volume");
    }

    @Test
    void operationalMetrics_whenDatabaseQueryFails_marksOnlyThatMetricUnavailable() {
        when(jdbcTemplate.queryForMap(anyString(), any()))
                .thenThrow(new IllegalStateException("validation_logs is missing"))
                .thenReturn(Map.of("approvals", 4, "edited", 1))
                .thenReturn(Map.of("started", 2, "completed", 2))
                .thenReturn(Map.of("attempts", 5, "successes", 5));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any()))
                .thenReturn(1L);

        List<OperationalMetricDto> metrics = service.operationalMetrics();

        assertThat(metrics.get(0).key()).isEqualTo("approval_turnaround_time");
        assertThat(metrics.get(0).status()).isEqualTo(HealthStatus.UNAVAILABLE);
        assertThat(metrics.get(0).detail()).isEqualTo("Metric could not be retrieved.");
        assertThat(metrics.subList(1, metrics.size()))
                .allSatisfy(metric -> assertThat(metric.status()).isEqualTo(HealthStatus.HEALTHY));
    }

    @Test
    void operationalMetrics_bindsTimeBoundAsSqlTimestamp() {
        // The PostgreSQL JDBC driver cannot infer a SQL type for a bare
        // java.time.Instant bound via JdbcTemplate, which failed every metric.
        // The 30-day cutoff must be passed as java.sql.Timestamp.
        when(jdbcTemplate.queryForMap(anyString(), any()))
                .thenReturn(Map.of("value", 0, "sample_size", 0));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any())).thenReturn(0L);

        service.operationalMetrics();

        ArgumentCaptor<Object> arg = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate, atLeastOnce()).queryForMap(anyString(), arg.capture());
        assertThat(arg.getAllValues()).allSatisfy(value -> assertThat(value).isInstanceOf(Timestamp.class));

        ArgumentCaptor<Object> objArg = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).queryForObject(anyString(), eq(Long.class), objArg.capture());
        assertThat(objArg.getValue()).isInstanceOf(Timestamp.class);
    }

    @Test
    void backgroundJobs_returnsARowForEveryExpectedJob() {
        when(scheduledJobRunRepository.findLatestRunsByJobName()).thenReturn(List.of());

        List<BackgroundJobHealthDto> jobs = service.backgroundJobs();

        assertThat(jobs).hasSize(15);
        assertThat(jobs).extracting(BackgroundJobHealthDto::jobName)
                .contains("Review Lock Cleanup", "Validation Deadline Notification",
                        "Embedding Failure Digest", "Empty Schedule Warning", "Job Run Retention");
        assertThat(jobs).allSatisfy(j ->
                assertThat(j.status()).isIn(HealthStatus.UNAVAILABLE, HealthStatus.SCHEDULED));
    }

    @Test
    void backgroundJobs_neverRunFrequentJobIsUnavailable_infrequentIsScheduled() {
        when(scheduledJobRunRepository.findLatestRunsByJobName()).thenReturn(List.of());

        // every-1-minute job that has never run — something is wrong
        assertThat(jobDto("Publishing Scheduler").status()).isEqualTo(HealthStatus.UNAVAILABLE);
        // daily / weekly jobs that simply are not due yet
        assertThat(jobDto("Stale Draft Slot Release").status()).isEqualTo(HealthStatus.SCHEDULED);
        assertThat(jobDto("Embedding Failure Digest").status()).isEqualTo(HealthStatus.SCHEDULED);
        assertThat(jobDto("Job Run Retention").status()).isEqualTo(HealthStatus.SCHEDULED);
        assertThat(jobDto("Embedding Failure Digest").detail()).isEqualTo("Awaiting first run.");
    }

    @Test
    void backgroundJobs_weeklyJobRunThreeDaysAgoIsHealthy() {
        when(scheduledJobRunRepository.findLatestRunsByJobName()).thenReturn(List.of(
                run("EmbeddingFailureDigestJob", "SUCCESS",
                        Instant.now().minus(3, ChronoUnit.DAYS))));

        assertThat(jobDto("Embedding Failure Digest").status()).isEqualTo(HealthStatus.HEALTHY);
    }

    @Test
    void backgroundJobs_fiveMinuteJobStaleAfterTwentyMinutesIsWarning() {
        when(scheduledJobRunRepository.findLatestRunsByJobName()).thenReturn(List.of(
                run("AbandonmentDetectorJob", "SUCCESS",
                        Instant.now().minus(20, ChronoUnit.MINUTES))));

        assertThat(jobDto("Abandonment Detector").status()).isEqualTo(HealthStatus.WARNING);
    }

    @Test
    void backgroundJobs_failedRunIsUnhealthy() {
        when(scheduledJobRunRepository.findLatestRunsByJobName()).thenReturn(List.of(
                run("PublishingSchedulerJob", "FAILED", Instant.now().minusSeconds(30))));

        assertThat(jobDto("Publishing Scheduler").status()).isEqualTo(HealthStatus.UNHEALTHY);
    }

    @Test
    void mediaStorage_usesLiveObjectStoreScanWhenAvailable() {
        when(jdbcTemplate.queryForObject("SELECT pg_database_size(current_database())", Long.class))
                .thenReturn(10_000L);
        when(mediaStorage.probeUsage()).thenReturn(java.util.Optional.of(
                new MediaStorageService.StorageUsage(750_000L, 42L, false, Instant.now())));

        var media = service.storage().stream()
                .filter(s -> s.name().equals("Media storage"))
                .findFirst()
                .orElseThrow();

        assertThat(media.usedBytes()).isEqualTo(750_000L);
        assertThat(media.usedPercent()).isEqualTo(75.0); // 750k of the 1,000,000 test budget
        assertThat(media.detail()).contains("Live object-store scan").contains("42 objects");
    }

    @Test
    void mediaStorage_fallsBackToDbSumWhenProbeUnavailable() {
        when(jdbcTemplate.queryForObject("SELECT pg_database_size(current_database())", Long.class))
                .thenReturn(10_000L);
        when(mediaStorage.probeUsage()).thenReturn(java.util.Optional.empty());
        when(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(file_size_bytes), 0) FROM media_assets WHERE purged_at IS NULL", Long.class))
                .thenReturn(200_000L);

        var media = service.storage().stream()
                .filter(s -> s.name().equals("Media storage"))
                .findFirst()
                .orElseThrow();

        assertThat(media.usedBytes()).isEqualTo(200_000L);
        assertThat(media.detail()).contains("Estimated from tracked media asset bytes");
    }

    @Test
    void mediaStorage_warnsAt80PercentAndGoesUnhealthyAt95Percent() {
        when(jdbcTemplate.queryForObject("SELECT pg_database_size(current_database())", Long.class))
                .thenReturn(10_000L);

        // 85% of the 1,000,000 test budget -> WARNING
        when(mediaStorage.probeUsage()).thenReturn(java.util.Optional.of(
                new MediaStorageService.StorageUsage(850_000L, 5L, false, Instant.now())));
        var warn = service.storage().stream().filter(s -> s.name().equals("Media storage")).findFirst().orElseThrow();
        assertThat(warn.status()).isEqualTo(HealthStatus.WARNING);

        // 96% -> UNHEALTHY with an overage-charge hint
        when(mediaStorage.probeUsage()).thenReturn(java.util.Optional.of(
                new MediaStorageService.StorageUsage(960_000L, 6L, false, Instant.now())));
        var crit = service.storage().stream().filter(s -> s.name().equals("Media storage")).findFirst().orElseThrow();
        assertThat(crit.status()).isEqualTo(HealthStatus.UNHEALTHY);
        assertThat(crit.detail()).contains("overage");
    }
}
