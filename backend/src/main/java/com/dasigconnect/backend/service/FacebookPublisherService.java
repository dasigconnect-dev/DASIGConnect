package com.dasigconnect.backend.service;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dasigconnect.backend.event.PostPublishedEvent;
import com.dasigconnect.backend.event.PublishFailedEvent;
import com.dasigconnect.backend.model.entity.FacebookPageToken;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.PublicationAttempt;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.repository.FacebookPageTokenRepository;
import com.dasigconnect.backend.repository.PublicationAttemptRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

/**
 * Publishes SCHEDULED submissions to the DASIG Facebook Page via Graph API v25.0.
 *
 * Photo-only: 2-step (stage each photo unpublished → single feed post with attached_media).
 * Video-only: single POST /{PAGE_ID}/videos call.
 * Mixed (images + video): not supported in the pilot — immediately transitions to PUBLISH_FAILED.
 *
 * Retry policy (GR-T5): up to 3 attempts with exponential backoff: 5s → 25s → 125s.
 *
 * IMPORTANT: This service must NEVER be called while holding an open DB transaction.
 * The scheduler loads submissions in one transaction, closes it, then calls publish().
 */
@Service
public class FacebookPublisherService {

    private static final Logger log = LoggerFactory.getLogger(FacebookPublisherService.class);

    private static final int MAX_RETRIES = 3;
    private static final long[] BACKOFF_MS = {5_000L, 25_000L, 125_000L};

    private final String pageAccessToken;
    private final String pageId;
    private final String appId;
    private final String appSecret;
    private final String apiVersion;

    private final TokenEncryptionService tokenEncryptionService;
    private final FacebookPageTokenRepository pageTokenRepository;
    private final PublicationAttemptRepository publicationAttemptRepository;
    private final SubmissionRepository submissionRepository;
    private final ApplicationEventPublisher eventPublisher;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FacebookPublisherService(
            @Value("${app.facebook.page-access-token:}") String pageAccessToken,
            @Value("${app.facebook.page-id:}") String pageId,
            @Value("${app.facebook.app-id:}") String appId,
            @Value("${app.facebook.app-secret:}") String appSecret,
            @Value("${app.facebook.api-version:v25.0}") String apiVersion,
            TokenEncryptionService tokenEncryptionService,
            FacebookPageTokenRepository pageTokenRepository,
            PublicationAttemptRepository publicationAttemptRepository,
            SubmissionRepository submissionRepository,
            ApplicationEventPublisher eventPublisher) {
        this.pageAccessToken = pageAccessToken;
        this.pageId = pageId;
        this.appId = appId;
        this.appSecret = appSecret;
        this.apiVersion = apiVersion;
        this.tokenEncryptionService = tokenEncryptionService;
        this.pageTokenRepository = pageTokenRepository;
        this.publicationAttemptRepository = publicationAttemptRepository;
        this.submissionRepository = submissionRepository;
        this.eventPublisher = eventPublisher;
    }

    public boolean isConfigured() {
        return pageId != null && !pageId.isBlank()
                && pageAccessToken != null && !pageAccessToken.isBlank();
    }

    /**
     * On startup, sync the env-supplied page access token to the DB if no active
     * token exists for this page. The DB record is the runtime source of truth.
     *
     * Note: @Transactional does not apply to @PostConstruct — Spring calls this
     * method on the raw bean before the CGLIB proxy is in place. The repository
     * methods manage their own transactions, so no outer transaction is needed.
     * A try-catch ensures a missing table (e.g. migration not yet applied on first
     * deploy) degrades to a warning rather than crashing the application context.
     */
    @PostConstruct
    public void syncTokenFromEnv() {
        if (!isConfigured() || !tokenEncryptionService.isConfigured()) {
            log.warn("FacebookPublisherService: page token or encryption not configured — publishing disabled.");
            return;
        }
        try {
            pageTokenRepository.findByPageIdAndIsActiveTrue(pageId).ifPresentOrElse(
                    existing -> log.info("Facebook page token already present for page {}.", pageId),
                    () -> {
                        FacebookPageToken token = new FacebookPageToken();
                        token.setPageId(pageId);
                        token.setEncryptedToken(tokenEncryptionService.encryptToken(pageAccessToken));
                        pageTokenRepository.save(token);
                        log.info("Facebook page token synced from env for page {}.", pageId);
                    }
            );
        } catch (Exception ex) {
            log.error("FacebookPublisherService: token sync failed at startup — publishing disabled until next restart. Cause: {}", ex.getMessage());
        }
    }

