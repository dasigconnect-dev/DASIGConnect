package com.dasigconnect.backend.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.dasigconnect.backend.model.entity.MediaAssetEmbeddingType;
import com.dasigconnect.backend.model.entity.MediaEmbeddingVersions;
import com.dasigconnect.backend.service.MediaProcessingQueueService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MediaAssetEmbeddingRepositoryTest {

    private static final String SEMANTIC_MODEL = "voyage-4-lite";

    @Test
    void semanticDefaultsUseTheSameVersionAsTheProcessingQueue() {
        MediaAssetEmbeddingRepository repository = mock(
                MediaAssetEmbeddingRepository.class, CALLS_REAL_METHODS);
        UUID institutionId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        List<UUID> assetIds = List.of(UUID.randomUUID());

        repository.countEmbeddingsForAssets(assetIds, MediaAssetEmbeddingType.SEMANTIC);
        repository.findTopSimilarWithScore(
                institutionId, MediaAssetEmbeddingType.SEMANTIC, "[0.1]", 8);
        repository.findTopSimilarToAssetsWithScore(
                institutionId,
                submissionId,
                MediaAssetEmbeddingType.SEMANTIC,
                assetIds,
                10,
                30);

        assertEquals(MediaProcessingQueueService.SEMANTIC_EMBEDDING_VERSION,
                MediaEmbeddingVersions.SEMANTIC);
        verify(repository).countCurrentEmbeddingsForAssets(
                assetIds,
                MediaAssetEmbeddingType.SEMANTIC.dbValue(),
                SEMANTIC_MODEL,
                MediaProcessingQueueService.SEMANTIC_EMBEDDING_VERSION);
        verify(repository).findTopSimilarWithScore(
                institutionId,
                MediaAssetEmbeddingType.SEMANTIC.dbValue(),
                "[0.1]",
                SEMANTIC_MODEL,
                MediaProcessingQueueService.SEMANTIC_EMBEDDING_VERSION,
                8);
        verify(repository).findTopSimilarToAssetsWithScore(
                institutionId,
                submissionId,
                MediaAssetEmbeddingType.SEMANTIC.dbValue(),
                assetIds,
                SEMANTIC_MODEL,
                MediaProcessingQueueService.SEMANTIC_EMBEDDING_VERSION,
                10,
                30);
    }
}
