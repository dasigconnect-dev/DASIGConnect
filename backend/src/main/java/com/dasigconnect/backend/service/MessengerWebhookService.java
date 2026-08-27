package com.dasigconnect.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class MessengerWebhookService {

    private static final Logger log = LoggerFactory.getLogger(MessengerWebhookService.class);
    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final String HMAC_SHA_256 = "HmacSHA256";

    private final String verifyToken;
    private final String appSecret;
    private final ObjectMapper objectMapper;
    private final MessengerConnectionService connections;
    private final MessengerDeliveryService delivery;

    public MessengerWebhookService(
            @Value("${app.messenger.verify-token:${FACEBOOK_MESSENGER_VERIFY_TOKEN:}}") String verifyToken,
            @Value("${app.messenger.app-secret:${app.facebook.app-secret:${FACEBOOK_APP_SECRET:}}}") String appSecret,
            ObjectMapper objectMapper,
            MessengerConnectionService connections,
            MessengerDeliveryService delivery) {
        this.verifyToken = verifyToken;
        this.appSecret = appSecret;
        this.objectMapper = objectMapper;
        this.connections = connections;
        this.delivery = delivery;
    }

    public boolean isVerificationRequestValid(String mode, String suppliedToken) {
        return "subscribe".equals(mode)
                && verifyToken != null
                && !verifyToken.isBlank()
                && suppliedToken != null
                && MessageDigest.isEqual(
                        verifyToken.getBytes(StandardCharsets.UTF_8),
                        suppliedToken.getBytes(StandardCharsets.UTF_8));
    }

    public boolean isSignatureValid(byte[] payload, String signatureHeader) {
        if (appSecret == null || appSecret.isBlank() || signatureHeader == null
                || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }

        final byte[] suppliedSignature;
        try {
            suppliedSignature = HexFormat.of()
                    .parseHex(signatureHeader.substring(SIGNATURE_PREFIX.length()));
        } catch (IllegalArgumentException ex) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
            return MessageDigest.isEqual(mac.doFinal(payload), suppliedSignature);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", ex);
        }
    }

    /**
     * Processes incoming Messenger webhook event payload.
     */
    public void accept(byte[] payload) {
        log.info("Accepted signed Messenger webhook event ({} bytes)", payload.length);
        try {
            JsonNode root = objectMapper.readTree(payload);
            for (JsonNode entry : root.path("entry")) {
                for (JsonNode event : entry.path("messaging")) {
                    String psid = event.path("sender").path("id").asText("");
                    if (psid.isBlank()) continue;
                    connections.recordInteraction(psid);
                    String text = event.path("message").path("text").asText("").trim();
                    if (!text.toUpperCase(Locale.ROOT).startsWith("CONNECT ")) continue;
                    String code = text.substring("CONNECT ".length()).trim();
                    connections.link(code, psid).ifPresentOrElse(
                            userId -> delivery.sendToPsid(psid,
                                    "Messenger notifications are now connected to your DASIGConnect account."),
                            () -> delivery.sendToPsid(psid,
                                    "That connection code is invalid or expired. Please generate a new code in DASIGConnect."));
                }
            }
        } catch (Exception ex) {
            log.warn("Unable to process Messenger webhook event: {}", ex.getMessage());
        }
    }
}