    /**
     * Entry point called by PublishingSchedulerJob.
     * Determines media type and routes to the correct publish path.
     * State transitions and event publishing happen here.
     *
     * MUST be called outside any active DB transaction.
     */
    public void publish(Submission submission, List<MediaAsset> mediaAssets) {
        if (!isConfigured()) {
            log.warn("Facebook publishing not configured — skipping submission {}.", submission.getId());
            return;
        }

        String token = resolveActiveToken();
        if (token == null) {
            markFailed(submission, "No active Facebook page token found.");
            return;
        }

        boolean hasImages = mediaAssets.stream().anyMatch(a -> isImage(a.getFileType()));
        boolean hasVideos = mediaAssets.stream().anyMatch(a -> isVideo(a.getFileType()));

        if (hasImages && hasVideos) {
            // Mixed media not supported in pilot per SRS constraint
            markFailed(submission, "Mixed media (images + video) is not supported for automated publishing.");
            return;
        }

        if (hasVideos) {
            publishVideoPost(submission, mediaAssets.stream().filter(a -> isVideo(a.getFileType())).findFirst().orElseThrow(), token);
        } else if (hasImages) {
            publishPhotoPost(submission, mediaAssets.stream().filter(a -> isImage(a.getFileType())).toList(), token);
        } else {
            markFailed(submission, "Submission has no media assets to publish.");
        }
    }

    // ── Photo publish (2-step) ────────────────────────────────────────────────

