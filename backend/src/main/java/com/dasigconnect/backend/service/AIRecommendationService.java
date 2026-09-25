package com.dasigconnect.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Value;
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
import com.dasigconnect.backend.model.entity.SubmissionMediaContext;
import com.dasigconnect.backend.repository.AiInteractionLogRepository;
import com.dasigconnect.backend.repository.AssetTagRepository;
import com.dasigconnect.backend.repository.MediaAlbumRepository;
import com.dasigconnect.backend.repository.MediaAssetEmbeddingRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionMediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionMediaContextRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private static final String LEGACY_RANKING_VERSION = "legacy-v1";
    private static final String HYBRID_RANKING_VERSION = "hybrid-v1";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SubmissionRepository submissionRepository;
    private final SubmissionMediaAssetRepository submissionMediaAssetRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final MediaAssetEmbeddingRepository mediaAssetEmbeddingRepository;
    private final AssetTagRepository assetTagRepository;
    private final AiInteractionLogRepository aiInteractionLogRepository;
    private final VoyageAIClient voyageAIClient;
    private final MediaAlbumRepository mediaAlbumRepository;
    private final SubmissionMediaContextRepository submissionMediaContextRepository;
    private final boolean visualSuggestionsEnabled;
    private final boolean hybridRankingEnabled;
    private final boolean hybridShadowEnabled;

    public AIRecommendationService(SubmissionRepository submissionRepository,
            SubmissionMediaAssetRepository submissionMediaAssetRepository,
            MediaAssetRepository mediaAssetRepository,
            MediaAssetEmbeddingRepository mediaAssetEmbeddingRepository,
            AssetTagRepository assetTagRepository,
            AiInteractionLogRepository aiInteractionLogRepository,
            VoyageAIClient voyageAIClient,
            MediaAlbumRepository mediaAlbumRepository,
            SubmissionMediaContextRepository submissionMediaContextRepository,
            @Value("${app.ai.media-suggestions.visual-enabled:true}") boolean visualSuggestionsEnabled,
            @Value("${app.ai.media-suggestions.hybrid-ranking-enabled:false}") boolean hybridRankingEnabled,
            @Value("${app.ai.media-suggestions.hybrid-shadow-enabled:true}") boolean hybridShadowEnabled) {
        this.submissionRepository = submissionRepository;
        this.submissionMediaAssetRepository = submissionMediaAssetRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.mediaAssetEmbeddingRepository = mediaAssetEmbeddingRepository;
        this.assetTagRepository = assetTagRepository;
        this.aiInteractionLogRepository = aiInteractionLogRepository;
        this.voyageAIClient = voyageAIClient;
        this.mediaAlbumRepository = mediaAlbumRepository;
        this.submissionMediaContextRepository = submissionMediaContextRepository;
        this.visualSuggestionsEnabled = visualSuggestionsEnabled;
        this.hybridRankingEnabled = hybridRankingEnabled;
        this.hybridShadowEnabled = hybridShadowEnabled;
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
        List<MediaAsset> selectedImageAssets = resolveSelectedImageAssets(dto, attachedAssets, attachedIds);
        Set<UUID> selectedImageIds = selectedImageAssets.stream()
                .map(MediaAsset::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, List<TagSignal>> selectedTagMap = loadTagSignalMap(List.copyOf(selectedImageIds));

        Map<UUID, Double> visualScores = visualSuggestionsEnabled
                ? loadAttachedCandidateScores(
                        institutionId, selectedImageIds, MediaAssetEmbeddingType.IMAGE, submissionId)
                : Map.of();
        Map<UUID, Double> selectedMediaSemanticScores = visualSuggestionsEnabled
                ? loadAttachedCandidateScores(
                        institutionId, selectedImageIds, MediaAssetEmbeddingType.SEMANTIC, submissionId)
                : Map.of();
        Map<UUID, Double> textSemanticScores = loadTextSemanticCandidateScores(
                institutionId, selectedImageAssets, selectedTagMap, dto, submissionId);
        Map<UUID, Double> semanticScores = combineSemanticScores(
                selectedMediaSemanticScores, textSemanticScores);

        List<UUID> candidateIds = new ArrayList<>(visualScores.keySet());
        semanticScores.keySet().stream()
                .filter(id -> !visualScores.containsKey(id))
                .forEach(candidateIds::add);
        if (candidateIds.isEmpty()) {
            return fallbackOrEmpty(institutionId, attachedIds, dto);
        }

        Map<UUID, MediaAsset> assetMap = mediaAssetRepository.findActiveByIds(candidateIds)
                .stream()
                .collect(Collectors.toMap(MediaAsset::getId, asset -> asset));
        Map<UUID, List<TagSignal>> tagMap = loadTagSignalMap(candidateIds);
        Map<UUID, UsageSignal> usageMap = loadUsageSignalMap(candidateIds);
        SubmissionMediaContext mediaContext = submissionMediaContextRepository.findById(submissionId)
                .filter(context -> institutionId.equals(context.getInstitutionId()))
                .orElse(null);
        List<RankedAsset> legacyRanking = rankCandidates(
                candidateIds, assetMap, attachedIds, semanticScores, visualScores,
                dto, tagMap, usageMap, selectedImageAssets, mediaContext, false);
        boolean hybridEnabledForInstitution = hybridRankingEnabled
                && submission.getInstitution().isAiMediaHybridRankingEnabled();
        List<RankedAsset> hybridRanking = hybridEnabledForInstitution || hybridShadowEnabled
                ? rankCandidates(candidateIds, assetMap, attachedIds, semanticScores, visualScores,
                        dto, tagMap, usageMap, selectedImageAssets, mediaContext, true)
                : List.of();
        if (hybridShadowEnabled) {
            logShadowComparison(submissionId, legacyRanking, hybridRanking);
        }

        List<RankedAsset> selectedRanking = hybridEnabledForInstitution ? hybridRanking : legacyRanking;
        String rankingVersion = hybridEnabledForInstitution ? HYBRID_RANKING_VERSION : LEGACY_RANKING_VERSION;
        List<MediaSuggestResultDto> rankedResults = selectedRanking.stream()
                .limit(8)
                .map(result -> MediaSuggestResultDto.from(
                        result.asset(), result.score(), result.reasons(), rankingVersion))
                .toList();

        if (rankedResults.isEmpty()) {
            if (hasTextContext(dto)) {
                log.info("No suggest-media candidates remained for submission {}; using metadata fallback.",
                        submissionId);
            } else {
                log.info("No visual media candidates are ready for submission {}.", submissionId);
            }
            return fallbackOrEmpty(institutionId, attachedIds, dto);
        }

        return rankedResults;
    }

    private static List<MediaAsset> resolveSelectedImageAssets(
            MediaSuggestRequestDto dto,
            List<MediaAsset> attachedAssets,
            Set<UUID> attachedIds) {
        Set<UUID> requestedIds = dto.getSelectedAssetIds() == null
                ? Set.of()
                : new LinkedHashSet<>(dto.getSelectedAssetIds());
        if (!requestedIds.isEmpty() && !attachedIds.containsAll(requestedIds)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "selectedAssetIds must reference media attached to this submission.");
        }

        return attachedAssets.stream()
                .filter(asset -> requestedIds.isEmpty() || requestedIds.contains(asset.getId()))
                .filter(asset -> asset.getFileType() != null && asset.getFileType().isImage())
                .toList();
    }

    private static List<RankedAsset> rankCandidates(
            List<UUID> candidateIds,
            Map<UUID, MediaAsset> assetMap,
            Set<UUID> attachedIds,
            Map<UUID, Double> semanticScores,
            Map<UUID, Double> visualScores,
            MediaSuggestRequestDto dto,
            Map<UUID, List<TagSignal>> tagMap,
            Map<UUID, UsageSignal> usageMap,
            List<MediaAsset> attachedAssets,
            SubmissionMediaContext mediaContext,
            boolean hybrid) {
        List<RankedAsset> ranked = candidateIds.stream()
                .filter(assetMap::containsKey)
                .filter(id -> !attachedIds.contains(id))
                .filter(id -> isTemporallyEligible(assetMap.get(id), Instant.now()))
                .map(id -> hybrid
                        ? rankAssetHybrid(
                                assetMap.get(id),
                                semanticScores.getOrDefault(id, 0.0),
                                visualScores.getOrDefault(id, 0.0),
                                dto,
                                tagMap.getOrDefault(id, List.of()),
                                usageMap.getOrDefault(id, UsageSignal.NEVER_USED),
                                attachedAssets,
                                mediaContext)
                        : rankAsset(
                                assetMap.get(id),
                                semanticScores.getOrDefault(id, 0.0),
                                visualScores.getOrDefault(id, 0.0),
                                dto,
                                tagMap.getOrDefault(id, List.of())))
                .filter(result -> result.score() >= 0.40)
                .sorted(rankedAssetComparator())
                .toList();
        return hybrid ? diversify(ranked, 8) : ranked;
    }

    private static Comparator<RankedAsset> rankedAssetComparator() {
        return Comparator.comparingDouble(RankedAsset::score).reversed()
                .thenComparing(result -> result.asset().getId());
    }

    private static void logShadowComparison(
            UUID submissionId, List<RankedAsset> legacy, List<RankedAsset> hybrid) {
        List<UUID> legacyTop = legacy.stream().limit(8).map(result -> result.asset().getId()).toList();
        List<UUID> hybridTop = hybrid.stream().limit(8).map(result -> result.asset().getId()).toList();
        long overlap = hybridTop.stream().filter(legacyTop::contains).count();
        boolean topChanged = !legacyTop.isEmpty() && !hybridTop.isEmpty()
                && !legacyTop.getFirst().equals(hybridTop.getFirst());
        log.info("Media ranking shadow comparison submission={} legacy={} hybrid={} overlap={} topChanged={}",
                submissionId, legacyTop.size(), hybridTop.size(), overlap, topChanged);
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
                    submissionId,
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

    private static RankedAsset rankAssetHybrid(
            MediaAsset asset,
            double semanticScore,
            double visualScore,
            MediaSuggestRequestDto dto,
            Collection<TagSignal> assetTags,
            UsageSignal usage,
            List<MediaAsset> attachedAssets,
            SubmissionMediaContext mediaContext) {
        Set<String> candidateTerms = structuredAssetTerms(asset, assetTags);
        Set<String> postTerms = normalizedTerms(dto);
        Set<String> contextTerms = new LinkedHashSet<>();
        if (mediaContext != null && mediaContext.getReadyAssetCount() > 0) {
            addLooseWords(contextTerms, mediaContext.getContextText());
        }

        double contextScore = overlapScore(contextTerms, candidateTerms);
        double metadataScore = overlapScore(postTerms, candidateTerms);
        String requestedCategory = normalize(dto.getCategory());
        if (!requestedCategory.isBlank() && requestedCategory.equals(normalize(asset.getAiCategory()))) {
            metadataScore = Math.max(metadataScore, 0.90);
        }
        double freshnessScore = freshnessScore(asset);
        double usageScore = usageDiversityScore(usage.count(), usage.lastUsedAt(), Instant.now());
        double qualityScore = qualityScore(asset);
        double sequenceScore = sequenceComplementScore(asset, attachedAssets);

        List<WeightedSignal> signals = new ArrayList<>();
        addSignal(signals, semanticScore, 0.30, semanticScore > 0.0);
        addSignal(signals, visualScore, 0.32, visualScore > 0.0);
        addSignal(signals, contextScore, 0.14, !contextTerms.isEmpty());
        addSignal(signals, metadataScore, 0.10, !postTerms.isEmpty());
        addSignal(signals, freshnessScore, 0.04, true);
        addSignal(signals, usageScore, 0.04, true);
        addSignal(signals, qualityScore, 0.05, qualityScore >= 0.0);
        addSignal(signals, sequenceScore, 0.05, sequenceScore >= 0.0);

        double totalWeight = signals.stream().mapToDouble(WeightedSignal::weight).sum();
        double weightedScore = signals.stream()
                .mapToDouble(signal -> signal.value() * signal.weight())
                .sum();
        double score = totalWeight == 0.0 ? 0.0 : weightedScore / totalWeight;

        List<String> reasons = new ArrayList<>();
        addSimilarityReason(reasons, semanticScore, "semantic", "post context");
        addSimilarityReason(reasons, visualScore, "visual", "selected media");
        if (contextScore >= 0.18) {
            List<String> matches = matchingTerms(contextTerms, candidateTerms, 3);
            reasons.add(matches.isEmpty()
                    ? "Matches the selected media's event context."
                    : "Matches event context: " + String.join(", ", matches) + ".");
        }
        if (metadataScore >= 0.25) {
            reasons.add("Metadata aligns with the post details.");
        }
        if (qualityScore >= 0.70) {
            reasons.add("Visual quality signals support publication use.");
        }
        if (sequenceScore >= 0.60) {
            reasons.add("Adds a complementary scene or composition to the selected sequence.");
        }
        if (usage.count() == 0) {
            reasons.add("Adds variety because this asset has not been used in another submission.");
        }
        if (reasons.isEmpty()) {
            reasons.add("Ranked from available visual and media context signals.");
        }

        return new RankedAsset(asset, clamp(score), List.copyOf(reasons));
    }

    private static void addSignal(
            List<WeightedSignal> signals, double value, double weight, boolean available) {
        if (available) {
            signals.add(new WeightedSignal(clamp(value), weight));
        }
    }

    private static void addSimilarityReason(
            List<String> reasons, double score, String signalName, String target) {
        if (score >= 0.75) {
            reasons.add("Strong " + signalName + " similarity to the " + target + ".");
        } else if (score >= 0.55) {
            reasons.add("Good " + signalName + " similarity to the " + target + ".");
        } else if (score >= 0.40) {
            reasons.add("Some " + signalName + " overlap with the " + target + ".");
        }
    }

    static double freshnessScore(MediaAsset asset) {
        if ("evergreen".equals(normalize(asset.getTemporalClassification()))) return 1.0;
        if (asset.getCreatedAt() == null) return 0.50;
        long ageDays = Math.max(0, Duration.between(asset.getCreatedAt(), Instant.now()).toDays());
        if (ageDays <= 30) return 1.0;
        if (ageDays <= 90) return 0.75;
        if (ageDays <= 365) return 0.45;
        return 0.20;
    }

    static double usageDiversityScore(long usageCount, Instant lastUsedAt, Instant now) {
        if (usageCount == 0) return 1.0;
        long daysSinceUse = lastUsedAt == null
                ? Long.MAX_VALUE
                : Math.max(0, Duration.between(lastUsedAt, now).toDays());
        if (daysSinceUse <= 7 && usageCount >= 3) return 0.20;
        if (usageCount >= 10) return 0.35;
        if (daysSinceUse <= 30 || usageCount >= 3) return 0.60;
        return 0.85;
    }

    static boolean isTemporallyEligible(MediaAsset asset, Instant now) {
        if (asset == null) return false;
        if ("expired".equals(normalize(asset.getTemporalClassification()))) return false;
        String expiration = normalize(asset.getPossibleExpiration());
        if (expiration.isBlank()) return true;
        try {
            return Instant.parse(expiration).isAfter(now);
        } catch (Exception ignored) {
            try {
                return LocalDate.parse(expiration).plusDays(1)
                        .atStartOfDay(ZoneOffset.UTC).toInstant().isAfter(now);
            } catch (Exception ignoredDate) {
                // Free-form or uncertain legacy values remain eligible.
                return true;
            }
        }
    }

    private static double qualityScore(MediaAsset asset) {
        String[] qualitySignals = asset.getVisualQualitySignals();
        if (qualitySignals == null || qualitySignals.length == 0) return -1.0;
        int positive = 0;
        int negative = 0;
        for (String raw : qualitySignals) {
            String signal = normalize(raw);
            if (containsAny(signal, "sharp", "clear", "well lit", "balanced", "good contrast", "focused")) {
                positive++;
            }
            if (containsAny(signal, "blurry", "blur", "dark", "overexposed", "underexposed",
                    "low quality", "obstructed")) {
                negative++;
            }
        }
        return clamp(0.60 + (positive * 0.15) - (negative * 0.20));
    }

    private static boolean containsAny(String value, String... fragments) {
        return Arrays.stream(fragments).anyMatch(value::contains);
    }

    private static double sequenceComplementScore(MediaAsset candidate, List<MediaAsset> attachedAssets) {
        if (attachedAssets == null || attachedAssets.isEmpty()) return -1.0;
        Set<String> candidateVisualTerms = visualTerms(candidate);
        if (candidateVisualTerms.isEmpty()) return -1.0;
        Set<String> selectedTerms = new LinkedHashSet<>();
        attachedAssets.forEach(asset -> selectedTerms.addAll(visualTerms(asset)));
        long novelTerms = candidateVisualTerms.stream().filter(term -> !selectedTerms.contains(term)).count();
        double novelty = (double) novelTerms / candidateVisualTerms.size();
        return clamp(0.30 + (0.70 * novelty));
    }

    private static Set<String> structuredAssetTerms(MediaAsset asset, Collection<TagSignal> tags) {
        Set<String> terms = normalizedAssetTerms(asset);
        tags.stream().map(TagSignal::label).forEach(value -> addLooseWords(terms, value));
        terms.addAll(visualTerms(asset));
        addEventHypothesisTerms(terms, asset.getEventHypotheses());
        return terms;
    }

    private static Set<String> visualTerms(MediaAsset asset) {
        Set<String> terms = new LinkedHashSet<>();
        addArrayTerms(terms, asset.getVisibleObjects());
        addArrayTerms(terms, asset.getSpecificSubjects());
        addArrayTerms(terms, asset.getObservedScenes());
        addArrayTerms(terms, asset.getObservedActivities());
        addArrayTerms(terms, asset.getEquipmentSignals());
        addArrayTerms(terms, asset.getRecognitionSignals());
        addArrayTerms(terms, asset.getCompositionSignals());
        return terms;
    }

    private static void addArrayTerms(Set<String> terms, String[] values) {
        if (values == null) return;
        Arrays.stream(values).forEach(value -> addLooseWords(terms, value));
    }

    private static void addEventHypothesisTerms(Set<String> terms, String eventHypotheses) {
        if (eventHypotheses == null || eventHypotheses.isBlank()) return;
        try {
            JsonNode values = OBJECT_MAPPER.readTree(eventHypotheses);
            if (!values.isArray()) return;
            values.forEach(value -> addLooseWords(terms,
                    value.path("eventType").asText(value.path("event_type").asText(""))));
        } catch (Exception ignored) {
            // Legacy malformed metadata must not block recommendations.
        }
    }

    private static double overlapScore(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) return 0.0;
        long overlap = left.stream().filter(right::contains).count();
        return clamp(overlap / Math.sqrt((double) left.size() * right.size()));
    }

    private static List<String> matchingTerms(Set<String> left, Set<String> right, int limit) {
        return left.stream().filter(right::contains).sorted().limit(limit).toList();
    }

    private static List<RankedAsset> diversify(List<RankedAsset> ranked, int limit) {
        if (ranked.size() <= 1) return ranked.stream().limit(limit).toList();
        List<RankedAsset> remaining = new ArrayList<>(ranked);
        List<RankedAsset> selected = new ArrayList<>();
        while (!remaining.isEmpty() && selected.size() < limit) {
            RankedAsset best = null;
            double bestMmr = Double.NEGATIVE_INFINITY;
            for (RankedAsset candidate : remaining) {
                double maxSimilarity = selected.stream()
                        .mapToDouble(chosen -> candidateSimilarity(candidate.asset(), chosen.asset()))
                        .max()
                        .orElse(0.0);
                double mmr = (0.85 * candidate.score()) - (0.15 * maxSimilarity);
                if (best == null || mmr > bestMmr
                        || (Math.abs(mmr - bestMmr) < 0.000001
                            && candidate.asset().getId().compareTo(best.asset().getId()) < 0)) {
                    best = candidate;
                    bestMmr = mmr;
                }
            }
            selected.add(best);
            remaining.remove(best);
        }
        return List.copyOf(selected);
    }

    private static double candidateSimilarity(MediaAsset left, MediaAsset right) {
        if (left.getContentHash() != null && left.getContentHash().equals(right.getContentHash())) {
            return 1.0;
        }
        Set<String> leftTerms = visualTerms(left);
        Set<String> rightTerms = visualTerms(right);
        Set<String> union = new LinkedHashSet<>(leftTerms);
        union.addAll(rightTerms);
        long intersection = leftTerms.stream().filter(rightTerms::contains).count();
        double termSimilarity = union.isEmpty() ? 0.0 : (double) intersection / union.size();
        double categorySimilarity = !normalize(left.getAiCategory()).isBlank()
                && normalize(left.getAiCategory()).equals(normalize(right.getAiCategory())) ? 1.0 : 0.0;
        return (0.75 * termSimilarity) + (0.25 * categorySimilarity);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
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

    private Map<UUID, UsageSignal> loadUsageSignalMap(List<UUID> assetIds) {
        if (assetIds.isEmpty()) return Map.of();
        Map<UUID, UsageSignal> result = new HashMap<>();
        for (Object[] row : submissionMediaAssetRepository.findUsageStatsByMediaAssetIds(assetIds)) {
            UUID assetId = toUuid(row[0]);
            long count = row[1] instanceof Number number ? number.longValue() : 0L;
            result.put(assetId, new UsageSignal(count, toInstant(row[2])));
        }
        return result;
    }

    private List<MediaSuggestResultDto> fallbackSuggestions(UUID institutionId, Set<UUID> attachedIds, MediaSuggestRequestDto dto) {
        List<MediaAsset> candidates = mediaAssetRepository
                .findVisibleReadyByInstitution(institutionId, PageRequest.of(0, 30))
                .stream()
                .filter(asset -> !attachedIds.contains(asset.getId()))
                .filter(asset -> isTemporallyEligible(asset, Instant.now()))
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

    private List<MediaSuggestResultDto> fallbackOrEmpty(
            UUID institutionId, Set<UUID> attachedIds, MediaSuggestRequestDto dto) {
        // In a visual-only request, an empty vector result commonly means the
        // selected images are not attached yet or their embeddings are still processing. Returning generic
        // metadata matches would stop the frontend's bounded visual retry and
        // make those images appear to influence results when they did not.
        if (!hasTextContext(dto)) {
            return List.of();
        }
        return fallbackSuggestions(institutionId, attachedIds, dto);
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

    private static Instant toInstant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime dateTime) return dateTime.toInstant();
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        return null;
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

    private record WeightedSignal(double value, double weight) {

    }

    private record UsageSignal(long count, Instant lastUsedAt) {
        private static final UsageSignal NEVER_USED = new UsageSignal(0, null);
    }
}
