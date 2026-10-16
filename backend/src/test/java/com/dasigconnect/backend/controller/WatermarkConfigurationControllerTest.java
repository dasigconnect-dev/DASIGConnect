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
import com.dasigconnect.backend.model.dto.settings.WatermarkConfigurationDto;
import com.dasigconnect.backend.model.dto.settings.WatermarkConfigurationRequestDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.TenantScopeService;
import com.dasigconnect.backend.service.WatermarkConfigurationService;
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

@WebMvcTest(WatermarkConfigurationController.class)
@Import(SecurityConfig.class)
class WatermarkConfigurationControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private WatermarkConfigurationService watermarkService;
    @MockitoBean private JWTService jwtService;
    @MockitoBean private TenantScopeService tenantScopeService;

    @Test
    void get_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/settings/watermark"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_asContributor_returnsConfiguration() throws Exception {
        UsernamePasswordAuthenticationToken auth = auth("contributor", UUID.randomUUID());
        WatermarkConfigurationDto response = configuration(true);
        when(watermarkService.get(any(JwtUserDetails.class))).thenReturn(response);

        mockMvc.perform(get("/api/v1/settings/watermark").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(response.id().toString()))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.elements").isEmpty());

        verify(watermarkService).get((JwtUserDetails) auth.getPrincipal());
    }

    @Test
    @WithMockUser(roles = "CONTRIBUTOR")
    void save_asContributor_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/settings/watermark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"elements\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void save_asAdmin_returnsSavedConfiguration() throws Exception {
        UsernamePasswordAuthenticationToken auth = auth("admin", null);
        WatermarkConfigurationDto response = configuration(false);
        when(watermarkService.save(
                any(WatermarkConfigurationRequestDto.class), any(JwtUserDetails.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/settings/watermark")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"elements\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        verify(watermarkService).save(
                any(WatermarkConfigurationRequestDto.class), eq((JwtUserDetails) auth.getPrincipal()));
    }

    @Test
    void save_withMissingElements_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/settings/watermark")
                        .with(authentication(auth("admin", null)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isBadRequest());
    }

    private static WatermarkConfigurationDto configuration(boolean enabled) {
        return new WatermarkConfigurationDto(
                UUID.randomUUID(), enabled, List.of(),
                Instant.parse("2026-10-16T08:00:00Z"), "admin@dasig.gov.ph");
    }

    private static UsernamePasswordAuthenticationToken auth(String role, UUID institutionId) {
        JwtUserDetails principal = new JwtUserDetails(
                UUID.randomUUID(), role + "@example.com", role, institutionId);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
    }
}
