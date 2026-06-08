package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.dasigconnect.backend.model.entity.FacebookPageToken;
import com.dasigconnect.backend.repository.FacebookPageTokenRepository;
import com.dasigconnect.backend.repository.PublicationAttemptRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;

class FacebookPublisherServiceTest {

    @Test
    void syncTokenFromEnv_refreshesExistingActiveToken() {
        TokenEncryptionService tokenEncryptionService = mock(TokenEncryptionService.class);
        FacebookPageTokenRepository pageTokenRepository = mock(FacebookPageTokenRepository.class);
        FacebookPageToken existing = new FacebookPageToken();
        existing.setId(UUID.randomUUID());
        existing.setPageId("12345");
        existing.setEncryptedToken("old-encrypted-token");
        existing.setExpiresAt(Instant.now().plusSeconds(60));
        existing.setLastValidatedAt(Instant.now());

        when(tokenEncryptionService.isConfigured()).thenReturn(true);
        when(tokenEncryptionService.encryptToken("new-page-token")).thenReturn("new-encrypted-token");
        when(pageTokenRepository.findByPageIdAndIsActiveTrue("12345")).thenReturn(Optional.of(existing));

        FacebookPublisherService service = newService(
                "new-page-token",
                "12345",
                tokenEncryptionService,
                pageTokenRepository);

        service.syncTokenFromEnv();

        assertThat(existing.getEncryptedToken()).isEqualTo("new-encrypted-token");
        assertThat(existing.isActive()).isTrue();
        assertThat(existing.getExpiresAt()).isNull();
        assertThat(existing.getLastValidatedAt()).isNull();
        verify(pageTokenRepository).save(existing);
    }

    private static FacebookPublisherService newService(
            String pageAccessToken,
            String pageId,
            TokenEncryptionService tokenEncryptionService,
            FacebookPageTokenRepository pageTokenRepository) {
        return new FacebookPublisherService(
                pageAccessToken,
                pageId,
                "app-id",
                "app-secret",
                "v25.0",
                tokenEncryptionService,
                pageTokenRepository,
                mock(PublicationAttemptRepository.class),
                mock(SubmissionRepository.class),
                mock(ApplicationEventPublisher.class));
    }
}
