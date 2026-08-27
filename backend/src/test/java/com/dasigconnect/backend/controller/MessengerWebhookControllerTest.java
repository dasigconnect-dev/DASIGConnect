package com.dasigconnect.backend.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.MessengerWebhookService;
import com.dasigconnect.backend.service.TenantScopeService;

@WebMvcTest(MessengerWebhookController.class)
@Import(SecurityConfig.class)
class MessengerWebhookControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean MessengerWebhookService webhookService;
    @MockitoBean JWTService jwtService;
    @MockitoBean TenantScopeService tenantScopeService;

    @Test
    void verify_validRequest_returnsChallenge() throws Exception {
        when(webhookService.isVerificationRequestValid("subscribe", "verify-secret")).thenReturn(true);

        mockMvc.perform(get("/api/v1/integrations/messenger/webhook")
                        .queryParam("hub.mode", "subscribe")
                        .queryParam("hub.verify_token", "verify-secret")
                        .queryParam("hub.challenge", "123456"))
                .andExpect(status().isOk())
                .andExpect(content().string("123456"));
    }

    @Test
    void verify_invalidToken_returns403() throws Exception {
        when(webhookService.isVerificationRequestValid("subscribe", "wrong")).thenReturn(false);

        mockMvc.perform(get("/api/v1/integrations/messenger/webhook")
                        .queryParam("hub.mode", "subscribe")
                        .queryParam("hub.verify_token", "wrong")
                        .queryParam("hub.challenge", "123456"))
                .andExpect(status().isForbidden());
    }

    @Test
    void receive_validSignature_acceptsEvent() throws Exception {
        byte[] payload = "{\"object\":\"page\",\"entry\":[]}".getBytes(StandardCharsets.UTF_8);
        when(webhookService.isSignatureValid(payload, "sha256=valid")).thenReturn(true);

        mockMvc.perform(post("/api/v1/integrations/messenger/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", "sha256=valid")
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string("EVENT_RECEIVED"));

        verify(webhookService).accept(payload);
    }

    @Test
    void receive_missingSignature_returns401() throws Exception {
        byte[] payload = "{\"object\":\"page\",\"entry\":[]}".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/integrations/messenger/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());

        verify(webhookService).isSignatureValid(payload, null);
        verifyNoMoreInteractions(webhookService);
    }
}