    private void publishPhotoPost(Submission submission, List<MediaAsset> images, String token) {
        String caption = buildPostMessage(submission);
        List<String> stagedPhotoIds = new ArrayList<>();
        String lastError = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                stagedPhotoIds.clear();

                // Step 1: Stage each photo unpublished
                for (MediaAsset image : images) {
                    String photoId = stagePhoto(image.getStorageUrl(), token);
                    stagedPhotoIds.add(photoId);
                }

                // Step 2: Publish as a single feed post with attached media
                String attachedMedia = buildAttachedMedia(stagedPhotoIds);
                String body = "message=" + encode(caption)
                        + "&attached_media=" + encode(attachedMedia)
                        + "&published=true"
                        + "&access_token=" + encode(token);

                String feedUrl = "https://graph.facebook.com/" + apiVersion + "/" + pageId + "/feed";
                JsonNode response = postForm(feedUrl, body);

                String postId = response.path("id").asText(null);
                if (postId == null || postId.isBlank()) {
                    throw new IOException("Feed post returned no post ID. Response: " + response);
                }

                recordAttempt(submission, attempt, "success", null, null);
                markPublished(submission, postId);
                return;

            } catch (Exception ex) {
                lastError = ex.getMessage();
                log.warn("Photo publish attempt {}/{} failed for submission {}: {}",
                        attempt, MAX_RETRIES, submission.getId(), lastError);
                recordAttempt(submission, attempt, "failed", lastError, toJson(stagedPhotoIds));
                cleanupStagedPhotos(stagedPhotoIds, token);

                if (attempt < MAX_RETRIES) {
                    sleep(BACKOFF_MS[attempt - 1]);
                }
            }
        }

        markFailed(submission, lastError);
    }

    // ── Video publish (single call) ───────────────────────────────────────────

    private void publishVideoPost(Submission submission, MediaAsset video, String token) {
        String caption = buildPostMessage(submission);
        String lastError = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String body = "file_url=" + encode(video.getStorageUrl())
                        + "&description=" + encode(caption)
                        + "&published=true"
                        + "&access_token=" + encode(token);

                String videoUrl = "https://graph.facebook.com/" + apiVersion + "/" + pageId + "/videos";
                JsonNode response = postForm(videoUrl, body);

                String postId = response.path("id").asText(null);
                if (postId == null || postId.isBlank()) {
                    throw new IOException("Video post returned no post ID. Response: " + response);
                }

                recordAttempt(submission, attempt, "success", null, null);
                markPublished(submission, postId);
                return;

            } catch (Exception ex) {
                lastError = ex.getMessage();
                log.warn("Video publish attempt {}/{} failed for submission {}: {}",
                        attempt, MAX_RETRIES, submission.getId(), lastError);
                recordAttempt(submission, attempt, "failed", lastError, null);

                if (attempt < MAX_RETRIES) {
                    sleep(BACKOFF_MS[attempt - 1]);
                }
            }
        }

        markFailed(submission, lastError);
    }

    // ── Graph API helpers ─────────────────────────────────────────────────────

    private String stagePhoto(String storageUrl, String token) throws IOException, InterruptedException {
        String body = "url=" + encode(storageUrl)
                + "&published=false"
                + "&access_token=" + encode(token);
        String url = "https://graph.facebook.com/" + apiVersion + "/" + pageId + "/photos";
        JsonNode response = postForm(url, body);
        String photoId = response.path("id").asText(null);
        if (photoId == null || photoId.isBlank()) {
            throw new IOException("Photo staging returned no photo ID. Response: " + response);
        }
        return photoId;
    }

    private void cleanupStagedPhotos(List<String> photoIds, String token) {
        for (String photoId : photoIds) {
            try {
                String url = "https://graph.facebook.com/" + apiVersion + "/" + photoId
                        + "?access_token=" + encode(token);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .DELETE()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    log.warn("Failed to delete staged photo {}: HTTP {}", photoId, response.statusCode());
                }
            } catch (Exception ex) {
                log.warn("Exception deleting staged photo {}: {}", photoId, ex.getMessage());
            }
        }
    }

    private JsonNode postForm(String url, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode node = objectMapper.readTree(response.body());
        if (node.has("error")) {
            throw new IOException("Graph API error: " + node.get("error").toString());
        }
        return node;
    }

    private String buildAttachedMedia(List<String> photoIds) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < photoIds.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"media_fbid\":\"").append(photoIds.get(i)).append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }

    // ── State transitions (short transactions, called after API calls) ─────────

    @Transactional
    public void markPublished(Submission submission, String postId) {
        Submission s = submissionRepository.findById(submission.getId()).orElse(submission);
        boolean isDirectPost = s.getStatus() == SubmissionStatus.direct_post_scheduled
                || s.getStatus() == SubmissionStatus.direct_post_publishing;
        s.setStatus(isDirectPost ? SubmissionStatus.admin_direct_post : SubmissionStatus.published);
        s.setPlatformPostId(postId);
        s.setPublishedAt(Instant.now());
        submissionRepository.save(s);
        String postUrl = "https://www.facebook.com/" + postId.replace("_", "/posts/");
        if (isDirectPost) {
            eventPublisher.publishEvent(new com.dasigconnect.backend.event.AdminDirectPostEvent(
                    s.getInstitution(), s.getCaption(), postUrl));
        } else {
            eventPublisher.publishEvent(new PostPublishedEvent(s, postUrl));
        }
        log.info("Submission {} published successfully as post {} (status={}).",
                s.getId(), postId, s.getStatus());
    }

    @Transactional
    public void markFailed(Submission submission, String error) {
        Submission s = submissionRepository.findById(submission.getId()).orElse(submission);
        boolean isDirectPost = s.getStatus() == SubmissionStatus.direct_post_scheduled
                || s.getStatus() == SubmissionStatus.direct_post_publishing;
        s.setStatus(isDirectPost ? SubmissionStatus.direct_post_failed : SubmissionStatus.publish_failed);
        submissionRepository.save(s);
        eventPublisher.publishEvent(new PublishFailedEvent(s, error));
        log.error("Submission {} publishing failed (status={}): {}", s.getId(), s.getStatus(), error);
    }

    @Transactional
    public void recordAttempt(Submission submission, int attemptNumber, String result, String error, String photoIds) {
        PublicationAttempt attempt = new PublicationAttempt();
        attempt.setSubmission(submissionRepository.getReferenceById(submission.getId()));
        attempt.setAttemptNumber(attemptNumber);
        attempt.setResult(result);
        attempt.setErrorDetail(error);
        attempt.setPhotoIdsStaged(photoIds);
        publicationAttemptRepository.save(attempt);
    }

    // ── Token resolution ──────────────────────────────────────────────────────

    private String resolveActiveToken() {
        return pageTokenRepository.findByPageIdAndIsActiveTrue(pageId)
                .map(t -> {
                    try {
                        return tokenEncryptionService.decryptToken(t.getEncryptedToken());
                    } catch (Exception ex) {
                        log.error("Failed to decrypt Facebook page token: {}", ex.getMessage());
                        return null;
                    }
                })
                .orElse(null);
    }

    // ── Token health check (called by TokenHealthCheckJob) ────────────────────

    /**
     * Validates the active token via the Graph API debug_token endpoint.
     * Returns the token expiry time, or null if invalid/missing.
     * Used by TokenHealthCheckJob (GR-T4).
     */
    public Instant validateToken() {
        String token = resolveActiveToken();
        if (token == null) return null;

        try {
            String url = "https://graph.facebook.com/" + apiVersion + "/debug_token"
                    + "?input_token=" + encode(token)
                    + "&access_token=" + encode(appId + "|" + appSecret);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode node = objectMapper.readTree(response.body());
            JsonNode data = node.path("data");

            boolean isValid = data.path("is_valid").asBoolean(false);
            if (!isValid) {
                log.warn("Facebook page token failed validation.");
                return null;
            }

            // Update last_validated_at in DB
            pageTokenRepository.findByPageIdAndIsActiveTrue(pageId).ifPresent(t -> {
                t.setLastValidatedAt(Instant.now());
                long expiresAt = data.path("expires_at").asLong(0L);
                if (expiresAt > 0) {
                    t.setExpiresAt(Instant.ofEpochSecond(expiresAt));
                }
                pageTokenRepository.save(t);
            });

            long expiresAt = data.path("expires_at").asLong(0L);
            return expiresAt > 0 ? Instant.ofEpochSecond(expiresAt) : null;

        } catch (Exception ex) {
            log.error("Token validation request failed: {}", ex.getMessage());
            return null;
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private static boolean isImage(MediaFileType type) {
        return type == MediaFileType.jpeg || type == MediaFileType.png
                || type == MediaFileType.webp || type == MediaFileType.gif;
    }

    private static boolean isVideo(MediaFileType type) {
        return type == MediaFileType.mp4 || type == MediaFileType.mov || type == MediaFileType.webm;
    }

    /**
     * Builds the Facebook post message by appending manually selected tags as hashtags.
     * Tags stored as comma-separated (e.g. "Science,Research,DOST") are appended
     * as "#Science #Research #DOST" on a new line after the caption.
     * Hashtags already present in the caption (starting with #) are preserved as-is.
     */
    private static String buildPostMessage(Submission submission) {
        String caption = submission.getCaption() != null ? submission.getCaption().trim() : "";
        String rawTags = submission.getTags();
        if (rawTags == null || rawTags.isBlank()) return caption;

        String hashtags = java.util.Arrays.stream(rawTags.split(","))
                .map(String::trim)
                .filter(t -> !t.isBlank())
                .map(t -> "#" + t.replace(" ", ""))
                .collect(java.util.stream.Collectors.joining(" "));

        if (hashtags.isBlank()) return caption;
        return caption.isBlank() ? hashtags : caption + "\n\n" + hashtags;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String toJson(List<String> ids) {
        if (ids == null || ids.isEmpty()) return null;
        return "[\"" + String.join("\",\"", ids) + "\"]";
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
