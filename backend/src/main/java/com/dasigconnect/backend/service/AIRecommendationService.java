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
import com.dasigconnect.backend.repository.MediaAssetSearchHit;
import com.dasigconnect.backend.repository.MediaAssetSearchRepository;
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
    private static final int RRF_K = 60;
    private static final int CANDIDATE_LIMIT = 60;
    private static final int SUGGESTION_LIMIT = 8;
    private static final double SEMANTIC_MIN_SIMILARITY = 0.45;
    private static final double IMAGE_MIN_SIMILARITY = 0.22;
    private static final double SELECTED_IMAGE_MIN_SIMILARITY = 0.36;
    private static final double SELECTED_SEMANTIC_MIN_SIMILARITY = 0.58;
    private static final double SELECTED_STRONG_IMAGE_SIMILARITY = 0.44;
    private static final Set<String> GENERIC_PROFILE_TERMS = Set.of(
            "photo", "image", "picture", "media", "event", "activity", "campus",
            "student", "students", "people", "person", "group", "team", "meeting",
            "program", "school", "university", "college", "cit", "cit u", "dasig",
            "jpg", "jpeg", "png", "webp", "file", "uploaded"
    );

    private final SubmissionRepository submissionRepository;
    private final SubmissionMediaAssetRepository submissionMediaAssetRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final MediaAssetEmbeddingRepository mediaAssetEmbeddingRepository;
    private final MediaAssetSearchRepository mediaAssetSearchRepository;
    private final AssetTagRepository assetTagRepository;
    private final AiInteractionLogRepository aiInteractionLogRepository;
    private final VoyageAIClient voyageAIClient;

    public AIRecommendationService(SubmissionRepository submissionRepository,
                                   SubmissionMediaAssetRepository submissionMediaAssetRepository,
                                   MediaAssetRepository mediaAssetRepository,
                                   MediaAssetEmbeddingRepository mediaAssetEmbeddingRepository,
                                   MediaAssetSearchRepository mediaAssetSearchRepository,
                                   AssetTagRepository assetTagRepository,
                                   AiInteractionLogRepository aiInteractionLogRepository,
                                   VoyageAIClient voyageAIClient) {
        this.submissionRepository = submissionRepository;
        this.submissionMediaAssetRepository = submissionMediaAssetRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.mediaAssetEmbeddingRepository = mediaAssetEmbeddingRepository;
        this.mediaAssetSearchRepository = mediaAssetSearchRepository;
        this.assetTagRepository = assetTagRepository;
        this.aiInteractionLogRepository = aiInteractionLogRepository;
        this.voyageAIClient = voyageAIClient;
    }

    /**
     * Finds media assets in the institution library that are similar to the
     * embedded assets already attached to the submission.
     * Returns up to 5 results, excluding assets already in the submission.
     */
    @Transactional
    public List<MediaAssetSummaryDto> getSimilarMedia(UUID submissionId, JwtUserDetails user) {
        Submission submission = loadAndAuthorise(submissionId, user);
        List<MediaAsset> attached = submissionMediaAssetRepository.findMediaAssetsBySubmissionId(submissionId);

        if (attached.isEmpty()) return List.of();

        UUID institutionId = submission.getInstitution().getId();
        Set<UUID> attachedIds = attached.stream().map(MediaAsset::getId).collect(Collectors.toSet());
        Map<UUID, List<TagSignal>> attachedTagMap = loadTagSignalMap(attached.stream().map(MediaAsset::getId).toList());
        Set<String> selectedProfile = selectedProfileTerms(attached, attachedTagMap);
        List<OptionalVector> queryVectors = availableEmbeddings(attached);
        if (queryVectors.isEmpty() && selectedProfile.isEmpty()) return List.of();

        List<MediaAssetSummaryDto> results = similarAssets(
                    institutionId,
                    attached,
                    attachedTagMap,
                    queryVectors,
                    attachedIds,
                    selectedProfile)
                .stream()
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

        String semanticVector = null;
        try {
            semanticVector = voyageAIClient.embedQuery(embeddingText);
        } catch (Exception e) {
            log.warn("Voyage semantic embedding failed for suggest-media on submission {}: {}", submissionId, e.getMessage());
        }

        String imageVector = null;
        try {
            imageVector = voyageAIClient.embedMultimodalTextQuery(embeddingText);
        } catch (Exception e) {
            log.warn("Voyage visual embedding failed for suggest-media on submission {}: {}", submissionId, e.getMessage());
        }

        if (semanticVector == null && imageVector == null) {
            return fallbackSuggestions(institutionId, attachedIds, dto);
        }

        String lexicalText = buildLexicalQueryText(dto, attachedAssets, attachedTagMap);
        List<MediaAssetSearchHit> lexicalHits = lexicalText.isBlank()
                ? List.of()
                : mediaAssetSearchRepository.searchLexical(
                        lexicalText, institutionId, false, null, CANDIDATE_LIMIT, 0);

        List<VectorHit> semanticHits = semanticVector == null
                ? List.of()
                : vectorHits(mediaAssetEmbeddingRepository.findTopSimilarWithScore(
                        institutionId, MediaAssetEmbeddingType.SEMANTIC, semanticVector, CANDIDATE_LIMIT),
                        SEMANTIC_MIN_SIMILARITY);
        List<VectorHit> imageHits = imageVector == null
                ? List.of()
                : vectorHits(mediaAssetEmbeddingRepository.findTopSimilarWithScore(
                        institutionId, MediaAssetEmbeddingType.IMAGE, imageVector, CANDIDATE_LIMIT),
                        IMAGE_MIN_SIMILARITY);

        List<Candidate> candidates = fuse(lexicalHits, semanticHits, imageHits).stream()
                .filter(candidate -> !attachedIds.contains(candidate.assetId()))
                .toList();
        if (candidates.isEmpty()) return fallbackSuggestions(institutionId, attachedIds, dto);

        List<UUID> candidateIds = candidates.stream().map(Candidate::assetId).toList();

        Map<UUID, MediaAsset> assetMap = mediaAssetRepository.findActiveByIds(candidateIds)
                .stream()
                .collect(Collectors.toMap(MediaAsset::getId, asset -> asset));
        Map<UUID, List<TagSignal>> tagMap = loadTagSignalMap(candidateIds);

        List<MediaSuggestResultDto> rankedResults = candidates.stream()
                .filter(candidate -> assetMap.containsKey(candidate.assetId()))
                .map(candidate -> rankAsset(
                        assetMap.get(candidate.assetId()),
                        candidate,
                        dto,
                        tagMap.getOrDefault(candidate.assetId(), List.of())
                ))
                .sorted(Comparator.comparingDouble(RankedAsset::score).reversed())
                .limit(SUGGESTION_LIMIT)
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

    private static String buildLexicalQueryText(MediaSuggestRequestDto dto,
                                                List<MediaAsset> attachedAssets,
                                                Map<UUID, List<TagSignal>> attachedTagMap) {
        List<String> parts = new ArrayList<>();
        addPart(parts, dto.getEventTitle());
        addPart(parts, dto.getCaption());
        addPart(parts, dto.getCategory());
        if (dto.getTags() != null) {
            dto.getTags().forEach(value -> addPart(parts, value));
        }
        if (attachedAssets != null) {
            attachedAssets.stream()
                    .limit(3)
                    .forEach(asset -> {
                        addPart(parts, asset.getTitle());
                        addPart(parts, normalizeFileName(asset.getFileName()));
                        addPart(parts, asset.getAiCategory());
                        addPart(parts, asset.getAiDescription());
                        addParts(parts, asset.getAiTags());
                        addParts(parts, asset.getSpecificSubjects());
                        addParts(parts, asset.getVisibleObjects());
                        addParts(parts, asset.getPossibleUseCases());
                        attachedTagMap.getOrDefault(asset.getId(), List.of())
                                .forEach(tag -> addPart(parts, tag.label()));
                    });
        }
        return parts.stream()
                .map(AIRecommendationService::normalize)
                .filter(part -> part.length() >= 2)
                .distinct()
                .collect(Collectors.joining(" "));
    }

    static double boostedScore(MediaAsset asset, double semanticScore, MediaSuggestRequestDto dto, Set<String> assetTags) {
        List<TagSignal> tagSignals = assetTags.stream()
                .map(label -> new TagSignal(label, "ai_generated"))
                .toList();
        return rankAsset(asset, Candidate.semanticOnly(asset.getId(), semanticScore), dto, tagSignals).score();
    }

    private static RankedAsset rankAsset(MediaAsset asset, Candidate candidate, MediaSuggestRequestDto dto, Collection<TagSignal> assetTags) {
        double semanticScore = candidate.semanticScore() == null ? 0.0 : candidate.semanticScore();
        double imageScore = candidate.imageScore() == null ? 0.0 : candidate.imageScore();
        double lexicalScore = candidate.lexicalScore() == null ? 0.0 : candidate.lexicalScore();
        double score = Math.max(semanticScore, imageScore) + candidate.fusionScore();
        List<String> reasons = new ArrayList<>();
        Set<String> queryTerms = normalizedTerms(dto);
        List<String> queryTokens = normalizedTokens(dto);

        if (candidate.lexicalRank() != null) {
            score += Math.min(0.14, 0.08 + lexicalScore * 0.08);
            reasons.add("Exact metadata or keyword match.");
        }
        if (semanticScore >= 0.75) {
            reasons.add("Strong semantic similarity to the title, caption, or tags.");
        } else if (semanticScore >= 0.55) {
            reasons.add("Similar to the post context from the title or caption.");
        } else if (semanticScore >= 0.40) {
            reasons.add("Some semantic overlap with the post context.");
        }
        if (imageScore >= 0.45) {
            score += 0.06;
            reasons.add("Strong visual similarity to the post context.");
        } else if (imageScore >= 0.30) {
            score += 0.035;
            reasons.add("Visual details match the post context.");
        } else if (imageScore >= IMAGE_MIN_SIMILARITY) {
            score += 0.015;
            reasons.add("Some visual overlap with the post context.");
        }
        if (semanticScore > 0 && imageScore > 0) {
            score += 0.04;
            reasons.add("Matched by both metadata meaning and visual embedding.");
        }

        String assetCategory = normalize(asset.getAiCategory());
        String requestCategory = normalize(dto.getCategory());
        if (!assetCategory.isBlank()) {
            if (assetCategory.equals(requestCategory)) {
                score += 0.10;
                reasons.add("Category matches " + asset.getAiCategory() + ".");
            } else if (queryTerms.contains(assetCategory) || queryTokens.contains(assetCategory)) {
                score += 0.06;
                reasons.add("Detected category appears in the post text.");
            }
        }

        List<String> matchingManualTags = assetTags.stream()
                .filter(TagSignal::manual)
                .map(TagSignal::label)
                .map(AIRecommendationService::normalize)
                .filter(tag -> queryTerms.contains(tag) || queryTokens.contains(tag))
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
                .filter(tag -> queryTerms.contains(tag) || queryTokens.contains(tag))
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
                .filter(term -> queryTerms.contains(term)
                        || queryTokens.contains(term)
                        || queryTokens.stream().anyMatch(token -> term.contains(token)))
                .limit(3)
                .toList();
        score += Math.min(0.12, matchingAssetTerms.size() * 0.04);
        if (!matchingAssetTerms.isEmpty()) {
            reasons.add("Asset details mention " + String.join(", ", matchingAssetTerms) + ".");
        }

        double metadataBoost = searchStyleMetadataBoost(asset, queryTokens);
        if (metadataBoost > 0) {
            score += metadataBoost;
            reasons.addAll(searchStyleReasons(asset, queryTokens));
        }

        boolean hasRichProfile = asset.getAiDescription() != null && !asset.getAiDescription().isBlank()
                && (!assetTags.isEmpty()
                    || hasAny(asset.getAiTags())
                    || hasAny(asset.getSpecificSubjects())
                    || hasAny(asset.getVisibleObjects())
                    || hasAny(asset.getPossibleUseCases()));
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

        return new RankedAsset(asset, Math.max(0.0, Math.min(1.0, score)), reasons.stream().distinct().limit(6).toList());
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

    private List<OptionalVector> availableEmbeddings(List<MediaAsset> attached) {
        List<OptionalVector> vectors = new ArrayList<>();
        for (MediaAsset asset : attached) {
            String embedding = mediaAssetEmbeddingRepository
                    .findEmbedding(asset.getId(), MediaAssetEmbeddingType.IMAGE)
                    .orElse(null);
            if (embedding != null) {
                vectors.add(new OptionalVector(MediaAssetEmbeddingType.IMAGE, embedding));
            }
        }
        for (MediaAsset asset : attached) {
            String embedding = mediaAssetEmbeddingRepository
                    .findEmbedding(asset.getId(), MediaAssetEmbeddingType.SEMANTIC)
                    .orElse(null);
            if (embedding != null) {
                vectors.add(new OptionalVector(MediaAssetEmbeddingType.SEMANTIC, embedding));
            }
        }
        for (MediaAsset asset : attached) {
            String embedding = mediaAssetRepository.findEmbeddingById(asset.getId()).orElse(null);
            if (embedding != null) {
                vectors.add(new OptionalVector(null, embedding));
            }
        }
        return vectors;
    }

    private List<MediaAsset> similarAssets(UUID institutionId,
                                           List<MediaAsset> attached,
                                           Map<UUID, List<TagSignal>> attachedTagMap,
                                           List<OptionalVector> queryVectors,
                                           Set<UUID> attachedIds,
                                           Set<String> selectedProfile) {
        List<VectorHit> imageHits = new ArrayList<>();
        List<VectorHit> semanticHits = new ArrayList<>();
        List<UUID> legacyIds = new ArrayList<>();

        for (OptionalVector queryVector : queryVectors) {
            if (queryVector.type() == null) {
                legacyIds.addAll(mediaAssetRepository.findTopSimilar(institutionId, queryVector.value())
                        .stream()
                        .map(MediaAsset::getId)
                        .filter(id -> !attachedIds.contains(id))
                        .toList());
                continue;
            }

            double floor = queryVector.type() == MediaAssetEmbeddingType.IMAGE
                    ? SELECTED_IMAGE_MIN_SIMILARITY
                    : SELECTED_SEMANTIC_MIN_SIMILARITY;
            List<VectorHit> hits = vectorHits(mediaAssetEmbeddingRepository.findTopSimilarWithScore(
                    institutionId, queryVector.type(), queryVector.value(), CANDIDATE_LIMIT), floor);
            if (queryVector.type() == MediaAssetEmbeddingType.IMAGE) {
                imageHits.addAll(hits);
            } else {
                semanticHits.addAll(hits);
            }
        }

        String lexicalText = selectedProfile.isEmpty()
                ? buildLexicalQueryText(new MediaSuggestRequestDto(), attached, attachedTagMap)
                : String.join(" ", selectedProfile);
        List<MediaAssetSearchHit> lexicalHits = lexicalText.isBlank()
                ? List.of()
                : mediaAssetSearchRepository.searchLexical(lexicalText, institutionId, false, null, CANDIDATE_LIMIT, 0);

        List<Candidate> candidates = fuse(lexicalHits, semanticHits, imageHits);
        List<UUID> orderedIds = candidates.stream()
                .map(Candidate::assetId)
                .toList();
        if (orderedIds.isEmpty() && selectedProfile.isEmpty()) {
            orderedIds = legacyIds.stream().distinct().toList();
        }
        orderedIds = orderedIds.stream()
                .filter(id -> !attachedIds.contains(id))
                .distinct()
                .toList();
        Map<UUID, MediaAsset> assets = mediaAssetRepository.findActiveByIds(orderedIds)
                .stream()
                .collect(Collectors.toMap(MediaAsset::getId, asset -> asset));
        Map<UUID, Candidate> candidateMap = candidates.stream()
                .collect(Collectors.toMap(Candidate::assetId, candidate -> candidate, (first, ignored) -> first));
        Map<UUID, List<TagSignal>> tagMap = loadTagSignalMap(orderedIds);
        return orderedIds.stream()
                .map(id -> {
                    MediaAsset asset = assets.get(id);
                    Candidate candidate = candidateMap.get(id);
                    if (asset == null) return null;
                    if (candidate == null || selectedCandidateCompatible(asset, candidate,
                            tagMap.getOrDefault(id, List.of()), selectedProfile)) {
                        return asset;
                    }
                    return null;
                })
                .filter(asset -> asset != null)
                .toList();
    }

    private static List<VectorHit> vectorHits(List<Object[]> rows, double minSimilarity) {
        List<VectorHit> hits = new ArrayList<>();
        int rank = 0;
        for (Object[] row : rows) {
            double score = row[1] instanceof Number number ? number.doubleValue() : 0.0;
            if (score < minSimilarity) {
                break;
            }
            hits.add(new VectorHit(toUuid(row[0]), score, ++rank));
        }
        return hits;
    }

    private static List<Candidate> fuse(List<MediaAssetSearchHit> lexicalHits,
                                        List<VectorHit> semanticHits,
                                        List<VectorHit> imageHits) {
        Map<UUID, CandidateBuilder> candidates = new LinkedHashMap<>();
        for (MediaAssetSearchHit hit : lexicalHits) {
            CandidateBuilder candidate = candidates.computeIfAbsent(hit.assetId(), CandidateBuilder::new);
            candidate.lexicalRank = hit.lexicalRank();
            candidate.lexicalScore = hit.lexicalScore();
            candidate.fusionScore += rrf(hit.lexicalRank()) * 1.35;
        }
        for (VectorHit hit : semanticHits) {
            CandidateBuilder candidate = candidates.computeIfAbsent(hit.assetId(), CandidateBuilder::new);
            candidate.semanticScore = max(candidate.semanticScore, hit.score());
            candidate.fusionScore += rrf(hit.rank());
        }
        for (VectorHit hit : imageHits) {
            CandidateBuilder candidate = candidates.computeIfAbsent(hit.assetId(), CandidateBuilder::new);
            candidate.imageScore = max(candidate.imageScore, hit.score());
            candidate.fusionScore += rrf(hit.rank()) * 1.15;
        }
        return candidates.values().stream()
                .map(CandidateBuilder::build)
                .sorted(Comparator.comparingDouble(Candidate::fusionScore).reversed())
                .toList();
    }

    private static double rrf(int rank) {
        return 1.0 / (RRF_K + rank);
    }

    private static Double max(Double current, double next) {
        return current == null ? next : Math.max(current, next);
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
                        Candidate.semanticOnly(asset.getId(), 0.20),
                        dto,
                        tagMap.getOrDefault(asset.getId(), List.of())
                ))
                .sorted(Comparator.comparingDouble(RankedAsset::score).reversed())
                .limit(SUGGESTION_LIMIT)
                .map(result -> MediaSuggestResultDto.from(result.asset(), result.score(), result.reasons()))
                .toList();
    }

    private static String selectedMediaContext(MediaAsset asset, Collection<TagSignal> tags) {
        StringBuilder sb = new StringBuilder();
        append(sb, "title", asset.getTitle());
        append(sb, "filename", normalizeFileName(asset.getFileName()));
        append(sb, "category", asset.getAiCategory());
        append(sb, "description", asset.getAiDescription());
        appendAll(sb, "ai_tags", arrayList(asset.getAiTags()));
        appendAll(sb, "subjects", arrayList(asset.getSpecificSubjects()));
        appendAll(sb, "objects", arrayList(asset.getVisibleObjects()));
        appendAll(sb, "use_cases", arrayList(asset.getPossibleUseCases()));
        appendAll(sb, "visual_style", arrayList(asset.getVisualStyle()));
        appendAll(sb, "colors", arrayList(asset.getDominantColors()));
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

    private static void addPart(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value);
        }
    }

    private static void addParts(List<String> parts, String[] values) {
        if (values == null) return;
        for (String value : values) {
            addPart(parts, value);
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
        addLooseWords(terms, asset.getTitle());
        addLooseWords(terms, asset.getFileName());
        addLooseWords(terms, asset.getAssetCode());
        addLooseWords(terms, asset.getAiCategory());
        addLooseWords(terms, asset.getAiDescription());
        addAllTerms(terms, arrayList(asset.getAiTags()));
        addAllTerms(terms, arrayList(asset.getSpecificSubjects()));
        addAllTerms(terms, arrayList(asset.getVisibleObjects()));
        addAllTerms(terms, arrayList(asset.getPossibleUseCases()));
        addAllTerms(terms, arrayList(asset.getVisualStyle()));
        addAllTerms(terms, arrayList(asset.getDominantColors()));
        return terms;
    }

    private static Set<String> selectedProfileTerms(List<MediaAsset> attached,
                                                    Map<UUID, List<TagSignal>> attachedTagMap) {
        Set<String> terms = new LinkedHashSet<>();
        for (MediaAsset asset : attached) {
            terms.addAll(assetProfileTerms(asset, attachedTagMap.getOrDefault(asset.getId(), List.of())));
        }
        return terms.stream()
                .filter(term -> term.length() >= 3)
                .filter(term -> !GENERIC_PROFILE_TERMS.contains(term))
                .toList()
                .stream()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean selectedCandidateCompatible(MediaAsset asset,
                                                       Candidate candidate,
                                                       Collection<TagSignal> assetTags,
                                                       Set<String> selectedProfile) {
        if (candidate.imageScore() != null && candidate.imageScore() >= SELECTED_STRONG_IMAGE_SIMILARITY) {
            return true;
        }
        if (candidate.lexicalRank() != null) {
            return true;
        }
        if (selectedProfile.isEmpty()) {
            return (candidate.imageScore() != null && candidate.imageScore() >= SELECTED_STRONG_IMAGE_SIMILARITY)
                    || (candidate.semanticScore() != null && candidate.semanticScore() >= 0.70);
        }
        Set<String> candidateTerms = assetProfileTerms(asset, assetTags);
        boolean profileOverlap = selectedProfile.stream().anyMatch(candidateTerms::contains);
        return (candidate.imageScore() != null && candidate.imageScore() >= SELECTED_STRONG_IMAGE_SIMILARITY)
                || (candidate.semanticScore() != null && candidate.semanticScore() >= 0.70 && profileOverlap);
    }

    private static Set<String> assetProfileTerms(MediaAsset asset, Collection<TagSignal> assetTags) {
        Set<String> terms = new LinkedHashSet<>(normalizedAssetTerms(asset));
        if (assetTags != null) {
            assetTags.stream().map(TagSignal::label).forEach(value -> addLooseWords(terms, value));
        }
        return terms.stream()
                .map(AIRecommendationService::normalize)
                .filter(term -> term.length() >= 2)
                .filter(term -> !GENERIC_PROFILE_TERMS.contains(term))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static List<String> normalizedTokens(MediaSuggestRequestDto dto) {
        Set<String> tokens = new LinkedHashSet<>();
        addLooseWords(tokens, dto.getEventTitle());
        addLooseWords(tokens, dto.getCaption());
        addTerm(tokens, dto.getCategory());
        addAllTerms(tokens, dto.getTags());
        return tokens.stream().filter(token -> token.length() >= 2).toList();
    }

    private static double searchStyleMetadataBoost(MediaAsset asset, List<String> tokens) {
        double boost = 0.0;
        if (containsAny(asset.getTitle(), tokens)) {
            boost += 0.06;
        }
        if (containsAny(asset.getAssetCode(), tokens) || containsAny(asset.getFileName(), tokens)) {
            boost += 0.045;
        }
        if (containsAny(asset.getAiCategory(), tokens) || containsAny(asset.getAiTags(), tokens)) {
            boost += 0.04;
        }
        if (containsAny(asset.getAiDescription(), tokens)
                || containsAny(asset.getSpecificSubjects(), tokens)
                || containsAny(asset.getVisibleObjects(), tokens)
                || containsAny(asset.getPossibleUseCases(), tokens)
                || containsAny(asset.getVisualStyle(), tokens)
                || containsAny(asset.getDominantColors(), tokens)) {
            boost += 0.025;
        }
        return boost;
    }

    private static List<String> searchStyleReasons(MediaAsset asset, List<String> tokens) {
        Map<String, Boolean> reasons = new LinkedHashMap<>();
        reasons.put("Title match", containsAny(asset.getTitle(), tokens));
        reasons.put("Filename match", containsAny(asset.getFileName(), tokens));
        reasons.put("Asset code match", containsAny(asset.getAssetCode(), tokens));
        reasons.put("AI category match", containsAny(asset.getAiCategory(), tokens));
        reasons.put("AI tag match", containsAny(asset.getAiTags(), tokens));
        reasons.put("Subject match", containsAny(asset.getSpecificSubjects(), tokens));
        reasons.put("Object match", containsAny(asset.getVisibleObjects(), tokens));
        reasons.put("Use-case match", containsAny(asset.getPossibleUseCases(), tokens));
        reasons.put("Visual style match", containsAny(asset.getVisualStyle(), tokens));
        reasons.put("Color match", containsAny(asset.getDominantColors(), tokens));
        return reasons.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .limit(3)
                .toList();
    }

    private static String normalizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return null;
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        return base.replace('_', ' ').replace('-', ' ').trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static boolean containsAny(String value, Collection<String> tokens) {
        if (value == null || value.isBlank() || tokens == null || tokens.isEmpty()) {
            return false;
        }
        String normalized = normalize(value);
        return tokens.stream()
                .map(AIRecommendationService::normalize)
                .filter(token -> token.length() >= 2)
                .anyMatch(normalized::contains);
    }

    private static boolean containsAny(String[] values, Collection<String> tokens) {
        if (values == null || values.length == 0) {
            return false;
        }
        return java.util.Arrays.stream(values).anyMatch(value -> containsAny(value, tokens));
    }

    private static boolean hasAny(String[] values) {
        return values != null && values.length > 0;
    }

    private static List<String> arrayList(String[] values) {
        return values == null ? List.of() : java.util.Arrays.asList(values);
    }

    private static UUID toUuid(Object value) {
        if (value instanceof UUID uuid) return uuid;
        return UUID.fromString(String.valueOf(value));
    }

    private record OptionalVector(MediaAssetEmbeddingType type, String value) {}

    private record TagSignal(String label, String source) {
        boolean manual() {
            return source == null || source.equalsIgnoreCase("manual");
        }
    }

    private record VectorHit(UUID assetId, double score, int rank) {}

    private record Candidate(UUID assetId, Integer lexicalRank, Double lexicalScore,
                             Double semanticScore, Double imageScore, double fusionScore) {
        static Candidate semanticOnly(UUID assetId, double semanticScore) {
            return new Candidate(assetId, null, null, semanticScore, null, 0.0);
        }
    }

    private static class CandidateBuilder {
        private final UUID assetId;
        private Integer lexicalRank;
        private Double lexicalScore;
        private Double semanticScore;
        private Double imageScore;
        private double fusionScore;

        private CandidateBuilder(UUID assetId) {
            this.assetId = assetId;
        }

        private Candidate build() {
            return new Candidate(assetId, lexicalRank, lexicalScore, semanticScore, imageScore, fusionScore);
        }
    }

    private record RankedAsset(MediaAsset asset, double score, List<String> reasons) {}
}
