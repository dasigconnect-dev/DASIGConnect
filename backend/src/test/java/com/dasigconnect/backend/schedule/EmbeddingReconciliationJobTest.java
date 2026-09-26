package com.dasigconnect.backend.schedule;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.service.MediaProcessingQueueService;
import com.dasigconnect.backend.service.ScheduledJobHealthService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class EmbeddingReconciliationJobTest {

    private final MediaAssetRepository mediaAssetRepository = mock(MediaAssetRepository.class);
    private final MediaProcessingQueueService queue = mock(MediaProcessingQueueService.class);
    private final ScheduledJobHealthService health = mock(ScheduledJobHealthService.class);

    private EmbeddingReconciliationJob job(boolean aiConfigured) {
        return new EmbeddingReconciliationJob(
                mediaAssetRepository, queue, health,
                aiConfigured ? "test-key" : "", aiConfigured ? "voyage-key" : "", 10);
    }

    @Test
    void reconcile_incompleteAssets_areIdempotentlyEnqueued() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        MediaAsset first = new MediaAsset();
        first.setId(firstId);
        MediaAsset second = new MediaAsset();
        second.setId(secondId);
        when(queue.availableBackfillSlots(10)).thenReturn(10);
        when(mediaAssetRepository.findNeedingProcessingVersion(
                eq(MediaProcessingQueueService.PROCESSING_VERSION), any()))
                .thenReturn(List.of(first, second));

        job(true).reconcile();

        verify(queue).enqueue(firstId);
        verify(queue).enqueue(secondId);
    }

    @Test
    void reconcile_readyImagesWithoutVisualVectors_areIdempotentlyEnqueued() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);
        when(queue.availableBackfillSlots(10)).thenReturn(10);
        when(mediaAssetRepository.findNeedingProcessingVersion(
                eq(MediaProcessingQueueService.PROCESSING_VERSION), any()))
                .thenReturn(List.of());
        when(mediaAssetRepository.findReadyImagesMissingImageEmbedding(any()))
                .thenReturn(List.of(asset));

        job(true).reconcile();

        verify(queue).enqueueImageOnly(assetId);
    }

    @Test
    void reconcile_aiNotConfigured_doesNothing() {
        job(false).reconcile();

        verify(mediaAssetRepository, never()).findNeedingProcessingVersion(any(), any());
    }

    @Test
    void reconcile_queueAtCapacity_defersBackfill() {
        when(queue.availableBackfillSlots(10)).thenReturn(0);

        job(true).reconcile();

        verify(mediaAssetRepository, never()).findNeedingProcessingVersion(any(), any());
    }
}
