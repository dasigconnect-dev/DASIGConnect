package com.dasigconnect.backend.service;

import com.dasigconnect.backend.external.ClaudeVisionClient;
import com.dasigconnect.backend.model.dto.media.AlbumAddAssetsRequestDto;
import com.dasigconnect.backend.model.dto.media.AlbumCreateRequestDto;
import com.dasigconnect.backend.model.dto.media.AlbumDetailDto;
import com.dasigconnect.backend.model.dto.media.AlbumGenerateSuggestionsRequestDto;
import com.dasigconnect.backend.model.dto.media.AlbumGenerateSuggestionsResponseDto;
import com.dasigconnect.backend.model.dto.media.AlbumPromptSuggestionAssetDto;
import com.dasigconnect.backend.model.dto.media.AlbumPromptSuggestionRequestDto;
import com.dasigconnect.backend.model.dto.media.AlbumPromptSuggestionResponseDto;
import com.dasigconnect.backend.model.dto.media.AlbumResponseDto;
import com.dasigconnect.backend.model.dto.media.AlbumSetCoverRequestDto;
import com.dasigconnect.backend.model.dto.media.AlbumUpdateRequestDto;
import com.dasigconnect.backend.model.entity.AlbumAsset;
import com.dasigconnect.backend.model.entity.MediaAlbum;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetEmbeddingType;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.AlbumAssetRepository;
import com.dasigconnect.backend.repository.InstitutionRepository;
import com.dasigconnect.backend.repository.MediaAlbumRepository;
import com.dasigconnect.backend.repository.MediaAssetEmbeddingRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.MediaImportBatchRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * UC-4.1b: many-to-many album (curated collection) management — Google Photos model
 * (ADR-0004). An asset can belong to several albums without moving from its folder.
 * Albums created here are {@code source = manual}; Phase 2 AI auto-grouping writes
 * {@code ai_suggested} albums into the same structure. Every state change is audited.
 */
@Service
public class MediaAlbumService {

    /** Cosine-similarity floor for two photos to land in the same suggested collection. Tunable. */
    private static final double SIMILARITY_THRESHOLD = 0.82;

    private final MediaAlbumRepository albumRepository;
    private final AlbumAssetRepository albumAssetRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final MediaAssetEmbeddingRepository mediaAssetEmbeddingRepository;
    private final MediaImportBatchRepository mediaImportBatchRepository;
    private final UserRepository userRepository;
    private final InstitutionRepository institutionRepository;
    private final ClaudeVisionClient claudeVisionClient;
    private final AuditLogService auditLogService;

