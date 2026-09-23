package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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
import com.dasigconnect.backend.repository.AnalyticsRepository.AnalyticsRowsPage;
import com.dasigconnect.backend.repository.AnalyticsRepository.CompletenessStats;
import com.dasigconnect.backend.repository.AnalyticsRepository.PostingDelayStats;
import com.dasigconnect.backend.repository.AnalyticsRepository.PublishedPostStats;
import com.dasigconnect.backend.repository.AnalyticsRepository.ValidatorStats;
import com.dasigconnect.backend.security.JwtUserDetails;

@ExtendWith(MockitoExtension.class)
class MetricsAggregatorServiceTest {

    @Mock
    private AnalyticsRepository analyticsRepository;

    @Mock
    private FacebookEngagementAnalyticsClient facebookInsightsClient;

    private MetricsAggregatorService service;

    @BeforeEach
    void setUp() {
        service = new MetricsAggregatorService(analyticsRepository, facebookInsightsClient);
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
                .thenReturn(new AiStats(10, 7, 8, 6, 4, 3, 2, 1));
        when(analyticsRepository.operationalHealth(any(), any(), any(), any()))
                .thenReturn(new AnalyticsRepository.OperationalStats(4, 1, 0, 4, 3, 2, 0));

        var summary = service.summary("30d", null, admin);

        assertThat(summary.adminView()).isTrue();
        assertThat(summary.aiPerformance()).isNotNull();
        assertThat(summary.operationalHealth()).isNotNull();
        // UC-3.2 postcondition: on-time publication rate (already the actual
        // ±5-minute window, baked into operationalHealth()'s SQL) is now also
        // checked against a 95% target, same pattern as content completeness.
        assertThat(summary.operationalHealth().onTimePublicationTarget()).isEqualTo(95.0);
        assertThat(summary.operationalHealth().onTimePublicationRate()).isEqualTo(66.67); // 2 of 3 successes on time
        assertThat(summary.operationalHealth().meetsOnTimePublicationTarget()).isFalse();
        assertThat(summary.contributorBreakdown()).isEmpty();
        assertThat(summary.validatorAnalytics()).isNull();

        ArgumentCaptor<AnalyticsScope> scopeCaptor = ArgumentCaptor.forClass(AnalyticsScope.class);
        org.mockito.Mockito.verify(analyticsRepository, org.mockito.Mockito.atLeastOnce())
                .averagePostingDelay(any(Instant.class), any(Instant.class), scopeCaptor.capture());
        assertThat(scopeCaptor.getValue().role()).isEqualTo("admin");
        assertThat(scopeCaptor.getValue().institutionIds()).isEmpty();
    }

    @Test
    void summary_adminCanSelectSeveralInstitutions_withoutSingleInstitutionDrilldown() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        JwtUserDetails admin = new JwtUserDetails(UUID.randomUUID(), "admin@test.local", "admin", null);
        stubCoreQueries();
        when(analyticsRepository.institutionFilterOptions()).thenReturn(List.of());
        when(analyticsRepository.postsByInstitution(any(), any(), any())).thenReturn(List.of());
        when(analyticsRepository.aiPerformance(any(), any(), any()))
                .thenReturn(new AiStats(10, 7, 8, 6, 4, 3, 2, 1));
        when(analyticsRepository.operationalHealth(any(), any(), any(), any()))
                .thenReturn(new AnalyticsRepository.OperationalStats(4, 1, 0, 4, 3, 2, 0));

        // Unsorted with a duplicate: the scope is normalized.
        var summary = service.summary("30d", List.of(second, first, second), admin);

