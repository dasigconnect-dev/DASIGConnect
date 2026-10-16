package com.dasigconnect.backend.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.model.dto.guardrail.GuardRailResult;
import com.dasigconnect.backend.model.dto.guardrail.GuardRailViolation;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.GuardRailService;
import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.TenantScopeService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GuardRailController.class)
@Import(SecurityConfig.class)
class GuardRailControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private GuardRailService guardRailService;
    @MockitoBean private JWTService jwtService;
    @MockitoBean private TenantScopeService tenantScopeService;

    @Test
    void validate_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/guardrails/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-10-20T08:00:00Z\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void validate_withUnsupportedRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/guardrails/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-10-20T08:00:00Z\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void validate_contributorUsesPrincipalInstitution() throws Exception {
        UUID principalInstitution = UUID.randomUUID();
        UUID requestedInstitution = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        Instant scheduledAt = Instant.parse("2026-10-20T08:00:00Z");
        GuardRailResult result = new GuardRailResult(
                List.of(), List.of(new GuardRailViolation("GR-S1", "High-volume day")));
        when(guardRailService.validate(principalInstitution, scheduledAt, submissionId)).thenReturn(result);

        mockMvc.perform(post("/api/v1/guardrails/validate")
                        .with(authentication(auth("contributor", principalInstitution)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduledAt":"%s","institutionId":"%s","submissionId":"%s"}
                                """.formatted(scheduledAt, requestedInstitution, submissionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.blocked").value(false))
                .andExpect(jsonPath("$.data.clean").value(false))
                .andExpect(jsonPath("$.data.softWarnings[0].code").value("GR-S1"));

        verify(guardRailService).validate(principalInstitution, scheduledAt, submissionId);
    }

    @Test
    void validate_adminWithoutInstitution_usesRequestedInstitution() throws Exception {
        UUID requestedInstitution = UUID.randomUUID();
        Instant scheduledAt = Instant.parse("2026-10-20T08:00:00Z");
        when(guardRailService.validate(requestedInstitution, scheduledAt, null))
                .thenReturn(new GuardRailResult());

        mockMvc.perform(post("/api/v1/guardrails/validate")
                        .with(authentication(auth("admin", null)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduledAt":"%s","institutionId":"%s"}
                                """.formatted(scheduledAt, requestedInstitution)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clean").value(true));

        verify(guardRailService).validate(requestedInstitution, scheduledAt, null);
    }

    @Test
    void validate_withoutScheduledAt_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/guardrails/validate")
                        .with(authentication(auth("moderator", null)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private static UsernamePasswordAuthenticationToken auth(String role, UUID institutionId) {
        JwtUserDetails principal = new JwtUserDetails(
                UUID.randomUUID(), role + "@example.com", role, institutionId);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
    }
}
