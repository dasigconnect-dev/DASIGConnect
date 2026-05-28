package com.dasigconnect.backend.service;

import com.dasigconnect.backend.external.ClaudeVisionClient;
import com.dasigconnect.backend.external.VoyageAIClient;
import com.dasigconnect.backend.model.dto.ai.MediaClassificationDto;
import com.dasigconnect.backend.model.entity.AssetTag;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.repository.AssetTagRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Classifies media assets using Claude Vision and generates Voyage AI embeddings (UC-3.3).
 *
 * Transaction discipline: each repository call runs in its own short implicit transaction.
 * No DB connection is held across external API calls (Claude or Voyage AI).
 * All methods are @Async — failures are logged and swallowed so upload is never blocked.
 */
@Service
public class AIClassificationService {

    private static final Logger log = LoggerFactory.getLogger(AIClassificationService.class);
    private static final int MAX_AI_TAGS_TO_STORE = 15;
    private static final DateTimeFormatter UPLOAD_MONTH_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);

    private final MediaAssetRepository mediaAssetRepository;
    private final AssetTagRepository assetTagRepository;
    private final ClaudeVisionClient claudeVisionClient;
    private final VoyageAIClient voyageAIClient;

    public AIClassificationService(MediaAssetRepository mediaAssetRepository,
                                   AssetTagRepository assetTagRepository,
                                   ClaudeVisionClient claudeVisionClient,
                                   VoyageAIClient voyageAIClient) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.assetTagRepository = assetTagRepository;
        this.claudeVisionClient = claudeVisionClient;
        this.voyageAIClient = voyageAIClient;
    }

    /**
     * Classifies a media asset and generates its embedding asynchronously.
     * Called immediately after upload — never blocks the upload response.
     *
     * @param assetId    UUID of the saved MediaAsset
     * @param storageUrl Supabase Storage URL used as image input for Claude
     */
    @Async
    public void classifyAndEmbed(UUID assetId, String storageUrl) {
        // Step 1: Classify via Claude Vision (external HTTP, no DB connection held)
        MediaClassificationDto result;
        try {
            result = claudeVisionClient.classifyMedia(List.of(storageUrl));
        } catch (Exception e) {
            log.warn("AI classification failed for asset {}: {}", assetId, e.getMessage());
            generateMetadataOnlyEmbedding(assetId);
            return;
        }

        // Step 2: Persist classification fields (short write transaction per repo method)
        try {
            mediaAssetRepository.updateClassification(
                    assetId, result.category(),
                    result.confidence(), result.description(),
                    claudeVisionClient.modelName());
        } catch (Exception e) {
            log.warn("Failed to persist classification for asset {}: {}", assetId, e.getMessage());
            return;
        }

        persistSuggestedTags(assetId, result.suggestedTags());

        // Step 3: Build richer embedding text from stable asset metadata + image scan.
        String embeddingText = mediaAssetRepository.findActiveById(assetId)
                .map(asset -> buildEmbeddingText(asset, result, List.of()))
                .orElseGet(() -> buildEmbeddingText(result));

        // Step 4: Generate embedding (external HTTP, no DB connection held)
        generateAndStoreEmbedding(assetId, embeddingText);
    }

    /**
     * Generates an embedding for a pre-classified asset.
     * Called by EmbeddingReconciliationJob for assets that were classified but not yet embedded.
     */
    public void generateAndStoreEmbedding(UUID assetId, String embeddingText) {
        String embeddingJson;
        try {
            embeddingJson = voyageAIClient.embed(embeddingText);
        } catch (Exception e) {
            log.warn("Voyage AI embedding failed for asset {}: {}", assetId, e.getMessage());
            return;
        }

        try {
            mediaAssetRepository.updateEmbedding(assetId, embeddingJson, voyageAIClient.modelName());
            log.info("Embedding stored for asset {}", assetId);
        } catch (Exception e) {
            log.warn("Failed to store embedding for asset {}: {}", assetId, e.getMessage());
        }
    }

    private void generateMetadataOnlyEmbedding(UUID assetId) {
        try {
            List<String> tagLabels = assetTagRepository
                    .findByMediaAssetIdOrderByCreatedAtAsc(assetId)
                    .stream()
                    .map(AssetTag::getLabel)
                    .toList();
            String embeddingText = mediaAssetRepository.findActiveById(assetId)
                    .map(asset -> buildEmbeddingText(asset, tagLabels))
                    .orElse("");
            if (embeddingText.isBlank()) {
                log.warn("Metadata-only embedding skipped for asset {} because no asset metadata was found.", assetId);
                return;
            }
            generateAndStoreEmbedding(assetId, embeddingText);
        } catch (Exception e) {
            log.warn("Metadata-only embedding failed for asset {}: {}", assetId, e.getMessage());
        }
    }

    private List<String> persistSuggestedTags(UUID assetId, Collection<String> suggestedTags) {
        if (suggestedTags == null || suggestedTags.isEmpty()) return List.of();

        MediaAsset asset = mediaAssetRepository.findActiveById(assetId).orElse(null);
        if (asset == null) return List.of();

        List<String> normalized = normalizeTags(suggestedTags);
        if (normalized.isEmpty()) return List.of();

        try {
            List<AssetTag> tagsToSave = normalized.stream()
                    .filter(label -> !assetTagRepository.existsByMediaAssetIdAndLabel(assetId, label))
                    .map(label -> {
                        AssetTag tag = new AssetTag();
                        tag.setMediaAsset(asset);
                        tag.setLabel(label);
                        tag.setSource("ai_generated");
                        return tag;
                    })
                    .toList();
            if (!tagsToSave.isEmpty()) {
                assetTagRepository.saveAll(tagsToSave);
            }
        } catch (Exception e) {
            log.warn("Failed to persist AI tags for asset {}: {}", assetId, e.getMessage());
        }

        return normalized;
    }

    public static String buildEmbeddingText(MediaClassificationDto result) {
        StringBuilder sb = new StringBuilder();
        append(sb, "category", result.category());
        append(sb, "description", result.description());
        appendAll(sb, "ai_tags", result.suggestedTags());
        return sb.toString().trim();
    }

    public static String buildEmbeddingText(MediaAsset asset, MediaClassificationDto result, Collection<String> manualTags) {
        StringBuilder sb = new StringBuilder();
        append(sb, "asset_code", asset.getAssetCode());
        append(sb, "filename", normalizeFileName(asset.getFileName()));
        append(sb, "media_type", asset.getFileType() != null ? asset.getFileType().name() : null);
        append(sb, "uploaded_month", asset.getCreatedAt() != null ? UPLOAD_MONTH_FORMATTER.format(asset.getCreatedAt()) : null);
        append(sb, "category", firstNonBlank(result.category(), asset.getAiCategory()));
        append(sb, "description", firstNonBlank(result.description(), asset.getAiDescription()));
        appendAll(sb, "ai_tags", result.suggestedTags());
        appendAll(sb, "asset_tags", manualTags);
        return sb.toString().trim();
    }

    public static String buildEmbeddingText(MediaAsset asset, Collection<String> manualTags) {
        MediaClassificationDto dto = new MediaClassificationDto(
                asset.getAiCategory(),
                asset.getAiConfidence() != null ? asset.getAiConfidence().doubleValue() : 0.5,
                asset.getAiDescription(),
                List.of()
        );
        return buildEmbeddingText(asset, dto, manualTags);
    }

    private static void append(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) return;
        sb.append(label).append(": ").append(value.trim()).append(". ");
    }

    private static void appendAll(StringBuilder sb, String label, Collection<String> values) {
        if (values == null || values.isEmpty()) return;
        List<String> cleaned = values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (!cleaned.isEmpty()) {
            sb.append(label).append(": ").append(String.join(", ", cleaned)).append(". ");
        }
    }

    private static String firstNonBlank(String first, String fallback) {
        return first != null && !first.isBlank() ? first : fallback;
    }

    private static String normalizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return null;
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        return base.replace('_', ' ').replace('-', ' ').trim();
    }

    private static List<String> normalizeTags(Collection<String> values) {
        Set<String> unique = new LinkedHashSet<>();
        if (values == null) return List.of();
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            String tag = value.trim().replaceAll("\\s+", " ");
            if (tag.length() > 50) {
                tag = tag.substring(0, 50).trim();
            }
            String key = tag.toLowerCase(Locale.ROOT);
            boolean exists = unique.stream().anyMatch(existing -> existing.toLowerCase(Locale.ROOT).equals(key));
            if (!exists) unique.add(tag);
            if (unique.size() >= MAX_AI_TAGS_TO_STORE) break;
        }
        return List.copyOf(unique);
    }
}
