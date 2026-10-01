package com.dasigconnect.backend.schedule;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.MediaProcessingJob;
import com.dasigconnect.backend.model.entity.MediaProcessingJobType;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionMediaAssetRepository;
import com.dasigconnect.backend.service.AIClassificationService;
import com.dasigconnect.backend.service.MediaProcessingQueueService;
import com.dasigconnect.backend.service.MediaImageEmbeddingService;
import com.dasigconnect.backend.service.MediaRetrievalEmbeddingService;
import com.dasigconnect.backend.service.MediaSemanticEmbeddingService;
import com.dasigconnect.backend.service.ScheduledJobHealthService;
import com.dasigconnect.backend.service.SubmissionMediaContextService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MediaProcessingWorkerTest {

    private final MediaProcessingQueueService queue = mock(MediaProcessingQueueService.class);
    private final MediaAssetRepository assets = mock(MediaAssetRepository.class);
    private final AIClassificationService classification = mock(AIClassificationService.class);
    private final MediaImageEmbeddingService imageEmbedding = mock(MediaImageEmbeddingService.class);
    private final MediaRetrievalEmbeddingService retrievalEmbedding = mock(MediaRetrievalEmbeddingService.class);
    private final MediaSemanticEmbeddingService semanticEmbedding = mock(MediaSemanticEmbeddingService.class);
    private final ScheduledJobHealthService health = mock(ScheduledJobHealthService.class);
    private final SubmissionMediaContextService context = mock(SubmissionMediaContextService.class);
    private final SubmissionMediaAssetRepository submissionMedia = mock(SubmissionMediaAssetRepository.class);

    private MediaProcessingWorker worker(boolean configured) {
        return worker(configured ? "gemini" : "", configured ? "voyage" : "");
    }

    private MediaProcessingWorker worker(String geminiApiKey, String voyageApiKey) {
        return new MediaProcessingWorker(
                queue, assets, classification, imageEmbedding, retrievalEmbedding, semanticEmbedding,
                health, context, submissionMedia, 2,
                geminiApiKey, voyageApiKey);
    }

    @Test
    void processBatch_successMarksAssetVersionAndCompletesLease() {
        UUID assetId = UUID.randomUUID();
        MediaProcessingJob job = mock(MediaProcessingJob.class);
        when(job.getAssetId()).thenReturn(assetId);
        when(job.getJobType()).thenReturn(MediaProcessingJobType.RETRIEVAL_EMBEDDINGS);
        when(job.getProcessingVersion()).thenReturn("media-ai-v1");
        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);
        asset.setFileType(MediaFileType.jpeg);
        asset.setStorageUrl("https://example.com/image.jpg");
        when(queue.claimBatch(anyString(), org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.eq(true))).thenReturn(List.of(job));
        when(assets.findActiveById(assetId)).thenReturn(Optional.of(asset));
        when(retrievalEmbedding.generateOrReuse(assetId, asset.getStorageUrl(), true)).thenReturn(true);

        worker(true).processBatch();

        verify(assets).markProcessingReady(assetId, "media-ai-v1");
        verify(queue).complete(org.mockito.ArgumentMatchers.eq(job), anyString());
        verify(queue, never()).fail(org.mockito.ArgumentMatchers.eq(job), anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void processBatch_providerFailureReleasesJobForRetry() {
        UUID assetId = UUID.randomUUID();
        MediaProcessingJob job = mock(MediaProcessingJob.class);
        when(job.getAssetId()).thenReturn(assetId);
        when(job.getJobType()).thenReturn(MediaProcessingJobType.RETRIEVAL_EMBEDDINGS);
        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);
        asset.setFileType(MediaFileType.jpeg);
        asset.setStorageUrl("https://example.com/image.jpg");
        when(queue.claimBatch(anyString(), org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.eq(true))).thenReturn(List.of(job));
        when(assets.findActiveById(assetId)).thenReturn(Optional.of(asset));
        when(job.getProcessingVersion()).thenReturn("media-ai-v1");
        when(retrievalEmbedding.generateOrReuse(assetId, asset.getStorageUrl(), true)).thenReturn(false);

        worker(true).processBatch();

        verify(queue).fail(org.mockito.ArgumentMatchers.eq(job), anyString(),
                org.mockito.ArgumentMatchers.any());
        verify(queue, never()).complete(org.mockito.ArgumentMatchers.eq(job), anyString());
    }

    @Test
    void processBatch_withoutProviderConfigurationClaimsOnlyContextJobs() {
        worker(false).processBatch();

        verify(queue).claimBatch(anyString(), org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.eq(false));
    }

    @Test
    void processBatch_geminiOnlyDoesNotClaimImageEmbeddingJobs() {
        worker("gemini", "").processBatch();

        verify(queue).claimBatch(anyString(), org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.eq(false));
    }

    @Test
    void processBatch_voyageConfigurationEnablesImageEmbeddingJobs() {
        worker("", "voyage").processBatch();

        verify(queue).claimBatch(anyString(), org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.eq(true));
    }

    @Test
    void processBatch_contextJobRebuildsWithoutCallingProviders() {
        UUID submissionId = UUID.randomUUID();
        MediaProcessingJob job = mock(MediaProcessingJob.class);
        when(job.getJobType()).thenReturn(MediaProcessingJobType.BUILD_SUBMISSION_CONTEXT);
        when(job.getSubmissionId()).thenReturn(submissionId);
        when(queue.claimBatch(anyString(), org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.eq(false))).thenReturn(List.of(job));

        worker(false).processBatch();

        verify(context).rebuild(submissionId);
        verify(queue).complete(org.mockito.ArgumentMatchers.eq(job), anyString());
        verify(classification, never()).processAsset(
                org.mockito.ArgumentMatchers.any(), anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void processBatch_retrievalCompletionQueuesOptionalEnrichmentAfterReady() {
        UUID assetId = UUID.randomUUID();
        MediaProcessingJob job = mock(MediaProcessingJob.class);
        when(job.getJobType()).thenReturn(MediaProcessingJobType.RETRIEVAL_EMBEDDINGS);
        when(job.getAssetId()).thenReturn(assetId);
        when(job.getProcessingVersion()).thenReturn("media-retrieval-v1");
        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);
        asset.setFileType(MediaFileType.jpeg);
        asset.setStorageUrl("https://example.com/retrieval.jpg");
        when(queue.claimBatch(anyString(), org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.eq(true)))
                .thenReturn(List.of(job));
        when(assets.findActiveById(assetId)).thenReturn(Optional.of(asset));
        when(retrievalEmbedding.generateOrReuse(assetId, asset.getStorageUrl(), true)).thenReturn(true);

        worker(true).processBatch();

        org.mockito.InOrder readinessOrder = org.mockito.Mockito.inOrder(assets, queue);
        readinessOrder.verify(assets).markProcessingReady(assetId, "media-retrieval-v1");
        readinessOrder.verify(queue).complete(org.mockito.ArgumentMatchers.eq(job), anyString());
        readinessOrder.verify(queue).enqueueEnrichment(assetId);
        verify(classification, never()).enrichAsset(
                org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    void processBatch_enrichmentFailureDoesNotChangeRetrievalReadiness() {
        UUID assetId = UUID.randomUUID();
        MediaProcessingJob job = mock(MediaProcessingJob.class);
        when(job.getJobType()).thenReturn(MediaProcessingJobType.ENRICH_MEDIA);
        when(job.getAssetId()).thenReturn(assetId);
        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);
        asset.setFileType(MediaFileType.jpeg);
        asset.setStorageUrl("https://example.com/enrich.jpg");
        when(queue.claimBatch(anyString(), org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.eq(true)))
                .thenReturn(List.of(job));
        when(assets.findActiveById(assetId)).thenReturn(Optional.of(asset));
        when(classification.enrichAsset(assetId, asset.getStorageUrl())).thenReturn(false);

        worker(true).processBatch();

        verify(queue).fail(org.mockito.ArgumentMatchers.eq(job), anyString(),
                org.mockito.ArgumentMatchers.any());
        verify(assets, never()).markProcessingReady(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
        verify(retrievalEmbedding, never()).generateOrReuse(
                org.mockito.ArgumentMatchers.any(), anyString(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void processBatch_imageOnlyJobStoresVectorWithoutChangingAssetState() {
        UUID assetId = UUID.randomUUID();
        MediaProcessingJob job = mock(MediaProcessingJob.class);
        when(job.getJobType()).thenReturn(MediaProcessingJobType.EMBED_IMAGE_ONLY);
        when(job.getAssetId()).thenReturn(assetId);
        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);
        asset.setFileType(MediaFileType.jpeg);
        asset.setStorageUrl("https://example.com/staged.jpg");
        when(queue.claimBatch(anyString(), org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.eq(true))).thenReturn(List.of(job));
        when(assets.findActiveById(assetId)).thenReturn(Optional.of(asset));
        when(imageEmbedding.generateOrReuse(assetId, asset.getStorageUrl())).thenReturn(true);

        worker(true).processBatch();

        verify(imageEmbedding).generateOrReuse(assetId, asset.getStorageUrl());
        verify(classification, never()).processAsset(
                org.mockito.ArgumentMatchers.any(), anyString(), org.mockito.ArgumentMatchers.any());
        verify(assets, never()).markProcessingReady(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
        verify(submissionMedia, never()).findSubmissionIdsByMediaAssetId(assetId);
        verify(queue).complete(org.mockito.ArgumentMatchers.eq(job), anyString());
        verify(queue, never()).fail(org.mockito.ArgumentMatchers.eq(job), anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void processBatch_imageOnlyFailureUsesExistingRetryPath() {
        UUID assetId = UUID.randomUUID();
        MediaProcessingJob job = mock(MediaProcessingJob.class);
        when(job.getJobType()).thenReturn(MediaProcessingJobType.EMBED_IMAGE_ONLY);
        when(job.getAssetId()).thenReturn(assetId);
        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);
        asset.setFileType(MediaFileType.jpeg);
        asset.setStorageUrl("https://example.com/staged.jpg");
        when(queue.claimBatch(anyString(), org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.eq(true))).thenReturn(List.of(job));
        when(assets.findActiveById(assetId)).thenReturn(Optional.of(asset));
        when(imageEmbedding.generateOrReuse(assetId, asset.getStorageUrl())).thenReturn(false);

        worker(true).processBatch();

        verify(queue).fail(org.mockito.ArgumentMatchers.eq(job), anyString(),
                org.mockito.ArgumentMatchers.any());
        verify(queue, never()).complete(org.mockito.ArgumentMatchers.eq(job), anyString());
        verify(assets, never()).markProcessingReady(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void processBatch_semanticOnlyRefreshesSearchAndSubmissionContexts() {
        UUID assetId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        MediaProcessingJob job = mock(MediaProcessingJob.class);
        when(job.getJobType()).thenReturn(MediaProcessingJobType.EMBED_SEMANTIC_ONLY);
        when(job.getAssetId()).thenReturn(assetId);
        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);
        asset.setFileType(MediaFileType.jpeg);
        when(queue.claimBatch(anyString(), org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.eq(true))).thenReturn(List.of(job));
        when(assets.findActiveById(assetId)).thenReturn(Optional.of(asset));
        when(semanticEmbedding.generateOrReuse(assetId)).thenReturn(true);
        when(submissionMedia.findSubmissionIdsByMediaAssetId(assetId))
                .thenReturn(List.of(submissionId));

        worker("", "voyage").processBatch();

        verify(semanticEmbedding).generateOrReuse(assetId);
        verify(queue).enqueueSubmissionContext(submissionId);
        verify(queue).complete(org.mockito.ArgumentMatchers.eq(job), anyString());
        verify(imageEmbedding, never()).generateOrReuse(
                org.mockito.ArgumentMatchers.any(), anyString());
        verify(retrievalEmbedding, never()).generateOrReuse(
                org.mockito.ArgumentMatchers.any(), anyString(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(classification, never()).enrichAsset(
                org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    void processBatch_imageOnlyJobForVideoCompletesWithoutProviderCall() {
        UUID assetId = UUID.randomUUID();
        MediaProcessingJob job = mock(MediaProcessingJob.class);
        when(job.getJobType()).thenReturn(MediaProcessingJobType.EMBED_IMAGE_ONLY);
        when(job.getAssetId()).thenReturn(assetId);
        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);
        asset.setFileType(MediaFileType.mp4);
        when(queue.claimBatch(anyString(), org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.eq(true))).thenReturn(List.of(job));
        when(assets.findActiveById(assetId)).thenReturn(Optional.of(asset));

        worker(true).processBatch();

        verify(imageEmbedding, never()).generateOrReuse(
                org.mockito.ArgumentMatchers.any(), anyString());
        verify(queue).complete(org.mockito.ArgumentMatchers.eq(job), anyString());
        verify(queue, never()).fail(org.mockito.ArgumentMatchers.eq(job), anyString(),
                org.mockito.ArgumentMatchers.any());
    }
}
