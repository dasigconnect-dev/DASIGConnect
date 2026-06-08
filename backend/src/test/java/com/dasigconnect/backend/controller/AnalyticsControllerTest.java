package com.dasigconnect.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.model.dto.analytics.AiPerformanceDto;
import com.dasigconnect.backend.model.dto.analytics.AdminAnalyticsDto;
import com.dasigconnect.backend.model.dto.analytics.AnalyticsSummaryDto;
import com.dasigconnect.backend.model.dto.analytics.ContentInsightOverviewDto;
import com.dasigconnect.backend.model.dto.analytics.ContentInsightsDto;
import com.dasigconnect.backend.model.dto.analytics.ContributorBreakdownDto;
import com.dasigconnect.backend.model.dto.analytics.KpiMetricDto;
import com.dasigconnect.backend.model.dto.analytics.OperationalHealthDto;
import com.dasigconnect.backend.model.dto.analytics.PageAudienceInsightsDto;
import com.dasigconnect.backend.service.FacebookInsightsSyncService;
import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.MetricsAggregatorService;
import com.dasigconnect.backend.service.MetricsAggregatorService.CsvExport;
import com.dasigconnect.backend.service.TenantScopeService;

@WebMvcTest(AnalyticsController.class)
@Import(SecurityConfig.class)
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MetricsAggregatorService metricsAggregatorService;

    @MockitoBean
    private FacebookInsightsSyncService facebookInsightsSyncService;

    @MockitoBean
    private com.dasigconnect.backend.service.FacebookPageAudienceService pageAudienceService;

    @MockitoBean
    private JWTService jwtService;

    @MockitoBean
    private TenantScopeService tenantScopeService;

    @Test
    void summary_withoutAuth_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/summary"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void summary_authenticated_returnsAnalyticsPayload() throws Exception {
        when(metricsAggregatorService.summary(eq("30d"), any(), any())).thenReturn(summaryDto());

        mockMvc.perform(get("/api/v1/analytics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("30d"))
                .andExpect(jsonPath("$.averagePostingDelay.value").value(2.5))
                .andExpect(jsonPath("$.contentCompleteness.targetMet").value(true))
                .andExpect(jsonPath("$.operationalHealth.publishingSuccessRate").value(95.0));
    }

    @Test
    @WithMockUser
    void export_authenticated_returnsCsvAttachment() throws Exception {
        when(metricsAggregatorService.export(eq("posting-delay"), eq("30d"), any(), any()))
                .thenReturn(new CsvExport("posting-delay.csv", "\"submission_id\"\r\n\"abc\"\r\n"));

        mockMvc.perform(get("/api/v1/analytics/export/posting-delay"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("posting-delay.csv")));
    }

    @Test
    @WithMockUser
    void contentInsights_authenticated_returnsInsightPayload() throws Exception {
        when(metricsAggregatorService.contentInsights(eq("30d"), any(), any())).thenReturn(contentInsightsDto());

        mockMvc.perform(get("/api/v1/analytics/content-insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metricsReady").value(true))
                .andExpect(jsonPath("$.overview.totalViews").value(31))
                .andExpect(jsonPath("$.overview.averageWatchTimeSeconds").value(23.0));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void syncFacebookInsights_asAdministrator_returnsSyncedCount() throws Exception {
        when(facebookInsightsSyncService.syncDueMetrics())
                .thenReturn(new FacebookInsightsSyncService.SyncResult(2, 2, 0, null));

        mockMvc.perform(post("/api/v1/analytics/facebook-insights/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncedPosts").value(2))
                .andExpect(jsonPath("$.duePosts").value(2));

        verify(facebookInsightsSyncService).syncDueMetrics();
    }

    @Test
    @WithMockUser(roles = "CONTRIBUTOR")
    void syncFacebookInsights_asContributor_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/analytics/facebook-insights/sync"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void audienceInsights_asAdministrator_returnsPayload() throws Exception {
        when(pageAudienceService.getAudienceInsights(eq("30d")))
                .thenReturn(PageAudienceInsightsDto.unavailable());

        mockMvc.perform(get("/api/v1/analytics/audience-insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    @WithMockUser(roles = "CONTRIBUTOR")
    void audienceInsights_asContributor_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/audience-insights"))
                .andExpect(status().isForbidden());
    }

    private AnalyticsSummaryDto summaryDto() {
        return new AnalyticsSummaryDto(
                "30d",
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-31T00:00:00Z"),
                Instant.parse("2026-05-31T00:00:00Z"),
                "administrator",
                true,
                null,
                List.of(),
                new KpiMetricDto("averagePostingDelay", "AVG Posting Delay", 2.5, "days", 8, null, true, null, List.of(2.0, 2.5), null, null),
                new KpiMetricDto("contentCompleteness", "Content Completeness", 96.0, "percent", 25, 95.0, true, 2.0, List.of(94.0, 96.0), null, null),
                new KpiMetricDto("totalPostsPublished", "Total Posts Published", 5, "posts", 5, 4.0, true, 1.0, List.of(4.0, 5.0), "Admin direct posts", 1L),
                List.of(),
                List.of(new ContributorBreakdownDto(null, "Contributor", 6, 5, 1, 1, 96.0, 2.5)),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                new AiPerformanceDto(0, 0, 0, 0, 0, 0, 0, 0, 0, true),
                new com.dasigconnect.backend.model.dto.analytics.FacebookEngagementDto(0, 0, 0, 0, 0, 0, 0, null),
                new AdminAnalyticsDto(1, 4, 1),
                new OperationalHealthDto(12, 0, 0, 0, 0, 20, 19, 95.0, 19, 100.0, 4));
    }

    private ContentInsightsDto contentInsightsDto() {
        return new ContentInsightsDto(
                "30d",
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-31T00:00:00Z"),
                Instant.parse("2026-05-31T00:00:00Z"),
                "administrator",
                true,
                null,
                true,
                new ContentInsightOverviewDto(1, 31, 20, 17, 11, 4, 2, 7, 9, 3, 120, 180, 92, 23, 14.17,
                        Instant.parse("2026-05-31T00:00:00Z")),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }
}
