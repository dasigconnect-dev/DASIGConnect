package com.dasigconnect.backend.service;

import java.util.UUID;
import org.springframework.stereotype.Service;

/** Completes both Voyage retrieval stages while preserving successful work on retry. */
@Service
public class MediaRetrievalEmbeddingService {

    private final MediaImageEmbeddingService imageEmbeddingService;
    private final MediaSemanticEmbeddingService semanticEmbeddingService;

    public MediaRetrievalEmbeddingService(
            MediaImageEmbeddingService imageEmbeddingService,
            MediaSemanticEmbeddingService semanticEmbeddingService) {
        this.imageEmbeddingService = imageEmbeddingService;
        this.semanticEmbeddingService = semanticEmbeddingService;
    }

    public boolean generateOrReuse(UUID assetId, String storageUrl, boolean image) {
        if (image && !imageEmbeddingService.generateOrReuse(assetId, storageUrl)) return false;
        return semanticEmbeddingService.generateOrReuse(assetId);
    }
}
