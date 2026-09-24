package com.dasigconnect.backend.service;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.external.VoyageAIClient;
import com.dasigconnect.backend.model.dto.ai.AlbumMatchCandidateDto;
import com.dasigconnect.backend.model.dto.ai.AlbumMatchRequestDto;
import com.dasigconnect.backend.model.dto.ai.AlbumMatchResponseDto;
import com.dasigconnect.backend.model.dto.ai.MediaSuggestRequestDto;
import com.dasigconnect.backend.model.dto.ai.MediaSuggestResultDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetSummaryDto;
import com.dasigconnect.backend.model.entity.AiInteractionLog;
import com.dasigconnect.backend.model.entity.MediaAlbum;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetEmbeddingType;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.repository.AiInteractionLogRepository;
import com.dasigconnect.backend.repository.AssetTagRepository;
import com.dasigconnect.backend.repository.MediaAlbumRepository;
import com.dasigconnect.backend.repository.MediaAssetEmbeddingRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionMediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.security.JwtUserDetails;

/**
 * Provides AI media recommendations for UC-3.3.
 *
 * Asset image understanding is generated once at upload time. Suggestion
 * requests reuse stored image embeddings for attached-media context and only
 * embed the contributor's text when text context is present. Candidate search
 * then applies lightweight category/tag/recency boosts. This keeps the feature
 * fast and avoids rescanning images during content submission.
 */
