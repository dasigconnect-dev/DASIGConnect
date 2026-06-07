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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FacebookInsightsClient {

    private static final Logger log = LoggerFactory.getLogger(FacebookInsightsClient.class);

    private final String pageId;
    private final String apiVersion;
    private final TokenEncryptionService tokenEncryptionService;
    private final FacebookPageTokenRepository pageTokenRepository;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FacebookInsightsClient(
            @Value("${app.facebook.page-id:}") String pageId,
            @Value("${app.facebook.api-version:v25.0}") String apiVersion,
            TokenEncryptionService tokenEncryptionService,
            FacebookPageTokenRepository pageTokenRepository) {
        this.pageId = pageId;
        this.apiVersion = apiVersion;
        this.tokenEncryptionService = tokenEncryptionService;
        this.pageTokenRepository = pageTokenRepository;
    }

    public boolean isConfigured() {
        return pageId != null && !pageId.isBlank() && tokenEncryptionService.isConfigured();
    }

    public FacebookPostMetricsSnapshot fetchPostMetrics(String facebookPostId) throws IOException, InterruptedException {
        String token = resolveActiveToken();
        if (token == null) {
            throw new IOException("No active Facebook page token found.");
        }

        JsonNode post = getJson(postMetricsUrl(facebookPostId, token));
        Integer reach = null;
        Integer impressions = null;
        JsonNode insights = null;
        try {
            insights = getJson(insightsUrl(facebookPostId, token));
            reach = metricValue(insights, "post_impressions_unique");
            impressions = metricValue(insights, "post_impressions");
        } catch (IOException ex) {
            log.warn("Facebook insights metrics unavailable for post {}: {}", facebookPostId, ex.getMessage());
        }

        return new FacebookPostMetricsSnapshot(
                countAt(post, "reactions", "summary", "total_count"),
                countAt(post, "comments", "summary", "total_count"),
                countAt(post, "shares", "count"),
                reach,
                impressions,
                post.toString(),
                insights == null ? null : insights.toString());
    }

    private String resolveActiveToken() {
        return pageTokenRepository.findByPageIdAndIsActiveTrue(pageId)
                .map(FacebookPageToken::getEncryptedToken)
                .map(encrypted -> {
                    try {
                        return tokenEncryptionService.decryptToken(encrypted);
                    } catch (Exception ex) {
                        log.error("Failed to decrypt Facebook page token for insights sync: {}", ex.getMessage());
                        return null;
                    }
                })
                .orElse(null);
    }

    private String postMetricsUrl(String facebookPostId, String token) {
        String fields = "reactions.limit(0).summary(true),comments.limit(0).summary(true),shares";
        return "https://graph.facebook.com/" + apiVersion + "/" + facebookPostId
                + "?fields=" + encode(fields)
                + "&access_token=" + encode(token);
    }

    private String insightsUrl(String facebookPostId, String token) {
        return "https://graph.facebook.com/" + apiVersion + "/" + facebookPostId + "/insights"
                + "?metric=" + encode("post_impressions,post_impressions_unique")
                + "&period=lifetime"
                + "&access_token=" + encode(token);
    }

    private JsonNode getJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode node = objectMapper.readTree(response.body());
        if (node.has("error")) {
            throw new IOException("Graph API error: " + node.get("error"));
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Graph API HTTP " + response.statusCode() + ": " + response.body());
        }
        return node;
    }

    private static int countAt(JsonNode node, String... path) {
        JsonNode current = node;
        for (String key : path) {
            current = current.path(key);
        }
        return current.isNumber() ? current.asInt() : 0;
    }

    private static Integer metricValue(JsonNode insights, String metricName) {
        JsonNode data = insights.path("data");
        if (!data.isArray()) {
            return null;
        }
        for (JsonNode metric : data) {
            if (metricName.equals(metric.path("name").asText())) {
                JsonNode values = metric.path("values");
                if (values.isArray() && !values.isEmpty()) {
                    JsonNode value = values.get(0).path("value");
                    return value.isNumber() ? value.asInt() : null;
                }
            }
        }
        return null;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record FacebookPostMetricsSnapshot(
            int reactions,
            int comments,
            int shares,
            Integer reach,
            Integer impressions,
            String rawPost,
            String rawInsights) {
    }
}
