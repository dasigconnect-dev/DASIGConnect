package com.dasigconnect.backend.controller;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.TenantScopeService;
import com.dasigconnect.backend.service.TokenManagementService;

@WebMvcTest(FacebookOAuthCallbackController.class)
@Import(SecurityConfig.class)
class FacebookOAuthCallbackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private TokenManagementService tokenManagementService;
    @MockitoBean private JWTService jwtService;
    @MockitoBean private TenantScopeService tenantScopeService;

    @Test
    void oauthCallback_withoutJwt_allowsMetaRedirectAndReturnsFrontendRedirect() throws Exception {
        mockMvc.perform(get("/api/admin/resolution/tokens/oauth-callback")
                        .param("code", "meta-code")
                        .param("state", "oauth-state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "http://localhost:5173/admin/resolution?tab=system-audit&facebook=connected"));

        verify(tokenManagementService).handleCallback("meta-code", "oauth-state");
    }

    @Test
    void oauthCallback_missingState_returns400() throws Exception {
        mockMvc.perform(get("/api/admin/resolution/tokens/oauth-callback")
                        .param("code", "meta-code"))
                .andExpect(status().isBadRequest());
    }
}
