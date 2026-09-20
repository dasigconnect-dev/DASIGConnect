package com.dasigconnect.backend.schedule;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.repository.AssetTagRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.service.AIClassificationService;
import com.dasigconnect.backend.service.ScheduledJobHealthService;

/**
 * Regression coverage: a stuck image asset that already has a classification
 * (ai_classified_at set) must only have its embedding retried, never be
 * reclassified via Claude Vision again — see AIClassificationServiceTest's
 * matching coverage on the 374-tag accumulation this used to cause.
 */
class EmbeddingReconciliationJobTest {

    private final MediaAssetRepository mediaAssetRepository = mock(MediaAssetRepository.class);
    private final AssetTagRepository assetTagRepository = mock(AssetTagRepository.class);
    private final AIClassificationService aiClassificationService = mock(AIClassificationService.class);
    private final ScheduledJobHealthService health = mock(ScheduledJobHealthService.class);

    private EmbeddingReconciliationJob job(boolean aiConfigured) {
        return new EmbeddingReconciliationJob(
                mediaAssetRepository, assetTagRepository, aiClassificationService, health,
                aiConfigured ? "test-key" : "", "");
    }

    private static MediaAsset imageAsset(UUID id, Instant aiClassifiedAt) {
        MediaAsset asset = new MediaAsset();
        asset.setId(id);
        asset.setFileType(MediaFileType.jpeg);
        asset.setStorageUrl("https://example.com/" + id + ".jpg");
        asset.setAiClassifiedAt(aiClassifiedAt);
        return asset;
    }

    @Test
    void reconcile_neverClassifiedImage_callsFullClassifyAndEmbed() {
        UUID assetId = UUID.randomUUID();
        when(mediaAssetRepository.findNeedingEmbedding()).thenReturn(List.of(imageAsset(assetId, null)));

        job(true).reconcile();

        verify(aiClassificationService).classifyAndEmbed(eq(assetId), anyString());
        verify(aiClassificationService, never()).retryStuckImageEmbedding(any(), anyString());
    }

    @Test
    void reconcile_alreadyClassifiedButStuckImage_onlyRetriesEmbedding() {
        UUID assetId = UUID.randomUUID();
        when(mediaAssetRepository.findNeedingEmbedding())
                .thenReturn(List.of(imageAsset(assetId, Instant.now())));

        job(true).reconcile();

        verify(aiClassificationService).retryStuckImageEmbedding(eq(assetId), anyString());
        verify(aiClassificationService, never()).classifyAndEmbed(any(), anyString());
    }

    @Test
    void reconcile_aiNotConfigured_doesNothing() {
        job(false).reconcile();

        verify(mediaAssetRepository, never()).findNeedingEmbedding();
    }
}
