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

    private final HttpClient httpClient = HttpClient.newHttpClient();
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
                + "&scope=" + encode("pages_manage_posts,pages_read_engagement,pages_show_list")
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