@Service
@Transactional(readOnly = true)
public class AIRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(AIRecommendationService.class);
    private static final int SUGGESTION_CANDIDATE_LIMIT = 30;
    private static final int ATTACHED_CANDIDATES_PER_ASSET = 12;

    private final SubmissionRepository submissionRepository;
    private final SubmissionMediaAssetRepository submissionMediaAssetRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final MediaAssetEmbeddingRepository mediaAssetEmbeddingRepository;
    private final AssetTagRepository assetTagRepository;
    private final AiInteractionLogRepository aiInteractionLogRepository;
    private final VoyageAIClient voyageAIClient;
    private final MediaAlbumRepository mediaAlbumRepository;

    public AIRecommendationService(SubmissionRepository submissionRepository,
            SubmissionMediaAssetRepository submissionMediaAssetRepository,
            MediaAssetRepository mediaAssetRepository,
            MediaAssetEmbeddingRepository mediaAssetEmbeddingRepository,
            AssetTagRepository assetTagRepository,
            AiInteractionLogRepository aiInteractionLogRepository,
            VoyageAIClient voyageAIClient,
            MediaAlbumRepository mediaAlbumRepository) {
        this.submissionRepository = submissionRepository;
        this.submissionMediaAssetRepository = submissionMediaAssetRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.mediaAssetEmbeddingRepository = mediaAssetEmbeddingRepository;
        this.assetTagRepository = assetTagRepository;
        this.aiInteractionLogRepository = aiInteractionLogRepository;
        this.voyageAIClient = voyageAIClient;
        this.mediaAlbumRepository = mediaAlbumRepository;
    }

    /**
     * Finds media assets in the institution library that are similar to the
     * first embedded asset already attached to the submission. Returns up to 5
     * results, excluding assets already in the submission.
     */
    @Transactional
    public List<MediaAssetSummaryDto> getSimilarMedia(UUID submissionId, JwtUserDetails user) {
        Submission submission = loadAndAuthorise(submissionId, user);
        List<MediaAsset> attached = submissionMediaAssetRepository.findMediaAssetsBySubmissionId(submissionId);

        if (attached.isEmpty()) {
            return List.of();
        }

        OptionalVector queryVector = firstAvailableEmbedding(attached);
        if (queryVector.value() == null) {
            return List.of();
        }

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
     * Suggests media assets from the institution library based on submission
     * context.
     *
     * The expensive image scan was already done when media was uploaded. At
     * request time we reuse every available attached IMAGE embedding, optionally
     * embed the contributor's text context once, and re-rank a bounded candidate
     * set with deterministic metadata boosts.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<MediaSuggestResultDto> suggestMedia(UUID submissionId, MediaSuggestRequestDto dto, JwtUserDetails user) {
        Submission submission = loadAndAuthorise(submissionId, user);
        UUID institutionId = submission.getInstitution().getId();

        List<MediaAsset> attachedAssets = submissionMediaAssetRepository.findMediaAssetsBySubmissionId(submissionId);
        Set<UUID> attachedIds = attachedAssets.stream().map(MediaAsset::getId).collect(Collectors.toSet());
        Map<UUID, List<TagSignal>> attachedTagMap = loadTagSignalMap(attachedAssets.stream().map(MediaAsset::getId).toList());

        Map<UUID, Double> visualScores = loadAttachedCandidateScores(
                institutionId, attachedIds, MediaAssetEmbeddingType.IMAGE, submissionId);
        Map<UUID, Double> selectedMediaSemanticScores = loadAttachedCandidateScores(
                institutionId, attachedIds, MediaAssetEmbeddingType.SEMANTIC, submissionId);
        Map<UUID, Double> textSemanticScores = loadTextSemanticCandidateScores(
                institutionId, attachedAssets, attachedTagMap, dto, submissionId);
        Map<UUID, Double> semanticScores = combineSemanticScores(
                selectedMediaSemanticScores, textSemanticScores);

        List<UUID> candidateIds = new ArrayList<>(visualScores.keySet());
        semanticScores.keySet().stream()
                .filter(id -> !visualScores.containsKey(id))
                .forEach(candidateIds::add);
        if (candidateIds.isEmpty()) {
            return fallbackSuggestions(institutionId, attachedIds, dto);
        }

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
                visualScores.getOrDefault(id, 0.0),
                dto,
                tagMap.getOrDefault(id, List.of())
        ))
                .filter(result -> result.score() >= 0.40)
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

    private Map<UUID, Double> loadAttachedCandidateScores(
            UUID institutionId,
            Set<UUID> attachedIds,
            MediaAssetEmbeddingType embeddingType,
            UUID submissionId) {
        if (attachedIds.isEmpty()) {
            return Map.of();
        }
        try {
            return scoreMap(mediaAssetEmbeddingRepository.findTopSimilarToAssetsWithScore(
                    institutionId,
                    embeddingType,
                    List.copyOf(attachedIds),
                    ATTACHED_CANDIDATES_PER_ASSET,
                    SUGGESTION_CANDIDATE_LIMIT));
        } catch (RuntimeException e) {
            // Attached-media retrieval is additive. Preserve the established
            // text path if a transient database error occurs.
            log.warn("{} media retrieval failed for submission {}: {}",
                    embeddingType, submissionId, e.getMessage());
            return Map.of();
        }
    }

    private Map<UUID, Double> loadTextSemanticCandidateScores(
            UUID institutionId,
            List<MediaAsset> attachedAssets,
            Map<UUID, List<TagSignal>> attachedTagMap,
            MediaSuggestRequestDto dto,
            UUID submissionId) {
        if (!hasTextContext(dto)) {
            return Map.of();
        }
        String embeddingText = buildQueryEmbeddingText(dto, attachedAssets, attachedTagMap);
        try {
            String queryVector = voyageAIClient.embedQuery(embeddingText);
            return scoreMap(mediaAssetEmbeddingRepository.findTopSimilarWithScore(
                    institutionId,
                    MediaAssetEmbeddingType.SEMANTIC,
                    queryVector,
                    SUGGESTION_CANDIDATE_LIMIT));
        } catch (Exception e) {
            log.warn("Voyage AI embedding failed for suggest-media on submission {}: {}", submissionId, e.getMessage());
            return Map.of();
        }
    }

    private static Map<UUID, Double> combineSemanticScores(
            Map<UUID, Double> selectedMediaScores,
            Map<UUID, Double> textScores) {
        Map<UUID, Double> combined = new LinkedHashMap<>(selectedMediaScores);
        textScores.forEach((id, textScore) -> combined.merge(
                id,
                textScore,
                (selectedScore, existingTextScore) -> (0.40 * selectedScore) + (0.60 * existingTextScore)));
        return combined;
    }

    private static Map<UUID, Double> scoreMap(List<Object[]> rows) {
        Map<UUID, Double> scores = new LinkedHashMap<>();
        for (Object[] row : rows) {
            UUID id = toUuid(row[0]);
            double score = row[1] instanceof Number number ? number.doubleValue() : 0.0;
            scores.merge(id, score, Math::max);
        }
        return scores;
    }

    private static boolean hasTextContext(MediaSuggestRequestDto dto) {
        return !normalize(dto.getEventTitle()).isBlank()
                || !normalize(dto.getCaption()).isBlank()
                || !normalize(dto.getCategory()).isBlank()
                || (dto.getTags() != null && dto.getTags().stream().anyMatch(tag -> !normalize(tag).isBlank()));
    }

    // NOT the same feature as UploadModal.tsx's Auto-Match (UC-2.1,
    // TEXT_MATCH_CONFIDENT/TEXT_MATCH_AMBIGUOUS = 0.6/0.3) — that one has no
    // visual signal available (the file isn't uploaded/embedded yet at
    // album-selection time) and scores pure tag/filename text overlap. Both
    // happen to produce a 0-1 score and land in the same confident/ambiguous/
    // none shape, but the numbers are calibrated independently for two
    // different scoring formulas. Do not "sync" these thresholds with that
    // one — a shared value would be a coincidence, not a contract.
    private static final double ALBUM_MATCH_CONFIDENT_THRESHOLD = 0.55;
    private static final double ALBUM_MATCH_AMBIGUOUS_THRESHOLD = 0.32;
    private static final double ALBUM_MATCH_EMBEDDING_WEIGHT = 0.65;
    private static final double ALBUM_MATCH_TAG_WEIGHT = 0.35;

    /**
     * Album Auto-Match (UC-1.7): ranks the institution's existing root albums
     * against the draft's current context — a blend of "closest existing asset"
     * visual similarity (Voyage embedding + pgvector, when available) and tag
     * overlap. Scoring is on the same 0-1 scale as {@link #suggestMedia} but
     * the bands mean something different here since the caller applies the
     * result directly rather than just listing options:
     * <ul>
     * <li>{@code confident} (&ge; {@value #ALBUM_MATCH_CONFIDENT_THRESHOLD}) —
     * top candidate only.</li>
     * <li>{@code ambiguous} (&ge; {@value #ALBUM_MATCH_AMBIGUOUS_THRESHOLD}) —
     * up to 3 ranked candidates.</li>
     * <li>{@code none} — no root album yet, or nothing cleared the ambiguous
     * floor.</li>
     * </ul>
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AlbumMatchResponseDto suggestAlbum(UUID submissionId, AlbumMatchRequestDto dto, JwtUserDetails user) {
        Submission submission = loadAndAuthoriseForAlbumMatch(submissionId, user);
        UUID institutionId = submission.getInstitution().getId();

        List<MediaAlbum> rootAlbums = mediaAlbumRepository.findByInstitutionIdOrderByName(institutionId)
                .stream()
                .filter(album -> album.getParentAlbum() == null)
                .toList();
        if (rootAlbums.isEmpty()) {
            return AlbumMatchResponseDto.of(AlbumMatchResponseDto.Status.none, List.of());
        }

        Set<String> draftTags = normalizedAlbumMatchTerms(dto);
        Map<UUID, Set<String>> albumTagMap = loadRootAlbumTagMap(institutionId);

        Map<UUID, Double> embeddingScores = Map.of();
        String embeddingText = buildAlbumMatchEmbeddingText(dto);
        if (!embeddingText.isBlank()) {
            try {
                String queryVector = voyageAIClient.embedQuery(embeddingText);
                embeddingScores = mediaAssetEmbeddingRepository
                        .findMaxSimilarityByRootAlbum(institutionId, MediaAssetEmbeddingType.SEMANTIC, queryVector)
                        .stream()
                        .collect(Collectors.toMap(
                                row -> toUuid(row[0]),
                                row -> row[1] instanceof Number number ? number.doubleValue() : 0.0));
            } catch (Exception e) {
                log.warn("Voyage AI embedding failed for album Auto-Match on submission {}: {}", submissionId, e.getMessage());
            }
        }

        boolean hasEmbeddingSignal = !embeddingScores.isEmpty();
        Map<UUID, Double> scores = embeddingScores;
        List<AlbumMatchCandidateDto> ranked = rootAlbums.stream()
                .map(album -> scoreAlbumMatch(
                album,
                draftTags,
                albumTagMap.getOrDefault(album.getId(), Set.of()),
                scores.getOrDefault(album.getId(), 0.0),
                hasEmbeddingSignal))
                .filter(candidate -> candidate.getScore() >= ALBUM_MATCH_AMBIGUOUS_THRESHOLD)
                .sorted(Comparator.comparingDouble(AlbumMatchCandidateDto::getScore).reversed())
                .limit(3)
                .toList();

        if (ranked.isEmpty()) {
            return AlbumMatchResponseDto.of(AlbumMatchResponseDto.Status.none, List.of());
        }
        if (ranked.get(0).getScore() >= ALBUM_MATCH_CONFIDENT_THRESHOLD) {
            return AlbumMatchResponseDto.of(AlbumMatchResponseDto.Status.confident, List.of(ranked.get(0)));
        }
        return AlbumMatchResponseDto.of(AlbumMatchResponseDto.Status.ambiguous, ranked);
    }

    private AlbumMatchCandidateDto scoreAlbumMatch(MediaAlbum album, Set<String> draftTags, Set<String> albumTags,
            double embeddingScore, boolean hasEmbeddingSignal) {
        Set<String> overlap = new LinkedHashSet<>(draftTags);
        overlap.retainAll(albumTags);
        double tagScore = draftTags.isEmpty() ? 0.0 : Math.min(1.0, overlap.size() / (double) draftTags.size());

        double score = hasEmbeddingSignal
                ? (ALBUM_MATCH_EMBEDDING_WEIGHT * embeddingScore) + (ALBUM_MATCH_TAG_WEIGHT * tagScore)
                : tagScore;

        List<String> reasons = new ArrayList<>();
        if (hasEmbeddingSignal && embeddingScore > 0) {
            reasons.add("Visually similar to existing media in \"" + album.getName() + "\".");
        }
        if (!overlap.isEmpty()) {
            reasons.add("Shares tag" + (overlap.size() > 1 ? "s " : " ") + String.join(", ", overlap) + ".");
        }
        return AlbumMatchCandidateDto.of(album.getId(), album.getName(), score, reasons);
    }

    private Map<UUID, Set<String>> loadRootAlbumTagMap(UUID institutionId) {
        Map<UUID, Set<String>> map = new HashMap<>();
        for (Object[] row : assetTagRepository.findLabelsByRootAlbumForInstitution(institutionId)) {
            UUID albumId = toUuid(row[0]);
            String label = row[1] instanceof String s ? s : null;
            if (label != null && !label.isBlank()) {
                map.computeIfAbsent(albumId, ignored -> new LinkedHashSet<>()).add(normalize(label));
            }
        }
        return map;
    }

    private static Set<String> normalizedAlbumMatchTerms(AlbumMatchRequestDto dto) {
        Set<String> terms = new LinkedHashSet<>();
        addAllTerms(terms, dto.getTags());
        addLooseWords(terms, dto.getEventTitle());
        return terms;
    }

    private static String buildAlbumMatchEmbeddingText(AlbumMatchRequestDto dto) {
        StringBuilder sb = new StringBuilder();
        append(sb, "event_title", dto.getEventTitle());
        append(sb, "caption", dto.getCaption());
        appendAll(sb, "tags", dto.getTags());
        return sb.toString().trim();
    }

    private Submission loadAndAuthoriseForAlbumMatch(UUID submissionId, JwtUserDetails user) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found."));
        // institutionId is null for moderators/admins — they act network-wide.
        UUID institutionId = user.institutionId();
        if (institutionId != null && !submission.getInstitution().getId().equals(institutionId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Submission does not belong to your institution.");
        }
        return submission;
    }

    @Transactional
    public void logInteraction(UUID submissionId, UUID institutionId, String type, String actionTaken) {
        try {
            // Admin composers have no institution of their own — fall back to the
            // submission's institution (ai_interaction_log.institution_id is NOT NULL).
            UUID resolvedInstitutionId = institutionId != null ? institutionId
                    : submissionRepository.findById(submissionId)
                            .map(s -> s.getInstitution() != null ? s.getInstitution().getId() : null)
                            .orElse(null);
            if (resolvedInstitutionId == null) {
                log.warn("Skipping AI interaction log for submission {}: no institution context", submissionId);
                return;
            }
            AiInteractionLog entry = new AiInteractionLog();
            entry.setSubmissionId(submissionId);
            entry.setInstitutionId(resolvedInstitutionId);
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

    private static RankedAsset rankAsset(MediaAsset asset, double semanticScore,
            MediaSuggestRequestDto dto, Collection<TagSignal> assetTags) {
        return rankAsset(asset, semanticScore, 0.0, dto, assetTags);
    }

    private static RankedAsset rankAsset(MediaAsset asset, double semanticScore, double visualScore,
            MediaSuggestRequestDto dto, Collection<TagSignal> assetTags) {
        boolean hasSemanticSignal = semanticScore > 0.0;
        boolean hasVisualSignal = visualScore > 0.0;
        double score = hasSemanticSignal && hasVisualSignal
                ? (0.55 * semanticScore) + (0.45 * visualScore)
                : Math.max(semanticScore, visualScore);
        List<String> reasons = new ArrayList<>();
        Set<String> queryTerms = normalizedTerms(dto);

        if (semanticScore >= 0.75) {
            reasons.add("Strong semantic similarity to the title, caption, or tags.");
        } else if (semanticScore >= 0.55) {
            reasons.add("Similar to the post context from the title or caption.");
        } else if (semanticScore >= 0.40) {
            reasons.add("Some semantic overlap with the post context.");
        }

        if (visualScore >= 0.75) {
            reasons.add("Strong visual similarity to the selected media.");
        } else if (visualScore >= 0.55) {
            reasons.add("Visually similar to the selected media.");
        } else if (visualScore >= 0.40) {
            reasons.add("Some visual overlap with the selected media.");
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
        boolean networkWide = "admin".equalsIgnoreCase(user.role())
                || "moderator".equalsIgnoreCase(user.role());
        boolean sameInstitution = user.institutionId() != null
                && submission.getInstitution().getId().equals(user.institutionId());
        boolean ownsSubmission = submission.getContributor().getId().equals(user.userId());
        if (!networkWide && (!sameInstitution
                || ("contributor".equalsIgnoreCase(user.role()) && !ownsSubmission))) {
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
            if (embedding != null) {
                return new OptionalVector(embedding);
            }
        }
        return new OptionalVector(null);
    }

    private Map<UUID, List<TagSignal>> loadTagSignalMap(List<UUID> assetIds) {
        if (assetIds.isEmpty()) {
            return Map.of();
        }
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
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<UUID> candidateIds = candidates.stream().map(MediaAsset::getId).toList();
        Map<UUID, List<TagSignal>> tagMap = loadTagSignalMap(candidateIds);

        return candidates.stream()
                .map(asset -> rankAsset(
                asset,
                0.35,
                dto,
                tagMap.getOrDefault(asset.getId(), List.of())
        ))
                .filter(result -> result.score() >= 0.40)
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
        if (value == null || value.isBlank()) {
            return;
        }
        sb.append(label).append(": ").append(value.trim()).append(". ");
    }

    private static void appendAll(StringBuilder sb, String label, Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
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
        if (values == null) {
            return;
        }
        values.forEach(value -> addTerm(terms, value));
    }

    private static void addTerm(Set<String> terms, String value) {
        String normalized = normalize(value);
        if (!normalized.isBlank()) {
            terms.add(normalized);
        }
    }

    private static void addLooseWords(Set<String> terms, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String word : value.split("[^A-Za-z0-9]+")) {
            if (word.length() >= 3) {
                addTerm(terms, word);
            }
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
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        return base.replace('_', ' ').replace('-', ' ').trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static UUID toUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(String.valueOf(value));
    }

    private record OptionalVector(String value) {

    }

    private record TagSignal(String label, String source) {

        boolean manual() {
            return source == null || source.equalsIgnoreCase("manual");
        }
    }

    private record RankedAsset(MediaAsset asset, double score, List<String> reasons) {

    }
}
