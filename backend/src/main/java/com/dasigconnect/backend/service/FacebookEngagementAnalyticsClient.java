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

    /**
     * Fetches reactions/comments/shares (and best-effort reach) for a single
     * Facebook post by its platform post ID. Reactions/comments/shares use the
     * same `/posts` fields already proven working by
     * {@link #fetchRecentPostEngagement()}; reach comes from a separate
     * Insights call that is allowed to fail (the Page token may not have
     * read_insights granted) without failing the whole fetch.
     */
    public PostEngagement fetchPostEngagement(String postId) throws IOException, InterruptedException {
        String token = resolveToken();
        if (postId == null || postId.isBlank() || token == null || token.isBlank()) {
            throw new IOException("Facebook engagement analytics is not configured.");
        }
        String fields = "reactions.limit(0).summary(true),comments.limit(0).summary(true),shares";
        String url = "https://graph.facebook.com/" + apiVersion + "/" + encode(postId)
                + "?fields=" + encode(fields) + "&access_token=" + encode(token);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(java.time.Duration.ofSeconds(8)).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Facebook analytics returned HTTP " + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        if (root.has("error")) {
            throw new IOException("Facebook analytics request failed for post " + postId + ".");
        }
        long reactions = root.path("reactions").path("summary").path("total_count").asLong(0);
        long comments = root.path("comments").path("summary").path("total_count").asLong(0);
        long shares = root.path("shares").path("count").asLong(0);
        Long reach = fetchReach(postId, token);
        return new PostEngagement(reach, reactions, comments, shares);
    }

    private Long fetchReach(String postId, String token) {
        try {
            String url = "https://graph.facebook.com/" + apiVersion + "/" + encode(postId)
                    + "/insights?metric=post_impressions_unique&access_token=" + encode(token);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(8)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (root.has("error")) {
                return null;
            }
            JsonNode values = root.path("data").path(0).path("values").path(0).path("value");
            return values.isMissingNode() ? null : values.asLong();
        } catch (Exception ignored) {
            return null;
        }
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

    /** reach may be null when the Insights call fails or is unavailable (missing read_insights permission). */
    public record PostEngagement(Long reach, long reactions, long comments, long shares) {}
}
