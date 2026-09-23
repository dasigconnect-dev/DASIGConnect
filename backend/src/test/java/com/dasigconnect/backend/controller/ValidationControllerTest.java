package com.dasigconnect.backend.controller;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.model.dto.validation.ValidationQueueCountsDto;
import com.dasigconnect.backend.model.dto.validation.ValidationQueuePageDto;
import com.dasigconnect.backend.model.dto.validation.ValidationDashboardSummaryDto;
import com.dasigconnect.backend.repository.ValidationLogRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.ReviewLockService;
import com.dasigconnect.backend.service.TenantScopeService;
import com.dasigconnect.backend.service.ValidationService;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ValidationController.class)
@Import(SecurityConfig.class)
class ValidationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private ValidationService validationService;
    @MockitoBean private ReviewLockService reviewLockService;
    @MockitoBean private ValidationLogRepository validationLogRepository;
    @MockitoBean private JWTService jwtService;
    @MockitoBean private TenantScopeService tenantScopeService;

    @Test
    void queuePage_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/validation/queue/page"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CONTRIBUTOR")
    void queuePage_contributorReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/validation/queue/page"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void queuePage_moderatorReturnsStablePageContract() throws Exception {
        when(validationService.getQueuePage(
                nullable(JwtUserDetails.class),
                anyString(),
                anyString(),
                anyInt(),
                anyInt(),
                anyString()))
                .thenReturn(new ValidationQueuePageDto(
                        List.of(),
                        0,
                        20,
                        42,
                        3,
                        true,
                        new ValidationQueueCountsDto(88, 12, 4, 3, 20, 41, 8)));

        mockMvc.perform(get("/api/v1/validation/queue/page")
                .param("view", "all")
                .param("sort", "submitted")
                .param("page", "0")
                .param("pageSize", "20")
                .param("search", "research"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.totalCount").value(42))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.counts.all").value(88))
                .andExpect(jsonPath("$.data.counts.in_review").value(4))
                .andExpect(jsonPath("$.data.counts.needs_revision").value(3));
    }

    @Test
    @WithMockUser(roles = "CONTRIBUTOR")
    void dashboardSummary_contributorReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/validation/dashboard-summary"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void dashboardSummary_moderatorReturnsAggregateContract() throws Exception {
        when(validationService.getDashboardSummary())
                .thenReturn(new ValidationDashboardSummaryDto(9, 14, 2, 6));

        mockMvc.perform(get("/api/v1/validation/dashboard-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.awaitingReview").value(9))
                .andExpect(jsonPath("$.data.approvedThisMonth").value(14))
                .andExpect(jsonPath("$.data.rejectedThisMonth").value(2))
                .andExpect(jsonPath("$.data.contributorCount").value(6));
    }
}
