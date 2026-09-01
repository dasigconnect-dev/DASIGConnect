package com.dasigconnect.backend.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dasigconnect.backend.service.MessengerWebhookService;

@RestController
@RequestMapping("/api/v1/integrations/messenger/webhook")
public class MessengerWebhookController {

    private final MessengerWebhookService webhookService;

    public MessengerWebhookController(MessengerWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {
        if (challenge == null || !webhookService.isVerificationRequestValid(mode, verifyToken)) {
            return ResponseEntity.status(403).body("Verification failed");
        }
        return ResponseEntity.ok(challenge);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> receive(
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody byte[] payload) {
        if (!webhookService.isSignatureValid(payload, signature)) {
            return ResponseEntity.status(401).body("Invalid signature");
        }
        webhookService.accept(payload);
        return ResponseEntity.ok("EVENT_RECEIVED");
    }
}
