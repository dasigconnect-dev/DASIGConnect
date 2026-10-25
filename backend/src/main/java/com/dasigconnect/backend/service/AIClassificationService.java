package com.dasigconnect.backend.service;

import com.dasigconnect.backend.external.MediaClassificationClient;
import com.dasigconnect.backend.external.VoyageAIClient;
import com.dasigconnect.backend.model.dto.ai.MediaClassificationDto;
import com.dasigconnect.backend.model.entity.AssetTag;
import com.dasigconnect.backend.model.entity.MediaAssetEmbeddingType;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetStatus;
import com.dasigconnect.backend.repository.MediaAssetEmbeddingRepository;
import com.dasigconnect.backend.repository.AssetTagRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Classifies media assets using Gemini and generates Voyage AI embeddings (UC-3.3).
 *
 * Transaction discipline: each repository call runs in its own short implicit transaction.
 * No DB connection is held across external API calls (Gemini or Voyage AI).
 * All methods are @Async — failures are logged and swallowed so upload is never blocked.
 */
@Service
public class AIClassificationService {

    private static final Logger log = LoggerFactory.getLogger(AIClassificationService.class);
    private static final int MAX_AI_TAGS_TO_STORE = 30;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter UPLOAD_MONTH_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);

    private final MediaAssetRepository mediaAssetRepository;
    private final MediaAssetEmbeddingRepository mediaAssetEmbeddingRepository;
    private final AssetTagRepository assetTagRepository;
    private final MediaClassificationClient classificationClient;
    private final VoyageAIClient voyageAIClient;
    private final MediaImageEmbeddingService mediaImageEmbeddingService;
    private final MediaProcessingQueueService mediaProcessingQueueService;

    private MediaAiTelemetryService mediaAiTelemetry;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setMediaAiTelemetry(MediaAiTelemetryService mediaAiTelemetry) {
        this.mediaAiTelemetry = mediaAiTelemetry;
    }

    public AIClassificationService(MediaAssetRepository mediaAssetRepository,
                                   MediaAssetEmbeddingRepository mediaAssetEmbeddingRepository,
                                   AssetTagRepository assetTagRepository,
                                   MediaClassificationClient classificationClient,
                                   VoyageAIClient voyageAIClient,
                                   MediaImageEmbeddingService mediaImageEmbeddingService,
                                   MediaProcessingQueueService mediaProcessingQueueService) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.mediaAssetEmbeddingRepository = mediaAssetEmbeddingRepository;
        this.assetTagRepository = assetTagRepository;
        this.classificationClient = classificationClient;
        this.voyageAIClient = voyageAIClient;
        this.mediaImageEmbeddingService = mediaImageEmbeddingService;
        this.mediaProcessingQueueService = mediaProcessingQueueService;
    }

    /**
     * Runs the durable worker pipeline synchronously. Persisted stages are
     * reused on retry so successful provider calls are not repeated.
     */
    public boolean processAsset(UUID assetId, String storageUrl) {
        return processAsset(assetId, storageUrl, null);
    }

    public boolean processAsset(UUID assetId, String storageUrl, String requiredProcessingVersion) {
        MediaAsset asset = mediaAssetRepository.findActiveById(assetId).orElse(null);
        if (asset == null) return false;

        if (asset.getFileType() != null && !asset.getFileType().isImage()) {
            if (mediaAssetEmbeddingRepository
                    .findEmbedding(assetId, MediaAssetEmbeddingType.SEMANTIC).isPresent()) {
                mediaAssetRepository.updateStatus(assetId, MediaAssetStatus.READY.name());
                return true;
            }
            List<String> tagLabels = assetTagRepository
                    .findByMediaAssetIdOrderByCreatedAtAsc(assetId)
                    .stream()
                    .map(AssetTag::getLabel)
                    .toList();
            String embeddingText = buildEmbeddingText(asset, tagLabels);
            if (embeddingText.isBlank() || !generateAndStoreEmbeddingInternal(assetId, embeddingText)) {
                mediaAssetRepository.updateStatus(assetId, MediaAssetStatus.FAILED.name());
                return false;
            }
            mediaAssetRepository.updateStatus(assetId, MediaAssetStatus.READY.name());
            return true;
        }

        MediaClassificationDto result = null;
        boolean metadataChanged = false;
        boolean requiresStructuredRefresh = requiredProcessingVersion != null
                && !requiredProcessingVersion.equals(asset.getAiProcessingVersion());
        boolean classifiedByCurrentModel = asset.getAiClassifiedAt() != null
                && Objects.equals(classificationClient.modelName(), asset.getAiClassificationModel())
                && MediaMetadataTaxonomy.SCHEMA_VERSION.equals(asset.getMetadataSchemaVersion())
                && MediaMetadataTaxonomy.PROMPT_VERSION.equals(asset.getMetadataPromptVersion());
        if (!classifiedByCurrentModel || requiresStructuredRefresh) {
            long classificationStartedAt = System.nanoTime();
            try {
                result = classifyAndNormalize(storageUrl);
                metadataChanged = persistClassification(assetId, result);
                persistSuggestedTags(assetId, result.suggestedTags());
                recordProviderStage("GEMINI_CLASSIFICATION", assetId,
                        classificationStartedAt, "SUCCESS");
            } catch (Exception error) {
                recordProviderStage("GEMINI_CLASSIFICATION", assetId,
                        classificationStartedAt, "FAILURE");
                log.warn("AI classification failed for asset {}: {}", assetId, error.getMessage());
                mediaAssetRepository.updateStatus(assetId, MediaAssetStatus.FAILED.name());
                return false;
            }
        } else {
            recordProviderStage("GEMINI_CLASSIFICATION", assetId, System.nanoTime(), "REUSED", 0, 1);
        }

        if (!mediaImageEmbeddingService.generateOrReuse(assetId, storageUrl)) {
            mediaAssetRepository.updateStatus(assetId, MediaAssetStatus.FAILED.name());
            return false;
        }

        boolean needsSemanticEmbedding = metadataChanged || mediaAssetEmbeddingRepository
                .findEmbedding(assetId, MediaAssetEmbeddingType.SEMANTIC).isEmpty();
        if (needsSemanticEmbedding) {
            MediaClassificationDto classification = result;
            List<String> tagLabels = assetTagRepository
                    .findByMediaAssetIdOrderByCreatedAtAsc(assetId)
                    .stream()
                    .map(AssetTag::getLabel)
                    .toList();
            String embeddingText = mediaAssetRepository.findActiveById(assetId)
                    .map(current -> classification == null
                            ? buildEmbeddingText(current, tagLabels)
                            : buildEmbeddingText(current, classification, tagLabels))
                    .orElse("");
            if (embeddingText.isBlank() || !generateAndStoreEmbeddingInternal(assetId, embeddingText)) {
                mediaAssetRepository.updateStatus(assetId, MediaAssetStatus.FAILED.name());
                return false;
            }
        } else {
            recordProviderStage("VOYAGE_SEMANTIC_EMBEDDING", assetId,
                    System.nanoTime(), "REUSED", 0, 1);
        }

        mediaAssetRepository.updateStatus(assetId, MediaAssetStatus.READY.name());
        return true;
    }

    /**
     * Adds optional Gemini metadata without controlling retrieval readiness.
     * A failure is returned to the durable queue for retry, but never changes
     * the media asset status or removes already stored Voyage vectors.
     */
    public boolean enrichAsset(UUID assetId, String storageUrl) {
        MediaAsset asset = mediaAssetRepository.findActiveById(assetId).orElse(null);
        if (asset == null || asset.getFileType() == null || !asset.getFileType().isImage()) {
            return true;
        }
        if (asset.getAiClassifiedAt() != null
                && Objects.equals(classificationClient.modelName(), asset.getAiClassificationModel())
                && MediaMetadataTaxonomy.SCHEMA_VERSION.equals(asset.getMetadataSchemaVersion())
                && MediaMetadataTaxonomy.PROMPT_VERSION.equals(asset.getMetadataPromptVersion())) {
            recordProviderStage("GEMINI_CLASSIFICATION", assetId,
                    System.nanoTime(), "REUSED", 0, 1);
            return true;
        }

        long startedAt = System.nanoTime();
        try {
            MediaClassificationDto result = classifyAndNormalize(storageUrl);
            boolean metadataChanged = persistClassification(assetId, result);
            persistSuggestedTags(assetId, result.suggestedTags());
            if (metadataChanged) {
                mediaAssetRepository.incrementSemanticRevision(assetId);
                mediaProcessingQueueService.semanticMetadataChangedAfterCommit(assetId);
            }
            recordProviderStage("GEMINI_CLASSIFICATION", assetId, startedAt, "SUCCESS");
            return true;
        } catch (Exception error) {
            recordProviderStage("GEMINI_CLASSIFICATION", assetId, startedAt, "FAILURE");
            log.warn("Optional media enrichment failed for asset {}: {}", assetId, error.getMessage());
            return false;
        }
    }

    /**
     * Classifies a media asset and generates its embedding asynchronously.
     * Legacy asynchronous entry point retained for compatibility. New uploads
     * use MediaProcessingWorker and the synchronous, stage-aware processAsset path.
     *
     * @param assetId    UUID of the saved MediaAsset
     * @param storageUrl storage URL used as image input for Gemini
     */
    @Async
    public void classifyAndEmbed(UUID assetId, String storageUrl) {
        // Step 1: Classify via Gemini (external HTTP, no DB connection held)
        MediaClassificationDto result;
        try {
            result = classifyAndNormalize(storageUrl);
        } catch (Exception e) {
            log.warn("AI classification failed for asset {}: {}", assetId, e.getMessage());
            mediaAssetRepository.updateStatus(assetId, MediaAssetStatus.FAILED.name());
            return;
        }

        // Step 2: Persist classification fields (short write transaction per repo method)
        try {
            persistClassification(assetId, result);
        } catch (Exception e) {
            log.warn("Failed to persist classification for asset {}: {}", assetId, e.getMessage());
            mediaAssetRepository.updateStatus(assetId, MediaAssetStatus.FAILED.name());
            return;
        }

        persistSuggestedTags(assetId, result.suggestedTags());

        // Step 3: Build richer embedding text from stable asset metadata + image scan.
        String embeddingText = mediaAssetRepository.findActiveById(assetId)
                .map(asset -> buildEmbeddingText(asset, result, List.of()))
                .orElseGet(() -> buildEmbeddingText(result));

        // Step 4: Generate image + semantic embeddings (external HTTP, no DB connection held)
        if (!mediaImageEmbeddingService.generateOrReuse(assetId, storageUrl)) {
            mediaAssetRepository.updateStatus(assetId, MediaAssetStatus.FAILED.name());
            return;
        }
        if (!generateAndStoreEmbeddingInternal(assetId, embeddingText)) {
            mediaAssetRepository.updateStatus(assetId, MediaAssetStatus.FAILED.name());
            return;
        }

        // Step 5: classification + both embeddings all succeeded — the asset is
        // no longer PROCESSING. (Previously nothing set this on the success
        // path, so every asset stayed "Processing…" forever regardless of
        // outcome — see UC-2.1.)
        mediaAssetRepository.updateStatus(assetId, MediaAssetStatus.READY.name());
    }

    /**
     * Generates an embedding for a pre-classified asset.
     * Called by EmbeddingReconciliationJob for assets that were classified but not yet embedded.
     */
    public void generateAndStoreEmbedding(UUID assetId, String embeddingText) {
        generateAndStoreEmbeddingInternal(assetId, embeddingText);
    }

    /**
     * Retries only the embedding step(s) for an image asset that already has
     * a classification (ai_classified_at set) but is stuck in PROCESSING/FAILED
     * because a prior embedding call failed. Used by EmbeddingReconciliationJob
     * instead of {@link #classifyAndEmbed} so a stuck asset isn't fully
     * reclassified by the vision provider on every 5-minute retry — that used to
     * silently re-run classifyAndEmbed's classification step indefinitely for
     * any asset stuck on the embedding side, which (before persistSuggestedTags
     * was made to replace rather than accumulate) let one asset's AI tags grow
     * to 374 over repeated retries.
     */
    public void retryStuckImageEmbedding(UUID assetId, String storageUrl) {
        if (!mediaImageEmbeddingService.generateOrReuse(assetId, storageUrl)) {
            mediaAssetRepository.updateStatus(assetId, MediaAssetStatus.FAILED.name());
            return;
        }
        List<String> tagLabels = assetTagRepository
                .findByMediaAssetIdOrderByCreatedAtAsc(assetId)
                .stream()
                .map(AssetTag::getLabel)
                .toList();
        String embeddingText = mediaAssetRepository.findActiveById(assetId)
                .map(asset -> buildEmbeddingText(asset, tagLabels))
                .orElse(null);
        if (embeddingText == null || !generateAndStoreEmbeddingInternal(assetId, embeddingText)) {
            mediaAssetRepository.updateStatus(assetId, MediaAssetStatus.FAILED.name());
            return;
        }
        mediaAssetRepository.updateStatus(assetId, MediaAssetStatus.READY.name());
    }

    private boolean generateAndStoreEmbeddingInternal(UUID assetId, String embeddingText) {
        long startedAt = System.nanoTime();
        String embeddingJson;
        try {
            embeddingJson = voyageAIClient.embedDocument(embeddingText);
        } catch (Exception e) {
            recordProviderStage("VOYAGE_SEMANTIC_EMBEDDING", assetId, startedAt, "FAILURE");
            log.warn("Voyage AI embedding failed for asset {}: {}", assetId, e.getMessage());
            return false;
        }

        try {
            mediaAssetEmbeddingRepository.upsert(
                    assetId,
                    MediaAssetEmbeddingType.SEMANTIC,
                    embeddingJson,
                    voyageAIClient.modelName());
            mediaAssetRepository.updateEmbedding(assetId, embeddingJson, voyageAIClient.modelName());
            recordProviderStage("VOYAGE_SEMANTIC_EMBEDDING", assetId, startedAt, "SUCCESS");
            log.info("Embedding stored for asset {}", assetId);
            return true;
        } catch (Exception e) {
            recordProviderStage("VOYAGE_SEMANTIC_EMBEDDING", assetId, startedAt, "FAILURE");
            log.warn("Failed to store embedding for asset {}: {}", assetId, e.getMessage());
            return false;
        }
    }

    private void recordProviderStage(String stage, UUID assetId, long startedAt, String outcome) {
        recordProviderStage(stage, assetId, startedAt, outcome, 1, 0);
    }

    private void recordProviderStage(String stage, UUID assetId, long startedAt, String outcome,
            int providerCalls, int duplicateCallsAvoided) {
        if (mediaAiTelemetry != null) {
            mediaAiTelemetry.record(stage, MediaAiTelemetryService.elapsedMillis(startedAt), outcome,
                    assetId, null, 1, providerCalls, duplicateCallsAvoided);
        }
    }

    private MediaClassificationDto classifyAndNormalize(String storageUrl) {
        return MediaMetadataTaxonomy.normalize(
                classificationClient.classifyMedia(List.of(storageUrl)));
    }

    private boolean persistClassification(UUID assetId, MediaClassificationDto rawResult) {
        MediaAsset asset = mediaAssetRepository.findActiveById(assetId).orElse(null);
        if (asset == null) return false;
        MediaClassificationDto result = MediaMetadataTaxonomy.normalize(rawResult);
        boolean semanticMetadataChanged = semanticMetadataChanged(asset, result);
        asset.setAiCategory(result.category());
        asset.setAiConfidence(java.math.BigDecimal.valueOf(result.confidence()));
        asset.setAiDescription(result.description());
        asset.setAssetType(result.assetType());
        asset.setMetadataAssetType(result.metadataAssetType());
        asset.setAssetSubtype(result.assetSubtype());
        asset.setCommunicationPurpose(result.communicationPurpose());
        asset.setPrimarySubject(result.primarySubject());
        asset.setVisualSetting(result.setting());
        asset.setPeoplePresence(result.peoplePresence());
        asset.setSemanticTopics(toArray(result.topics()));
        asset.setDetectedTitle(result.detectedTitle());
        asset.setTextLanguages(toArray(result.textLanguages()));
        asset.setTextDensity(result.textDensity());
        asset.setOcrConfidence(result.ocrConfidence() == null
                ? null : java.math.BigDecimal.valueOf(result.ocrConfidence()));
        asset.setMetadataSchemaVersion(MediaMetadataTaxonomy.SCHEMA_VERSION);
        asset.setMetadataPromptVersion(MediaMetadataTaxonomy.PROMPT_VERSION);
        asset.setVisibleObjects(toArray(result.visibleObjects()));
        asset.setSpecificSubjects(toArray(result.specificSubjects()));
        asset.setVisualStyle(toArray(result.visualStyle()));
        asset.setDominantColors(toArray(result.dominantColors()));
        asset.setPossibleUseCases(toArray(result.possibleUseCases()));
        asset.setAiTags(toArray(result.suggestedTags()));
        asset.setExcludedCategories(toArray(result.excludedCategories()));
        asset.setObservedScenes(toArray(result.observedScenes()));
        asset.setObservedActivities(toArray(result.observedActivities()));
        asset.setPeopleCountRange(result.peopleCountRange());
        asset.setEquipmentSignals(toArray(result.equipmentSignals()));
        asset.setRecognitionSignals(toArray(result.recognitionSignals()));
        asset.setOcrText(toArray(result.ocrText()));
        asset.setVisibleDates(toArray(result.visibleDates()));
        asset.setEventHypotheses(toJson(result.eventHypotheses()));
        asset.setTemporalClassification(result.temporalClassification());
        asset.setPossibleExpiration(result.possibleExpiration());
        asset.setVisualQualitySignals(toArray(result.visualQualitySignals()));
        asset.setCompositionSignals(toArray(result.compositionSignals()));
        asset.setAiClassifiedAt(java.time.Instant.now());
        asset.setAiClassificationModel(classificationClient.modelName());
        mediaAssetRepository.save(asset);
        return semanticMetadataChanged;
    }

    private static boolean semanticMetadataChanged(MediaAsset asset, MediaClassificationDto result) {
        return !Objects.equals(asset.getAiDescription(), result.description())
                || !Objects.equals(asset.getMetadataAssetType(), result.metadataAssetType())
                || !Objects.equals(asset.getAssetSubtype(), result.assetSubtype())
                || !Objects.equals(asset.getCommunicationPurpose(), result.communicationPurpose())
                || !Objects.equals(asset.getPrimarySubject(), result.primarySubject())
                || !Objects.equals(asset.getVisualSetting(), result.setting())
                || !Arrays.equals(asset.getSemanticTopics(), toArray(result.topics()))
                || !Arrays.equals(asset.getObservedActivities(), toArray(result.observedActivities()))
                || !Arrays.equals(asset.getVisibleObjects(), toArray(result.visibleObjects()))
                || !Arrays.equals(asset.getOcrText(), toArray(result.ocrText()))
                || !Objects.equals(asset.getDetectedTitle(), result.detectedTitle());
    }

    /**
     * Replaces this asset's AI-generated tags with the current classification
     * run's output (bounded to MAX_AI_TAGS_TO_STORE by normalizeTags), rather
     * than only adding to what's there. This used to be purely additive —
     * exact-label dedup (DB unique constraint on (media_asset_id, label))
     * blocked a re-run from re-inserting an identical label, but an AI model's
     * wording is not deterministic across calls ("Outdoor Event" one run,
     * "outdoor gathering" the next), so every reclassification of the same
     * asset just kept adding ~15-30 more rows with no ceiling — one asset in
     * production had accumulated 374. Manual tags are untouched.
     */
    private List<String> persistSuggestedTags(UUID assetId, Collection<String> suggestedTags) {
        if (suggestedTags == null || suggestedTags.isEmpty()) return List.of();

        MediaAsset asset = mediaAssetRepository.findActiveById(assetId).orElse(null);
        if (asset == null) return List.of();

        List<String> normalized = normalizeTags(suggestedTags);
        if (normalized.isEmpty()) return List.of();

        try {
            assetTagRepository.deleteByMediaAssetIdAndSource(assetId, "ai_generated");
            // A remaining match now can only be a manual tag (AI-sourced ones were
            // just cleared) — skip it to avoid violating the (media_asset_id, label)
            // unique constraint; the manual tag already covers that label.
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
            assetTagRepository.saveAll(tagsToSave);
        } catch (Exception e) {
            log.warn("Failed to persist AI tags for asset {}: {}", assetId, e.getMessage());
        }

        return normalized;
    }

    public static String buildEmbeddingText(MediaClassificationDto result) {
        StringBuilder sb = new StringBuilder();
        MediaClassificationDto normalized = MediaMetadataTaxonomy.normalize(result);
        append(sb, "caption", normalized.description());
        append(sb, "primary_subject", normalized.primarySubject());
        append(sb, "asset_type", normalized.metadataAssetType());
        append(sb, "asset_subtype", normalized.assetSubtype());
        append(sb, "communication_purpose", normalized.communicationPurpose());
        appendAll(sb, "topics", normalized.topics());
        appendAll(sb, "activities", normalized.observedActivities());
        appendAll(sb, "visible_objects", normalized.visibleObjects());
        appendAll(sb, "ocr", normalized.ocrText());
        append(sb, "detected_title", normalized.detectedTitle());
        append(sb, "setting", normalized.setting());
        return sb.toString().trim();
    }

    public static String buildEmbeddingText(MediaAsset asset, MediaClassificationDto result, Collection<String> manualTags) {
        StringBuilder sb = new StringBuilder();
        append(sb, "asset_code", asset.getAssetCode());
        append(sb, "filename", normalizeFileName(asset.getFileName()));
        append(sb, "media_type", asset.getFileType() != null ? asset.getFileType().name() : null);
        append(sb, "uploaded_month", asset.getCreatedAt() != null ? UPLOAD_MONTH_FORMATTER.format(asset.getCreatedAt()) : null);
        append(sb, "caption", firstNonBlank(result.description(), asset.getAiDescription()));
        append(sb, "primary_subject", firstNonBlank(result.primarySubject(), asset.getPrimarySubject()));
        append(sb, "asset_type", firstNonBlank(result.metadataAssetType(), asset.getMetadataAssetType()));
        append(sb, "asset_subtype", firstNonBlank(result.assetSubtype(), asset.getAssetSubtype()));
        append(sb, "communication_purpose", firstNonBlank(
                result.communicationPurpose(), asset.getCommunicationPurpose()));
        appendAll(sb, "topics", firstNonEmpty(result.topics(), asset.getSemanticTopics()));
        appendAll(sb, "visible_objects", firstNonEmpty(result.visibleObjects(), asset.getVisibleObjects()));
        appendAll(sb, "asset_tags", manualTags);
        appendAll(sb, "activities", firstNonEmpty(
                result.observedActivities(), asset.getObservedActivities()));
        appendAll(sb, "ocr", firstNonEmpty(result.ocrText(), asset.getOcrText()));
        append(sb, "detected_title", firstNonBlank(result.detectedTitle(), asset.getDetectedTitle()));
        append(sb, "setting", firstNonBlank(result.setting(), asset.getVisualSetting()));
        return sb.toString().trim();
    }

    public static String buildEmbeddingText(MediaAsset asset, Collection<String> manualTags) {
        StringBuilder sb = new StringBuilder();
        append(sb, "asset_code", asset.getAssetCode());
        append(sb, "filename", normalizeFileName(asset.getFileName()));
        append(sb, "media_type", asset.getFileType() != null ? asset.getFileType().name() : null);
        append(sb, "uploaded_month", asset.getCreatedAt() != null
                ? UPLOAD_MONTH_FORMATTER.format(asset.getCreatedAt()) : null);
        append(sb, "caption", asset.getAiDescription());
        append(sb, "primary_subject", asset.getPrimarySubject());
        append(sb, "asset_type", asset.getMetadataAssetType());
        append(sb, "asset_subtype", asset.getAssetSubtype());
        append(sb, "communication_purpose", asset.getCommunicationPurpose());
        appendAll(sb, "topics", asset.getSemanticTopics());
        appendAll(sb, "visible_objects", asset.getVisibleObjects());
        appendAll(sb, "asset_tags", manualTags);
        appendAll(sb, "activities", asset.getObservedActivities());
        appendAll(sb, "ocr", asset.getOcrText());
        append(sb, "detected_title", asset.getDetectedTitle());
        append(sb, "setting", asset.getVisualSetting());
        return sb.toString().trim();
    }

    private static String[] toArray(Collection<String> values) {
        if (values == null || values.isEmpty()) return new String[0];
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toArray(String[]::new);
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

    private static void appendAll(StringBuilder sb, String label, String[] values) {
        if (values != null) appendAll(sb, label, Arrays.asList(values));
    }

    private static String firstNonBlank(String first, String fallback) {
        return first != null && !first.isBlank() ? first : fallback;
    }

    private static Collection<String> firstNonEmpty(Collection<String> first, String[] fallback) {
        if (first != null && !first.isEmpty()) return first;
        if (fallback == null || fallback.length == 0) return List.of();
        return Arrays.stream(fallback).toList();
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

    private static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not serialize media classification context", error);
        }
    }
}
