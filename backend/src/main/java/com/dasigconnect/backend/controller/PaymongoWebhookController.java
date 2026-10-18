package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.service.PaymongoWebhookService;
import com.dasigconnect.backend.service.PaymongoWebhookSignatureVerifier;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks/paymongo")
public class PaymongoWebhookController {
    private final PaymongoWebhookSignatureVerifier signatureVerifier;
    private final PaymongoWebhookService webhookService;

    public PaymongoWebhookController(
            PaymongoWebhookSignatureVerifier signatureVerifier,
            PaymongoWebhookService webhookService) {
        this.signatureVerifier = signatureVerifier;
        this.webhookService = webhookService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> receive(
            @RequestHeader(name = "Paymongo-Signature", required = false) String signature,
            @RequestBody byte[] payload) {
        if (!signatureVerifier.isValid(payload, signature)) {
            return ResponseEntity.status(401).body(Map.of("status", "invalid_signature"));
        }
        PaymongoWebhookService.WebhookResult result = webhookService.accept(payload);
        return ResponseEntity.ok(Map.of("status", result.name().toLowerCase()));
    }
}
