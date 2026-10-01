package com.dasigconnect.backend.service;

import com.dasigconnect.backend.external.VoyageAIClient;
import com.dasigconnect.backend.model.entity.AssetTag;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetEmbeddingType;
import com.dasigconnect.backend.repository.AssetTagRepository;
import com.dasigconnect.backend.repository.MediaAssetEmbeddingRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import java.util.Collection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Generates a Voyage semantic vector from user metadata plus persisted Gemini observations. */
@Service
public class MediaSemanticEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(MediaSemanticEmbeddingService.class);

    private final MediaAssetRepository assetRepository;
    private final AssetTagRepository tagRepository;
    private final MediaAssetEmbeddingRepository embeddingRepository;
    private final VoyageAIClient voyageAIClient;
    private final MediaSearchCacheService mediaSearchCache;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private MediaAiTelemetryService mediaAiTelemetry;

    public MediaSemanticEmbeddingService(
            MediaAssetRepository assetRepository,
            AssetTagRepository tagRepository,
            MediaAssetEmbeddingRepository embeddingRepository,
            VoyageAIClient voyageAIClient,
            MediaSearchCacheService mediaSearchCache) {
        this.assetRepository = assetRepository;
        this.tagRepository = tagRepository;
        this.embeddingRepository = embeddingRepository;
        this.voyageAIClient = voyageAIClient;
        this.mediaSearchCache = mediaSearchCache;
    }

    public boolean generateOrReuse(UUID assetId) {
        long startedAt = System.nanoTime();
        String model = voyageAIClient.modelName();
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
        String inputHash = semanticInputHash(input);
        long semanticRevision = asset.getSemanticRevision();
        String processingVersion = MediaProcessingQueueService.SEMANTIC_EMBEDDING_VERSION;

        if (embeddingRepository.existsCurrentVersionedEmbedding(
                assetId, MediaAssetEmbeddingType.SEMANTIC.dbValue(), model,
                inputHash, semanticRevision, processingVersion)) {
            MediaAsset currentAsset = assetRepository.findActiveById(assetId).orElse(null);
            if (currentAsset != null && !model.equals(currentAsset.getEmbeddingModel())) {
                String storedEmbedding = embeddingRepository
                        .findEmbedding(assetId, MediaAssetEmbeddingType.SEMANTIC)
                        .orElse(null);
                if (storedEmbedding == null) return false;
                try {
                    if (assetRepository.updateEmbeddingIfSemanticRevision(
                            assetId, storedEmbedding, model, semanticRevision) == 0) {
                        record(assetId, startedAt, "FAILURE", 0, 1);
                        return false;
                    }
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

        String embedding;
        try {
            embedding = voyageAIClient.embedDocument(input);
        } catch (Exception error) {
            log.warn("Voyage AI semantic embedding failed for asset {}: {}", assetId, error.getMessage());
            record(assetId, startedAt, "FAILURE", 1, 0);
            return false;
        }

        try {
            int stored = embeddingRepository.upsertSemanticIfCurrent(
                    assetId, embedding, model, inputHash, processingVersion, semanticRevision);
            if (stored == 0) {
                log.info("Discarded stale semantic embedding result for asset {} revision {}",
                        assetId, semanticRevision);
                record(assetId, startedAt, "FAILURE", 1, 0);
                return false;
            }
            if (assetRepository.updateEmbeddingIfSemanticRevision(
                    assetId, embedding, model, semanticRevision) == 0) {
                log.info("Discarded stale legacy semantic embedding result for asset {} revision {}",
                        assetId, semanticRevision);
                record(assetId, startedAt, "FAILURE", 1, 0);
                return false;
            }
            mediaSearchCache.invalidateAll();
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
        append(text, "category", asset.getAiCategory());
        append(text, "description", asset.getAiDescription());
        append(text, "asset type", asset.getAssetType());
        appendAll(text, "visible objects", asset.getVisibleObjects());
        appendAll(text, "specific subjects", asset.getSpecificSubjects());
        appendAll(text, "scenes", asset.getObservedScenes());
        appendAll(text, "activities", asset.getObservedActivities());
        append(text, "people", asset.getPeopleCountRange());
        appendAll(text, "equipment", asset.getEquipmentSignals());
        appendAll(text, "recognition signals", asset.getRecognitionSignals());
        appendAll(text, "visible text", asset.getOcrText());
        appendAll(text, "visible dates", asset.getVisibleDates());
        appendAll(text, "visual style", asset.getVisualStyle());
        appendAll(text, "dominant colors", asset.getDominantColors());
        appendAll(text, "use cases", asset.getPossibleUseCases());
        appendAll(text, "ai tags", asset.getAiTags());
        append(text, "temporal", asset.getTemporalClassification());
        return text.toString().trim();
    }

    static String semanticInputHash(String input) {
        try {
            String normalized = input == null ? "" : input.trim()
                    .replaceAll("\\s+", " ")
                    .toLowerCase(Locale.ROOT);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Could not hash semantic embedding input", error);
        }
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
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        if (!cleaned.isEmpty()) append(target, label, String.join(", ", cleaned));
    }

    private static void appendAll(StringBuilder target, String label, String[] values) {
        if (values != null) appendAll(target, label, List.of(values));
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
