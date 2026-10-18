package com.dasigconnect.backend.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.PaymongoWebhookService;
import com.dasigconnect.backend.service.PaymongoWebhookSignatureVerifier;
import com.dasigconnect.backend.service.TenantScopeService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymongoWebhookController.class)
@Import(SecurityConfig.class)
class PaymongoWebhookControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean PaymongoWebhookSignatureVerifier signatureVerifier;
    @MockitoBean PaymongoWebhookService webhookService;
    @MockitoBean JWTService jwtService;
    @MockitoBean TenantScopeService tenantScopeService;

    @Test
    void receive_invalidSignature_returnsUnauthorizedWithoutProcessing() throws Exception {
        byte[] payload = "{\"data\":{\"id\":\"evt_1\"}}".getBytes(StandardCharsets.UTF_8);
        when(signatureVerifier.isValid(payload, "invalid")).thenReturn(false);

        mockMvc.perform(post("/api/v1/webhooks/paymongo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Paymongo-Signature", "invalid")
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("invalid_signature"));

        verifyNoInteractions(webhookService);
    }

    @Test
    void receive_validSignature_returnsProcessingResult() throws Exception {
        byte[] payload = "{\"data\":{\"id\":\"evt_1\"}}".getBytes(StandardCharsets.UTF_8);
        when(signatureVerifier.isValid(payload, "valid")).thenReturn(true);
        when(webhookService.accept(payload)).thenReturn(PaymongoWebhookService.WebhookResult.PROCESSED);

        mockMvc.perform(post("/api/v1/webhooks/paymongo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Paymongo-Signature", "valid")
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("processed"));

        verify(webhookService).accept(payload);
    }

    @Test
    void receive_duplicateEvent_returnsIdempotentResult() throws Exception {
        byte[] payload = "{\"data\":{\"id\":\"evt_duplicate\"}}".getBytes(StandardCharsets.UTF_8);
        when(signatureVerifier.isValid(payload, "valid")).thenReturn(true);
        when(webhookService.accept(payload)).thenReturn(PaymongoWebhookService.WebhookResult.DUPLICATE);

        mockMvc.perform(post("/api/v1/webhooks/paymongo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Paymongo-Signature", "valid")
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("duplicate"));
    }
}
