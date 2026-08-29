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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dasigconnect.backend.event.PostPublishedEvent;
import com.dasigconnect.backend.event.PublishFailedEvent;
import com.dasigconnect.backend.event.TokenPublishingSuspendedEvent;
import com.dasigconnect.backend.event.TokenValidationFailedEvent;
import com.dasigconnect.backend.model.entity.FacebookPageToken;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.PublicationAttempt;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionMediaAsset;
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

    public static final String TOKEN_EXPIRED_BLOCKED_PREFIX = "TOKEN_EXPIRED_BLOCKED";
    public static final String TOKEN_EXPIRED_24H_PREFIX = "TOKEN_EXPIRED_24H_ESCALATION";
    public static final String TOKEN_EXPIRED_48H_PREFIX = "TOKEN_EXPIRED_48H_FAILED";

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
    private final WatermarkApplicationService watermarkApplicationService;

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
            ApplicationEventPublisher eventPublisher,
            WatermarkApplicationService watermarkApplicationService) {
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
        this.watermarkApplicationService = watermarkApplicationService;
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

        publishInternal(submission, mediaAssets, Map.of(), Map.of());
    }

    public void publishMediaLinks(Submission submission, List<SubmissionMediaAsset> mediaLinks) {
        List<MediaAsset> mediaAssets = mediaLinks.stream()
                .map(SubmissionMediaAsset::getMediaAsset)
                .toList();
        Map<UUID, String> mediaCaptions = mediaLinks.stream()
                .collect(Collectors.toMap(
                        link -> link.getMediaAsset().getId(),
                        link -> normalizeCaption(link.getCaption()),
                        (left, right) -> left));
        Map<UUID, String> photoPublishUrls = mediaLinks.stream()
                .filter(link -> isImage(link.getMediaAsset().getFileType()))
                .collect(Collectors.toMap(
                        link -> link.getMediaAsset().getId(),
                        link -> watermarkApplicationService.resolvePublishUrl(submission, link),
                        (left, right) -> left));
        publishInternal(submission, mediaAssets, mediaCaptions, photoPublishUrls);
    }

    public boolean hasUsableActiveToken() {
        return isConfigured() && resolveActiveToken().status() == TokenStatus.READY;
    }

    private void publishInternal(
            Submission submission,
            List<MediaAsset> mediaAssets,
            Map<UUID, String> mediaCaptions,
            Map<UUID, String> photoPublishUrls) {
        if (!isConfigured()) {
            log.warn("Facebook publishing not configured - skipping submission {}.", submission.getId());
            return;
        }

        TokenResolution tokenResolution = resolveActiveToken();
        if (tokenResolution.status() == TokenStatus.MISSING) {
            markFailed(submission, "No active Facebook page token found.");
            return;
        }
        if (tokenResolution.status() == TokenStatus.EXPIRED) {
            suspendForTokenExpiry(submission, tokenResolution.message());
            return;
        }
        String token = tokenResolution.token().orElseThrow();

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
            publishPhotoPost(
                    submission,
                    mediaAssets.stream().filter(a -> isImage(a.getFileType())).toList(),
                    mediaCaptions,
                    photoPublishUrls,
                    token);
        } else {
            markFailed(submission, "Submission has no media assets to publish.");
        }
    }

    // ── Photo publish (2-step) ────────────────────────────────────────────────

    private void publishPhotoPost(
            Submission submission,
            List<MediaAsset> images,
            Map<UUID, String> mediaCaptions,
            Map<UUID, String> photoPublishUrls,
            String token) {
        String caption = buildPostMessage(submission);
        List<String> stagedPhotoIds = new ArrayList<>();
        String lastError = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                stagedPhotoIds.clear();

                // Step 1: Stage each photo unpublished
                for (MediaAsset image : images) {
                    String photoUrl = photoPublishUrls.getOrDefault(image.getId(), image.getStorageUrl());
                    String photoId = stagePhoto(photoUrl, mediaCaptions.get(image.getId()), token);
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

    private String stagePhoto(String storageUrl, String mediaCaption, String token) throws IOException, InterruptedException {
        String body = "url=" + encode(storageUrl)
                + "&published=false"
                + (mediaCaption == null || mediaCaption.isBlank() ? "" : "&caption=" + encode(mediaCaption))
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
        clearTokenSuspension(s);
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
        if (!isTokenFinalFailure(error)) {
            clearTokenSuspension(s);
        }
        submissionRepository.save(s);
        eventPublisher.publishEvent(new PublishFailedEvent(s, error));
        log.error("Submission {} publishing failed (status={}): {}", s.getId(), s.getStatus(), error);
    }

    @Transactional
    public void suspendForTokenExpiry(Submission submission, String error) {
        Submission s = submissionRepository.findById(submission.getId()).orElse(submission);
        if (s.getStatus() == SubmissionStatus.publishing) {
            s.setStatus(SubmissionStatus.scheduled);
        } else if (s.getStatus() == SubmissionStatus.direct_post_publishing) {
            s.setStatus(SubmissionStatus.direct_post_scheduled);
        }
        if (s.getTokenBlockedAt() == null) {
            s.setTokenBlockedAt(Instant.now());
        }
        submissionRepository.save(s);
        boolean firstTokenBlock = s.getTokenEscalated24hAt() == null
                && s.getTokenFinalFailedAt() == null
                && !publicationAttemptRepository.existsBySubmissionIdAndErrorDetailStartingWith(
                        s.getId(),
                        TOKEN_EXPIRED_BLOCKED_PREFIX);
        if (firstTokenBlock) {
            recordAttempt(s, 1, "failed", TOKEN_EXPIRED_BLOCKED_PREFIX + ": " + error, null);
            eventPublisher.publishEvent(new TokenValidationFailedEvent());
            eventPublisher.publishEvent(new TokenPublishingSuspendedEvent(
                    s,
                    TokenPublishingSuspendedEvent.Stage.FIRST_ALERT,
                    error));
        }
        log.error("Submission {} publishing suspended because Facebook token is unavailable: {}", s.getId(), error);
    }

    private void clearTokenSuspension(Submission submission) {
        submission.setTokenBlockedAt(null);
        submission.setTokenEscalated24hAt(null);
        submission.setTokenFinalFailedAt(null);
    }

    private static boolean isTokenFinalFailure(String error) {
        return error != null && error.contains("not reauthorized within 48 hours");
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

    private TokenResolution resolveActiveToken() {
        return pageTokenRepository.findByPageIdAndIsActiveTrue(pageId)
                .map(t -> {
                    if (t.getExpiresAt() != null && !t.getExpiresAt().isAfter(Instant.now())) {
                        return TokenResolution.expired("Facebook Page Access Token expired at " + t.getExpiresAt() + ".");
                    }
                    try {
                        return TokenResolution.ready(tokenEncryptionService.decryptToken(t.getEncryptedToken()));
                    } catch (Exception ex) {
                        log.error("Failed to decrypt Facebook page token: {}", ex.getMessage());
                        return TokenResolution.missing("Facebook Page Access Token could not be decrypted.");
                    }
                })
                .orElseGet(() -> TokenResolution.missing("No active Facebook page token found."));
    }

    private String resolveActiveTokenValue() {
        TokenResolution resolved = resolveActiveToken();
        return resolved.status() == TokenStatus.READY ? resolved.token().orElse(null) : null;
    }

    // ── Token health check (called by TokenHealthCheckJob) ────────────────────

    /** What the {@code debug_token} probe concluded about the active page token. */
    public enum TokenValidationOutcome {
        /** {@code is_valid: true} and Graph returned a real {@code expires_at}. */
        VALID,
        /** {@code is_valid: true} with {@code expires_at: 0} — a long-lived / non-expiring token. Healthy. */
        VALID_NON_EXPIRING,
        /** Graph says the page token is invalid or expired — needs re-authorization. */
        REJECTED,
        /** Nothing to validate (no active token, or app id/secret not set so {@code debug_token} can't be called). */
        NOT_CONFIGURED,
        /** Graph could not be reached / parsed — transient, retry next run. */
        UNREACHABLE
    }

    /** Result of {@link #validateToken()}. {@code expiresAt} is only set for {@link TokenValidationOutcome#VALID}. */
    public record TokenValidation(TokenValidationOutcome outcome, Instant expiresAt, String detail) {
        public boolean healthy() {
            return outcome == TokenValidationOutcome.VALID
                    || outcome == TokenValidationOutcome.VALID_NON_EXPIRING
                    || outcome == TokenValidationOutcome.NOT_CONFIGURED;
        }
    }

    /**
     * Validates the active token via the Graph API {@code debug_token} endpoint.
     * Never throws — every failure mode maps to a {@link TokenValidation} outcome
     * so the caller (TokenHealthCheckJob, GR-T4) can tell a dead token apart from
     * an unconfigured app or a transient network error.
     */
    public TokenValidation validateToken() {
        if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) {
            return new TokenValidation(TokenValidationOutcome.NOT_CONFIGURED, null,
                    "Facebook app id/secret are not set; debug_token cannot be called.");
        }
        String token = resolveActiveTokenValue();
        if (token == null) {
            return new TokenValidation(TokenValidationOutcome.NOT_CONFIGURED, null,
                    "No active Facebook page token is available to validate.");
        }

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

            // A top-level error with no data node means the request itself was
            // rejected (usually a bad app id/secret) — not a verdict on the token.
            if (node.has("error") && !data.isObject()) {
                String msg = node.path("error").path("message").asText("debug_token request was rejected.");
                log.warn("Facebook debug_token request rejected (verify app id/secret): {}", msg);
                return new TokenValidation(TokenValidationOutcome.NOT_CONFIGURED, null,
                        "debug_token request rejected — verify app id/secret: " + msg);
            }

            boolean isValid = data.path("is_valid").asBoolean(false);
            if (!isValid) {
                String reason = data.path("error").path("message")
                        .asText("Graph API reports the page token as invalid or expired.");
                log.warn("Facebook page token failed validation: {}", reason);
                return new TokenValidation(TokenValidationOutcome.REJECTED, null, reason);
            }

            long expiresAtEpoch = data.path("expires_at").asLong(0L);
            Instant expiresAt = expiresAtEpoch > 0 ? Instant.ofEpochSecond(expiresAtEpoch) : null;

            // Refresh last_validated_at (and expiry, when the token actually has one).
            pageTokenRepository.findByPageIdAndIsActiveTrue(pageId).ifPresent(t -> {
                t.setLastValidatedAt(Instant.now());
                if (expiresAt != null) {
                    t.setExpiresAt(expiresAt);
                }
                pageTokenRepository.save(t);
            });

            return expiresAt != null
                    ? new TokenValidation(TokenValidationOutcome.VALID, expiresAt, "Token is valid.")
                    : new TokenValidation(TokenValidationOutcome.VALID_NON_EXPIRING, null,
                            "Token is valid and does not expire.");
        } catch (Exception ex) {
            log.error("Token validation request failed: {}", ex.getMessage());
            return new TokenValidation(TokenValidationOutcome.UNREACHABLE, null,
                    "Could not reach the Facebook Graph API to validate the token: " + ex.getMessage());
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

    private static String normalizeCaption(String caption) {
        return caption == null || caption.isBlank() ? "" : caption.trim();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private enum TokenStatus {
        READY,
        MISSING,
        EXPIRED
    }

    private record TokenResolution(TokenStatus status, Optional<String> token, String message) {
        static TokenResolution ready(String token) {
            return new TokenResolution(TokenStatus.READY, Optional.of(token), "");
        }

        static TokenResolution missing(String message) {
            return new TokenResolution(TokenStatus.MISSING, Optional.empty(), message);
        }

        static TokenResolution expired(String message) {
            return new TokenResolution(TokenStatus.EXPIRED, Optional.empty(), message);
        }
    }
}
