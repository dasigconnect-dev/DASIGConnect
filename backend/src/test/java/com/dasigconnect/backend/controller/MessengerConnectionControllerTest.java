package com.dasigconnect.backend.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.model.dto.notification.MessengerConnectionDto;
import com.dasigconnect.backend.model.dto.notification.MessengerLinkCodeDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.MessengerConnectionService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MessengerConnectionController.class)
@Import(SecurityConfig.class)
class MessengerConnectionControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private MessengerConnectionService messengerConnectionService;
    @MockitoBean private JWTService jwtService;
    @MockitoBean private TenantScopeService tenantScopeService;

    @Test
    void status_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/integrations/messenger/connection"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void status_withAuthentication_returnsConnection() throws Exception {
        UsernamePasswordAuthenticationToken auth = auth("contributor", UUID.randomUUID());
        Instant linkedAt = Instant.parse("2026-10-16T08:00:00Z");
        when(messengerConnectionService.status((JwtUserDetails) auth.getPrincipal()))
                .thenReturn(new MessengerConnectionDto(true, true, linkedAt));

        mockMvc.perform(get("/api/v1/integrations/messenger/connection")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.linkedAt").value(linkedAt.toString()));
    }

    @Test
    void createCode_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/messenger/connection/link-code"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createCode_withAuthentication_returnsLinkCode() throws Exception {
        UsernamePasswordAuthenticationToken auth = auth("moderator", null);
        Instant expiresAt = Instant.parse("2026-10-16T08:10:00Z");
        when(messengerConnectionService.createLinkCode((JwtUserDetails) auth.getPrincipal()))
                .thenReturn(new MessengerLinkCodeDto("CONNECT ABC123", expiresAt));

        mockMvc.perform(post("/api/v1/integrations/messenger/connection/link-code")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CONNECT ABC123"))
                .andExpect(jsonPath("$.expiresAt").value(expiresAt.toString()));
    }

    @Test
    void disconnect_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/integrations/messenger/connection"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disconnect_withAuthentication_returns204() throws Exception {
        UsernamePasswordAuthenticationToken auth = auth("admin", null);

        mockMvc.perform(delete("/api/v1/integrations/messenger/connection")
                        .with(authentication(auth)))
                .andExpect(status().isNoContent());

        verify(messengerConnectionService).disconnect((JwtUserDetails) auth.getPrincipal());
    }

    private static UsernamePasswordAuthenticationToken auth(String role, UUID institutionId) {
        JwtUserDetails principal = new JwtUserDetails(
                UUID.randomUUID(), role + "@example.com", role, institutionId);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
    }
}
