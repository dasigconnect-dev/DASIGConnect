package com.dasigconnect.backend.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MessengerDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(MessengerDeliveryService.class);

    private final MessengerConnectionService connections;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final boolean enabled;
    private final String pageId;
    private final String accessToken;
    private final String apiVersion;

    public MessengerDeliveryService(MessengerConnectionService connections, ObjectMapper objectMapper,
            @Value("${app.messenger.enabled:false}") boolean enabled,
            @Value("${app.messenger.page-id:}") String pageId,
            @Value("${app.messenger.page-access-token:}") String accessToken,
            @Value("${app.messenger.api-version:v25.0}") String apiVersion) {
        this.connections = connections;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.pageId = pageId;
        this.accessToken = accessToken;
        this.apiVersion = apiVersion;
    }

    public void sendToUser(UUID userId, String message) {
        connections.psidFor(userId).ifPresent(psid -> sendToPsid(psid, message));
    }

    public boolean sendToPsid(String psid, String message) {
        if (!enabled || pageId.isBlank() || accessToken.isBlank()) return false;
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "recipient", Map.of("id", psid),
                    "messaging_type", "RESPONSE",
                    "message", Map.of("text", message)));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://graph.facebook.com/" + apiVersion + "/"
                            + pageId + "/messages"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 == 2) return true;
            log.warn("Messenger delivery rejected with HTTP {}", response.statusCode());
        } catch (Exception ex) {
            log.warn("Messenger delivery failed: {}", ex.getMessage());
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
        }
        return false;
    }
}
