package com.dasigconnect.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.dasigconnect.backend.service.PaymongoWebhookService;
import com.dasigconnect.backend.service.PaymongoWebhookSignatureVerifier;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PaymongoWebhookControllerDisabledTest {

    @Test
    void receive_whenSubscriptionFeatureIsDisabled_returnsNotFoundWithoutProcessing() {
        PaymongoWebhookSignatureVerifier signatureVerifier = mock(PaymongoWebhookSignatureVerifier.class);
        PaymongoWebhookService webhookService = mock(PaymongoWebhookService.class);
        PaymongoWebhookController controller =
                new PaymongoWebhookController(signatureVerifier, webhookService, false);

        var response = controller.receive("signature", "{}".getBytes(StandardCharsets.UTF_8));

        assertEquals(404, response.getStatusCode().value());
        assertEquals("disabled", response.getBody().get("status"));
        verifyNoInteractions(signatureVerifier, webhookService);
    }
}
