package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.model.dto.systemhealth.HealthStatus;
import com.dasigconnect.backend.model.dto.systemhealth.OperationalMetricDto;
import com.dasigconnect.backend.repository.ScheduledJobRunRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;

class SystemHealthServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    private final SystemHealthService service = new SystemHealthService(
            jdbcTemplate,
            mock(ScheduledJobRunRepository.class),
            mock(TokenManagementService.class),
            mock(JavaMailSender.class),
            1_000_000,
            1_000_000,
            80,
            "",
            "");

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
}
