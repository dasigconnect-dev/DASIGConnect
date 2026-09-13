package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.entity.FacebookPageToken;
import com.dasigconnect.backend.repository.FacebookPageTokenRepository;
import com.dasigconnect.backend.repository.PublicationAttemptRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers only {@code bootstrapTokenFromEnvIfEmpty()} — the rest of this service
 * makes real Graph API calls and isn't unit-tested (pre-existing gap, not
 * introduced here).
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

    private FacebookPublisherService build(String envPageAccessToken, String envPageId) {
        return new FacebookPublisherService(
                envPageAccessToken, envPageId, "app-id", "app-secret", "v25.0",
                tokenEncryptionService, pageTokenRepository, publicationAttemptRepository,
                submissionRepository, eventPublisher, watermarkApplicationService, auditLogService);
    }

    @Test
    void bootstrap_tableAlreadyHasARow_ignoresEnvEntirely() {
        when(pageTokenRepository.count()).thenReturn(1L);

        build("raw-token", "page-id").bootstrapTokenFromEnvIfEmpty();

        verify(pageTokenRepository, never()).save(any());
    }

    @Test
    void bootstrap_emptyTableWithEnvConfigured_seedsOneRow() {
        when(pageTokenRepository.count()).thenReturn(0L);
        when(tokenEncryptionService.isConfigured()).thenReturn(true);
        when(tokenEncryptionService.encryptToken("raw-token")).thenReturn("encrypted");

        build("raw-token", "new-page").bootstrapTokenFromEnvIfEmpty();

        var captor = org.mockito.ArgumentCaptor.forClass(FacebookPageToken.class);
        verify(pageTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getPageId()).isEqualTo("new-page");
        assertThat(captor.getValue().getEncryptedToken()).isEqualTo("encrypted");
    }

    @Test
    void bootstrap_emptyTableButEnvBlank_doesNothing() {
        when(pageTokenRepository.count()).thenReturn(0L);

        build("", "").bootstrapTokenFromEnvIfEmpty();

        verify(pageTokenRepository, never()).save(any());
    }

    @Test
    void bootstrap_emptyTableButEncryptionNotConfigured_doesNothing() {
        when(pageTokenRepository.count()).thenReturn(0L);
        when(tokenEncryptionService.isConfigured()).thenReturn(false);

        build("raw-token", "new-page").bootstrapTokenFromEnvIfEmpty();

        verify(pageTokenRepository, never()).save(any());
    }

    @Test
    void isConfigured_reflectsWhetherAnActiveRowExists() {
        when(pageTokenRepository.findFirstByIsActiveTrue())
                .thenReturn(java.util.Optional.of(new FacebookPageToken()));

        assertThat(build("", "").isConfigured()).isTrue();
    }
}
