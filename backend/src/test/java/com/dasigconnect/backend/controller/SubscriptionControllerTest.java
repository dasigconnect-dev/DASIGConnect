package com.dasigconnect.backend.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.model.dto.subscription.SubscriptionPlanDto;
import com.dasigconnect.backend.model.dto.subscription.SubscriptionStatusDto;
import com.dasigconnect.backend.model.dto.subscription.SubscriptionUsageMetricDto;
import com.dasigconnect.backend.model.dto.subscription.SubscriptionUsageSummaryDto;
import com.dasigconnect.backend.model.entity.OrganizationStatus;
import com.dasigconnect.backend.model.entity.SubscriptionExportLevel;
import com.dasigconnect.backend.model.entity.SubscriptionLifecycleStatus;
import com.dasigconnect.backend.model.entity.SubscriptionSupportLevel;
import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.SubscriptionQueryService;
import com.dasigconnect.backend.service.SubscriptionAdministrationService;
import com.dasigconnect.backend.service.SubscriptionUsageService;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.TenantScopeService;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@WebMvcTest(SubscriptionController.class)
@Import(SecurityConfig.class)
class SubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubscriptionQueryService subscriptionQueryService;

    @MockitoBean
    private SubscriptionUsageService subscriptionUsageService;

    @MockitoBean
    private SubscriptionAdministrationService subscriptionAdministrationService;

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

    @Test
    @WithMockUser(roles = "CONTRIBUTOR")
    void usage_asContributor_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/subscription/usage"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void usage_asAdmin_returnsRequestedMonthAndMetrics() throws Exception {
        UUID organizationId = UUID.randomUUID();
        YearMonth month = YearMonth.of(2026, 10);
        SubscriptionUsageSummaryDto usage = new SubscriptionUsageSummaryDto(
                organizationId,
                month,
                Instant.parse("2026-09-30T16:00:00Z"),
                Instant.parse("2026-10-31T16:00:00Z"),
                SubscriptionLifecycleStatus.ACTIVE,
                false,
                List.of(new SubscriptionUsageMetricDto("submissions", 12, 50, "COUNT")));
        when(subscriptionUsageService.monthlySummary(month)).thenReturn(usage);

        mockMvc.perform(get("/api/v1/subscription/usage").param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.organizationId").value(organizationId.toString()))
                .andExpect(jsonPath("$.data.month").value("2026-10"))
                .andExpect(jsonPath("$.data.metrics[0].resource").value("submissions"))
                .andExpect(jsonPath("$.data.metrics[0].used").value(12));
    }

    @Test
    void changePlan_asAdminOwner_returnsUpdatedPlan() throws Exception {
        UUID planId = UUID.randomUUID();
        JwtUserDetails owner = new JwtUserDetails(
                UUID.randomUUID(), "owner@dasig.test", "admin", null, true);
        SubscriptionStatusDto updated = new SubscriptionStatusDto(
                UUID.randomUUID(), "DASIG Central Visayas", "dasig-central-visayas",
                OrganizationStatus.ACTIVE, UUID.randomUUID(), SubscriptionLifecycleStatus.ACTIVE,
                Instant.parse("2026-10-18T00:00:00Z"), Instant.parse("2026-11-18T00:00:00Z"),
                null, plan(), true);
        when(subscriptionAdministrationService.changePlan(planId, owner)).thenReturn(updated);

        mockMvc.perform(post("/api/v1/subscription/admin/change-plan")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                owner, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"planId":"%s"}
                                """.formatted(planId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plan.code").value("FREE_PILOT"));
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void changePlan_asModerator_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/subscription/admin/change-plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"planId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void recordRenewal_asAdminOwner_returnsActiveSubscription() throws Exception {
        JwtUserDetails owner = new JwtUserDetails(
                UUID.randomUUID(), "owner@dasig.test", "admin", null, true);
        SubscriptionStatusDto updated = new SubscriptionStatusDto(
                UUID.randomUUID(), "DASIG Central Visayas", "dasig-central-visayas",
                OrganizationStatus.ACTIVE, UUID.randomUUID(), SubscriptionLifecycleStatus.ACTIVE,
                Instant.parse("2026-10-18T00:00:00Z"), Instant.parse("2026-11-18T00:00:00Z"),
                null, plan(), true);
        when(subscriptionAdministrationService.recordManualRenewal(1, "OR-100", owner)).thenReturn(updated);

        mockMvc.perform(post("/api/v1/subscription/admin/record-renewal")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                owner, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"billingMonths":1,"paymentReference":"OR-100"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscriptionStatus").value("ACTIVE"));
    }

    @Test
    void extendGracePeriod_withInvalidDays_returnsBadRequest() throws Exception {
        JwtUserDetails owner = new JwtUserDetails(
                UUID.randomUUID(), "owner@dasig.test", "admin", null, true);

        mockMvc.perform(post("/api/v1/subscription/admin/extend-grace-period")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                owner, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"days":31}
                                """))
                .andExpect(status().isBadRequest());
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
