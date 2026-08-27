package com.dasigconnect.backend.schedule;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.dasigconnect.backend.event.EmbeddingFailureDigestEvent;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.service.ScheduledJobHealthService;

class EmbeddingFailureDigestJobTest {

    private final MediaAssetRepository mediaAssetRepository = mock(MediaAssetRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final ScheduledJobHealthService health = mock(ScheduledJobHealthService.class);
    private final EmbeddingFailureDigestJob job =
            new EmbeddingFailureDigestJob(mediaAssetRepository, eventPublisher, health);

    @Test
    void scanFailedEmbeddings_publishesDigestAndRecordsSuccess() {
        when(mediaAssetRepository.countFailedAssets()).thenReturn(3L);
        when(mediaAssetRepository.findSampleFailedFilenames()).thenReturn(List.of("a.jpg", "b.png"));

        job.scanFailedEmbeddings();

        verify(eventPublisher).publishEvent(any(EmbeddingFailureDigestEvent.class));
        verify(health).recordSuccess(eq("EmbeddingFailureDigestJob"), any());
    }

    @Test
    void scanFailedEmbeddings_noFailuresStillRecordsSuccess() {
        when(mediaAssetRepository.countFailedAssets()).thenReturn(0L);

        job.scanFailedEmbeddings();

        verify(eventPublisher, never()).publishEvent(any());
        verify(health).recordSuccess(eq("EmbeddingFailureDigestJob"), any());
    }

    @Test
    void scanFailedEmbeddings_recordsFailureWhenQueryThrows() {
        when(mediaAssetRepository.countFailedAssets()).thenThrow(new RuntimeException("boom"));

        job.scanFailedEmbeddings();

        verify(health).recordFailure(eq("EmbeddingFailureDigestJob"), any(), any());
        verify(health, never()).recordSuccess(any(), any());
    }
}