        assertThat(summary.selectedInstitutionIds()).containsExactly(first, second);
        assertThat(summary.contributorBreakdown()).isEmpty();
        assertThat(summary.validatorAnalytics()).isNull();
        org.mockito.Mockito.verify(analyticsRepository, org.mockito.Mockito.never())
                .contributorBreakdown(any(), any(), any());
    }

    @Test
    void summary_aiAdoptionRatesAreUsedOverOffered() {
        JwtUserDetails admin = new JwtUserDetails(UUID.randomUUID(), "admin@test.local", "admin", null);
        stubCoreQueries();
        when(analyticsRepository.institutionFilterOptions()).thenReturn(List.of());
        when(analyticsRepository.postsByInstitution(any(), any(), any())).thenReturn(List.of());
        // captions 10 generated / 7 applied; media 8 shown / 6 added; album 4 outcomes / 3 kept; templates 2 / 1
        when(analyticsRepository.aiPerformance(any(), any(), any()))
                .thenReturn(new AiStats(10, 7, 8, 6, 4, 3, 2, 1));
        when(analyticsRepository.operationalHealth(any(), any(), any(), any()))
                .thenReturn(new AnalyticsRepository.OperationalStats(4, 1, 0, 4, 3, 2, 0));

        var ai = service.summary("30d", null, admin).aiPerformance();

        assertThat(ai.captionAcceptanceRate()).isEqualTo(70.0);
        assertThat(ai.mediaRecommendationRelevanceRate()).isEqualTo(75.0);
        assertThat(ai.albumMatchKeptRate()).isEqualTo(75.0);
        assertThat(ai.templateDraftSaveRate()).isEqualTo(50.0);
        assertThat(ai.insufficientData()).isFalse(); // 10 + 8 + 4 + 2 = 24 events
    }

    @Test
    void summary_adminCanFilterByInstitutionAndSeesDrilldownContent() {
        UUID institutionId = UUID.randomUUID();
        JwtUserDetails admin = new JwtUserDetails(UUID.randomUUID(), "admin@test.local", "admin", null);
        stubCoreQueries();
        when(analyticsRepository.institutionFilterOptions()).thenReturn(List.of());
        when(analyticsRepository.postsByInstitution(any(), any(), any())).thenReturn(List.of());
        when(analyticsRepository.aiPerformance(any(), any(), any()))
                .thenReturn(new AiStats(10, 7, 8, 6, 4, 3, 2, 1));
        when(analyticsRepository.operationalHealth(any(), any(), any(), any()))
                .thenReturn(new AnalyticsRepository.OperationalStats(4, 1, 0, 4, 3, 2, 0));
        when(analyticsRepository.contributorBreakdown(any(), any(), any()))
                .thenReturn(List.of(new ContributorBreakdownDto(UUID.randomUUID(), "Contributor", 5, 4, 1, 1, 95.0, 2.35)));
        when(analyticsRepository.validatorStats(any(), any(), any(), any()))
                .thenReturn(new ValidatorStats(5, 2, 1, 1.25, 1));

        var summary = service.summary("30d", List.of(institutionId), admin);

        assertThat(summary.contributorBreakdown()).hasSize(1);
        assertThat(summary.validatorAnalytics()).isNotNull();
        assertThat(summary.validatorAnalytics().institutionSubmissionVolume()).isEqualTo(5);

        ArgumentCaptor<AnalyticsScope> scopeCaptor = ArgumentCaptor.forClass(AnalyticsScope.class);
        org.mockito.Mockito.verify(analyticsRepository, org.mockito.Mockito.atLeastOnce())
                .averagePostingDelay(any(Instant.class), any(Instant.class), scopeCaptor.capture());
        assertThat(scopeCaptor.getValue().institutionIds()).containsExactly(institutionId);
    }

    @Test
    void summary_contributorIsInstitutionScopedWithoutAdminOnlyMetrics() {
        UUID institutionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        JwtUserDetails contributor = new JwtUserDetails(userId, "contributor@test.local", "contributor", institutionId);
        stubCoreQueries();
        when(analyticsRepository.contributorStats(any(), any(), any()))
                .thenReturn(new AnalyticsRepository.ContributorStats(5, 4, 1, 1));

        var summary = service.summary("30d", null, contributor);

        assertThat(summary.adminView()).isFalse();
        assertThat(summary.aiPerformance()).isNull();
        assertThat(summary.operationalHealth()).isNull();
        assertThat(summary.contributorBreakdown()).isEmpty();

        ArgumentCaptor<AnalyticsScope> scopeCaptor = ArgumentCaptor.forClass(AnalyticsScope.class);
        org.mockito.Mockito.verify(analyticsRepository, org.mockito.Mockito.atLeastOnce())
                .averagePostingDelay(any(Instant.class), any(Instant.class), scopeCaptor.capture());
        assertThat(scopeCaptor.getValue().role()).isEqualTo("contributor");
        assertThat(scopeCaptor.getValue().institutionIds()).containsExactly(institutionId);
    }

    @Test
    void summary_contributorCannotPassInstitutionFilter() {
        UUID institutionId = UUID.randomUUID();
        JwtUserDetails contributor = new JwtUserDetails(UUID.randomUUID(), "contributor@test.local", "contributor", institutionId);

        assertThatThrownBy(() -> service.summary("30d", List.of(UUID.randomUUID()), contributor))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void summary_moderatorIsNetworkWideEngagementAndWorkflowOnly() {
        JwtUserDetails moderator = new JwtUserDetails(UUID.randomUUID(), "mod@test.local", "moderator", null);
        stubCoreQueries();

        var summary = service.summary("30d", null, moderator);

        assertThat(summary.adminView()).isFalse();
        assertThat(summary.aiPerformance()).isNull();
        assertThat(summary.operationalHealth()).isNull();
        assertThat(summary.validatorAnalytics()).isNull();
        assertThat(summary.contributorAnalytics()).isNull();
        assertThat(summary.facebookEngagement()).isNotNull();

        ArgumentCaptor<AnalyticsScope> scopeCaptor = ArgumentCaptor.forClass(AnalyticsScope.class);
        org.mockito.Mockito.verify(analyticsRepository, org.mockito.Mockito.atLeastOnce())
                .averagePostingDelay(any(Instant.class), any(Instant.class), scopeCaptor.capture());
        assertThat(scopeCaptor.getValue().role()).isEqualTo("moderator");
        assertThat(scopeCaptor.getValue().institutionIds()).isEmpty();
    }

    @Test
    void summary_moderatorCannotPassInstitutionFilter() {
        JwtUserDetails moderator = new JwtUserDetails(UUID.randomUUID(), "mod@test.local", "moderator", null);

        assertThatThrownBy(() -> service.summary("30d", List.of(UUID.randomUUID()), moderator))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void export_operationalHealth_rejectedForModerator() {
        JwtUserDetails moderator = new JwtUserDetails(UUID.randomUUID(), "mod@test.local", "moderator", null);

        assertThatThrownBy(() -> service.export("operational-health", "7d", null, moderator))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void export_aiPerformance_rejectedForContributor() {
        JwtUserDetails contributor = new JwtUserDetails(UUID.randomUUID(), "contributor@test.local", "contributor", UUID.randomUUID());

        assertThatThrownBy(() -> service.export("ai-performance", "7d", null, contributor))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void export_returnsCsvWithHeaders() {
        JwtUserDetails admin = new JwtUserDetails(UUID.randomUUID(), "admin@test.local", "admin", null);
        when(analyticsRepository.exportRows(any(), any(), any(), any()))
                .thenReturn(List.of(Map.of("metric", "publication_attempts", "value", 5)));

        var export = service.export("operational-health", "7d", null, admin);

        assertThat(export.filename()).contains("DASIGConnect_Analytics_Admin_Network_operational_health_7D").endsWith(".csv");
        assertThat(export.content()).contains("\"metric\",\"value\"");
        assertThat(export.content()).contains("\"publication_attempts\",\"5\"");
    }

    @Test
    void export_neutralizesCsvFormulaInjectionInFreeTextFields() {
        // CWE-1236: exported rows include user-controlled free text (event
        // titles, contributor names) with no sanitization at write time.
        // Quoting alone doesn't stop Excel/Sheets from evaluating a cell
        // starting with =/+/-/@ as a formula when the CSV is opened later.
        JwtUserDetails admin = new JwtUserDetails(UUID.randomUUID(), "admin@test.local", "admin", null);
        when(analyticsRepository.exportRows(any(), any(), any(), any()))
                .thenReturn(List.of(Map.of(
                        "event_title", "=HYPERLINK(\"http://evil.example\",\"Click me\")",
                        "contributor_name", "+1;DDE")));

        var export = service.export("posts-by-institution", "7d", null, admin);

        assertThat(export.content()).contains("\"'=HYPERLINK(\"\"http://evil.example\"\",\"\"Click me\"\")\"");
        assertThat(export.content()).contains("\"'+1;DDE\"");
    }

    @Test
    void report_capsPageSizeAndReturnsOnlyPagedRows() {
        JwtUserDetails admin = new JwtUserDetails(UUID.randomUUID(), "admin@test.local", "admin", null);
        when(analyticsRepository.reportRows(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new AnalyticsRowsPage(List.of(Map.of("submission_id", "abc")), 250, 2, 100));

        var report = service.report("posting-delay", "30d", null, 2, 500, admin);

        assertThat(report.aggregateRows()).hasSize(1);
        assertThat(report.totalCount()).isEqualTo(250);
        assertThat(report.page()).isEqualTo(2);
        assertThat(report.pageSize()).isEqualTo(100);
        org.mockito.Mockito.verify(analyticsRepository)
                .reportRows(any(), any(), any(), any(), eq(2), eq(100));
        org.mockito.Mockito.verify(analyticsRepository, org.mockito.Mockito.never())
                .dailyBreakdown(any(), any(), any(), any());
    }

    @Test
    void report_customDateRangeCoversWholeDaysInPhilippineTime() {
        JwtUserDetails admin = new JwtUserDetails(UUID.randomUUID(), "admin@test.local", "admin", null);
        when(analyticsRepository.reportRows(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new AnalyticsRowsPage(List.of(), 0, 1, 50));
        when(analyticsRepository.dailyBreakdown(any(), any(), any(), any())).thenReturn(List.of());

        var report = service.report("posting-delay", "2026-08-01..2026-08-31", null, 1, 50, admin);

        // Aug 1 00:00 PHT (UTC+8) through the start of Sep 1 PHT; both ends inclusive.
        assertThat(report.periodStart()).isEqualTo(Instant.parse("2026-07-31T16:00:00Z"));
        assertThat(report.periodEnd()).isEqualTo(Instant.parse("2026-08-31T16:00:00Z"));
        assertThat(report.range()).isEqualTo("2026-08-01..2026-08-31");
    }

    @Test
    void report_customDateRangeEndingTodayIsCappedAtNow() {
        JwtUserDetails admin = new JwtUserDetails(UUID.randomUUID(), "admin@test.local", "admin", null);
        when(analyticsRepository.reportRows(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new AnalyticsRowsPage(List.of(), 0, 1, 50));
        when(analyticsRepository.dailyBreakdown(any(), any(), any(), any())).thenReturn(List.of());
        String today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Manila")).toString();

        var report = service.report("posting-delay", today + ".." + today, null, 1, 50, admin);

        assertThat(report.periodEnd()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void summary_rejectsInvalidCustomRanges() {
        JwtUserDetails admin = new JwtUserDetails(UUID.randomUUID(), "admin@test.local", "admin", null);
        String future = java.time.LocalDate.now().plusDays(10).toString();

        assertThatThrownBy(() -> service.summary("2026-08-31..2026-08-01", null, admin))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("start is after its end");
        assertThatThrownBy(() -> service.summary("2024-01-01..2025-06-01", null, admin))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("at most 366 days");
        assertThatThrownBy(() -> service.summary(future + ".." + future, null, admin))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("future");
        assertThatThrownBy(() -> service.summary("2026-08-01..next-week", null, admin))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("YYYY-MM-DD..YYYY-MM-DD");
    }

    @Test
    void summary_rejectsUnsupportedRange() {
        JwtUserDetails admin = new JwtUserDetails(UUID.randomUUID(), "admin@test.local", "admin", null);

        assertThatThrownBy(() -> service.summary("13d", null, admin))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unsupported analytics range");
    }
}
