package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.entity.FacebookPageToken;
import com.dasigconnect.backend.repository.FacebookPageTokenRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenManagementServiceTest {

    @Mock FacebookPageTokenRepository pageTokenRepository;
    @Mock TokenEncryptionService tokenEncryptionService;
    @Mock AuditLogService auditLogService;
    @Mock HttpClient httpClient;
    @Mock HttpResponse<String> graphResponse;

    @InjectMocks TokenManagementService service;

    private final JwtUserDetails admin =
            new JwtUserDetails(UUID.randomUUID(), "admin@example.com", "admin", null, false);
    private final JwtUserDetails owner =
            new JwtUserDetails(UUID.randomUUID(), "owner@example.com", "admin", null, true);

    @BeforeEach
    void injectMockHttpClient() {
        ReflectionTestUtils.setField(service, "httpClient", httpClient);
        ReflectionTestUtils.setField(service, "apiVersion", "v25.0");
        ReflectionTestUtils.setField(service, "appId", "app-id");
        ReflectionTestUtils.setField(service, "appSecret", "app-secret");
        ReflectionTestUtils.setField(service, "redirectUri", "https://backend.example/api/v1/system-health/oauth-callback");
    }

    private static String extractState(String authorizationUrl) {
        for (String param : authorizationUrl.split("[?&]")) {
            if (param.startsWith("state=")) {
                return java.net.URLDecoder.decode(param.substring("state=".length()), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("No state param in URL: " + authorizationUrl);
    }

    @Test
    void setManualToken_validForPage_encryptsStoresAndAudits() throws Exception {
        UUID tokenId = UUID.randomUUID();
        FacebookPageToken token = new FacebookPageToken();
        ReflectionTestUtils.setField(token, "id", tokenId);
        token.setPageId("123456");
        token.setActive(false);

        when(pageTokenRepository.findById(tokenId)).thenReturn(Optional.of(token));
        when(graphResponse.body()).thenReturn("{\"id\":\"123456\"}");
        org.mockito.Mockito.doReturn(graphResponse).when(httpClient).send(any(), any());
        when(tokenEncryptionService.encryptToken("raw-token")).thenReturn("encrypted-blob");
        when(pageTokenRepository.save(any(FacebookPageToken.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.setManualToken(tokenId, "  raw-token  ", admin);

        assertThat(token.getEncryptedToken()).isEqualTo("encrypted-blob");
        assertThat(token.isActive()).isTrue();
        assertThat(token.getLastValidatedAt()).isNotNull();
        assertThat(result.getPageId()).isEqualTo("123456");
        verify(auditLogService).recordSystemAction("TOKEN_MANUALLY_SET", tokenId,
                java.util.Map.of("pageId", "123456", "setBy", admin.userId().toString()));
    }

    @Test
    void setManualToken_tokenValidButForADifferentPage_rejectedWithoutSaving() throws Exception {
        UUID tokenId = UUID.randomUUID();
        FacebookPageToken token = new FacebookPageToken();
        token.setPageId("123456");

        when(pageTokenRepository.findById(tokenId)).thenReturn(Optional.of(token));
        when(graphResponse.body()).thenReturn("{\"id\":\"999999\"}");
        org.mockito.Mockito.doReturn(graphResponse).when(httpClient).send(any(), any());

        assertThatThrownBy(() -> service.setManualToken(tokenId, "raw-token", admin))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not valid for page");
        verify(pageTokenRepository, never()).save(any());
    }

    @Test
    void setManualToken_facebookRejectsToken_rejectedWithoutSaving() throws Exception {
        UUID tokenId = UUID.randomUUID();
        FacebookPageToken token = new FacebookPageToken();
        token.setPageId("123456");

        when(pageTokenRepository.findById(tokenId)).thenReturn(Optional.of(token));
        when(graphResponse.body()).thenReturn("{\"error\":{\"message\":\"Invalid OAuth access token.\"}}");
        org.mockito.Mockito.doReturn(graphResponse).when(httpClient).send(any(), any());

        assertThatThrownBy(() -> service.setManualToken(tokenId, "garbage-token", admin))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Facebook rejected this token");
        verify(pageTokenRepository, never()).save(any());
    }

    @Test
    void setManualToken_blankToken_isRejected() {
        UUID tokenId = UUID.randomUUID();
        FacebookPageToken token = new FacebookPageToken();
        token.setPageId("123456");
        when(pageTokenRepository.findById(tokenId)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.setManualToken(tokenId, "   ", admin))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void setManualToken_unknownTokenId_returns404() {
        UUID tokenId = UUID.randomUUID();
        when(pageTokenRepository.findById(tokenId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setManualToken(tokenId, "raw-token", admin))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void connectPage_nonOwnerAdmin_isForbidden() {
        assertThatThrownBy(() -> service.connectPage("new-page", "raw-token", admin))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Admin Owner");
        verify(pageTokenRepository, never()).save(any());
    }

    @Test
    void connectPage_blankFields_isRejected() {
        assertThatThrownBy(() -> service.connectPage(" ", "raw-token", owner))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("required");
        assertThatThrownBy(() -> service.connectPage("new-page", " ", owner))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("required");
    }

    @Test
    void connectPage_newPage_validatesDeactivatesOldAndCreatesNewRow() throws Exception {
        FacebookPageToken oldActive = new FacebookPageToken();
        oldActive.setPageId("old-page");
        oldActive.setActive(true);

        when(pageTokenRepository.findFirstByIsActiveTrue()).thenReturn(Optional.of(oldActive));
        when(graphResponse.body()).thenReturn("{\"id\":\"new-page\"}");
        org.mockito.Mockito.doReturn(graphResponse).when(httpClient).send(any(), any());
        when(pageTokenRepository.findByIsActiveTrueAndPageIdNot("new-page")).thenReturn(java.util.List.of(oldActive));
        when(pageTokenRepository.findByPageId("new-page")).thenReturn(Optional.empty());
        when(tokenEncryptionService.encryptToken("raw-token")).thenReturn("encrypted-blob");
        when(pageTokenRepository.save(any(FacebookPageToken.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.connectPage("new-page", "raw-token", owner);

        assertThat(oldActive.isActive()).isFalse();
        assertThat(result.getPageId()).isEqualTo("new-page");
        verify(pageTokenRepository).saveAll(java.util.List.of(oldActive));
        verify(auditLogService).recordSystemAction("FACEBOOK_PAGE_CONNECTED", null, java.util.Map.of(
                "fromPageId", "old-page", "toPageId", "new-page", "connectedBy", owner.userId().toString()));
    }

    @Test
    void connectPage_tokenNotValidForTargetPage_rejectedWithoutSaving() throws Exception {
        when(graphResponse.body()).thenReturn("{\"id\":\"different-page\"}");
        org.mockito.Mockito.doReturn(graphResponse).when(httpClient).send(any(), any());

        assertThatThrownBy(() -> service.connectPage("target-page", "raw-token", owner))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not valid for page");
        verify(pageTokenRepository, never()).save(any());
        verify(pageTokenRepository, never()).saveAll(any());
    }

    @Test
    void initOAuth_thenHandleCallback_statelessStateRoundTrips() throws Exception {
        UUID tokenId = UUID.randomUUID();
        FacebookPageToken token = new FacebookPageToken();
        ReflectionTestUtils.setField(token, "id", tokenId);
        token.setPageId("123456");

        when(pageTokenRepository.findById(tokenId)).thenReturn(Optional.of(token));

        var init = service.initOAuth(tokenId, admin);
        String state = extractState(init.getAuthorizationUrl());

        when(graphResponse.body()).thenReturn(
                "{\"access_token\":\"short-lived\"}",
                "{\"access_token\":\"long-lived\"}",
                "{\"access_token\":\"page-token\"}");
        org.mockito.Mockito.doReturn(graphResponse).when(httpClient).send(any(), any());
        when(tokenEncryptionService.encryptToken("page-token")).thenReturn("encrypted-blob");
        when(pageTokenRepository.save(any(FacebookPageToken.class))).thenAnswer(inv -> inv.getArgument(0));

        String result = service.handleCallback("auth-code", state);

        assertThat(result).contains("reauthorized successfully");
        assertThat(token.getEncryptedToken()).isEqualTo("encrypted-blob");
        assertThat(token.isActive()).isTrue();
        verify(auditLogService).recordSystemAction(
                org.mockito.ArgumentMatchers.eq("TOKEN_REAUTHORIZED"),
                org.mockito.ArgumentMatchers.eq(tokenId),
                org.mockito.ArgumentMatchers.argThat(map ->
                        "123456".equals(map.get("pageId")) && map.containsKey("reauthorizedAt")));
    }

    @Test
    void handleCallback_survivesServiceRestart_becauseStateIsSelfContained() throws Exception {
        // Simulates the exact bug this replaced: initOAuth on one instance,
        // handleCallback on a "fresh" instance (e.g. after a redeploy) that
        // never saw the original initOAuth call — no shared memory needed.
        UUID tokenId = UUID.randomUUID();
        FacebookPageToken token = new FacebookPageToken();
        ReflectionTestUtils.setField(token, "id", tokenId);
        token.setPageId("123456");
        when(pageTokenRepository.findById(tokenId)).thenReturn(Optional.of(token));
        var init = service.initOAuth(tokenId, admin);
        String state = extractState(init.getAuthorizationUrl());

        TokenManagementService freshInstance = new TokenManagementService(
                pageTokenRepository, tokenEncryptionService, auditLogService,
                "app-id", "app-secret", "v25.0", "https://backend.example/callback");
        ReflectionTestUtils.setField(freshInstance, "httpClient", httpClient);

        when(graphResponse.body()).thenReturn(
                "{\"access_token\":\"short-lived\"}",
                "{\"access_token\":\"long-lived\"}",
                "{\"access_token\":\"page-token\"}");
        org.mockito.Mockito.doReturn(graphResponse).when(httpClient).send(any(), any());
        when(tokenEncryptionService.encryptToken("page-token")).thenReturn("encrypted-blob");
        when(pageTokenRepository.save(any(FacebookPageToken.class))).thenAnswer(inv -> inv.getArgument(0));

        String result = freshInstance.handleCallback("auth-code", state);

        assertThat(result).contains("reauthorized successfully");
    }

    @Test
    void handleCallback_tamperedState_isRejected() {
        assertThatThrownBy(() -> service.handleCallback("auth-code", "not-a-real-state"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid or expired OAuth state");
    }

    @Test
    void handleCallback_stateSignedWithDifferentSecret_isRejected() throws Exception {
        UUID tokenId = UUID.randomUUID();
        when(pageTokenRepository.findById(tokenId)).thenReturn(Optional.of(new FacebookPageToken()));
        var init = service.initOAuth(tokenId, admin);
        String legitimateState = extractState(init.getAuthorizationUrl());

        ReflectionTestUtils.setField(service, "appSecret", "a-different-secret");

        assertThatThrownBy(() -> service.handleCallback("auth-code", legitimateState))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid or expired OAuth state");
    }
}
