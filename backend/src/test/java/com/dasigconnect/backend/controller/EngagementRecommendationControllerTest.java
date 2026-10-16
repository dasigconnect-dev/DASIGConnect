package com.dasigconnect.backend.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.model.dto.engagement.EngagementRecommendationDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.EngagementRecommendationService;
import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.TenantScopeService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EngagementRecommendationController.class)
@Import(SecurityConfig.class)
class EngagementRecommendationControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private EngagementRecommendationService engagementRecommendationService;
    @MockitoBean private JWTService jwtService;
    @MockitoBean private TenantScopeService tenantScopeService;

    @Test
    void recommendations_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/engagement-recommendations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void recommendations_withUnsupportedRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/engagement-recommendations"))
                .andExpect(status().isForbidden());
    }

    @Test
    void recommendations_forContributor_delegatesUserAndInstitution() throws Exception {
        UUID userInstitution = UUID.randomUUID();
        UUID requestedInstitution = UUID.randomUUID();
        UsernamePasswordAuthenticationToken auth = auth("contributor", userInstitution);
        Instant scheduledAt = Instant.parse("2026-10-20T08:00:00Z");
        EngagementRecommendationDto response = new EngagementRecommendationDto(
                true,
                "HISTORICAL",
                "Based on recent engagement",
                "Asia/Manila",
                20,
                List.of(new EngagementRecommendationDto.RecommendedSlotDto(
                        scheduledAt, "Tuesday 4:00 PM", 0.92, List.of())));
        when(engagementRecommendationService.recommend(
                (JwtUserDetails) auth.getPrincipal(), requestedInstitution)).thenReturn(response);

        mockMvc.perform(get("/api/v1/engagement-recommendations")
                        .param("institutionId", requestedInstitution.toString())
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.source").value("HISTORICAL"))
                .andExpect(jsonPath("$.data.sampleSize").value(20))
                .andExpect(jsonPath("$.data.slots[0].scheduledAt").value(scheduledAt.toString()))
                .andExpect(jsonPath("$.data.slots[0].score").value(0.92));

        verify(engagementRecommendationService).recommend(
                (JwtUserDetails) auth.getPrincipal(), requestedInstitution);
    }

    private static UsernamePasswordAuthenticationToken auth(String role, UUID institutionId) {
        JwtUserDetails principal = new JwtUserDetails(
                UUID.randomUUID(), role + "@example.com", role, institutionId);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
    }
}
