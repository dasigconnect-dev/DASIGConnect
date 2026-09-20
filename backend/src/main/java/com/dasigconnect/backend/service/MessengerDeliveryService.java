package com.dasigconnect.backend.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.dasigconnect.backend.model.entity.FacebookPageToken;
import com.dasigconnect.backend.repository.FacebookPageTokenRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class MessengerDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(MessengerDeliveryService.class);

    private final MessengerConnectionService connections;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final boolean enabled;
    private final String apiVersion;

    // Messenger sends through the same Page Access Token used for publishing
    // (a page token with pages_messaging carries Send API rights too), so
    // this must resolve the same DB-driven active FacebookPageToken row that
    // FacebookPublisherService uses (see CLAUDE.md's "connected Facebook Page
    // is DB-driven, not env-driven") rather than a static env var — those env
    // vars are a one-time bootstrap only and go stale the moment the token is
    // reauthorized/rotated via System Health -> Tokens, silently breaking
    // Messenger delivery while publishing keeps working fine.
    private final TokenEncryptionService tokenEncryptionService;
    private final FacebookPageTokenRepository pageTokenRepository;

    public MessengerDeliveryService(
            MessengerConnectionService connections,
            ObjectMapper objectMapper,
            TokenEncryptionService tokenEncryptionService,
            FacebookPageTokenRepository pageTokenRepository,
            @Value("${app.messenger.enabled:true}") boolean enabled,
            @Value("${app.messenger.api-version:${app.facebook.api-version:v25.0}}") String apiVersion) {
        this.connections = connections;
        this.objectMapper = objectMapper;
        this.tokenEncryptionService = tokenEncryptionService;
        this.pageTokenRepository = pageTokenRepository;
        this.enabled = enabled;
        this.apiVersion = apiVersion;
    }

    /**
     * Dispatches a Messenger notification to a user by UUID.
     * Silently skips without error if the user has not linked Messenger (A5)
     * or if Messenger is not enabled.
     */
    public boolean sendToUser(UUID userId, String message) {
        if (userId == null) return false;
        return connections.psidFor(userId)
                .map(psid -> sendToPsid(psid, message))
                .orElse(false);
    }

    public boolean sendToPsid(String psid, String message) {
        if (!enabled) return false;
        if (psid == null || psid.isBlank() || message == null || message.isBlank()) {
            return false;
        }
        Optional<FacebookPageToken> active = pageTokenRepository.findFirstByIsActiveTrue();
        if (active.isEmpty()) {
            log.warn("Messenger delivery skipped: no active Facebook page token found.");
            return false;
        }
        FacebookPageToken token = active.get();
        if (token.getExpiresAt() != null && !token.getExpiresAt().isAfter(Instant.now())) {
            log.warn("Messenger delivery skipped: Facebook page token expired at {}.", token.getExpiresAt());
            return false;
        }
        String pageId = token.getPageId();
        String accessToken;
        try {
            accessToken = tokenEncryptionService.decryptToken(token.getEncryptedToken());
        } catch (Exception ex) {
            log.error("Messenger delivery skipped: failed to decrypt Facebook page token: {}", ex.getMessage());
            return false;
        }
        if (pageId == null || pageId.isBlank() || accessToken == null || accessToken.isBlank()) {
            return false;
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "recipient", Map.of("id", psid),
                    "messaging_type", "RESPONSE",
                    "message", Map.of("text", message)));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://graph.facebook.com/" + apiVersion + "/" + pageId + "/messages"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 == 2) {
                log.info("Messenger delivery succeeded for recipient PSID {}", psid);
                return true;
            }
            log.warn("Messenger delivery rejected with HTTP {}: {}", response.statusCode(), response.body());
        } catch (Exception ex) {
            log.warn("Messenger delivery failed: {}", ex.getMessage());
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
        }
        return false;
    }
}
