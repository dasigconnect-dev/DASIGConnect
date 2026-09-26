package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.external.ClaudeVisionClient;
import com.dasigconnect.backend.external.VoyageAIClient;
import com.dasigconnect.backend.model.entity.MediaAssetEmbeddingType;
import com.dasigconnect.backend.repository.MediaAssetEmbeddingRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MediaImageEmbeddingServiceTest {

    @Mock private MediaAssetEmbeddingRepository embeddingRepository;
    @Mock private ClaudeVisionClient imagePreparation;
    @Mock private VoyageAIClient voyageAIClient;
    @Mock private MediaAiTelemetryService telemetry;

    private MediaImageEmbeddingService service() {
        MediaImageEmbeddingService service =
                new MediaImageEmbeddingService(embeddingRepository, imagePreparation, voyageAIClient);
        ReflectionTestUtils.setField(service, "mediaAiTelemetry", telemetry);
        return service;
    }

    @Test
    void generateOrReuse_currentEmbeddingExists_skipsImageAndProviderCalls() {
        UUID assetId = UUID.randomUUID();
        String model = "voyage-multimodal-3.5";
        when(voyageAIClient.multimodalModelName()).thenReturn(model);
        when(embeddingRepository.existsCurrentEmbedding(
                assetId, MediaAssetEmbeddingType.IMAGE, model)).thenReturn(true);

        assertThat(service().generateOrReuse(assetId, "https://example.com/image.jpg")).isTrue();

        verify(imagePreparation, never()).prepareImageForEmbedding(anyString());
        verify(imagePreparation, never()).classifyMedia(any());
        verify(voyageAIClient, never()).embedImageDocument(any(), anyString());
        verify(voyageAIClient, never()).embedDocument(anyString());
        verify(embeddingRepository, never()).upsert(
                any(), any(MediaAssetEmbeddingType.class), anyString(), anyString());
        verify(telemetry).record(eq("VOYAGE_IMAGE_EMBEDDING"), any(Long.class), eq("REUSED"),
                eq(assetId), eq(null), eq(1), eq(0), eq(1));
    }

    @Test
    void generateOrReuse_missingEmbedding_createsImageEmbeddingOnly() {
        UUID assetId = UUID.randomUUID();
        String model = "voyage-multimodal-3.5";
        when(voyageAIClient.multimodalModelName()).thenReturn(model);
        when(imagePreparation.prepareImageForEmbedding("https://example.com/image.png"))
                .thenReturn(new ClaudeVisionClient.PreparedImage(new byte[]{1, 2, 3}, "image/png"));
        when(voyageAIClient.embedImageDocument(any(), anyString())).thenReturn("[0.1]");

        assertThat(service().generateOrReuse(assetId, "https://example.com/image.png")).isTrue();

        verify(voyageAIClient).embedImageDocument(any(), eq("image/png"));
        verify(embeddingRepository).upsert(
                assetId, MediaAssetEmbeddingType.IMAGE, "[0.1]", model);
        verify(imagePreparation, never()).classifyMedia(any());
        verify(voyageAIClient, never()).embedDocument(anyString());
    }

    @Test
    void generateOrReuse_imagePreparationFails_returnsFalseWithoutProviderCall() {
        UUID assetId = UUID.randomUUID();
        when(voyageAIClient.multimodalModelName()).thenReturn("voyage-multimodal-3.5");
        when(imagePreparation.prepareImageForEmbedding(anyString()))
                .thenThrow(new RuntimeException("fetch failed"));

        assertThat(service().generateOrReuse(assetId, "https://example.com/image.jpg")).isFalse();

        verify(voyageAIClient, never()).embedImageDocument(any(), anyString());
        verify(embeddingRepository, never()).upsert(
                any(), any(MediaAssetEmbeddingType.class), anyString(), anyString());
    }

    @Test
    void generateOrReuse_providerFails_returnsFalseWithoutPersistence() {
        UUID assetId = UUID.randomUUID();
        when(voyageAIClient.multimodalModelName()).thenReturn("voyage-multimodal-3.5");
        when(imagePreparation.prepareImageForEmbedding(anyString()))
                .thenReturn(new ClaudeVisionClient.PreparedImage(new byte[]{1}, "image/jpeg"));
        when(voyageAIClient.embedImageDocument(any(), anyString()))
                .thenThrow(new RuntimeException("provider unavailable"));

        assertThat(service().generateOrReuse(assetId, "https://example.com/image.jpg")).isFalse();

        verify(embeddingRepository, never()).upsert(
                any(), any(MediaAssetEmbeddingType.class), anyString(), anyString());
    }

    @Test
    void generateOrReuse_persistenceFails_returnsFalse() {
        UUID assetId = UUID.randomUUID();
        String model = "voyage-multimodal-3.5";
        when(voyageAIClient.multimodalModelName()).thenReturn(model);
        when(imagePreparation.prepareImageForEmbedding(anyString()))
                .thenReturn(new ClaudeVisionClient.PreparedImage(new byte[]{1}, "image/jpeg"));
        when(voyageAIClient.embedImageDocument(any(), anyString())).thenReturn("[0.1]");
        doThrow(new RuntimeException("database unavailable"))
                .when(embeddingRepository)
                .upsert(assetId, MediaAssetEmbeddingType.IMAGE, "[0.1]", model);

        assertThat(service().generateOrReuse(assetId, "https://example.com/image.jpg")).isFalse();

        verify(embeddingRepository).upsert(
                assetId, MediaAssetEmbeddingType.IMAGE, "[0.1]", model);
    }
}
