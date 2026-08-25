package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.entity.FacebookPageToken;
import com.dasigconnect.backend.repository.FacebookPageTokenRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Reads recent Page post engagement without exposing Facebook credentials to the browser. */
@Component
public class FacebookEngagementAnalyticsClient {

    private final FacebookPageTokenRepository tokenRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final String configuredToken;
    private final String pageId;
    private final String apiVersion;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public FacebookEngagementAnalyticsClient(
            FacebookPageTokenRepository tokenRepository,
            TokenEncryptionService tokenEncryptionService,
            @Value("${app.facebook.page-access-token:}") String configuredToken,
            @Value("${app.facebook.page-id:}") String pageId,
            @Value("${app.facebook.api-version:v25.0}") String apiVersion) {
        this(tokenRepository, tokenEncryptionService, configuredToken, pageId, apiVersion,
                HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build(),
                new ObjectMapper());
    }

    FacebookEngagementAnalyticsClient(
            FacebookPageTokenRepository tokenRepository,
            TokenEncryptionService tokenEncryptionService,
            String configuredToken,
            String pageId,
            String apiVersion,
            HttpClient httpClient,
            ObjectMapper objectMapper) {
        this.tokenRepository = tokenRepository;
        this.tokenEncryptionService = tokenEncryptionService;
        this.configuredToken = configuredToken;
        this.pageId = pageId;
        this.apiVersion = apiVersion;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public List<EngagementSample> fetchRecentPostEngagement() throws IOException, InterruptedException {
        String token = resolveToken();
        if (pageId == null || pageId.isBlank() || token == null || token.isBlank()) {
            throw new IOException("Facebook engagement analytics is not configured.");
        }
        String fields = "created_time,reactions.limit(0).summary(true),comments.limit(0).summary(true),shares";
        String url = "https://graph.facebook.com/" + apiVersion + "/" + encode(pageId)
                + "/posts?fields=" + encode(fields) + "&limit=100&access_token=" + encode(token);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(java.time.Duration.ofSeconds(8)).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Facebook analytics returned HTTP " + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        if (root.has("error")) throw new IOException("Facebook analytics request failed.");
        List<EngagementSample> samples = new ArrayList<>();
        for (JsonNode post : root.path("data")) {
            String created = post.path("created_time").asText("");
            if (created.isBlank()) continue;
            long likes = post.path("reactions").path("summary").path("total_count").asLong(0);
            long comments = post.path("comments").path("summary").path("total_count").asLong(0);
            long shares = post.path("shares").path("count").asLong(0);
            samples.add(new EngagementSample(parseFacebookInstant(created), likes + comments + shares));
        }
        return samples;
    }

    private String resolveToken() {
        return tokenRepository.findByPageIdAndIsActiveTrue(pageId)
                .map(FacebookPageToken::getEncryptedToken)
                .map(value -> {
                    try { return tokenEncryptionService.decryptToken(value); }
                    catch (RuntimeException ignored) { return configuredToken; }
                })
                .orElse(configuredToken);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static Instant parseFacebookInstant(String value) {
        if (value.matches(".*[+-]\\d{4}$")) {
            value = value.substring(0, value.length() - 2) + ":" + value.substring(value.length() - 2);
        }
        return java.time.OffsetDateTime.parse(value).toInstant();
    }

    public record EngagementSample(Instant publishedAt, double engagementScore) {}
}
