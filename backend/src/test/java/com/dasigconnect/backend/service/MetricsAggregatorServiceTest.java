package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.model.dto.analytics.ContributorBreakdownDto;
import com.dasigconnect.backend.repository.AnalyticsRepository;
import com.dasigconnect.backend.repository.AnalyticsRepository.AiStats;
import com.dasigconnect.backend.repository.AnalyticsRepository.AnalyticsScope;
import com.dasigconnect.backend.repository.AnalyticsRepository.CompletenessStats;
import com.dasigconnect.backend.repository.AnalyticsRepository.PostingDelayStats;
import com.dasigconnect.backend.repository.AnalyticsRepository.PublishedPostStats;
import com.dasigconnect.backend.repository.AnalyticsRepository.ValidatorStats;
import com.dasigconnect.backend.security.JwtUserDetails;

@ExtendWith(MockitoExtension.class)
class MetricsAggregatorServiceTest {

    @Mock
    private AnalyticsRepository analyticsRepository;

    private MetricsAggregatorService service;

    @BeforeEach
    void setUp() {
        service = new MetricsAggregatorService(analyticsRepository);
    }

    @Test
    void summary_aggregatesKpisAndScopesValidatorToInstitution() {
        UUID institutionId = UUID.randomUUID();
        JwtUserDetails validator = new JwtUserDetails(UUID.randomUUID(), "validator@test.local", "validator", institutionId);

        when(analyticsRepository.averagePostingDelay(any(), any(), any()))
                .thenReturn(new PostingDelayStats(2.345, 6));
        when(analyticsRepository.contentCompleteness(any(), any(), any()))
                .thenReturn(new CompletenessStats(19, 20));
        when(analyticsRepository.publishedPostStats(any(), any(), any()))
                .thenReturn(new PublishedPostStats(4, 3, 1, 0));
        when(analyticsRepository.contributorBreakdown(any(), any(), any()))
                .thenReturn(List.of(new ContributorBreakdownDto(UUID.randomUUID(), "Contributor", 5, 4, 1, 1, 95.0, 2.35)));
        when(analyticsRepository.validatorStats(any(), any(), any(), any()))
                .thenReturn(new ValidatorStats(5, 2, 1, 1.25, 1));
        when(analyticsRepository.statusBreakdown(any())).thenReturn(List.of());
        when(analyticsRepository.contentIssues(any(), any(), any())).thenReturn(List.of());
        when(analyticsRepository.topCategories(any(), any(), any())).thenReturn(List.of());
        when(analyticsRepository.postingDelaySparkline(any(), any(), any()))
                .thenReturn(List.of(2.1, 2.2, 2.35));
        when(analyticsRepository.completenessSparkline(any(), any(), any()))
                .thenReturn(List.of(90.0, 95.0, 95.0));
        when(analyticsRepository.publishedPostsSparkline(any(), any(), any()))
                .thenReturn(List.of(2.0, 3.0, 4.0));
        when(analyticsRepository.aiPerformance(any(), any(), any()))
                .thenReturn(new AiStats(10, 7, 4, 1, 8, 6));

        var summary = service.summary("30d", null, validator);

        assertThat(summary.averagePostingDelay().value()).isEqualTo(2.35);
        assertThat(summary.contentCompleteness().value()).isEqualTo(95.0);
        assertThat(summary.contentCompleteness().targetMet()).isTrue();
        assertThat(summary.totalPostsPublished().value()).isEqualTo(4);
        assertThat(summary.totalPostsPublished().secondaryValue()).isZero();
        assertThat(summary.totalPostsPublished().sparkline()).containsExactly(2.0, 3.0, 4.0);
        assertThat(summary.aiPerformance().captionAcceptanceRate()).isEqualTo(70.0);
        assertThat(summary.aiPerformance().insufficientData()).isFalse();
        assertThat(summary.adminView()).isFalse();
        assertThat(summary.operationalHealth()).isNull();
        assertThat(summary.contributorBreakdown()).hasSize(1);

        ArgumentCaptor<AnalyticsScope> scopeCaptor = ArgumentCaptor.forClass(AnalyticsScope.class);
        org.mockito.Mockito.verify(analyticsRepository, org.mockito.Mockito.atLeastOnce())
                .averagePostingDelay(any(Instant.class), any(Instant.class), scopeCaptor.capture());
        assertThat(scopeCaptor.getValue().role()).isEqualTo("validator");
        assertThat(scopeCaptor.getValue().institutionId()).isEqualTo(institutionId);
        assertThat(scopeCaptor.getValue().userId()).isNull();
    }

    @Test
    void export_returnsCsvWithHeaders() {
        JwtUserDetails admin = new JwtUserDetails(UUID.randomUUID(), "admin@test.local", "administrator", null);
        when(analyticsRepository.exportRows(any(), any(), any(), any()))
                .thenReturn(List.of(Map.of("metric", "publication_attempts", "value", 5)));

        var export = service.export("operational-health", "7d", null, admin);

        assertThat(export.filename()).contains("DASIGConnect_Analytics_Administrator_Network_operational_health_7D").endsWith(".csv");
        assertThat(export.content()).contains("\"metric\",\"value\"");
        assertThat(export.content()).contains("\"publication_attempts\",\"5\"");
    }

    @Test
    void summary_rejectsUnsupportedRange() {
        JwtUserDetails admin = new JwtUserDetails(UUID.randomUUID(), "admin@test.local", "administrator", null);

        assertThatThrownBy(() -> service.summary("13d", null, admin))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unsupported analytics range");
    }
}
