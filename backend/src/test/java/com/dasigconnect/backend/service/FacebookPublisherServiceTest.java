package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.entity.FacebookPageToken;
import com.dasigconnect.backend.repository.FacebookPageTokenRepository;
import com.dasigconnect.backend.repository.PublicationAttemptRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers only {@code syncTokenFromEnv()} — the rest of this service makes real
 * Graph API calls and isn't unit-tested (pre-existing gap, not introduced here).
 */
@ExtendWith(MockitoExtension.class)
class FacebookPublisherServiceTest {

    @Mock TokenEncryptionService tokenEncryptionService;
    @Mock FacebookPageTokenRepository pageTokenRepository;
    @Mock PublicationAttemptRepository publicationAttemptRepository;
    @Mock SubmissionRepository submissionRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock WatermarkApplicationService watermarkApplicationService;
    @Mock AuditLogService auditLogService;

    private FacebookPublisherService build(String pageAccessToken, String pageId) {
        return new FacebookPublisherService(
                pageAccessToken, pageId, "app-id", "app-secret", "v25.0",
                tokenEncryptionService, pageTokenRepository, publicationAttemptRepository,
                submissionRepository, eventPublisher, watermarkApplicationService, auditLogService);
    }

    @Test
    void syncTokenFromEnv_newPage_deactivatesStaleRowsAndCreatesNewOne() {
        FacebookPageToken staleRow = new FacebookPageToken();
        staleRow.setPageId("old-page");
        staleRow.setActive(true);

        when(tokenEncryptionService.isConfigured()).thenReturn(true);
        when(pageTokenRepository.findByIsActiveTrueAndPageIdNot("new-page")).thenReturn(List.of(staleRow));
        when(pageTokenRepository.findByPageId("new-page")).thenReturn(Optional.empty());
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted");

        build("raw-token", "new-page").syncTokenFromEnv();

        assertThat(staleRow.isActive()).isFalse();
        verify(pageTokenRepository).saveAll(List.of(staleRow));
        verify(pageTokenRepository).save(any(FacebookPageToken.class));
    }

    @Test
    void syncTokenFromEnv_switchingBackToAPreviouslyUsedPage_reactivatesInsteadOfDuplicating() {
        FacebookPageToken existingInactiveRow = new FacebookPageToken();
        existingInactiveRow.setPageId("returning-page");
        existingInactiveRow.setActive(false);
        existingInactiveRow.setEncryptedToken("old-encrypted");

        when(tokenEncryptionService.isConfigured()).thenReturn(true);
        when(pageTokenRepository.findByIsActiveTrueAndPageIdNot("returning-page")).thenReturn(List.of());
        when(pageTokenRepository.findByPageId("returning-page")).thenReturn(Optional.of(existingInactiveRow));
        when(tokenEncryptionService.decryptToken("old-encrypted")).thenReturn("old-raw-token");
        when(tokenEncryptionService.encryptToken("new-raw-token")).thenReturn("new-encrypted");

        build("new-raw-token", "returning-page").syncTokenFromEnv();

        assertThat(existingInactiveRow.isActive()).isTrue();
        assertThat(existingInactiveRow.getEncryptedToken()).isEqualTo("new-encrypted");
        verify(pageTokenRepository, times(1)).save(existingInactiveRow);
    }

    @Test
    void syncTokenFromEnv_noStaleRows_doesNotCallSaveAll() {
        when(tokenEncryptionService.isConfigured()).thenReturn(true);
        when(pageTokenRepository.findByIsActiveTrueAndPageIdNot("page")).thenReturn(List.of());
        when(pageTokenRepository.findByPageId("page")).thenReturn(Optional.empty());
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted");

        build("raw-token", "page").syncTokenFromEnv();

        verify(pageTokenRepository, never()).saveAll(any());
    }
}
