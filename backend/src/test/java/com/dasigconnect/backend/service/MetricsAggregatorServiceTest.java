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
import org.springframework.http.HttpStatus;
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

    private void stubCoreQueries() {
        when(analyticsRepository.averagePostingDelay(any(), any(), any()))
                .thenReturn(new PostingDelayStats(2.345, 6));
        when(analyticsRepository.contentCompleteness(any(), any(), any()))
                .thenReturn(new CompletenessStats(19, 20));
        when(analyticsRepository.publishedPostStats(any(), any(), any()))
                .thenReturn(new PublishedPostStats(4, 3, 1, 0));
        when(analyticsRepository.statusBreakdown(any())).thenReturn(List.of());
        when(analyticsRepository.contentIssues(any(), any(), any())).thenReturn(List.of());
        when(analyticsRepository.topCategories(any(), any(), any())).thenReturn(List.of());
        when(analyticsRepository.postingDelaySparkline(any(), any(), any()))
                .thenReturn(List.of(2.1, 2.2, 2.35));
        when(analyticsRepository.completenessSparkline(any(), any(), any()))
                .thenReturn(List.of(90.0, 95.0, 95.0));
        when(analyticsRepository.publishedPostsSparkline(any(), any(), any()))
                .thenReturn(List.of(2.0, 3.0, 4.0));
        when(analyticsRepository.facebookEngagement(any(), any(), any()))
                .thenReturn(new AnalyticsRepository.FacebookEngagementStats(0, 0, 0, 0, 0, 0));
    }

    @Test
    void summary_adminIsNetworkWideByDefaultWithAdminOnlyMetrics() {
        JwtUserDetails admin = new JwtUserDetails(UUID.randomUUID(), "admin@test.local", "admin", null);
        stubCoreQueries();
        when(analyticsRepository.institutionFilterOptions()).thenReturn(List.of());
        when(analyticsRepository.postsByInstitution(any(), any(), any())).thenReturn(List.of());
        when(analyticsRepository.aiPerformance(any(), any(), any()))
                .thenReturn(new AiStats(10, 7, 4, 1, 8, 6));
        when(analyticsRepository.operationalHealth(any(), any(), any(), any()))
                .thenReturn(new AnalyticsRepository.OperationalStats(4, 1, 0, 4, 3, 2, 0));

        var summary = service.summary("30d", null, null, admin);

        assertThat(summary.adminView()).isTrue();
        assertThat(summary.aiPerformance()).isNotNull();
        assertThat(summary.operationalHealth()).isNotNull();
        assertThat(summary.contributorBreakdown()).isEmpty();
        assertThat(summary.validatorAnalytics()).isNull();

        ArgumentCaptor<AnalyticsScope> scopeCaptor = ArgumentCaptor.forClass(AnalyticsScope.class);
        org.mockito.Mockito.verify(analyticsRepository, org.mockito.Mockito.atLeastOnce())
                .averagePostingDelay(any(Instant.class), any(Instant.class), scopeCaptor.capture());
        assertThat(scopeCaptor.getValue().role()).isEqualTo("admin");
        assertThat(scopeCaptor.getValue().institutionId()).isNull();
    }

    @Test
    void summary_adminCanFilterByInstitutionAndSeesDrilldownContent() {
        UUID institutionId = UUID.randomUUID();
        JwtUserDetails admin = new JwtUserDetails(UUID.randomUUID(), "admin@test.local", "admin", null);
        stubCoreQueries();
        when(analyticsRepository.institutionFilterOptions()).thenReturn(List.of());
        when(analyticsRepository.postsByInstitution(any(), any(), any())).thenReturn(List.of());
        when(analyticsRepository.aiPerformance(any(), any(), any()))
                .thenReturn(new AiStats(10, 7, 4, 1, 8, 6));
        when(analyticsRepository.operationalHealth(any(), any(), any(), any()))
                .thenReturn(new AnalyticsRepository.OperationalStats(4, 1, 0, 4, 3, 2, 0));
        when(analyticsRepository.contributorBreakdown(any(), any(), any()))
                .thenReturn(List.of(new ContributorBreakdownDto(UUID.randomUUID(), "Contributor", 5, 4, 1, 1, 95.0, 2.35)));
        when(analyticsRepository.validatorStats(any(), any(), any(), any()))
                .thenReturn(new ValidatorStats(5, 2, 1, 1.25, 1));

        var summary = service.summary("30d", institutionId, null, admin);

        assertThat(summary.contributorBreakdown()).hasSize(1);
        assertThat(summary.validatorAnalytics()).isNotNull();
        assertThat(summary.validatorAnalytics().institutionSubmissionVolume()).isEqualTo(5);

        ArgumentCaptor<AnalyticsScope> scopeCaptor = ArgumentCaptor.forClass(AnalyticsScope.class);
        org.mockito.Mockito.verify(analyticsRepository, org.mockito.Mockito.atLeastOnce())
                .averagePostingDelay(any(Instant.class), any(Instant.class), scopeCaptor.capture());
        assertThat(scopeCaptor.getValue().institutionId()).isEqualTo(institutionId);
    }

    @Test
    void summary_contributorIsInstitutionScopedWithoutAdminOnlyMetrics() {
        UUID institutionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        JwtUserDetails contributor = new JwtUserDetails(userId, "contributor@test.local", "contributor", institutionId);
        stubCoreQueries();
        when(analyticsRepository.contributorStats(any(), any(), any()))
                .thenReturn(new AnalyticsRepository.ContributorStats(5, 4, 1, 1));

        var summary = service.summary("30d", null, null, contributor);

        assertThat(summary.adminView()).isFalse();
        assertThat(summary.aiPerformance()).isNull();
        assertThat(summary.operationalHealth()).isNull();
        assertThat(summary.contributorBreakdown()).isEmpty();

        ArgumentCaptor<AnalyticsScope> scopeCaptor = ArgumentCaptor.forClass(AnalyticsScope.class);
        org.mockito.Mockito.verify(analyticsRepository, org.mockito.Mockito.atLeastOnce())
                .averagePostingDelay(any(Instant.class), any(Instant.class), scopeCaptor.capture());
        assertThat(scopeCaptor.getValue().role()).isEqualTo("contributor");
        assertThat(scopeCaptor.getValue().institutionId()).isEqualTo(institutionId);
    }

    @Test
    void summary_contributorCannotPassInstitutionFilter() {
        UUID institutionId = UUID.randomUUID();
        JwtUserDetails contributor = new JwtUserDetails(UUID.randomUUID(), "contributor@test.local", "contributor", institutionId);

        assertThatThrownBy(() -> service.summary("30d", UUID.randomUUID(), null, contributor))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void export_aiPerformance_rejectedForContributor() {
        JwtUserDetails contributor = new JwtUserDetails(UUID.randomUUID(), "contributor@test.local", "contributor", UUID.randomUUID());

        assertThatThrownBy(() -> service.export("ai-performance", "7d", null, null, contributor))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void export_returnsCsvWithHeaders() {
        JwtUserDetails admin = new JwtUserDetails(UUID.randomUUID(), "admin@test.local", "admin", null);
        when(analyticsRepository.exportRows(any(), any(), any(), any()))
                .thenReturn(List.of(Map.of("metric", "publication_attempts", "value", 5)));

        var export = service.export("operational-health", "7d", null, null, admin);

        assertThat(export.filename()).contains("DASIGConnect_Analytics_Admin_Network_operational_health_7D").endsWith(".csv");
        assertThat(export.content()).contains("\"metric\",\"value\"");
        assertThat(export.content()).contains("\"publication_attempts\",\"5\"");
    }

    @Test
    void summary_rejectsUnsupportedRange() {
        JwtUserDetails admin = new JwtUserDetails(UUID.randomUUID(), "admin@test.local", "admin", null);

        assertThatThrownBy(() -> service.summary("13d", null, null, admin))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unsupported analytics range");
    }
}
