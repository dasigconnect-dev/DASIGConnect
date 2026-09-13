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
}
