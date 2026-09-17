package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.entity.FacebookPageToken;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.repository.FacebookPageTokenRepository;
import com.dasigconnect.backend.repository.PublicationAttemptRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import java.util.Optional;
import java.util.UUID;
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
    @Mock SlotReservationService slotReservationService;

    private FacebookPublisherService build(String envPageAccessToken, String envPageId) {
        return new FacebookPublisherService(
                envPageAccessToken, envPageId, "app-id", "app-secret", "v25.0",
                tokenEncryptionService, pageTokenRepository, publicationAttemptRepository,
                submissionRepository, eventPublisher, watermarkApplicationService, auditLogService,
                slotReservationService);
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

    @Test
    void markPublished_releasesTheSlotReservation() {
        // A locked SlotReservation serves no further purpose once the post is
        // actually out -- leaving it locked forever is what let already-published
        // submissions silently violate GR-H1's network-wide ±30-minute rule
        // (found and fixed 2026-09-17, see V95 migration).
        UUID submissionId = UUID.randomUUID();
        Submission submission = new Submission();
        submission.setId(submissionId);
        submission.setStatus(SubmissionStatus.publishing);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        build("", "").markPublished(submission, "1234_5678");

        verify(slotReservationService).release(submissionId);
    }
}
