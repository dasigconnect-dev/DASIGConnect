package com.dasigconnect.backend.service;
import com.dasigconnect.backend.external.VoyageAIClient;
import com.dasigconnect.backend.model.dto.ai.MediaSuggestRequestDto;
import com.dasigconnect.backend.model.dto.ai.MediaSuggestResultDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetSummaryDto;
import com.dasigconnect.backend.model.entity.AiInteractionLog;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetEmbeddingType;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.repository.AiInteractionLogRepository;
import com.dasigconnect.backend.repository.AssetTagRepository;
import com.dasigconnect.backend.repository.MediaAssetEmbeddingRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionMediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Provides AI media recommendations for UC-3.3.
 *
 * Asset image understanding is generated once at upload time. Suggestion requests only
 * embed the contributor's current text context and run pgvector search, then apply
 * lightweight category/tag/recency boosts. This keeps the feature fast and avoids
 * rescanning images during content submission.
 */
@Service
@Transactional(readOnly = true)
public class AIRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(AIRecommendationService.class);

    private final SubmissionRepository submissionRepository;
    private final SubmissionMediaAssetRepository submissionMediaAssetRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final MediaAssetEmbeddingRepository mediaAssetEmbeddingRepository;
    private final AssetTagRepository assetTagRepository;
    private final AiInteractionLogRepository aiInteractionLogRepository;
    private final VoyageAIClient voyageAIClient;

    public AIRecommendationService(SubmissionRepository submissionRepository,
                                   SubmissionMediaAssetRepository submissionMediaAssetRepository,
                                   MediaAssetRepository mediaAssetRepository,
                                   MediaAssetEmbeddingRepository mediaAssetEmbeddingRepository,
                                   AssetTagRepository assetTagRepository,
                                   AiInteractionLogRepository aiInteractionLogRepository,
                                   VoyageAIClient voyageAIClient) {
        this.submissionRepository = submissionRepository;
        this.submissionMediaAssetRepository = submissionMediaAssetRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.mediaAssetEmbeddingRepository = mediaAssetEmbeddingRepository;
        this.assetTagRepository = assetTagRepository;
        this.aiInteractionLogRepository = aiInteractionLogRepository;
        this.voyageAIClient = voyageAIClient;
    }

    /**
     * Finds media assets in the institution library that are similar to the first
     * embedded asset already attached to the submission.
     * Returns up to 5 results, excluding assets already in the submission.
     */
    @Transactional
    public List<MediaAssetSummaryDto> getSimilarMedia(UUID submissionId, JwtUserDetails user) {
        Submission submission = loadAndAuthorise(submissionId, user);
        List<MediaAsset> attached = submissionMediaAssetRepository.findMediaAssetsBySubmissionId(submissionId);

        if (attached.isEmpty()) return List.of();

        OptionalVector queryVector = firstAvailableEmbedding(attached);
        if (queryVector.value() == null) return List.of();

        UUID institutionId = submission.getInstitution().getId();
        Set<UUID> attachedIds = attached.stream().map(MediaAsset::getId).collect(Collectors.toSet());

        List<MediaAssetSummaryDto> results = mediaAssetRepository
                .findTopSimilar(institutionId, queryVector.value())
                .stream()
                .filter(a -> !attachedIds.contains(a.getId()))
                .limit(5)
                .map(MediaAssetSummaryDto::from)
                .toList();

        logInteraction(submissionId, institutionId, "media_recommendation", "shown");
        return results;
    }

    /**
     * Suggests media assets from the institution library based on submission context.
     *
     * The expensive image scan was already done when media was uploaded. At request time
     * we embed the contributor's title/caption/category/tags once, fetch pgvector nearest
     * neighbors, and re-rank a small candidate set with deterministic metadata boosts.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<MediaSuggestResultDto> suggestMedia(UUID submissionId, MediaSuggestRequestDto dto, JwtUserDetails user) {
        Submission submission = loadAndAuthorise(submissionId, user);
        UUID institutionId = submission.getInstitution().getId();

        List<MediaAsset> attachedAssets = submissionMediaAssetRepository.findMediaAssetsBySubmissionId(submissionId);
        Set<UUID> attachedIds = attachedAssets.stream().map(MediaAsset::getId).collect(Collectors.toSet());
        Map<UUID, List<TagSignal>> attachedTagMap = loadTagSignalMap(attachedAssets.stream().map(MediaAsset::getId).toList());

        String embeddingText = buildQueryEmbeddingText(dto, attachedAssets, attachedTagMap);
        if (embeddingText.isBlank()) {
            return List.of();
        }

        String queryVector;
        try {
            queryVector = voyageAIClient.embedQuery(embeddingText);
        } catch (Exception e) {
            log.warn("Voyage AI embedding failed for suggest-media on submission {}: {}", submissionId, e.getMessage());
            return fallbackSuggestions(institutionId, attachedIds, dto);
        }

        List<Object[]> rows = mediaAssetEmbeddingRepository.findTopSimilarWithScore(
                institutionId, MediaAssetEmbeddingType.SEMANTIC, queryVector, 30);
        List<UUID> candidateIds = new ArrayList<>();
        Map<UUID, Double> semanticScores = new LinkedHashMap<>();
        for (Object[] row : rows) {
            UUID id = toUuid(row[0]);
            double score = row[1] instanceof Number number ? number.doubleValue() : 0.0;
            candidateIds.add(id);
            semanticScores.put(id, score);
        }
        if (candidateIds.isEmpty()) return fallbackSuggestions(institutionId, attachedIds, dto);

        Map<UUID, MediaAsset> assetMap = mediaAssetRepository.findActiveByIds(candidateIds)
                .stream()
                .collect(Collectors.toMap(MediaAsset::getId, asset -> asset));
        Map<UUID, List<TagSignal>> tagMap = loadTagSignalMap(candidateIds);

        List<MediaSuggestResultDto> rankedResults = candidateIds.stream()
                .filter(id -> assetMap.containsKey(id))
                .filter(id -> !attachedIds.contains(id))
                .map(id -> rankAsset(
                        assetMap.get(id),
                        semanticScores.getOrDefault(id, 0.0),
                        dto,
                        tagMap.getOrDefault(id, List.of())
                ))
                .sorted(Comparator.comparingDouble(RankedAsset::score).reversed())
                .limit(8)
                .map(result -> MediaSuggestResultDto.from(result.asset(), result.score(), result.reasons()))
                .toList();

        if (rankedResults.isEmpty()) {
            log.info("No suggest-media candidates remained after excluding attached assets for submission {}; using metadata fallback.",
                    submissionId);
            return fallbackSuggestions(institutionId, attachedIds, dto);
        }

        return rankedResults;
    }

    @Transactional
    public void logInteraction(UUID submissionId, UUID institutionId, String type, String actionTaken) {
        try {
            AiInteractionLog entry = new AiInteractionLog();
            entry.setSubmissionId(submissionId);
            entry.setInstitutionId(institutionId);
            entry.setInteractionType(type);
            entry.setActionTaken(actionTaken);
            aiInteractionLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to log AI interaction for submission {}: {}", submissionId, e.getMessage());
        }
    }

    static String buildQueryEmbeddingText(MediaSuggestRequestDto dto) {
        return buildQueryEmbeddingText(dto, List.of(), Map.of());
    }

    private static String buildQueryEmbeddingText(MediaSuggestRequestDto dto,
                                                 List<MediaAsset> attachedAssets,
                                                 Map<UUID, List<TagSignal>> attachedTagMap) {
        StringBuilder sb = new StringBuilder();
        append(sb, "event_title", dto.getEventTitle());
        append(sb, "caption", dto.getCaption());
        append(sb, "category", dto.getCategory());
        appendAll(sb, "tags", dto.getTags());
        if (attachedAssets != null && !attachedAssets.isEmpty()) {
            String selectedContext = attachedAssets.stream()
                    .limit(3)
                    .map(asset -> selectedMediaContext(asset, attachedTagMap.getOrDefault(asset.getId(), List.of())))
                    .filter(text -> !text.isBlank())
                    .collect(Collectors.joining(" "));
            append(sb, "selected_media_context", selectedContext);
        }
        return sb.toString().trim();
    }

    static double boostedScore(MediaAsset asset, double semanticScore, MediaSuggestRequestDto dto, Set<String> assetTags) {
        List<TagSignal> tagSignals = assetTags.stream()
                .map(label -> new TagSignal(label, "ai_generated"))
                .toList();
        return rankAsset(asset, semanticScore, dto, tagSignals).score();
    }

    private static RankedAsset rankAsset(MediaAsset asset, double semanticScore, MediaSuggestRequestDto dto, Collection<TagSignal> assetTags) {
        double score = semanticScore;
        List<String> reasons = new ArrayList<>();
        Set<String> queryTerms = normalizedTerms(dto);

        if (semanticScore >= 0.75) {
            reasons.add("Strong semantic similarity to the title, caption, or tags.");
        } else if (semanticScore >= 0.55) {
            reasons.add("Similar to the post context from the title or caption.");
        } else if (semanticScore >= 0.40) {
            reasons.add("Some semantic overlap with the post context.");
        }

        String assetCategory = normalize(asset.getAiCategory());
        String requestCategory = normalize(dto.getCategory());
        if (!assetCategory.isBlank()) {
            if (assetCategory.equals(requestCategory)) {
                score += 0.10;
                reasons.add("Category matches " + asset.getAiCategory() + ".");
            } else if (queryTerms.contains(assetCategory)) {
                score += 0.06;
                reasons.add("Detected category appears in the post text.");
            }
        }

        List<String> matchingManualTags = assetTags.stream()
                .filter(TagSignal::manual)
                .map(TagSignal::label)
                .map(AIRecommendationService::normalize)
                .filter(queryTerms::contains)
                .distinct()
                .limit(3)
                .toList();
        score += Math.min(0.18, matchingManualTags.size() * 0.06);
        if (!matchingManualTags.isEmpty()) {
            reasons.add("Shares manual tags: " + matchingManualTags.stream()
                    .map(tag -> "#" + tag)
                    .collect(Collectors.joining(", ")) + ".");
        }

        List<String> matchingAiTags = assetTags.stream()
                .filter(tag -> !tag.manual())
                .map(TagSignal::label)
                .map(AIRecommendationService::normalize)
                .filter(queryTerms::contains)
                .distinct()
                .limit(3)
                .toList();
        score += Math.min(0.105, matchingAiTags.size() * 0.035);
        if (!matchingAiTags.isEmpty()) {
            reasons.add("AI detected tags: " + matchingAiTags.stream()
                    .map(tag -> "#" + tag)
                    .collect(Collectors.joining(", ")) + ".");
        }

        List<String> matchingAssetTerms = normalizedAssetTerms(asset).stream()
                .filter(queryTerms::contains)
                .limit(3)
                .toList();
        score += Math.min(0.08, matchingAssetTerms.size() * 0.03);
        if (!matchingAssetTerms.isEmpty()) {
            reasons.add("Asset details mention " + String.join(", ", matchingAssetTerms) + ".");
        }

        boolean hasRichProfile = asset.getAiDescription() != null && !asset.getAiDescription().isBlank()
                && !assetTags.isEmpty();
        if (hasRichProfile) {
            score += 0.02;
            reasons.add("Has AI description and tags for stronger matching.");
        }

        if (asset.getCreatedAt() != null) {
            long ageDays = Math.max(0, Duration.between(asset.getCreatedAt(), Instant.now()).toDays());
            if (ageDays <= 30) {
                score += 0.03;
                if (!reasons.isEmpty()) {
                    reasons.add("Recently uploaded, used as a freshness tie-breaker.");
                }
            } else if (ageDays <= 90) {
                score += 0.015;
                if (!reasons.isEmpty()) {
                    reasons.add("Recent enough to help break close matches.");
                }
            }
        }

        if (reasons.isEmpty()) {
            reasons.add("Ranked from available media metadata.");
        }

        return new RankedAsset(asset, Math.max(0.0, Math.min(1.0, score)), reasons);
    }

    private Submission loadAndAuthorise(UUID submissionId, JwtUserDetails user) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found."));
        boolean isAdmin = user.role() != null && user.role().toLowerCase(Locale.ROOT).contains("admin");
        if (!isAdmin && !submission.getInstitution().getId().equals(user.institutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Submission does not belong to your institution.");
        }
        return submission;
    }

    private OptionalVector firstAvailableEmbedding(List<MediaAsset> attached) {
        for (MediaAsset asset : attached) {
            String embedding = mediaAssetEmbeddingRepository
                    .findEmbedding(asset.getId(), MediaAssetEmbeddingType.SEMANTIC)
                    .orElseGet(() -> mediaAssetRepository.findEmbeddingById(asset.getId()).orElse(null));
            if (embedding != null) return new OptionalVector(embedding);
        }
        return new OptionalVector(null);
    }

    private Map<UUID, List<TagSignal>> loadTagSignalMap(List<UUID> assetIds) {
        if (assetIds.isEmpty()) return Map.of();
        Map<UUID, List<TagSignal>> result = new HashMap<>();
        for (Object[] row : assetTagRepository.findLabelsAndSourcesByMediaAssetIds(assetIds)) {
            UUID id = toUuid(row[0]);
            String label = row[1] instanceof String s ? s : null;
            String source = row[2] instanceof String s ? s : "manual";
            if (label != null && !label.isBlank()) {
                result.computeIfAbsent(id, ignored -> new ArrayList<>()).add(new TagSignal(label, source));
            }
        }
        return result;
    }

    private List<MediaSuggestResultDto> fallbackSuggestions(UUID institutionId, Set<UUID> attachedIds, MediaSuggestRequestDto dto) {
        List<MediaAsset> candidates = mediaAssetRepository.findReadyByInstitution(institutionId)
                .stream()
                .filter(asset -> !attachedIds.contains(asset.getId()))
                .limit(30)
                .toList();
        if (candidates.isEmpty()) return List.of();

        List<UUID> candidateIds = candidates.stream().map(MediaAsset::getId).toList();
        Map<UUID, List<TagSignal>> tagMap = loadTagSignalMap(candidateIds);

        return candidates.stream()
                .map(asset -> rankAsset(
                        asset,
                        0.35,
                        dto,
                        tagMap.getOrDefault(asset.getId(), List.of())
                ))
                .sorted(Comparator.comparingDouble(RankedAsset::score).reversed())
                .limit(8)
                .map(result -> MediaSuggestResultDto.from(result.asset(), result.score(), result.reasons()))
                .toList();
    }

    private static String selectedMediaContext(MediaAsset asset, Collection<TagSignal> tags) {
        StringBuilder sb = new StringBuilder();
        append(sb, "filename", normalizeFileName(asset.getFileName()));
        append(sb, "category", asset.getAiCategory());
        append(sb, "description", asset.getAiDescription());
        appendAll(sb, "tags", tags.stream().map(TagSignal::label).toList());
        return sb.toString().trim();
    }

    private static Set<String> normalizedTerms(MediaSuggestRequestDto dto) {
        Set<String> terms = new LinkedHashSet<>();
        addTerm(terms, dto.getCategory());
        addAllTerms(terms, dto.getTags());
        addLooseWords(terms, dto.getEventTitle());
        addLooseWords(terms, dto.getCaption());
        return terms;
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

    private static void addAllTerms(Set<String> terms, Collection<String> values) {
        if (values == null) return;
        values.forEach(value -> addTerm(terms, value));
    }

    private static void addTerm(Set<String> terms, String value) {
        String normalized = normalize(value);
        if (!normalized.isBlank()) terms.add(normalized);
    }

    private static void addLooseWords(Set<String> terms, String value) {
        if (value == null || value.isBlank()) return;
        for (String word : value.split("[^A-Za-z0-9]+")) {
            if (word.length() >= 3) addTerm(terms, word);
        }
    }

    private static Set<String> normalizedAssetTerms(MediaAsset asset) {
        Set<String> terms = new LinkedHashSet<>();
        addLooseWords(terms, asset.getFileName());
        addLooseWords(terms, asset.getAssetCode());
        addLooseWords(terms, asset.getAiCategory());
        addLooseWords(terms, asset.getAiDescription());
        return terms;
    }

    private static String normalizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return null;
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        return base.replace('_', ' ').replace('-', ' ').trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static UUID toUuid(Object value) {
        if (value instanceof UUID uuid) return uuid;
        return UUID.fromString(String.valueOf(value));
    }

    private record OptionalVector(String value) {}

    private record TagSignal(String label, String source) {
        boolean manual() {
            return source == null || source.equalsIgnoreCase("manual");
        }
    }

    private record RankedAsset(MediaAsset asset, double score, List<String> reasons) {}
}
