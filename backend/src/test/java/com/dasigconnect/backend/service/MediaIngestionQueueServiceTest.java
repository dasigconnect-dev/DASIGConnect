package com.dasigconnect.backend.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetStatus;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MediaIngestionQueueServiceTest {

    @Mock private AIClassificationService aiClassificationService;
    @Mock private MediaQualityAnalysisService mediaQualityAnalysisService;
    @Mock private MediaAssetRepository mediaAssetRepository;

    private MediaIngestionQueueService service;

    /** Run submitted tasks synchronously on the calling thread for deterministic assertions. */
    private static final Executor SYNCHRONOUS = Runnable::run;

    @BeforeEach
    void setUp() {
        service = new MediaIngestionQueueService(SYNCHRONOUS, aiClassificationService, mediaQualityAnalysisService, mediaAssetRepository);
    }

    private MediaAsset asset(UUID id, MediaAssetStatus status) {
        MediaAsset a = new MediaAsset();
        a.setId(id);
        a.setStatus(status);
        a.setFileType(MediaFileType.jpeg);
        return a;
    }

    @Test
    void enqueue_processingAsset_runsClassifyAndEmbed() {
        UUID id = UUID.randomUUID();
        when(mediaAssetRepository.findActiveById(id)).thenReturn(Optional.of(asset(id, MediaAssetStatus.PROCESSING)));

        service.enqueue(id, "https://storage/x.jpg");

        verify(aiClassificationService).classifyAndEmbed(id, "https://storage/x.jpg");
    }

    @Test
    void enqueue_processingImage_runsQualityAnalysisBeforeClassification() {
        UUID id = UUID.randomUUID();
        when(mediaAssetRepository.findActiveById(id)).thenReturn(Optional.of(asset(id, MediaAssetStatus.PROCESSING)));

        service.enqueue(id, "https://storage/x.jpg");

        var ordered = org.mockito.Mockito.inOrder(mediaQualityAnalysisService, aiClassificationService);
        ordered.verify(mediaQualityAnalysisService).analyzeImage(id, "https://storage/x.jpg");
        ordered.verify(aiClassificationService).classifyAndEmbed(id, "https://storage/x.jpg");
    }

    @Test
    void enqueue_readyAsset_skips() {
        UUID id = UUID.randomUUID();
        when(mediaAssetRepository.findActiveById(id)).thenReturn(Optional.of(asset(id, MediaAssetStatus.READY)));

        service.enqueue(id, "https://storage/x.jpg");

        verify(aiClassificationService, never()).classifyAndEmbed(any(), any());
    }

    @Test
    void enqueue_missingAsset_skips() {
        UUID id = UUID.randomUUID();
        when(mediaAssetRepository.findActiveById(id)).thenReturn(Optional.empty());

        service.enqueue(id, "https://storage/x.jpg");

        verify(aiClassificationService, never()).classifyAndEmbed(any(), any());
    }

    @Test
    void enqueue_workerException_isSwallowed() {
        UUID id = UUID.randomUUID();
        when(mediaAssetRepository.findActiveById(id)).thenReturn(Optional.of(asset(id, MediaAssetStatus.PROCESSING)));
        doThrow(new RuntimeException("boom")).when(aiClassificationService).classifyAndEmbed(eq(id), any());

        assertDoesNotThrow(() -> service.enqueue(id, "https://storage/x.jpg"));
    }
}
