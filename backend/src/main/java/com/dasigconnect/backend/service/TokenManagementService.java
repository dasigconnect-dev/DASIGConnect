package com.dasigconnect.backend.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.model.dto.exception.OAuthInitResponseDto;
import com.dasigconnect.backend.model.dto.exception.TokenStatusDto;
import com.dasigconnect.backend.model.entity.FacebookPageToken;
import com.dasigconnect.backend.repository.FacebookPageTokenRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * UC-3.5 Category E — Token Management.
 *
 * Handles Facebook Page Access Token status display and OAuth 2.0 re-authentication flow.
 *
 * OAuth state (CSRF): stored in-memory with a 10-minute TTL (acceptable for single-instance capstone deployment).
 */
@Service
@Transactional
public class TokenManagementService {

    private static final Logger log = LoggerFactory.getLogger(TokenManagementService.class);

    private static final String META_OAUTH_URL = "https://www.facebook.com/dialog/oauth";
    private static final String META_TOKEN_URL = "https://graph.facebook.com/oauth/access_token";
    private static final String META_PAGE_TOKEN_URL = "https://graph.facebook.com/%s/%s";

    /** CSRF state map: state → tokenId awaiting re-auth. Entries expire after 10 minutes. */
    private final ConcurrentHashMap<String, OAuthState> pendingStates = new ConcurrentHashMap<>();

    private final FacebookPageTokenRepository pageTokenRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final AuditLogService auditLogService;

    private final String appId;
    private final String appSecret;
    private final String apiVersion;
    private final String redirectUri;

    // Not final: swapped for a mock in TokenManagementServiceTest via ReflectionTestUtils.
    private HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TokenManagementService(
            FacebookPageTokenRepository pageTokenRepository,
            TokenEncryptionService tokenEncryptionService,
            AuditLogService auditLogService,
            @Value("${app.facebook.app-id:}") String appId,
            @Value("${app.facebook.app-secret:}") String appSecret,
            @Value("${app.facebook.api-version:v25.0}") String apiVersion,
            @Value("${app.facebook.oauth-redirect-uri:}") String redirectUri) {
        this.pageTokenRepository = pageTokenRepository;
        this.tokenEncryptionService = tokenEncryptionService;
        this.auditLogService = auditLogService;
        this.appId = appId;
        this.appSecret = appSecret;
        this.apiVersion = apiVersion;
        this.redirectUri = redirectUri;
    }

    @Transactional(readOnly = true)
    public List<TokenStatusDto> getAllTokenStatuses() {
        return pageTokenRepository.findAll()
                .stream()
                .map(TokenStatusDto::from)
                .toList();
    }

