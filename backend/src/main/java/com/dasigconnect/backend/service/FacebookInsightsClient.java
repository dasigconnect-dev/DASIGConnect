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
import java.util.LinkedHashMap;
import java.util.Map;
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
        Map<String, JsonNode> insightMetrics = new LinkedHashMap<>();
        reach = fetchIntegerInsight(facebookPostId, token, insightMetrics, "post_impressions_unique");
        impressions = fetchIntegerInsight(facebookPostId, token, insightMetrics, "post_impressions");
        Integer postClicks = fetchIntegerInsight(facebookPostId, token, insightMetrics, "post_clicks");
        Integer views = fetchIntegerInsight(facebookPostId, token, insightMetrics, "post_video_views");
        Integer uniqueViews = fetchIntegerInsight(facebookPostId, token, insightMetrics, "post_video_views_unique");
        Long averageWatchTimeMs = fetchLongInsight(facebookPostId, token, insightMetrics, "post_video_avg_time_watched");
        Long watchTimeMs = fetchLongInsight(facebookPostId, token, insightMetrics, "post_video_view_time");
        Integer fifteenSecondViews = fetchIntegerInsight(facebookPostId, token, insightMetrics, "post_video_views_15s");
        Integer sixtySecondViews = fetchIntegerInsight(
                facebookPostId, token, insightMetrics, "post_video_views_60s_excludes_shorter");
        JsonNode reactionsByType = fetchJsonInsight(facebookPostId, token, insightMetrics, "post_reactions_by_type_total");
        String videoId = firstVideoId(post);

        return new FacebookPostMetricsSnapshot(
                countAt(post, "reactions", "summary", "total_count"),
                countAt(post, "comments", "summary", "total_count"),
                countAt(post, "shares", "count"),
                reach,
                impressions,
                videoId,
                postClicks == null ? 0 : postClicks,
                views == null ? 0 : views,
                uniqueViews == null ? 0 : uniqueViews,
                fifteenSecondViews == null ? 0 : fifteenSecondViews,
                sixtySecondViews == null ? 0 : sixtySecondViews,
                watchTimeMs,
                averageWatchTimeMs,
                reactionsByType == null ? null : reactionsByType.toString(),
                post.toString(),
                insightMetrics.isEmpty() ? null : objectMapper.writeValueAsString(insightMetrics),
                null);
    }

    /**
     * UC-4.8 Phase 5b: page-level audience. {@code followers_count}/{@code fan_count} are
     * generally available; the {@code page_fans_*} demographic breakdowns are best-effort
     * (Meta deprecated most of them) and return null JSON when unavailable.
     */
    public PageAudienceSnapshot fetchPageAudience() throws IOException, InterruptedException {
        String token = resolveActiveToken();
        if (token == null) {
            throw new IOException("No active Facebook page token found.");
        }
        JsonNode page = getJson(pageFieldsUrl(token));
        Integer followers = page.path("followers_count").isNumber() ? page.get("followers_count").asInt() : null;
        Integer fans = page.path("fan_count").isNumber() ? page.get("fan_count").asInt() : null;
        return new PageAudienceSnapshot(
                followers,
                fans,
                fetchLifetimeInsightValue(token, "page_fans_gender_age"),
                fetchLifetimeInsightValue(token, "page_fans_country"),
                fetchLifetimeInsightValue(token, "page_fans_city"),
                page.toString());
    }

    private String pageFieldsUrl(String token) {
        return "https://graph.facebook.com/" + apiVersion + "/" + pageId
                + "?fields=" + encode("followers_count,fan_count")
                + "&access_token=" + encode(token);
    }

    private String pageInsightsUrl(String metric, String token) {
        return "https://graph.facebook.com/" + apiVersion + "/" + pageId + "/insights"
                + "?metric=" + encode(metric)
                + "&period=lifetime"
                + "&access_token=" + encode(token);
    }

    /** Returns the lifetime insight {@code value} object as JSON text, or null if unavailable. */
    private String fetchLifetimeInsightValue(String token, String metric) throws InterruptedException {
        try {
            JsonNode insights = getJson(pageInsightsUrl(metric, token));
            JsonNode data = insights.path("data");
            if (data.isArray() && !data.isEmpty()) {
                JsonNode values = data.get(0).path("values");
                if (values.isArray() && !values.isEmpty()) {
                    JsonNode value = values.get(0).path("value");
                    if (!value.isMissingNode() && !value.isNull() && value.size() > 0) {
                        return value.toString();
                    }
                }
            }
        } catch (IOException ex) {
            log.warn("Page audience metric {} unavailable: {}", metric, ex.getMessage());
        }
        return null;
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
        String fields = "reactions.limit(0).summary(true),comments.limit(0).summary(true),shares,"
                + "attachments{media_type,target{id}}";
        return "https://graph.facebook.com/" + apiVersion + "/" + facebookPostId
                + "?fields=" + encode(fields)
                + "&access_token=" + encode(token);
    }

    private String insightsUrl(String facebookPostId, String token, String metricName) {
        return "https://graph.facebook.com/" + apiVersion + "/" + facebookPostId + "/insights"
                + "?metric=" + encode(metricName)
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

    private Integer fetchIntegerInsight(
            String facebookPostId,
            String token,
            Map<String, JsonNode> rawMetrics,
            String metricName) throws InterruptedException {
        Long value = fetchLongInsight(facebookPostId, token, rawMetrics, metricName);
        if (value == null) {
            return null;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : value.intValue();
    }

    private Long fetchLongInsight(
            String facebookPostId,
            String token,
            Map<String, JsonNode> rawMetrics,
            String metricName) throws InterruptedException {
        JsonNode value = fetchJsonInsight(facebookPostId, token, rawMetrics, metricName);
        return value != null && value.isNumber() ? value.asLong() : null;
    }

    private JsonNode fetchJsonInsight(
            String facebookPostId,
            String token,
            Map<String, JsonNode> rawMetrics,
            String metricName) throws InterruptedException {
        try {
            JsonNode insights = getJson(insightsUrl(facebookPostId, token, metricName));
            rawMetrics.put(metricName, insights);
            JsonNode data = insights.path("data");
            if (data.isArray() && !data.isEmpty()) {
                JsonNode values = data.get(0).path("values");
                if (values.isArray() && !values.isEmpty()) {
                    return values.get(0).path("value");
                }
            }
        } catch (IOException ex) {
            log.warn("Facebook insight metric {} unavailable for post {}: {}",
                    metricName, facebookPostId, ex.getMessage());
        }
        return null;
    }

    private static String firstVideoId(JsonNode post) {
        JsonNode attachments = post.path("attachments").path("data");
        if (!attachments.isArray()) {
            return null;
        }
        for (JsonNode attachment : attachments) {
            if ("video".equalsIgnoreCase(attachment.path("media_type").asText())) {
                String id = attachment.path("target").path("id").asText(null);
                return id == null || id.isBlank() ? null : id;
            }
        }
        return null;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Page-level audience snapshot; demographic JSON fields are null when unavailable. */
    public record PageAudienceSnapshot(
            Integer followersCount,
            Integer fanCount,
            String ageGenderJson,
            String countriesJson,
            String citiesJson,
            String rawJson) {
    }

    public record FacebookPostMetricsSnapshot(
            int reactions,
            int comments,
            int shares,
            Integer reach,
            Integer impressions,
            String videoId,
            int postClicks,
            int views,
            int uniqueViews,
            int fifteenSecondViews,
            int sixtySecondViews,
            Long watchTimeMs,
            Long averageWatchTimeMs,
            String reactionsByType,
            String rawPost,
            String rawInsights,
            String rawVideoInsights) {
    }
}
