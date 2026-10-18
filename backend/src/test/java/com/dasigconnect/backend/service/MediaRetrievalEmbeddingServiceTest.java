package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MediaRetrievalEmbeddingServiceTest {

    @Mock
    private MediaImageEmbeddingService imageEmbeddingService;

    @Mock
    private MediaSemanticEmbeddingService semanticEmbeddingService;

    private MediaRetrievalEmbeddingService service;

    private final UUID assetId = UUID.randomUUID();
    private final String storageUrl = "https://storage.supabase.co/media/test.jpg";

    @BeforeEach
    void setUp() {
        service = new MediaRetrievalEmbeddingService(imageEmbeddingService, semanticEmbeddingService);
    }

    @Test
    void generateOrReuse_imageTypeSuccess_runsBothEmbeddingStages() {
        when(imageEmbeddingService.generateOrReuse(assetId, storageUrl)).thenReturn(true);
        when(semanticEmbeddingService.generateOrReuse(assetId)).thenReturn(true);

        boolean result = service.generateOrReuse(assetId, storageUrl, true);

        assertThat(result).isTrue();
        verify(imageEmbeddingService).generateOrReuse(assetId, storageUrl);
        verify(semanticEmbeddingService).generateOrReuse(assetId);
    }

    @Test
    void generateOrReuse_imageTypeImageStageFails_shortCircuitsAndReturnsFalse() {
        when(imageEmbeddingService.generateOrReuse(assetId, storageUrl)).thenReturn(false);

        boolean result = service.generateOrReuse(assetId, storageUrl, true);

        assertThat(result).isFalse();
        verify(imageEmbeddingService).generateOrReuse(assetId, storageUrl);
        verifyNoInteractions(semanticEmbeddingService);
    }

    @Test
    void generateOrReuse_nonImage_skipsImageStageAndRunsSemanticStage() {
        when(semanticEmbeddingService.generateOrReuse(assetId)).thenReturn(true);

        boolean result = service.generateOrReuse(assetId, "https://storage.supabase.co/media/video.mp4", false);

        assertThat(result).isTrue();
        verifyNoInteractions(imageEmbeddingService);
        verify(semanticEmbeddingService).generateOrReuse(assetId);
    }

    @Test
    void generateOrReuse_nonImageSemanticFails_returnsFalse() {
        when(semanticEmbeddingService.generateOrReuse(assetId)).thenReturn(false);

        boolean result = service.generateOrReuse(assetId, "https://storage.supabase.co/media/video.mp4", false);

        assertThat(result).isFalse();
        verifyNoInteractions(imageEmbeddingService);
        verify(semanticEmbeddingService).generateOrReuse(assetId);
    }
}