    /**
     * Builds the Meta OAuth authorization URL for re-authentication.
     * Stores a CSRF state token keyed to the token record.
     */
    public OAuthInitResponseDto initOAuth(UUID tokenId, JwtUserDetails admin) {
        FacebookPageToken token = pageTokenRepository.findById(tokenId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Facebook page token not found."));

        String state = UUID.randomUUID().toString();
        pendingStates.put(state, new OAuthState(tokenId, Instant.now().plusSeconds(600)));

        String url = META_OAUTH_URL
                + "?client_id=" + encode(appId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&scope=" + encode("pages_manage_posts,pages_read_engagement,pages_show_list,read_insights")
                + "&response_type=code"
                + "&state=" + encode(state);

        log.info("Admin {} initiated OAuth for token {} (page {}).",
                admin.userId(), tokenId, token.getPageId());
        return new OAuthInitResponseDto(url);
    }

    /**
     * Handles the OAuth callback: exchanges the auth code for a long-lived Page Access Token,
     * encrypts and stores it, then resumes GR-T4 health checks.
     */
    public String handleCallback(String code, String state) {
        OAuthState oauthState = pendingStates.remove(state);
        if (oauthState == null || oauthState.expiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid or expired OAuth state. Please restart the re-authentication flow.");
        }

        FacebookPageToken token = pageTokenRepository.findById(oauthState.tokenId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Facebook page token not found."));

        try {
            // Step 1: Exchange auth code for short-lived user token
            String shortLivedToken = exchangeCodeForToken(code);

            // Step 2: Exchange for long-lived user token
            String longLivedUserToken = exchangeForLongLivedToken(shortLivedToken);

            // Step 3: Retrieve the Page Access Token from the long-lived user token
            String pageAccessToken = fetchPageAccessToken(token.getPageId(), longLivedUserToken);

            // Step 4: Encrypt and store
            token.setEncryptedToken(tokenEncryptionService.encryptToken(pageAccessToken));
            token.setActive(true);
            token.setLastValidatedAt(Instant.now());
            pageTokenRepository.save(token);

            auditLogService.recordSystemAction("TOKEN_REAUTHORIZED", token.getId(),
                    Map.of("pageId", token.getPageId(),
                           "reauthorizedAt", Instant.now().toString()));

            log.info("Facebook page token {} reauthorized successfully.", token.getId());
            return "Facebook integration reauthorized successfully. Automated publishing has resumed.";

        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("OAuth callback failed for token {}: {}", oauthState.tokenId(), ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to complete Facebook re-authentication: " + ex.getMessage());
        }
    }

    /**
     * Manually sets the Page Access Token on an existing token row — an
     * alternative to {@link #initOAuth} for an admin who already has a
     * long-lived token (e.g. from the Graph API Explorer). Encrypts and stores
     * it exactly as the OAuth callback does, but skips the Meta OAuth dance.
     * Does not create a new page — {@code tokenId} must already exist, so this
     * can never change which page the system publishes to (that's fixed by
     * {@code FACEBOOK_PAGE_ID} in the environment, seeded at startup).
     *
     * The candidate token is checked against the live Graph API before
     * anything is persisted — an admin pasting a bad/mismatched token gets an
     * immediate 400 instead of silently breaking publishing until the next
     * TokenHealthCheckJob run discovers it.
     */
    public TokenStatusDto setManualToken(UUID tokenId, String accessToken, JwtUserDetails admin) {
        FacebookPageToken token = pageTokenRepository.findById(tokenId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Facebook page token not found."));

        String trimmed = accessToken == null ? "" : accessToken.trim();
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Access token cannot be blank.");
        }

        assertTokenBelongsToPage(token.getPageId(), trimmed);

        token.setEncryptedToken(tokenEncryptionService.encryptToken(trimmed));
        token.setActive(true);
        token.setLastValidatedAt(Instant.now());
        token.setExpiresAt(null);
        pageTokenRepository.save(token);

        auditLogService.recordSystemAction("TOKEN_MANUALLY_SET", token.getId(),
                Map.of("pageId", token.getPageId(), "setBy", admin.userId().toString()));

        log.info("Admin {} manually set the Facebook page token {} (page {}).",
                admin.userId(), tokenId, token.getPageId());
        return TokenStatusDto.from(token);
    }

    /**
     * Confirms {@code candidateToken} actually works for {@code pageId}, via
     * {@code GET /{page-id}?fields=id}. Facebook rejects the call outright if
     * the token is expired/invalid, and a Page Access Token can only read its
     * own page this way, so a successful response whose {@code id} doesn't
     * match {@code pageId} would mean Facebook's contract changed, not that
     * the token is fine — treated as a failure either way.
     */
    private void assertTokenBelongsToPage(String pageId, String candidateToken) {
        String url = String.format(META_PAGE_TOKEN_URL, apiVersion, pageId)
                + "?fields=id&access_token=" + encode(candidateToken);
        JsonNode node;
        try {
            node = getJson(url);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Facebook rejected this token: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Interrupted while validating the token against Facebook.");
        }
        String returnedId = node.path("id").asText(null);
        if (returnedId == null || !returnedId.equals(pageId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This token is not valid for page " + pageId + ".");
        }
    }

    /**
     * Owner-only: connects a different Facebook Page. This is the ONLY in-app
     * way to change which page the system publishes to — everything else
     * (env vars) is a one-time bootstrap seed, never read again once any page
     * is connected (see {@code FacebookPublisherService.bootstrapTokenFromEnvIfEmpty}).
     * Validates the token against Graph API first (same check as
     * {@link #setManualToken}), deactivates every other page's row, and
     * creates or reactivates the target page's row so switching back to a
     * previously-connected page doesn't hit the {@code page_id} unique
     * constraint with a duplicate insert.
     */
    public TokenStatusDto connectPage(String pageId, String accessToken, JwtUserDetails owner) {
        if (owner == null || !owner.adminOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the Admin Owner can connect a different Facebook Page.");
        }
        String trimmedPageId = pageId == null ? "" : pageId.trim();
        String trimmedToken = accessToken == null ? "" : accessToken.trim();
        if (trimmedPageId.isEmpty() || trimmedToken.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page ID and access token are both required.");
        }

        assertTokenBelongsToPage(trimmedPageId, trimmedToken);

        String previousPageId = pageTokenRepository.findFirstByIsActiveTrue()
                .map(FacebookPageToken::getPageId)
                .orElse(null);

        deactivateOtherActiveTokens(trimmedPageId);

        FacebookPageToken token = pageTokenRepository.findByPageId(trimmedPageId).orElseGet(FacebookPageToken::new);
        token.setPageId(trimmedPageId);
        token.setEncryptedToken(tokenEncryptionService.encryptToken(trimmedToken));
        token.setActive(true);
        token.setLastValidatedAt(Instant.now());
        token.setExpiresAt(null);
        pageTokenRepository.save(token);

        auditLogService.recordSystemAction("FACEBOOK_PAGE_CONNECTED", token.getId(), Map.of(
                "fromPageId", previousPageId == null ? "" : previousPageId,
                "toPageId", trimmedPageId,
                "connectedBy", owner.userId().toString()));

        log.info("Owner {} connected a different Facebook Page: {} -> {}.",
                owner.userId(), previousPageId, trimmedPageId);
        return TokenStatusDto.from(token);
    }

    private void deactivateOtherActiveTokens(String currentPageId) {
        List<FacebookPageToken> stale = pageTokenRepository.findByIsActiveTrueAndPageIdNot(currentPageId);
        if (stale.isEmpty()) return;
        for (FacebookPageToken token : stale) {
            token.setActive(false);
        }
        pageTokenRepository.saveAll(stale);
        log.info("Deactivated {} Facebook page token(s) for page(s) other than {} (page connect).",
                stale.size(), currentPageId);
    }

    // ── OAuth helpers ─────────────────────────────────────────────────────────

    private String exchangeCodeForToken(String code) throws IOException, InterruptedException {
        String url = META_TOKEN_URL
                + "?client_id=" + encode(appId)
                + "&client_secret=" + encode(appSecret)
                + "&redirect_uri=" + encode(redirectUri)
                + "&code=" + encode(code);

        JsonNode node = getJson(url);
        String accessToken = node.path("access_token").asText(null);
        if (accessToken == null || accessToken.isBlank()) {
            throw new IOException("No access_token in code exchange response: " + node);
        }
        return accessToken;
    }

    private String exchangeForLongLivedToken(String shortLivedToken) throws IOException, InterruptedException {
        String url = META_TOKEN_URL
                + "?grant_type=fb_exchange_token"
                + "&client_id=" + encode(appId)
                + "&client_secret=" + encode(appSecret)
                + "&fb_exchange_token=" + encode(shortLivedToken);

        JsonNode node = getJson(url);
        String accessToken = node.path("access_token").asText(null);
        if (accessToken == null || accessToken.isBlank()) {
            throw new IOException("No access_token in long-lived token exchange response: " + node);
        }
        return accessToken;
    }

    private String fetchPageAccessToken(String pageId, String userToken) throws IOException, InterruptedException {
        String url = String.format(META_PAGE_TOKEN_URL, apiVersion, pageId)
                + "?fields=access_token&access_token=" + encode(userToken);

        JsonNode node = getJson(url);
        String pageToken = node.path("access_token").asText(null);
        if (pageToken == null || pageToken.isBlank()) {
            throw new IOException("No access_token in page token response: " + node);
        }
        return pageToken;
    }

    private JsonNode getJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode node = objectMapper.readTree(response.body());
        if (node.has("error")) {
            throw new IOException("Meta API error: " + node.get("error").toString());
        }
        return node;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record OAuthState(UUID tokenId, Instant expiresAt) {}
}
