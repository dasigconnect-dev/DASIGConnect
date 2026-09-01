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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Reads recent Page post engagement without exposing Facebook credentials to the browser. */
@Component
public class FacebookEngagementAnalyticsClient {

    private static final Logger log = LoggerFactory.getLogger(FacebookEngagementAnalyticsClient.class);

    private final FacebookPageTokenRepository tokenRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final String configuredToken;
    private final String pageId;
    private final String apiVersion;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    /** Post-reach insight metrics to try in order. Empty = skip the reach call entirely. */
    private final List<String> reachMetrics;
    /** Page-level insight metrics for the Page Performance card. Empty = skip that call. */
    private final List<String> pageInsightMetrics;

    @Autowired
    public FacebookEngagementAnalyticsClient(
            FacebookPageTokenRepository tokenRepository,
            TokenEncryptionService tokenEncryptionService,
            @Value("${app.facebook.page-access-token:}") String configuredToken,
            @Value("${app.facebook.page-id:}") String pageId,
            @Value("${app.facebook.api-version:v25.0}") String apiVersion,
            @Value("${app.facebook.reach-metrics:post_impressions_unique}") String reachMetrics,
            @Value("${app.facebook.page-insight-metrics:page_impressions_unique,page_post_engagements,page_fan_adds,page_views_total}") String pageInsightMetrics) {
        this(tokenRepository, tokenEncryptionService, configuredToken, pageId, apiVersion,
                HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build(),
                new ObjectMapper(), reachMetrics, pageInsightMetrics);
    }

    FacebookEngagementAnalyticsClient(
            FacebookPageTokenRepository tokenRepository,
            TokenEncryptionService tokenEncryptionService,
            String configuredToken,
            String pageId,
            String apiVersion,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String reachMetrics,
            String pageInsightMetrics) {
        this.tokenRepository = tokenRepository;
        this.tokenEncryptionService = tokenEncryptionService;
        this.configuredToken = configuredToken;
        this.pageId = pageId;
        this.apiVersion = apiVersion;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.reachMetrics = splitMetrics(reachMetrics);
        this.pageInsightMetrics = splitMetrics(pageInsightMetrics);
    }

    private static List<String> splitMetrics(String csv) {
        return csv == null ? List.of()
                : java.util.Arrays.stream(csv.split(","))
                        .map(String::trim)
                        .filter(m -> !m.isEmpty())
                        .toList();
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

    /**
     * Best-effort post reach. Tries each metric in {@code app.facebook.reach-metrics}
     * (in order) and returns the first that resolves; an empty config list skips the
     * insights call entirely. Returns {@code null} when every configured metric fails —
     * one WARN per post names the Graph error so a persistently null reach is
     * diagnosable (missing {@code read_insights}, metric deprecated on this API version,
     * non-post id, …).
     */
    private Long fetchReach(String postId, String token) {
        if (reachMetrics.isEmpty()) {
            return null;
        }
        String lastError = null;
        for (String metric : reachMetrics) {
            InsightResult result = queryInsightMetric(postId, token, metric);
            if (result.value() != null) {
                if (!metric.equals(reachMetrics.get(0))) {
                    log.info("Reach for post {} resolved via fallback metric '{}' ({}).",
                            postId, metric, result.value());
                }
                return result.value();
            }
            lastError = result.error();
        }
        log.warn("Reach unavailable for post {} — none of {} accepted by Graph API {}. Last error: {}",
                postId, reachMetrics, apiVersion, lastError);
        return null;
    }

    /** One Page-post insight metric. Never throws; carries the Graph error text for the caller to log once. */
    private InsightResult queryInsightMetric(String postId, String token, String metric) {
        try {
            String url = "https://graph.facebook.com/" + apiVersion + "/" + encode(postId)
                    + "/insights?metric=" + encode(metric) + "&period=lifetime&access_token=" + encode(token);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(8)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300 || root.has("error")) {
                return new InsightResult(null,
                        root.has("error") ? root.get("error").toString() : "HTTP " + response.statusCode() + " " + truncate(response.body()));
            }
            JsonNode values = root.path("data").path(0).path("values").path(0).path("value");
            return new InsightResult(values.isMissingNode() ? null : values.asLong(), null);
        } catch (Exception ex) {
            return new InsightResult(null, ex.toString());
        }
    }

    /**
     * Page-level aggregate insights for {@code [since, until)} — Page reach,
     * engagements, new follows, views. Uses the {@code /{page-id}/insights} edge
     * (still supported, unlike per-post reach) with {@code period=day} and sums
     * the daily buckets. Returns metric-name → summed value; an empty map means
     * the call is disabled (no configured metrics), unconfigured, or Graph
     * rejected it (logged once). Never throws.
     */
    public Map<String, Long> fetchPageInsights(Instant since, Instant until) {
        if (pageInsightMetrics.isEmpty()) {
            return Map.of();
        }
        String token = resolveToken();
        if (pageId == null || pageId.isBlank() || token == null || token.isBlank()) {
            return Map.of();
        }
        try {
            String url = "https://graph.facebook.com/" + apiVersion + "/" + encode(pageId)
                    + "/insights?metric=" + encode(String.join(",", pageInsightMetrics))
                    + "&period=day"
                    + "&since=" + since.getEpochSecond()
                    + "&until=" + until.getEpochSecond()
                    + "&access_token=" + encode(token);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300 || root.has("error")) {
                log.warn("Facebook Page insights {} failed (HTTP {}): {}", pageInsightMetrics, response.statusCode(),
                        root.has("error") ? root.get("error").toString() : truncate(response.body()));
                return Map.of();
            }
            Map<String, Long> totals = new LinkedHashMap<>();
            for (JsonNode metricNode : root.path("data")) {
                String name = metricNode.path("name").asText("");
                if (name.isEmpty()) {
                    continue;
                }
                long sum = 0;
                for (JsonNode point : metricNode.path("values")) {
                    JsonNode value = point.path("value");
                    if (value.isNumber()) {
                        sum += value.asLong();
                    }
                }
                totals.put(name, sum);
            }
            return totals;
        } catch (Exception ex) {
            log.warn("Facebook Page insights {} errored: {}", pageInsightMetrics, ex.toString());
            return Map.of();
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 300 ? value : value.substring(0, 300) + "…";
    }

    private record InsightResult(Long value, String error) {}

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
