package com.dasigconnect.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.external.ClaudeVisionClient;
import com.dasigconnect.backend.model.dto.ai.TopPostTemplateSuggestionDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.AiAdoptionTrackingService;
import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.TenantScopeService;
import com.dasigconnect.backend.service.TopPostTemplateService;
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

@WebMvcTest(AiTemplateController.class)
@Import(SecurityConfig.class)
class AiTemplateControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TopPostTemplateService topPostTemplateService;
    @MockitoBean private AiAdoptionTrackingService adoptionTrackingService;
    @MockitoBean private JWTService jwtService;
    @MockitoBean private TenantScopeService tenantScopeService;

    @Test
    void fromTopPosts_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/ai/templates/from-top-posts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void fromTopPosts_withUnsupportedRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/ai/templates/from-top-posts"))
                .andExpect(status().isForbidden());
    }

    @Test
    void fromTopPosts_availableSuggestion_tracksGeneratedDraft() throws Exception {
        UUID institutionId = UUID.randomUUID();
        when(topPostTemplateService.suggest()).thenReturn(availableSuggestion());

        mockMvc.perform(post("/api/v1/ai/templates/from-top-posts")
                        .with(authentication(auth(UUID.randomUUID(), "contributor", institutionId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.name").value("Community Event Update"))
                .andExpect(jsonPath("$.data.postsConsidered").value(12));

        verify(adoptionTrackingService).recordTemplateDraft(institutionId, "generated");
    }

    @Test
    void fromTopPosts_trackingFailure_doesNotFailSuggestion() throws Exception {
        UUID institutionId = UUID.randomUUID();
        when(topPostTemplateService.suggest()).thenReturn(availableSuggestion());
        doThrow(new RuntimeException("analytics unavailable"))
                .when(adoptionTrackingService).recordTemplateDraft(institutionId, "generated");

        mockMvc.perform(post("/api/v1/ai/templates/from-top-posts")
                        .with(authentication(auth(UUID.randomUUID(), "admin", institutionId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    void fromTopPosts_providerTimeout_returns504() throws Exception {
        when(topPostTemplateService.suggest()).thenThrow(
                new ClaudeVisionClient.ClaudeApiException("Claude API timed out after 30 seconds."));

        mockMvc.perform(post("/api/v1/ai/templates/from-top-posts")
                        .with(authentication(auth(UUID.randomUUID(), "moderator", null))))
                .andExpect(status().isGatewayTimeout());
    }

    @Test
    void fromTopPosts_providerError_returns503() throws Exception {
        when(topPostTemplateService.suggest()).thenThrow(
                new ClaudeVisionClient.ClaudeApiException("Claude API error (HTTP 500)."));

        mockMvc.perform(post("/api/v1/ai/templates/from-top-posts")
                        .with(authentication(auth(UUID.randomUUID(), "contributor", UUID.randomUUID()))))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void fromTopPosts_afterHourlyLimit_returns429WithHeaders() throws Exception {
        UUID userId = UUID.randomUUID();
        UsernamePasswordAuthenticationToken auth = auth(userId, "contributor", UUID.randomUUID());
        when(topPostTemplateService.suggest()).thenReturn(
                TopPostTemplateSuggestionDto.unavailable("Not enough history", "DASIGCONNECT", 2));

        for (int request = 0; request < 10; request++) {
            mockMvc.perform(post("/api/v1/ai/templates/from-top-posts").with(authentication(auth)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/v1/ai/templates/from-top-posts").with(authentication(auth)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().exists("X-RateLimit-Reset"));
    }

    private static TopPostTemplateSuggestionDto availableSuggestion() {
        return new TopPostTemplateSuggestionDto(
                true, null, "Community Event Update", "Join our upcoming event.",
                List.of("community", "event"), List.of("Short captions performed well"),
                "FACEBOOK", 12, List.of());
    }

    private static UsernamePasswordAuthenticationToken auth(
            UUID userId, String role, UUID institutionId) {
        JwtUserDetails principal = new JwtUserDetails(
                userId, role + "@example.com", role, institutionId);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
    }
}
