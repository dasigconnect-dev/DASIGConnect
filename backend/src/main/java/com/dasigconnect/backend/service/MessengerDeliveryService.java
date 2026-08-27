package com.dasigconnect.backend.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
    private final String pageId;
    private final String accessToken;
    private final String apiVersion;

    public MessengerDeliveryService(
            MessengerConnectionService connections,
            ObjectMapper objectMapper,
            @Value("${app.messenger.enabled:true}") boolean enabled,
            @Value("${app.messenger.page-id:${app.facebook.page-id:}}") String pageId,
            @Value("${app.messenger.page-access-token:${app.facebook.page-access-token:}}") String accessToken,
            @Value("${app.messenger.api-version:${app.facebook.api-version:v25.0}}") String apiVersion) {
        this.connections = connections;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.pageId = pageId;
        this.accessToken = accessToken;
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
        if (!enabled || pageId == null || pageId.isBlank() || accessToken == null || accessToken.isBlank()) {
            return false;
        }
        if (psid == null || psid.isBlank() || message == null || message.isBlank()) {
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
