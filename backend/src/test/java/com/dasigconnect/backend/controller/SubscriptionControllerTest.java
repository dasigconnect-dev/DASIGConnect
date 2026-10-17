package com.dasigconnect.backend.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.model.dto.subscription.SubscriptionPlanDto;
import com.dasigconnect.backend.model.dto.subscription.SubscriptionStatusDto;
import com.dasigconnect.backend.model.entity.OrganizationStatus;
import com.dasigconnect.backend.model.entity.SubscriptionExportLevel;
import com.dasigconnect.backend.model.entity.SubscriptionLifecycleStatus;
import com.dasigconnect.backend.model.entity.SubscriptionSupportLevel;
import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.SubscriptionQueryService;
import com.dasigconnect.backend.service.TenantScopeService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SubscriptionController.class)
@Import(SecurityConfig.class)
class SubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubscriptionQueryService subscriptionQueryService;

    @MockitoBean
    private JWTService jwtService;

    @MockitoBean
    private TenantScopeService tenantScopeService;

    @Test
    void currentSubscription_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/subscription"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CONTRIBUTOR")
    void currentSubscription_asContributor_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/subscription"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void plans_asModerator_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/subscription/plans"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void currentSubscription_asAdmin_returnsStatus() throws Exception {
        SubscriptionPlanDto plan = plan();
        SubscriptionStatusDto statusDto = new SubscriptionStatusDto(
                UUID.randomUUID(),
                "DASIG Central Visayas",
                "dasig-central-visayas",
                OrganizationStatus.ACTIVE,
                UUID.randomUUID(),
                SubscriptionLifecycleStatus.TRIAL,
                Instant.parse("2026-10-18T00:00:00Z"),
                Instant.parse("2026-12-18T00:00:00Z"),
                null,
                plan,
                false);
        when(subscriptionQueryService.currentSubscription()).thenReturn(statusDto);

        mockMvc.perform(get("/api/v1/subscription"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.organizationSlug").value("dasig-central-visayas"))
                .andExpect(jsonPath("$.data.subscriptionStatus").value("TRIAL"))
                .andExpect(jsonPath("$.data.plan.code").value("FREE_PILOT"))
                .andExpect(jsonPath("$.data.enforcementEnabled").value(false));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void plans_asAdmin_returnsActivePlans() throws Exception {
        when(subscriptionQueryService.activePlans()).thenReturn(List.of(plan()));

        mockMvc.perform(get("/api/v1/subscription/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Free Pilot"))
                .andExpect(jsonPath("$.data[0].monthlyAiCaptionLimit").value(100));
    }

    private static SubscriptionPlanDto plan() {
        return new SubscriptionPlanDto(
                UUID.randomUUID(),
                "FREE_PILOT",
                "Free Pilot",
                1,
                0,
                3,
                15,
                10_000_000_000L,
                50,
                100,
                300,
                5,
                30,
                SubscriptionExportLevel.NONE,
                SubscriptionSupportLevel.BEST_EFFORT,
                Instant.parse("2026-10-18T00:00:00Z"),
                null);
    }
}
