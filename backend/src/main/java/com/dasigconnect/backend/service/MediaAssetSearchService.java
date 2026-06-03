package com.dasigconnect.backend.service;

import com.dasigconnect.backend.external.VoyageAIClient;
import com.dasigconnect.backend.model.dto.media.MediaAssetSearchRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetSearchResponseDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetSearchResultDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetSummaryDto;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetEmbeddingType;
import com.dasigconnect.backend.repository.MediaAssetEmbeddingRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.MediaAssetSearchHit;
import com.dasigconnect.backend.repository.MediaAssetSearchRepository;
import com.dasigconnect.backend.repository.MediaAssetVectorHit;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MediaAssetSearchService {

    private static final Logger log = LoggerFactory.getLogger(MediaAssetSearchService.class);
    private static final int RRF_K = 60;
    private static final int MIN_RETRIEVAL_LIMIT = 60;
    private static final int MAX_RETRIEVAL_LIMIT = 200;

    private final MediaAssetSearchRepository searchRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final MediaAssetEmbeddingRepository embeddingRepository;
    private final VoyageAIClient voyageAIClient;
    private final SearchQueryParser queryParser;

    public MediaAssetSearchService(MediaAssetSearchRepository searchRepository,
                                   MediaAssetRepository mediaAssetRepository,
                                   MediaAssetEmbeddingRepository embeddingRepository,
                                   VoyageAIClient voyageAIClient,
                                   SearchQueryParser queryParser) {
        this.searchRepository = searchRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.embeddingRepository = embeddingRepository;
        this.voyageAIClient = voyageAIClient;
        this.queryParser = queryParser;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MediaAssetSearchResponseDto search(MediaAssetSearchRequestDto dto, JwtUserDetails user) {
        String query = dto.getQuery() == null ? "" : dto.getQuery().trim();
        if (query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search query is required.");
        }

        SearchScope scope = resolveScope(dto, user);
        String mediaType = normalizeMediaType(dto.getMediaType());
        int page = dto.getPage() == null ? 1 : Math.max(1, dto.getPage());
        int pageSize = dto.getPageSize() == null ? 24 : Math.min(Math.max(1, dto.getPageSize()), 100);
        int offset = (page - 1) * pageSize;

        // Stage 0: deterministic intent router. A purely temporal query
        // ("photos uploaded on June 1") becomes a date browse, not a relevance search.
        SearchQueryParser.ParsedSearchQuery parsed = queryParser.parse(query);
        if (parsed.dateOnly() && parsed.hasDateRange()) {
            return dateBrowse(query, scope, mediaType, parsed, page, pageSize);
        }

        boolean dateFiltered = parsed.hasDateRange();
        // Embed/lexically search the subject text only — keep date tokens out of the vectors.
        String retrievalText = parsed.text().isBlank() ? query : parsed.text();
        // When also date-filtering, over-fetch wide so the post-filter still fills a page.
        int retrievalLimit = dateFiltered ? MAX_RETRIEVAL_LIMIT : retrievalLimit(offset, pageSize);

        String semanticVector = embedSemanticQuery(retrievalText);
        String imageVector = embedImageQuery(retrievalText);

        List<MediaAssetSearchHit> hits = searchRepository.searchLexical(
                retrievalText,
                scope.institutionId(),
                scope.networkScope(),
                mediaType,
                retrievalLimit,
                0);

        List<MediaAssetVectorHit> semanticHits = semanticVector == null
                ? List.of()
                : vectorHits(embeddingRepository.findTopSimilarWithScoreScoped(
                        scope.institutionId(),
                        scope.networkScope(),
                        MediaAssetEmbeddingType.SEMANTIC,
                        semanticVector,
                        mediaType,
                        retrievalLimit));
        List<MediaAssetVectorHit> imageHits = imageVector == null
                ? List.of()
                : vectorHits(embeddingRepository.findTopSimilarWithScoreScoped(
                        scope.institutionId(),
                        scope.networkScope(),
                        MediaAssetEmbeddingType.IMAGE,
                        imageVector,
                        mediaType,
                        retrievalLimit));

        List<Candidate> candidates = fuse(hits, semanticHits, imageHits);
        if (candidates.isEmpty()) {
            return new MediaAssetSearchResponseDto(query, List.of(), 0, page, pageSize);
        }

        Map<UUID, MediaAsset> assets = mediaAssetRepository.findActiveByIdsWithInstitutionAndUploader(
                        candidates.stream().map(candidate -> candidate.assetId).toList())
                .stream()
                .collect(Collectors.toMap(MediaAsset::getId, Function.identity()));

        List<String> tokens = searchTokens(retrievalText);
        List<MediaAssetSearchResultDto> rankedItems = candidates.stream()
                .map(candidate -> toResult(candidate, assets.get(candidate.assetId), tokens))
                .filter(result -> result != null)
                .filter(result -> !dateFiltered
                        || withinRange(result.getAsset().getCreatedAt(), parsed.createdFrom(), parsed.createdTo()))
                .sorted(Comparator.comparingDouble(MediaAssetSearchResultDto::getScore).reversed())
                .toList();

        List<MediaAssetSearchResultDto> items = rankedItems.stream()
                .skip(offset)
                .limit(pageSize)
                .toList();

        int totalCount = dateFiltered
                ? rankedItems.size()
                : Math.max(
                        searchRepository.countLexical(retrievalText, scope.institutionId(),
                                scope.networkScope(), mediaType),
                        rankedItems.size());

        return new MediaAssetSearchResponseDto(query, items, totalCount, page, pageSize);
    }

    /**
     * Pure date browse for temporal-only queries: newest-first assets whose
     * created_at falls inside the parsed [from, to) range, tenant-scoped.
     */
    private MediaAssetSearchResponseDto dateBrowse(String query, SearchScope scope, String mediaType,
                                                   SearchQueryParser.ParsedSearchQuery parsed,
                                                   int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        OffsetDateTime from = parsed.fromUtc();
        OffsetDateTime to = parsed.toUtc();

        List<UUID> ids = searchRepository.findIdsByDateRange(
                scope.institutionId(), scope.networkScope(), mediaType, from, to, pageSize, offset);
        int total = searchRepository.countByDateRange(
                scope.institutionId(), scope.networkScope(), mediaType, from, to);
        if (ids.isEmpty()) {
            return new MediaAssetSearchResponseDto(query, List.of(), total, page, pageSize, true);
        }

        Map<UUID, MediaAsset> assets = mediaAssetRepository.findActiveByIdsWithInstitutionAndUploader(ids)
                .stream()
                .collect(Collectors.toMap(MediaAsset::getId, Function.identity()));

        List<MediaAssetSearchResultDto> items = ids.stream()
                .map(assets::get)
                .filter(Objects::nonNull)
                .map(asset -> new MediaAssetSearchResultDto(
                        MediaAssetSummaryDto.from(asset), 0.0, null, null, List.of("Uploaded date")))
                .toList();

        return new MediaAssetSearchResponseDto(query, items, total, page, pageSize, true);
    }

    private boolean withinRange(Instant createdAt, Instant from, Instant to) {
        if (createdAt == null || from == null || to == null) {
            return false;
        }
        return !createdAt.isBefore(from) && createdAt.isBefore(to);
    }

    private int retrievalLimit(int offset, int pageSize) {
        return Math.min(MAX_RETRIEVAL_LIMIT, Math.max(MIN_RETRIEVAL_LIMIT, offset + pageSize * 3));
    }

    private String embedSemanticQuery(String query) {
        try {
            return voyageAIClient.embedQuery(query);
        } catch (VoyageAIClient.VoyageApiException ex) {
            log.warn("Semantic media search unavailable: {}", ex.getMessage());
            return null;
        }
    }

    private String embedImageQuery(String query) {
        try {
            return voyageAIClient.embedMultimodalTextQuery(query);
        } catch (VoyageAIClient.VoyageApiException ex) {
            log.warn("Visual media search unavailable: {}", ex.getMessage());
            return null;
        }
    }

    private List<MediaAssetVectorHit> vectorHits(List<Object[]> rows) {
        List<MediaAssetVectorHit> hits = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            hits.add(MediaAssetVectorHit.from(rows.get(i), i + 1));
        }
        return hits;
    }

    private List<Candidate> fuse(List<MediaAssetSearchHit> lexicalHits,
                                 List<MediaAssetVectorHit> semanticHits,
                                 List<MediaAssetVectorHit> imageHits) {
        Map<UUID, Candidate> candidates = new HashMap<>();

        for (MediaAssetSearchHit hit : lexicalHits) {
            Candidate candidate = candidates.computeIfAbsent(hit.assetId(), Candidate::new);
            candidate.lexicalRank = hit.lexicalRank();
            candidate.lexicalScore = hit.lexicalScore();
            candidate.score += rrf(hit.lexicalRank());
        }
        for (MediaAssetVectorHit hit : semanticHits) {
            Candidate candidate = candidates.computeIfAbsent(hit.assetId(), Candidate::new);
            candidate.semanticRank = hit.rank();
            candidate.semanticScore = hit.vectorScore();
            candidate.score += rrf(hit.rank());
        }
        for (MediaAssetVectorHit hit : imageHits) {
            Candidate candidate = candidates.computeIfAbsent(hit.assetId(), Candidate::new);
            candidate.imageRank = hit.rank();
            candidate.imageScore = hit.vectorScore();
            candidate.score += rrf(hit.rank());
        }

        return candidates.values().stream()
                .sorted(Comparator.comparingDouble((Candidate candidate) -> candidate.score).reversed())
                .toList();
    }

    private double rrf(int rank) {
        return 1.0 / (RRF_K + rank);
    }

    private MediaAssetSearchResultDto toResult(Candidate candidate, MediaAsset asset, List<String> tokens) {
        if (asset == null) {
            return null;
        }
        double finalScore = candidate.score + metadataBoost(asset, tokens);
        return new MediaAssetSearchResultDto(
                MediaAssetSummaryDto.from(asset),
                roundScore(finalScore),
                candidate.lexicalRank,
                roundNullable(candidate.lexicalScore),
                candidate.semanticRank,
                roundNullable(candidate.semanticScore),
                candidate.imageRank,
                roundNullable(candidate.imageScore),
                matchReasons(asset, tokens, candidate));
    }

    private double metadataBoost(MediaAsset asset, List<String> tokens) {
        double boost = 0.0;
        if (containsAny(asset.getTitle(), tokens)) {
            boost += 0.004;
        }
        if (containsAny(asset.getAssetCode(), tokens) || containsAny(asset.getFileName(), tokens)) {
            boost += 0.003;
        }
        if (containsAny(asset.getAiCategory(), tokens) || containsAny(asset.getAiTags(), tokens)) {
            boost += 0.002;
        }
        if (containsAny(asset.getAiDescription(), tokens)
                || containsAny(asset.getSpecificSubjects(), tokens)
                || containsAny(asset.getVisibleObjects(), tokens)
                || containsAny(asset.getPossibleUseCases(), tokens)) {
            boost += 0.001;
        }
        return boost;
    }

    private Double roundNullable(Double score) {
        return score == null ? null : roundScore(score);
    }

    private SearchScope resolveScope(MediaAssetSearchRequestDto dto, JwtUserDetails user) {
        if (isAdmin(user)) {
            if ("network".equalsIgnoreCase(dto.getScope())) {
                return new SearchScope(null, true);
            }
            if (dto.getInstitutionId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Select an institution or network scope before searching media.");
            }
            return new SearchScope(dto.getInstitutionId(), false);
        }
        if (user.institutionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Media search requires an institution-scoped user.");
        }
        if (dto.getInstitutionId() != null && !dto.getInstitutionId().equals(user.institutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You cannot search media for another institution.");
        }
        return new SearchScope(user.institutionId(), false);
    }

    private boolean isAdmin(JwtUserDetails user) {
        return user.role() != null && user.role().toLowerCase(Locale.ROOT).contains("admin");
    }

    private String normalizeMediaType(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            return null;
        }
        String normalized = mediaType.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("image") && !normalized.equals("video")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "mediaType must be image or video.");
        }
        return normalized;
    }

    private List<String> matchReasons(MediaAsset asset, List<String> tokens, Candidate candidate) {
        List<String> reasons = new ArrayList<>(matchReasons(asset, tokens));
        if (candidate.semanticRank != null) {
            reasons.add("Semantic similarity");
        }
        if (candidate.imageRank != null) {
            reasons.add("Visual similarity");
        }
        if (candidate.lexicalRank != null && reasons.isEmpty()) {
            reasons.add("Full-text match");
        }
        return reasons.stream().distinct().limit(5).toList();
    }

    private List<String> matchReasons(MediaAsset asset, List<String> tokens) {
        Map<String, Boolean> reasons = new LinkedHashMap<>();
        reasons.put("Title match", containsAny(asset.getTitle(), tokens));
        reasons.put("Filename match", containsAny(asset.getFileName(), tokens));
        reasons.put("Asset code", containsAny(asset.getAssetCode(), tokens));
        reasons.put("AI category", containsAny(asset.getAiCategory(), tokens));
        reasons.put("AI description", containsAny(asset.getAiDescription(), tokens));
        reasons.put("AI tags", containsAny(asset.getAiTags(), tokens));
        reasons.put("Subjects", containsAny(asset.getSpecificSubjects(), tokens));
        reasons.put("Objects", containsAny(asset.getVisibleObjects(), tokens));
        reasons.put("Use cases", containsAny(asset.getPossibleUseCases(), tokens));

        return reasons.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .limit(4)
                .toList();
    }

    private boolean containsAny(String value, List<String> tokens) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = normalize(value);
        return tokens.stream().anyMatch(normalized::contains);
    }

    private boolean containsAny(String[] values, List<String> tokens) {
        if (values == null || values.length == 0) {
            return false;
        }
        return java.util.Arrays.stream(values).anyMatch(value -> containsAny(value, tokens));
    }

    private List<String> searchTokens(String query) {
        return java.util.Arrays.stream(normalize(query).split("\\s+"))
                .filter(token -> token.length() >= 2)
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private double roundScore(double score) {
        return Math.round(score * 10000.0) / 10000.0;
    }

    private static class Candidate {
        private final UUID assetId;
        private double score;
        private Integer lexicalRank;
        private Double lexicalScore;
        private Integer semanticRank;
        private Double semanticScore;
        private Integer imageRank;
        private Double imageScore;

        private Candidate(UUID assetId) {
            this.assetId = assetId;
        }
    }

    private record SearchScope(UUID institutionId, boolean networkScope) {}
}
