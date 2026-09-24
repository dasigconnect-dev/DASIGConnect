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
    private final ScheduledJobHealthService health = mock(ScheduledJobHealthService.class);
    private final SubmissionMediaContextService context = mock(SubmissionMediaContextService.class);
    private final SubmissionMediaAssetRepository submissionMedia = mock(SubmissionMediaAssetRepository.class);

    private MediaProcessingWorker worker(boolean configured) {
        return new MediaProcessingWorker(
                queue, assets, classification, health, context, submissionMedia, 2,
                configured ? "anthropic" : "", configured ? "voyage" : "");
    }

    @Test
    void processBatch_successMarksAssetVersionAndCompletesLease() {
        UUID assetId = UUID.randomUUID();
        MediaProcessingJob job = mock(MediaProcessingJob.class);
        when(job.getAssetId()).thenReturn(assetId);
        when(job.getProcessingVersion()).thenReturn("media-ai-v1");
        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);
        asset.setFileType(MediaFileType.jpeg);
        asset.setStorageUrl("https://example.com/image.jpg");
        when(queue.claimBatch(anyString(), org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(true))).thenReturn(List.of(job));
        when(assets.findActiveById(assetId)).thenReturn(Optional.of(asset));
        when(classification.processAsset(assetId, asset.getStorageUrl(), "media-ai-v1")).thenReturn(true);

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
        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);
        asset.setFileType(MediaFileType.jpeg);
        asset.setStorageUrl("https://example.com/image.jpg");
        when(queue.claimBatch(anyString(), org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(true))).thenReturn(List.of(job));
        when(assets.findActiveById(assetId)).thenReturn(Optional.of(asset));
        when(job.getProcessingVersion()).thenReturn("media-ai-v1");
        when(classification.processAsset(assetId, asset.getStorageUrl(), "media-ai-v1")).thenReturn(false);

        worker(true).processBatch();

        verify(queue).fail(org.mockito.ArgumentMatchers.eq(job), anyString(),
                org.mockito.ArgumentMatchers.any());
        verify(queue, never()).complete(org.mockito.ArgumentMatchers.eq(job), anyString());
    }

    @Test
    void processBatch_withoutProviderConfigurationClaimsOnlyContextJobs() {
        worker(false).processBatch();

        verify(queue).claimBatch(anyString(), org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(false));
    }

    @Test
    void processBatch_contextJobRebuildsWithoutCallingProviders() {
        UUID submissionId = UUID.randomUUID();
        MediaProcessingJob job = mock(MediaProcessingJob.class);
        when(job.getJobType()).thenReturn(MediaProcessingJobType.BUILD_SUBMISSION_CONTEXT);
        when(job.getSubmissionId()).thenReturn(submissionId);
        when(queue.claimBatch(anyString(), org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(false))).thenReturn(List.of(job));

        worker(false).processBatch();

        verify(context).rebuild(submissionId);
        verify(queue).complete(org.mockito.ArgumentMatchers.eq(job), anyString());
        verify(classification, never()).processAsset(
                org.mockito.ArgumentMatchers.any(), anyString(), org.mockito.ArgumentMatchers.any());
    }
}
