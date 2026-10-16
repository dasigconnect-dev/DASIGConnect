package com.dasigconnect.backend.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.TenantScopeService;
import com.dasigconnect.backend.service.TokenManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FacebookOAuthCallbackController.class)
@Import(SecurityConfig.class)
class FacebookOAuthCallbackControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TokenManagementService tokenManagementService;
    @MockitoBean private JWTService jwtService;
    @MockitoBean private TenantScopeService tenantScopeService;

    @Test
    void oauthCallback_isPublicAndDelegatesCodeAndState() throws Exception {
        when(tokenManagementService.handleCallback("oauth-code", "one-time-state"))
                .thenReturn("Facebook Page connected. You may close this window.");

        mockMvc.perform(get("/api/v1/facebook/oauth-callback")
                        .param("code", "oauth-code")
                        .param("state", "one-time-state"))
                .andExpect(status().isOk())
                .andExpect(content().string("Facebook Page connected. You may close this window."));

        verify(tokenManagementService).handleCallback("oauth-code", "one-time-state");
    }

    @Test
    void oauthCallback_withoutCode_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/facebook/oauth-callback")
                        .param("state", "one-time-state"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oauthCallback_withoutState_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/facebook/oauth-callback")
                        .param("code", "oauth-code"))
                .andExpect(status().isBadRequest());
    }
}
