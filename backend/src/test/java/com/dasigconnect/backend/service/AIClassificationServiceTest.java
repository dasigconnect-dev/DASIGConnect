package com.dasigconnect.backend.service;

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
    @Mock private VoyageAIClient voyageAIClient;

    private AIClassificationService service() {
        return new AIClassificationService(
                mediaAssetRepository, mediaAssetEmbeddingRepository, assetTagRepository,
                claudeVisionClient, voyageAIClient);
    }

    private static MediaClassificationDto classification() {
        return new MediaClassificationDto("Event", 0.9, "A campus event.", List.of("students"));
    }

    @Test
    void classifyAndEmbed_bothEmbeddingsSucceed_marksReady() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);

        when(claudeVisionClient.classifyMedia(any())).thenReturn(classification());
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset));
        when(claudeVisionClient.prepareImageForEmbedding(anyString()))
                .thenReturn(new ClaudeVisionClient.PreparedImage(new byte[]{1, 2, 3}, "image/jpeg"));
        when(voyageAIClient.embedImageDocument(any(), anyString())).thenReturn("[0.1]");
        when(voyageAIClient.multimodalModelName()).thenReturn("voyage-multimodal");
        when(voyageAIClient.embedDocument(anyString())).thenReturn("[0.2]");
        when(voyageAIClient.modelName()).thenReturn("voyage-4-lite");

        service().classifyAndEmbed(assetId, "https://example.com/a.jpg");

        verify(mediaAssetRepository).updateStatus(assetId, MediaAssetStatus.READY.name());
        verify(mediaAssetRepository, never()).updateStatus(assetId, MediaAssetStatus.FAILED.name());
    }

    @Test
    void classifyAndEmbed_claudeFails_marksFailedAndNeverReady() {
        UUID assetId = UUID.randomUUID();
        when(claudeVisionClient.classifyMedia(any())).thenThrow(new RuntimeException("Claude down"));

        service().classifyAndEmbed(assetId, "https://example.com/a.jpg");

        verify(mediaAssetRepository).updateStatus(assetId, MediaAssetStatus.FAILED.name());
        verify(mediaAssetRepository, never()).updateStatus(eq(assetId), eq(MediaAssetStatus.READY.name()));
    }

    @Test
    void classifyAndEmbed_semanticEmbeddingFails_marksFailedAndNeverReady() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);

        when(claudeVisionClient.classifyMedia(any())).thenReturn(classification());
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset));
        when(claudeVisionClient.prepareImageForEmbedding(anyString()))
                .thenReturn(new ClaudeVisionClient.PreparedImage(new byte[]{1, 2, 3}, "image/jpeg"));
        when(voyageAIClient.embedImageDocument(any(), anyString())).thenReturn("[0.1]");
        when(voyageAIClient.multimodalModelName()).thenReturn("voyage-multimodal");
        when(voyageAIClient.embedDocument(anyString())).thenThrow(new RuntimeException("Voyage down"));

        service().classifyAndEmbed(assetId, "https://example.com/a.jpg");

        verify(mediaAssetRepository).updateStatus(assetId, MediaAssetStatus.FAILED.name());
        verify(mediaAssetRepository, never()).updateStatus(eq(assetId), eq(MediaAssetStatus.READY.name()));
    }
}
