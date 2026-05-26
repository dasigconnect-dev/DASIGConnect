package com.dasigconnect.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.dasigconnect.backend.model.dto.analytics.AnalyticsSummaryDto;
import com.dasigconnect.backend.model.dto.analytics.KpiMetricDto;
import com.dasigconnect.backend.model.dto.analytics.OperationalHealthDto;
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
        when(metricsAggregatorService.summary(eq("30d"), any())).thenReturn(summaryDto());

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
        when(metricsAggregatorService.export(eq("posting-delay"), eq("30d"), any()))
                .thenReturn(new CsvExport("posting-delay.csv", "\"submission_id\"\r\n\"abc\"\r\n"));

        mockMvc.perform(get("/api/v1/analytics/export/posting-delay"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("posting-delay.csv")));
    }

    private AnalyticsSummaryDto summaryDto() {
        return new AnalyticsSummaryDto(
                "30d",
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-31T00:00:00Z"),
                new KpiMetricDto("averagePostingDelay", "AVG Posting Delay", 2.5, "days", 8, null, true),
                new KpiMetricDto("contentCompleteness", "Content Completeness", 96.0, "percent", 25, 95.0, true),
                new KpiMetricDto("totalPostsPublished", "Total Posts Published", 5, "posts", 5, 4.0, true),
                List.of(),
                new AiPerformanceDto(0, 0, 0, 0, 0, 0, 0, 0, 0, true),
                new OperationalHealthDto(12, 0, 0, 0, 0, 20, 19, 95.0, 19, 100.0, 4));
    }
}
