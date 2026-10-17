package com.dasigconnect.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.model.dto.settings.PageSettingsDto;
import com.dasigconnect.backend.model.dto.settings.UpdatePageSettingsRequestDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.PageSettingsService;
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

@WebMvcTest(PageSettingsController.class)
@Import(SecurityConfig.class)
class PageSettingsControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private PageSettingsService pageSettingsService;
    @MockitoBean private JWTService jwtService;
    @MockitoBean private TenantScopeService tenantScopeService;

    @Test
    void get_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/settings/page"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void get_asModerator_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/settings/page"))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_asAdmin_passesInstitutionAndActor() throws Exception {
        UUID institutionId = UUID.randomUUID();
        UsernamePasswordAuthenticationToken auth = adminAuth();
        PageSettingsDto response = settings(institutionId, true);
        when(pageSettingsService.get(eq(institutionId), any(JwtUserDetails.class))).thenReturn(response);

        mockMvc.perform(get("/api/v1/settings/page")
                        .param("institutionId", institutionId.toString())
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.institutionId").value(institutionId.toString()))
                .andExpect(jsonPath("$.data.guardrailsEnforced").value(true))
                .andExpect(jsonPath("$.data.dailyVolumeCap").value(20));

        verify(pageSettingsService).get(institutionId, (JwtUserDetails) auth.getPrincipal());
    }

    @Test
    void update_asAdmin_delegatesRequest() throws Exception {
        UUID institutionId = UUID.randomUUID();
        UsernamePasswordAuthenticationToken auth = adminAuth();
        when(pageSettingsService.update(
                eq(institutionId), any(UpdatePageSettingsRequestDto.class), any(JwtUserDetails.class)))
                .thenReturn(settings(institutionId, false));

        mockMvc.perform(put("/api/v1/settings/page")
                        .param("institutionId", institutionId.toString())
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guardrailsEnforced\":false,\"dailyVolumeCap\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.guardrailsEnforced").value(false));

        verify(pageSettingsService).update(
                eq(institutionId), any(UpdatePageSettingsRequestDto.class),
                eq((JwtUserDetails) auth.getPrincipal()));
    }

    private static PageSettingsDto settings(UUID institutionId, boolean enforced) {
        return new PageSettingsDto(
                institutionId, enforced, 8, 20, 30, 2, 90, 5, 20,
                Instant.parse("2026-10-16T08:00:00Z"));
    }

    private static UsernamePasswordAuthenticationToken adminAuth() {
        JwtUserDetails principal = new JwtUserDetails(
                UUID.randomUUID(), "admin@dasig.gov.ph", "admin", null);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
