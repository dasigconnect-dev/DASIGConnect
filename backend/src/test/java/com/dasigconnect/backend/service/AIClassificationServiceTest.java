package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dasigconnect.backend.external.ClaudeVisionClient;
import com.dasigconnect.backend.external.MediaClassificationClient;
import com.dasigconnect.backend.external.VoyageAIClient;
import com.dasigconnect.backend.model.dto.ai.MediaClassificationDto;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetStatus;
import com.dasigconnect.backend.repository.AssetTagRepository;
import com.dasigconnect.backend.repository.MediaAssetEmbeddingRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;

/**
 * Covers the UC-2.1 bug fix: an asset must leave PROCESSING once classification
 * and both embeddings actually succeed, and must land on FAILED (not silently
 * stay PROCESSING) the moment any step fails.
 */
@ExtendWith(MockitoExtension.class)
class AIClassificationServiceTest {

    @Mock private MediaAssetRepository mediaAssetRepository;
    @Mock private MediaAssetEmbeddingRepository mediaAssetEmbeddingRepository;
    @Mock private AssetTagRepository assetTagRepository;
    @Mock private ClaudeVisionClient claudeVisionClient;
    @Mock private MediaClassificationClient classificationClient;
    @Mock private VoyageAIClient voyageAIClient;
    @Mock private MediaImageEmbeddingService mediaImageEmbeddingService;
    @Mock private MediaProcessingQueueService mediaProcessingQueueService;

    private AIClassificationService service() {
        return service(mediaImageEmbeddingService);
    }

    private AIClassificationService service(MediaImageEmbeddingService imageEmbeddingService) {
        return new AIClassificationService(
                mediaAssetRepository, mediaAssetEmbeddingRepository, assetTagRepository,
                classificationClient, voyageAIClient, imageEmbeddingService, mediaProcessingQueueService);
    }

    private AIClassificationService serviceWithRealImageEmbedding() {
        return service(new MediaImageEmbeddingService(
                mediaAssetEmbeddingRepository, claudeVisionClient, voyageAIClient));
    }

    private static MediaClassificationDto classification() {
        return new MediaClassificationDto("Event", 0.9, "A campus event.", List.of("students"));
    }

