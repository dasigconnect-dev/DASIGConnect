package com.dasigconnect.backend.service;

import com.dasigconnect.backend.external.VoyageAIClient;
import com.dasigconnect.backend.model.entity.AssetTag;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetEmbeddingType;
import com.dasigconnect.backend.repository.AssetTagRepository;
import com.dasigconnect.backend.repository.MediaAssetEmbeddingRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Generates the trusted-metadata semantic vector independently of Claude. */
@Service
public class MediaSemanticEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(MediaSemanticEmbeddingService.class);

    private final MediaAssetRepository assetRepository;
    private final AssetTagRepository tagRepository;
    private final MediaAssetEmbeddingRepository embeddingRepository;
    private final VoyageAIClient voyageAIClient;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private MediaAiTelemetryService mediaAiTelemetry;

    public MediaSemanticEmbeddingService(
            MediaAssetRepository assetRepository,
            AssetTagRepository tagRepository,
            MediaAssetEmbeddingRepository embeddingRepository,
            VoyageAIClient voyageAIClient) {
        this.assetRepository = assetRepository;
        this.tagRepository = tagRepository;
        this.embeddingRepository = embeddingRepository;
        this.voyageAIClient = voyageAIClient;
    }

    public boolean generateOrReuse(UUID assetId) {
        long startedAt = System.nanoTime();
        String model = voyageAIClient.modelName();
        if (embeddingRepository.existsCurrentEmbedding(
                assetId, MediaAssetEmbeddingType.SEMANTIC, model)) {
            MediaAsset asset = assetRepository.findActiveById(assetId).orElse(null);
            if (asset != null && !model.equals(asset.getEmbeddingModel())) {
                String storedEmbedding = embeddingRepository
                        .findEmbedding(assetId, MediaAssetEmbeddingType.SEMANTIC)
                        .orElse(null);
                if (storedEmbedding == null) return false;
                try {
                    assetRepository.updateEmbedding(assetId, storedEmbedding, model);
                } catch (Exception error) {
                    log.warn("Failed to repair legacy semantic embedding for asset {}: {}",
                            assetId, error.getMessage());
                    record(assetId, startedAt, "FAILURE", 0, 1);
                    return false;
                }
            }
            record(assetId, startedAt, "REUSED", 0, 1);
            return true;
        }

        MediaAsset asset = assetRepository.findActiveWithAlbumById(assetId).orElse(null);
        if (asset == null) return false;
        List<String> manualTags = tagRepository.findByMediaAssetIdOrderByCreatedAtAsc(assetId)
                .stream()
                .filter(tag -> tag.getSource() == null || "manual".equalsIgnoreCase(tag.getSource()))
                .map(AssetTag::getLabel)
                .toList();
        String input = buildTrustedMetadataText(asset, manualTags);
        if (input.isBlank()) {
            record(assetId, startedAt, "FAILURE", 0, 0);
            return false;
        }

        String embedding;
        try {
            embedding = voyageAIClient.embedDocument(input);
        } catch (Exception error) {
            log.warn("Voyage AI semantic embedding failed for asset {}: {}", assetId, error.getMessage());
            record(assetId, startedAt, "FAILURE", 1, 0);
            return false;
        }

        try {
            embeddingRepository.upsert(assetId, MediaAssetEmbeddingType.SEMANTIC, embedding, model);
            assetRepository.updateEmbedding(assetId, embedding, model);
            record(assetId, startedAt, "SUCCESS", 1, 0);
            return true;
        } catch (Exception error) {
            log.warn("Failed to store semantic embedding for asset {}: {}", assetId, error.getMessage());
            record(assetId, startedAt, "FAILURE", 1, 0);
            return false;
        }
    }

    static String buildTrustedMetadataText(MediaAsset asset, Collection<String> manualTags) {
        StringBuilder text = new StringBuilder();
        append(text, "title", asset.getDisplayTitle());
        append(text, "filename", cleanFileName(asset.getFileName()));
        if (asset.getMediaAlbum() != null) append(text, "album", asset.getMediaAlbum().getName());
        appendAll(text, "tags", manualTags);
        append(text, "format", asset.getAssetType());
        append(text, "temporal", asset.getTemporalClassification());
        return text.toString().trim();
    }

    private static String cleanFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return null;
        int extension = fileName.lastIndexOf('.');
        String base = extension > 0 ? fileName.substring(0, extension) : fileName;
        return base.replace('_', ' ').replace('-', ' ').replaceAll("\\s+", " ").trim();
    }

    private static void append(StringBuilder target, String label, String value) {
        if (value != null && !value.isBlank()) {
            target.append(label).append(": ").append(value.trim()).append(". ");
        }
    }

    private static void appendAll(StringBuilder target, String label, Collection<String> values) {
        if (values == null) return;
        List<String> cleaned = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (!cleaned.isEmpty()) append(target, label, String.join(", ", cleaned));
    }

    private void record(UUID assetId, long startedAt, String outcome,
            int providerCalls, int duplicateCallsAvoided) {
        if (mediaAiTelemetry != null) {
            mediaAiTelemetry.record("VOYAGE_SEMANTIC_EMBEDDING",
                    MediaAiTelemetryService.elapsedMillis(startedAt), outcome,
                    assetId, null, 1, providerCalls, duplicateCallsAvoided);
        }
    }
}