    public MediaAlbumService(MediaAlbumRepository albumRepository,
                             AlbumAssetRepository albumAssetRepository,
                             MediaAssetRepository mediaAssetRepository,
                             MediaAssetEmbeddingRepository mediaAssetEmbeddingRepository,
                             MediaImportBatchRepository mediaImportBatchRepository,
                             UserRepository userRepository,
                             InstitutionRepository institutionRepository,
                             ClaudeVisionClient claudeVisionClient,
                             AuditLogService auditLogService) {
        this.albumRepository = albumRepository;
        this.albumAssetRepository = albumAssetRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.mediaAssetEmbeddingRepository = mediaAssetEmbeddingRepository;
        this.mediaImportBatchRepository = mediaImportBatchRepository;
        this.userRepository = userRepository;
        this.institutionRepository = institutionRepository;
        this.claudeVisionClient = claudeVisionClient;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public AlbumResponseDto create(AlbumCreateRequestDto dto, JwtUserDetails user) {
        UUID institutionId = resolveTargetInstitution(user, dto.getInstitutionId());
        User actor = loadActor(user);

        MediaAlbum album = new MediaAlbum();
        album.setInstitution(institutionRepository.getReferenceById(institutionId));
        album.setName(dto.getName().trim());
        album.setDescription(trimToNull(dto.getDescription()));
        album.setSource(MediaAlbum.SOURCE_MANUAL);
        album.setCreatedBy(actor);
        album = albumRepository.save(album);

        audit(actor, "ALBUM_CREATED", album.getId(), Map.of("name", album.getName()));
        return AlbumResponseDto.from(album, 0);
    }

    @Transactional
    public AlbumGenerateSuggestionsResponseDto generateSuggestedFromImportBatch(
            AlbumGenerateSuggestionsRequestDto dto,
            JwtUserDetails user) {
        UUID institutionId = resolveTargetInstitution(user, dto.getInstitutionId());
        mediaImportBatchRepository.findByIdAndInstitution(dto.getImportBatchId(), institutionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import batch not found."));

        User actor = loadActor(user);
        List<MediaAsset> batchAssets = mediaAssetRepository.findActiveByImportBatch(dto.getImportBatchId(), institutionId);
        if (batchAssets.isEmpty()) {
            return new AlbumGenerateSuggestionsResponseDto(dto.getImportBatchId(), 0, List.of());
        }

        // UC-4.2: cluster by image-embedding similarity (not coarse category), then name each
        // cluster with one best-effort Claude call. Falls back gracefully (see helpers).
        List<List<MediaAsset>> clusters = clusterAssets(batchAssets);
        List<AlbumResponseDto> createdAlbums = new ArrayList<>();
        Set<String> usedNames = new HashSet<>();

        for (List<MediaAsset> cluster : clusters) {
            List<MediaAsset> assets = cluster.stream()
                    .sorted(Comparator.comparing(MediaAsset::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            if (assets.size() < dto.getMinGroupSize()) {
                continue;
            }

            String albumName = uniqueName(nameCluster(assets, dto.getImportBatchId()), institutionId, usedNames);

            MediaAlbum album = new MediaAlbum();
            album.setInstitution(institutionRepository.getReferenceById(institutionId));
            album.setName(albumName);
            album.setDescription("AI-suggested from import batch " + dto.getImportBatchId());
            album.setSource(MediaAlbum.SOURCE_AI_SUGGESTED);
            album.setCreatedBy(actor);
            album.setCoverAsset(assets.get(0));
            album = albumRepository.save(album);

            int order = 0;
            for (MediaAsset asset : assets) {
                AlbumAsset link = new AlbumAsset();
                link.setAlbum(album);
                link.setAsset(asset);
                link.setDisplayOrder(order++);
                albumAssetRepository.save(link);
            }

            audit(actor, "AI_ALBUM_SUGGESTED", album.getId(),
                    Map.of("importBatchId", dto.getImportBatchId().toString(), "assetCount", String.valueOf(assets.size())));
            createdAlbums.add(AlbumResponseDto.from(album, assets.size()));
        }

        return new AlbumGenerateSuggestionsResponseDto(dto.getImportBatchId(), clusters.size(), createdAlbums);
    }

    @Transactional(readOnly = true)
    public AlbumPromptSuggestionResponseDto suggestFromPrompt(AlbumPromptSuggestionRequestDto dto,
                                                              JwtUserDetails user) {
        UUID institutionId = resolveTargetInstitution(user, dto.getInstitutionId());
        String prompt = dto.getPrompt() == null ? "" : dto.getPrompt().trim();
        if (prompt.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prompt is required.");
        }

        List<String> promptTokens = promptTokens(prompt);
        int limit = dto.getLimit() == null ? 30 : Math.max(1, Math.min(60, dto.getLimit()));
        List<PromptCandidate> scored = mediaAssetRepository.findActiveByInstitution(institutionId).stream()
                .map(asset -> scorePromptCandidate(asset, prompt, promptTokens))
                .filter(candidate -> candidate.score() > 0)
                .sorted(Comparator.comparingDouble(PromptCandidate::score).reversed()
                        .thenComparing(candidate -> candidate.asset().getCreatedAt(),
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        List<AlbumPromptSuggestionAssetDto> candidates = scored.stream()
                .limit(limit)
                .map(candidate -> AlbumPromptSuggestionAssetDto.from(
                        candidate.asset(),
                        roundScore(candidate.score()),
                        confidence(candidate.score()),
                        candidate.reasons()))
                .toList();

        return new AlbumPromptSuggestionResponseDto(
                prompt,
                suggestedPromptAlbumName(prompt),
                scored.size(),
                candidates);
    }

    @Transactional
    public AlbumResponseDto update(UUID id, AlbumUpdateRequestDto dto, JwtUserDetails user) {
        User actor = loadActor(user);
        MediaAlbum album = requireAlbum(id, user);
        album.setName(dto.getName().trim());
        album.setDescription(trimToNull(dto.getDescription()));
        album = albumRepository.save(album);
        audit(actor, "ALBUM_UPDATED", album.getId(), Map.of("name", album.getName()));
        return AlbumResponseDto.from(album, albumAssetRepository.countByAlbumId(album.getId()));
    }

    @Transactional
    public void delete(UUID id, JwtUserDetails user) {
        User actor = loadActor(user);
        MediaAlbum album = requireAlbum(id, user);
        String name = album.getName();
        // album_assets rows are removed by the FK ON DELETE CASCADE; the assets themselves are untouched.
        albumRepository.delete(album);
        audit(actor, "ALBUM_DELETED", id, Map.of("name", name));
    }

    @Transactional
    public AlbumDetailDto addAssets(UUID id, AlbumAddAssetsRequestDto dto, JwtUserDetails user) {
        User actor = loadActor(user);
        MediaAlbum album = requireAlbum(id, user);
        UUID institutionId = operationInstitution(album, user);

        long order = albumAssetRepository.countByAlbumId(id);
        int added = 0;
        for (UUID assetId : dto.getAssetIds()) {
            if (albumAssetRepository.existsByAlbumIdAndAssetId(id, assetId)) {
                continue; // idempotent: already in the album
            }
            MediaAsset asset = requireInstitutionAsset(assetId, institutionId);
            AlbumAsset link = new AlbumAsset();
            link.setAlbum(album);
            link.setAsset(asset);
            link.setDisplayOrder((int) (order + added));
            albumAssetRepository.save(link);
            added++;
        }
        audit(actor, "ALBUM_ASSETS_ADDED", id, Map.of("added", String.valueOf(added)));
        return detail(album);
    }

    @Transactional
    public void removeAsset(UUID id, UUID assetId, JwtUserDetails user) {
        User actor = loadActor(user);
        requireAlbum(id, user); // scope + existence check
        albumAssetRepository.deleteByAlbumIdAndAssetId(id, assetId);
        audit(actor, "ALBUM_ASSET_REMOVED", id, Map.of("assetId", assetId.toString()));
    }

    @Transactional
    public AlbumResponseDto setCover(UUID id, AlbumSetCoverRequestDto dto, JwtUserDetails user) {
        User actor = loadActor(user);
        MediaAlbum album = requireAlbum(id, user);
        UUID institutionId = operationInstitution(album, user);
        if (dto.getCoverAssetId() == null) {
            album.setCoverAsset(null);
        } else {
            album.setCoverAsset(requireInstitutionAsset(dto.getCoverAssetId(), institutionId));
        }
        album = albumRepository.save(album);
        audit(actor, "ALBUM_COVER_SET", id, Map.of("coverAssetId", String.valueOf(dto.getCoverAssetId())));
        return AlbumResponseDto.from(album, albumAssetRepository.countByAlbumId(id));
    }

    @Transactional(readOnly = true)
    public List<AlbumResponseDto> list(UUID requestedInstitutionId, JwtUserDetails user) {
        UUID institutionId = resolveTargetInstitution(user, requestedInstitutionId);
        return albumRepository.findByInstitution(institutionId).stream()
                .map(a -> AlbumResponseDto.from(a, albumAssetRepository.countByAlbumId(a.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public AlbumDetailDto get(UUID id, JwtUserDetails user) {
        return detail(requireAlbum(id, user));
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private AlbumDetailDto detail(MediaAlbum album) {
        List<MediaAsset> assets = albumAssetRepository.findByAlbumOrdered(album.getId()).stream()
                .map(AlbumAsset::getAsset)
                .toList();
        return AlbumDetailDto.from(album, assets);
    }

    private UUID resolveTargetInstitution(JwtUserDetails user, UUID requestedInstitutionId) {
        if (isAdmin(user)) {
            if (requestedInstitutionId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Select an institution before managing albums.");
            }
            return requestedInstitutionId;
        }
        if (user.institutionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Album operations require an institution-scoped user.");
        }
        if (requestedInstitutionId != null && !requestedInstitutionId.equals(user.institutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You cannot manage albums for another institution.");
        }
        return user.institutionId();
    }

    private boolean isAdmin(JwtUserDetails user) {
        return user.role() != null && user.role().toLowerCase().contains("admin");
    }

    private User loadActor(JwtUserDetails user) {
        return userRepository.findById(user.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Authenticated user not found."));
    }

    private MediaAlbum requireAlbum(UUID id, UUID institutionId) {
        return albumRepository.findByIdAndInstitution(id, institutionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Album not found."));
    }

    private MediaAlbum requireAlbum(UUID id, JwtUserDetails user) {
        if (isAdmin(user)) {
            return albumRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Album not found."));
        }
        return requireAlbum(id, resolveTargetInstitution(user, null));
    }

    private UUID operationInstitution(MediaAlbum album, JwtUserDetails user) {
        if (!isAdmin(user)) {
            return resolveTargetInstitution(user, null);
        }
        if (album.getInstitution() == null || album.getInstitution().getId() == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Album has no institution.");
        }
        return album.getInstitution().getId();
    }

    private MediaAsset requireInstitutionAsset(UUID assetId, UUID institutionId) {
        MediaAsset asset = mediaAssetRepository.findActiveById(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found."));
        if (!asset.getInstitution().getId().equals(institutionId)) {
            // Don't leak cross-tenant existence — treat as not found.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found.");
        }
        return asset;
    }

    private String groupKeyForAsset(MediaAsset asset) {
        if (asset.getAiCategory() != null && !asset.getAiCategory().isBlank()) {
            return asset.getAiCategory().trim();
        }
        if (asset.getFileType() != null) {
            return asset.getFileType().isImage() ? "Photos" : "Videos";
        }
        return "Media";
    }

    private PromptCandidate scorePromptCandidate(MediaAsset asset, String prompt, List<String> tokens) {
        double score = 0.0;
        List<String> reasons = new ArrayList<>();
        String normalizedPrompt = normalize(prompt);

        score += scoreText(asset.getTitle(), tokens, normalizedPrompt, 5.0, "Title match", reasons);
        score += scoreText(asset.getAiDescription(), tokens, normalizedPrompt, 4.0, "Description match", reasons);
        score += scoreText(asset.getAiCategory(), tokens, normalizedPrompt, 3.0, "Category match", reasons);
        score += scoreText(asset.getAssetType(), tokens, normalizedPrompt, 3.0, "Asset type match", reasons);
        score += scoreArray(asset.getAiTags(), tokens, normalizedPrompt, 4.0, "AI tags", reasons);
        score += scoreArray(asset.getSpecificSubjects(), tokens, normalizedPrompt, 3.5, "Subjects", reasons);
        score += scoreArray(asset.getVisibleObjects(), tokens, normalizedPrompt, 3.0, "Objects", reasons);
        score += scoreArray(asset.getPossibleUseCases(), tokens, normalizedPrompt, 2.5, "Use cases", reasons);
        score += scoreText(asset.getFileName(), tokens, normalizedPrompt, 1.5, "Filename match", reasons);

        return new PromptCandidate(asset, score, reasons.stream().distinct().limit(4).toList());
    }

    private double scoreText(String value, List<String> tokens, String normalizedPrompt, double weight,
                             String reason, List<String> reasons) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return 0.0;
        }
        double score = 0.0;
        if (!normalizedPrompt.isBlank() && normalized.contains(normalizedPrompt)) {
            score += weight * 1.5;
        }
        long matches = tokens.stream().filter(normalized::contains).count();
        if (matches > 0) {
            score += matches * weight;
            reasons.add(reason);
        }
        return score;
    }

    private double scoreArray(String[] values, List<String> tokens, String normalizedPrompt, double weight,
                              String reason, List<String> reasons) {
        if (values == null || values.length == 0) {
            return 0.0;
        }
        String joined = java.util.Arrays.stream(values)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
        return scoreText(joined, tokens, normalizedPrompt, weight, reason, reasons);
    }

    private List<String> promptTokens(String prompt) {
        Set<String> stopWords = Set.of(
                "a", "an", "and", "are", "at", "by", "find", "for", "from", "image", "images",
                "in", "into", "me", "media", "of", "on", "or", "photo", "photos", "picture",
                "pictures", "show", "the", "to", "video", "videos", "with");
        return java.util.Arrays.stream(normalize(prompt).split("\\s+"))
                .filter(token -> token.length() >= 2)
                .filter(token -> !stopWords.contains(token))
                .distinct()
                .toList();
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private String confidence(double score) {
        if (score >= 14.0) {
            return "high";
        }
        if (score >= 7.0) {
            return "medium";
        }
        return "low";
    }

    private double roundScore(double score) {
        return Math.round(score * 100.0) / 100.0;
    }

    private String suggestedPromptAlbumName(String prompt) {
        List<String> words = promptTokens(prompt).stream().limit(5).toList();
        if (words.isEmpty()) {
            return "Prompt Collection";
        }
        String title = words.stream()
                .map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1))
                .collect(Collectors.joining(" "));
        return title + " Collection";
    }

    /**
     * Cluster batch assets by image-embedding cosine similarity (UC-4.2). Assets whose image
     * embedding is missing (still PROCESSING, video, or failed) fall back to category grouping
     * so nothing in the batch is dropped.
     */
    private List<List<MediaAsset>> clusterAssets(List<MediaAsset> assets) {
        List<MediaAsset> withEmbedding = new ArrayList<>();
        List<double[]> vectors = new ArrayList<>();
        List<MediaAsset> withoutEmbedding = new ArrayList<>();
        for (MediaAsset asset : assets) {
            double[] vector = mediaAssetEmbeddingRepository
                    .findEmbedding(asset.getId(), MediaAssetEmbeddingType.IMAGE)
                    .map(MediaAlbumService::parseVector)
                    .orElse(null);
            if (vector != null) {
                withEmbedding.add(asset);
                vectors.add(vector);
            } else {
                withoutEmbedding.add(asset);
            }
        }

        List<List<MediaAsset>> clusters = new ArrayList<>(similarityClusters(withEmbedding, vectors));
        withoutEmbedding.stream()
                .collect(Collectors.groupingBy(this::groupKeyForAsset, LinkedHashMap::new, Collectors.toList()))
                .values()
                .forEach(clusters::add);
        return clusters;
    }

    /** Single-link agglomerative clustering via union-find over pairwise cosine similarity. */
    private static List<List<MediaAsset>> similarityClusters(List<MediaAsset> assets, List<double[]> vectors) {
        int n = assets.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (cosine(vectors.get(i), vectors.get(j)) >= SIMILARITY_THRESHOLD) {
                    parent[find(parent, i)] = find(parent, j);
                }
            }
        }
        Map<Integer, List<MediaAsset>> grouped = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            grouped.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(assets.get(i));
        }
        return new ArrayList<>(grouped.values());
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    static double cosine(double[] a, double[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /** Parse a pgvector {@code embedding::text} value ("[0.1,0.2,...]") into a double[]. */
    static double[] parseVector(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("[")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("]")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isBlank()) {
            return null;
        }
        String[] parts = trimmed.split(",");
        double[] vector = new double[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                vector[i] = Double.parseDouble(parts[i].trim());
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return vector;
    }

    /** One best-effort Claude name from the cluster's already-extracted metadata, else deterministic. */
    private String nameCluster(List<MediaAsset> assets, UUID importBatchId) {
        String aiName = claudeVisionClient.suggestAlbumName(buildClusterContext(assets));
        if (aiName != null && !aiName.isBlank()) {
            return aiName.trim();
        }
        return suggestedAlbumName(dominantCategory(assets), importBatchId);
    }

    private String buildClusterContext(List<MediaAsset> assets) {
        StringBuilder context = new StringBuilder();
        context.append("Photo count: ").append(assets.size()).append('\n');
        context.append("Dominant category: ").append(dominantCategory(assets)).append('\n');
        assets.stream()
                .map(MediaAsset::getAiDescription)
                .filter(description -> description != null && !description.isBlank())
                .limit(3)
                .forEach(description -> context.append("Description: ").append(description).append('\n'));
        List<String> tags = assets.stream()
                .filter(asset -> asset.getAiTags() != null)
                .flatMap(asset -> java.util.Arrays.stream(asset.getAiTags()))
                .filter(tag -> tag != null && !tag.isBlank())
                .distinct()
                .limit(12)
                .toList();
        if (!tags.isEmpty()) {
            context.append("Tags: ").append(String.join(", ", tags)).append('\n');
        }
        return context.toString();
    }

    private String dominantCategory(List<MediaAsset> assets) {
        return assets.stream()
                .map(this::groupKeyForAsset)
                .collect(Collectors.groupingBy(key -> key, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Media");
    }

    /** Avoid name collisions within the run and against existing AI-suggested albums. */
    private String uniqueName(String base, UUID institutionId, Set<String> usedNames) {
        String candidate = (base == null || base.isBlank()) ? "Suggested Collection" : base.trim();
        String name = candidate;
        int suffix = 2;
        while (usedNames.contains(name.toLowerCase(Locale.ROOT))
                || albumRepository.existsByInstitutionAndSourceAndName(
                        institutionId, MediaAlbum.SOURCE_AI_SUGGESTED, name)) {
            name = candidate + " " + suffix++;
        }
        usedNames.add(name.toLowerCase(Locale.ROOT));
        return name;
    }

    private String suggestedAlbumName(String key, UUID importBatchId) {
        String normalized = key == null || key.isBlank() ? "Media" : key.trim().replaceAll("\\s+", " ");
        String title = java.util.Arrays.stream(normalized.split(" "))
                .filter(part -> !part.isBlank())
                .map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
        return title + " - AI suggestion " + importBatchId.toString().substring(0, 8);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void audit(User actor, String action, UUID resourceId, Map<String, ?> metadata) {
        auditLogService.record(actor, action, null, null, resourceId, metadata);
    }

    private record PromptCandidate(MediaAsset asset, double score, List<String> reasons) {}
}