    @Test
    void classifyAndEmbed_bothEmbeddingsSucceed_marksReady() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);

        when(classificationClient.classifyMedia(any())).thenReturn(classification());
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset));
        when(mediaImageEmbeddingService.generateOrReuse(assetId, "https://example.com/a.jpg"))
                .thenReturn(true);
        when(voyageAIClient.embedDocument(anyString())).thenReturn("[0.2]");
        when(voyageAIClient.modelName()).thenReturn("voyage-4-lite");

        service().classifyAndEmbed(assetId, "https://example.com/a.jpg");

        verify(mediaAssetRepository).updateStatus(assetId, MediaAssetStatus.READY.name());
        verify(mediaAssetRepository, never()).updateStatus(assetId, MediaAssetStatus.FAILED.name());
    }

    @Test
    void classifyAndEmbed_geminiFails_marksFailedAndNeverReady() {
        UUID assetId = UUID.randomUUID();
        when(classificationClient.classifyMedia(any())).thenThrow(new RuntimeException("Gemini down"));

        service().classifyAndEmbed(assetId, "https://example.com/a.jpg");

        verify(mediaAssetRepository).updateStatus(assetId, MediaAssetStatus.FAILED.name());
        verify(mediaAssetRepository, never()).updateStatus(eq(assetId), eq(MediaAssetStatus.READY.name()));
    }

    @Test
    void classifyAndEmbed_semanticEmbeddingFails_marksFailedAndNeverReady() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);

        when(classificationClient.classifyMedia(any())).thenReturn(classification());
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset));
        when(mediaImageEmbeddingService.generateOrReuse(assetId, "https://example.com/a.jpg"))
                .thenReturn(true);
        when(voyageAIClient.embedDocument(anyString())).thenThrow(new RuntimeException("Voyage down"));

        service().classifyAndEmbed(assetId, "https://example.com/a.jpg");

        verify(mediaAssetRepository).updateStatus(assetId, MediaAssetStatus.FAILED.name());
        verify(mediaAssetRepository, never()).updateStatus(eq(assetId), eq(MediaAssetStatus.READY.name()));
    }

    @Test
    void retryStuckImageEmbedding_bothStepsSucceed_marksReady() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);
        asset.setAiClassifiedAt(java.time.Instant.now());

        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset));
        when(mediaImageEmbeddingService.generateOrReuse(assetId, "https://example.com/a.jpg"))
                .thenReturn(true);
        when(voyageAIClient.embedDocument(anyString())).thenReturn("[0.2]");
        when(voyageAIClient.modelName()).thenReturn("voyage-4-lite");

        service().retryStuckImageEmbedding(assetId, "https://example.com/a.jpg");

        // Claude Vision must NOT be called again — that's the whole point of this path.
        verify(classificationClient, never()).classifyMedia(any());
        verify(mediaAssetRepository).updateStatus(assetId, MediaAssetStatus.READY.name());
        verify(mediaAssetRepository, never()).updateStatus(assetId, MediaAssetStatus.FAILED.name());
    }

    @Test
    void retryStuckImageEmbedding_imageEmbeddingStillFails_marksFailedWithoutCallingClaude() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);
        asset.setAiClassifiedAt(java.time.Instant.now());

        when(mediaImageEmbeddingService.generateOrReuse(assetId, "https://example.com/a.jpg"))
                .thenReturn(false);

        service().retryStuckImageEmbedding(assetId, "https://example.com/a.jpg");

        verify(classificationClient, never()).classifyMedia(any());
        verify(mediaAssetRepository).updateStatus(assetId, MediaAssetStatus.FAILED.name());
        verify(mediaAssetRepository, never()).updateStatus(eq(assetId), eq(MediaAssetStatus.READY.name()));
    }

    @Test
    void processAsset_existingImageEmbedding_onlyRunsMissingSemanticStage() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);
        asset.setFileType(com.dasigconnect.backend.model.entity.MediaFileType.jpeg);
        asset.setAiClassifiedAt(java.time.Instant.now());
        asset.setAiClassificationModel("gemini-3.8-flash");
        asset.setFileName("event.jpg");
        String imageModel = "voyage-multimodal-3.5";
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset));
        when(classificationClient.modelName()).thenReturn("gemini-3.8-flash");
        when(voyageAIClient.multimodalModelName()).thenReturn(imageModel);
        when(mediaAssetEmbeddingRepository.existsCurrentVersionedEmbedding(
                eq(assetId),
                eq(com.dasigconnect.backend.model.entity.MediaAssetEmbeddingType.IMAGE.dbValue()),
                eq(imageModel), anyString(), eq(0L),
                eq(MediaProcessingQueueService.IMAGE_EMBEDDING_VERSION))).thenReturn(true);
        when(mediaAssetEmbeddingRepository.findEmbedding(
                assetId, com.dasigconnect.backend.model.entity.MediaAssetEmbeddingType.SEMANTIC))
                .thenReturn(Optional.empty());
        when(voyageAIClient.embedDocument(anyString())).thenReturn("[0.2]");
        when(voyageAIClient.modelName()).thenReturn("voyage-4-lite");

        boolean completed = serviceWithRealImageEmbedding()
                .processAsset(assetId, "https://example.com/a.jpg");

        assertThat(completed).isTrue();
        verify(classificationClient, never()).classifyMedia(any());
        verify(claudeVisionClient, never()).prepareImageForVisualEmbedding(anyString());
        verify(voyageAIClient, never()).embedImageDocument(any(), anyString());
        verify(mediaAssetEmbeddingRepository, never()).upsertVersioned(
                eq(assetId),
                eq(com.dasigconnect.backend.model.entity.MediaAssetEmbeddingType.IMAGE.dbValue()),
                anyString(), anyString(), anyString(), eq(0L), anyString());
        verify(mediaAssetRepository).updateStatus(assetId, MediaAssetStatus.READY.name());
    }

    @Test
    void processAsset_staleImageEmbedding_regeneratesWithCurrentModel() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);
        asset.setFileType(com.dasigconnect.backend.model.entity.MediaFileType.jpeg);
        asset.setAiClassifiedAt(java.time.Instant.now());
        asset.setAiClassificationModel("gemini-3.8-flash");
        asset.setAiProcessingVersion("media-ai-v2");
        when(classificationClient.modelName()).thenReturn("gemini-3.8-flash");
        String imageModel = "voyage-multimodal-3.5";
        String storageUrl = "https://example.com/a.jpg";
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset));
        when(voyageAIClient.multimodalModelName()).thenReturn(imageModel);
        when(claudeVisionClient.prepareImageForVisualEmbedding(storageUrl))
                .thenReturn(new ClaudeVisionClient.PreparedImage(new byte[]{1, 2}, "image/jpeg"));
        when(voyageAIClient.embedImageDocument(any(), eq("image/jpeg"))).thenReturn("[0.1]");
        when(mediaAssetEmbeddingRepository.findEmbedding(
                assetId, com.dasigconnect.backend.model.entity.MediaAssetEmbeddingType.SEMANTIC))
                .thenReturn(Optional.of("[0.2]"));

        boolean completed = serviceWithRealImageEmbedding()
                .processAsset(assetId, storageUrl, "media-ai-v2");

        assertThat(completed).isTrue();
        verify(voyageAIClient).embedImageDocument(any(), eq("image/jpeg"));
        verify(mediaAssetEmbeddingRepository).upsertVersioned(
                eq(assetId),
                eq(com.dasigconnect.backend.model.entity.MediaAssetEmbeddingType.IMAGE.dbValue()),
                eq("[0.1]"), eq(imageModel), anyString(), eq(0L),
                eq(MediaProcessingQueueService.IMAGE_EMBEDDING_VERSION));
        verify(classificationClient, never()).classifyMedia(any());
        verify(voyageAIClient, never()).embedDocument(anyString());
        verify(mediaAssetRepository).updateStatus(assetId, MediaAssetStatus.READY.name());
    }

    @Test
    void processAsset_newProcessingVersion_refreshesStructuredClassificationAndSemanticEmbedding() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);
        asset.setFileType(com.dasigconnect.backend.model.entity.MediaFileType.jpeg);
        asset.setAiClassifiedAt(java.time.Instant.now());
        asset.setAiProcessingVersion("media-ai-v1");
        asset.setFileName("legacy-event.jpg");
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset));
        when(classificationClient.classifyMedia(any())).thenReturn(classification());
        when(mediaImageEmbeddingService.generateOrReuse(
                assetId, "https://example.com/legacy-event.jpg"))
                .thenReturn(true);
        when(voyageAIClient.embedDocument(anyString())).thenReturn("[0.2]");
        when(voyageAIClient.modelName()).thenReturn("voyage-4-lite");

        boolean completed = service().processAsset(
                assetId, "https://example.com/legacy-event.jpg", "media-ai-v2");

        assertThat(completed).isTrue();
        verify(classificationClient).classifyMedia(any());
        verify(voyageAIClient).embedDocument(anyString());
        verify(claudeVisionClient, never()).prepareImageForVisualEmbedding(anyString());
    }

    @Test
    void classifyAndEmbed_reclassifyingAnAsset_replacesRatherThanAccumulatesAiTags() {
        // Regression: persistSuggestedTags used to only skip an exact-label
        // duplicate, so a second classifyAndEmbed run for the same asset just
        // kept adding more ai_generated rows forever (one production asset hit
        // 374). It must now clear the prior run's AI tags before inserting.
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);

        when(classificationClient.classifyMedia(any())).thenReturn(classification());
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset));
        when(mediaImageEmbeddingService.generateOrReuse(assetId, "https://example.com/a.jpg"))
                .thenReturn(true);
        when(voyageAIClient.embedDocument(anyString())).thenReturn("[0.2]");
        when(voyageAIClient.modelName()).thenReturn("voyage-4-lite");

        service().classifyAndEmbed(assetId, "https://example.com/a.jpg");

        verify(assetTagRepository).deleteByMediaAssetIdAndSource(assetId, "ai_generated");
    }

    @Test
    void enrichAsset_geminiFailureDoesNotMarkSearchableAssetFailed() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);
        asset.setFileType(com.dasigconnect.backend.model.entity.MediaFileType.jpeg);
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset));
        when(classificationClient.classifyMedia(any())).thenThrow(new RuntimeException("Gemini down"));

        boolean completed = service().enrichAsset(assetId, "https://example.com/a.jpg");

        assertThat(completed).isFalse();
        verify(mediaAssetRepository, never()).updateStatus(
                eq(assetId), eq(MediaAssetStatus.FAILED.name()));
        verify(mediaAssetRepository, never()).incrementSemanticRevision(assetId);
        verify(mediaProcessingQueueService, never()).semanticMetadataChangedAfterCommit(assetId);
    }

    @Test
    void enrichAsset_successInvalidatesOnlySemanticEmbedding() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);
        asset.setFileType(com.dasigconnect.backend.model.entity.MediaFileType.jpeg);
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset));
        when(classificationClient.classifyMedia(any())).thenReturn(classification());

        assertThat(service().enrichAsset(assetId, "https://example.com/a.jpg")).isTrue();

        verify(mediaAssetRepository).incrementSemanticRevision(assetId);
        verify(mediaProcessingQueueService).semanticMetadataChangedAfterCommit(assetId);
        verify(mediaImageEmbeddingService, never()).generateOrReuse(any(), anyString());
    }

    @Test
    void enrichAsset_existingClaudeMetadata_isRefreshedByGemini() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);
        asset.setFileType(com.dasigconnect.backend.model.entity.MediaFileType.jpeg);
        asset.setAiClassifiedAt(java.time.Instant.now());
        asset.setAiClassificationModel("claude-haiku-4-5-20251001");
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset));
        when(classificationClient.modelName()).thenReturn("gemini-3.8-flash");
        when(classificationClient.classifyMedia(any())).thenReturn(classification());

        assertThat(service().enrichAsset(assetId, "https://example.com/a.jpg")).isTrue();

        verify(classificationClient).classifyMedia(any());
        assertThat(asset.getAiClassificationModel()).isEqualTo("gemini-3.8-flash");
        verify(mediaAssetRepository).incrementSemanticRevision(assetId);
        verify(mediaProcessingQueueService).semanticMetadataChangedAfterCommit(assetId);
    }
}
