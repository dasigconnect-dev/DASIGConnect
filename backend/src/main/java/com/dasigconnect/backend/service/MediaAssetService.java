package com.dasigconnect.backend.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.model.dto.media.AddAssetTagRequestDto;
import com.dasigconnect.backend.model.dto.media.AssetTagDto;
import com.dasigconnect.backend.model.dto.media.MediaAlbumDto;
import com.dasigconnect.backend.model.dto.media.MediaAlbumRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetAddToDraftRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetAlbumRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetBulkDeleteRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetBulkDeleteResponseDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetDetailDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetListResponseDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetSummaryDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetUploadRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetUploadUrlRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetUploadUrlResponseDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetUsageDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetUseInNewPostRequestDto;
import com.dasigconnect.backend.model.dto.submission.AttachAssetDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionCreateDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionResponseDto;
import com.dasigconnect.backend.model.entity.AssetTag;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaAlbum;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetStatus;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.AssetTagRepository;
import com.dasigconnect.backend.repository.InstitutionRepository;
import com.dasigconnect.backend.repository.MediaAlbumRepository;
import com.dasigconnect.backend.repository.MediaAssetEmbeddingRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionMediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.security.JwtUserDetails;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
@Transactional
public class MediaAssetService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MediaAssetService.class);

    private final MediaAssetRepository mediaAssetRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionMediaAssetRepository submissionMediaAssetRepository;
    private final AssetTagRepository assetTagRepository;
    private final MediaAlbumRepository mediaAlbumRepository;
    private final MediaAssetEmbeddingRepository mediaAssetEmbeddingRepository;
    private final InstitutionRepository institutionRepository;
    private final SubmissionService submissionService;
    private final SupabaseStorageService supabaseStorageService;
    private final AIClassificationService aiClassificationService;
    private final com.dasigconnect.backend.external.VoyageAIClient voyageAIClient;

    @PersistenceContext
    private EntityManager entityManager;

    public MediaAssetService(
            MediaAssetRepository mediaAssetRepository,
            SubmissionRepository submissionRepository,
            SubmissionMediaAssetRepository submissionMediaAssetRepository,
            AssetTagRepository assetTagRepository,
            MediaAlbumRepository mediaAlbumRepository,
            MediaAssetEmbeddingRepository mediaAssetEmbeddingRepository,
            InstitutionRepository institutionRepository,
            SubmissionService submissionService,
            SupabaseStorageService supabaseStorageService,
            AIClassificationService aiClassificationService,
            com.dasigconnect.backend.external.VoyageAIClient voyageAIClient) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.submissionRepository = submissionRepository;
        this.submissionMediaAssetRepository = submissionMediaAssetRepository;
        this.assetTagRepository = assetTagRepository;
        this.mediaAlbumRepository = mediaAlbumRepository;
        this.mediaAssetEmbeddingRepository = mediaAssetEmbeddingRepository;
        this.institutionRepository = institutionRepository;
        this.submissionService = submissionService;
        this.supabaseStorageService = supabaseStorageService;
        this.aiClassificationService = aiClassificationService;
        this.voyageAIClient = voyageAIClient;
    }

    /**
     * The shared default institution ("DASIG Central Visayas"). Its media
     * library is visible to every institution, and any contributor may add
     * folders/files to it — but deletion stays scoped to the owning
     * institution.
     */
    private UUID sharedInstitutionId() {
        return institutionRepository.findFirstByIsProtectedTrueOrderByCreatedAtAsc()
                .map(Institution::getId)
                .orElse(null);
    }

    /**
     * Institution ids a non-admin user may browse: their own plus the shared
     * default.
     */
    private java.util.Set<UUID> visibleInstitutionIds(JwtUserDetails user) {
        java.util.Set<UUID> ids = new java.util.LinkedHashSet<>();
        if (user.institutionId() != null) {
            ids.add(user.institutionId());
        }
        UUID shared = sharedInstitutionId();
        if (shared != null) {
            ids.add(shared);
        }
        return ids;
    }

    @Transactional(readOnly = true)
    public MediaAssetListResponseDto list(
            String query,
            String aiCategory,
            String mediaType,
            UUID uploaderId,
            UUID institutionId,
            UUID albumId,
            String sort,
            int page,
            int pageSize,
            String scope,
            JwtUserDetails user) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        String trimmedQuery = query == null ? "" : query.trim().toLowerCase();
        String trimmedCategory = aiCategory == null ? "" : aiCategory.trim();
        String trimmedMediaType = mediaType == null ? "" : mediaType.trim().toLowerCase();

        boolean administrator = isAdmin(user);
        boolean networkScope = administrator && "network".equalsIgnoreCase(scope);
        List<MediaAsset> source;
        if (administrator && institutionId != null) {
            source = mediaAssetRepository.findActiveByInstitution(institutionId);
        } else if (administrator || networkScope) {
            source = mediaAssetRepository.findAllActive();
        } else {
            // Own institution + the shared default institution.
            source = mediaAssetRepository.findActiveByInstitutionIds(visibleInstitutionIds(user));
        }

        List<UUID> sourceIds = source.stream().map(MediaAsset::getId).toList();
        Set<UUID> attachedAssetIds = submissionMediaAssetRepository.findAssetIdsWithAnySubmissionLink(sourceIds);
        Set<UUID> assetIdsUsedBeyondDraft = submissionMediaAssetRepository.findAssetIdsUsedBeyondDraft(sourceIds);

        List<MediaAsset> filtered = source
                .stream()
                .filter(asset -> isPublishedToRepository(asset, attachedAssetIds, assetIdsUsedBeyondDraft))
                // Folder scoping is ignored while searching so matches are never hidden by the current folder.
                .filter(asset -> !trimmedQuery.isBlank()
                || albumId == null
                || (asset.getMediaAlbum() != null && albumId.equals(asset.getMediaAlbum().getId())))
                .filter(asset -> trimmedQuery.isBlank()
                || containsIgnoreCase(asset.getFileName(), trimmedQuery)
                || containsIgnoreCase(asset.getAssetCode(), trimmedQuery))
                .filter(asset -> trimmedCategory.isBlank()
                || (asset.getAiCategory() != null && asset.getAiCategory().equalsIgnoreCase(trimmedCategory)))
                .filter(asset -> trimmedMediaType.isBlank()
                || ("image".equals(trimmedMediaType) ? asset.getFileType().isImage() : asset.getFileType().isVideo()))
                .filter(asset -> uploaderId == null
                || (asset.getUploader() != null && uploaderId.equals(asset.getUploader().getId())))
                .sorted(resolveSort(sort))
                .toList();

        int totalCount = filtered.size();
        int fromIndex = Math.min((safePage - 1) * safePageSize, totalCount);
        int toIndex = Math.min(fromIndex + safePageSize, totalCount);
        List<MediaAssetSummaryDto> items = filtered.subList(fromIndex, toIndex)
                .stream()
                .map(MediaAssetSummaryDto::from)
                .toList();

        return new MediaAssetListResponseDto(items, totalCount, safePage, safePageSize);
    }

    /**
     * Meaning-based asset search: embeds the query with Voyage AI and ranks the
     * viewer's visible assets by pgvector cosine similarity, then appends plain
     * keyword matches (covers assets without an embedding and Voyage outages).
     */
    @Transactional(readOnly = true)
    public MediaAssetListResponseDto semanticSearch(String query, UUID institutionId, JwtUserDetails user) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.length() < 2) {
            return new MediaAssetListResponseDto(List.of(), 0, 1, 0);
        }
        boolean administrator = isAdmin(user);

        List<MediaAsset> scope;
        java.util.Set<UUID> institutionScope = null; // null => network-wide (admin)
        if (administrator && institutionId != null) {
            institutionScope = java.util.Set.of(institutionId);
            scope = mediaAssetRepository.findActiveByInstitution(institutionId);
        } else if (administrator) {
            scope = mediaAssetRepository.findAllActive();
        } else {
            institutionScope = visibleInstitutionIds(user);
            scope = institutionScope.isEmpty() ? List.of()
                    : mediaAssetRepository.findActiveByInstitutionIds(institutionScope);
        }

        List<UUID> scopeIds = scope.stream().map(MediaAsset::getId).toList();
        Set<UUID> attached = submissionMediaAssetRepository.findAssetIdsWithAnySubmissionLink(scopeIds);
        Set<UUID> beyondDraft = submissionMediaAssetRepository.findAssetIdsUsedBeyondDraft(scopeIds);
        java.util.Map<UUID, MediaAsset> byId = new java.util.HashMap<>();
        for (MediaAsset a : scope) {
            if (isPublishedToRepository(a, attached, beyondDraft)) {
                byId.put(a.getId(), a);
            }
        }

        java.util.LinkedHashMap<UUID, MediaAsset> ordered = new java.util.LinkedHashMap<>();
        if (!byId.isEmpty()) {
            try {
                String queryVector = voyageAIClient.embedQuery(trimmed);
                List<Object[]> hits = institutionScope == null
                        ? mediaAssetRepository.findTopSimilarAllInstitutions(queryVector)
                        : mediaAssetRepository.findTopSimilarInInstitutions(institutionScope, queryVector);
                for (Object[] row : hits) {
                    MediaAsset a = byId.get(UUID.fromString((String) row[0]));
                    if (a != null) {
                        ordered.putIfAbsent(a.getId(), a);
                    }
                }
            } catch (RuntimeException e) {
                log.warn("Semantic media search fell back to keyword matching: {}", e.getMessage());
            }

            String lower = trimmed.toLowerCase();
            byId.values().stream()
                    .filter(a -> !ordered.containsKey(a.getId()))
                    .filter(a -> matchesKeyword(a, lower))
                    .sorted(resolveSort("newest"))
                    .forEach(a -> ordered.put(a.getId(), a));
        }

        List<MediaAssetSummaryDto> items = ordered.values().stream()
                .limit(60)
                .map(MediaAssetSummaryDto::from)
                .toList();
        return new MediaAssetListResponseDto(items, items.size(), 1, items.size());
    }

    private static boolean matchesKeyword(MediaAsset a, String lower) {
        if (containsIgnoreCase(a.getFileName(), lower)
                || containsIgnoreCase(a.getAssetCode(), lower)
                || containsIgnoreCase(a.getAiDescription(), lower)
                || containsIgnoreCase(a.getAiCategory(), lower)) {
            return true;
        }
        String[] tags = a.getAiTags();
        if (tags != null) {
            for (String t : tags) {
                if (containsIgnoreCase(t, lower)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Transactional(readOnly = true)
    public MediaAssetDetailDto get(UUID id, JwtUserDetails user) {
        MediaAsset asset = mediaAssetRepository.findActiveById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media asset not found."));
        if (!isAdmin(user) && !visibleInstitutionIds(user).contains(asset.getInstitution().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media asset not found.");
        }
        if (!isPublishedToRepository(asset)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media asset not found.");
        }
        List<MediaAssetUsageDto> usedIn = submissionMediaAssetRepository
                .findByMediaAssetIdOrderByCreatedAtDesc(id)
                .stream()
                .map(MediaAssetUsageDto::from)
                .toList();
        List<AssetTagDto> tags = assetTagRepository
                .findByMediaAssetIdOrderByCreatedAtAsc(id)
                .stream()
                .map(AssetTagDto::from)
                .toList();
        return MediaAssetDetailDto.from(asset, usedIn, tags);
    }

    public SubmissionResponseDto useInNewPost(UUID assetId, MediaAssetUseInNewPostRequestDto dto, JwtUserDetails user) {
        MediaAsset asset = loadAsset(assetId, user);
        SubmissionCreateDto createDto = new SubmissionCreateDto();
        createDto.setEventTitle(dto.getEventTitle());
        createDto.setEventDate(dto.getEventDate());
        createDto.setCaption(dto.getCaption());
        createDto.setDescription(dto.getDescription());
        createDto.setCategory(dto.getCategory());
        createDto.setTags(dto.getTags());

        SubmissionResponseDto response = submissionService.create(createDto, user);

        AttachAssetDto attachDto = new AttachAssetDto();
        attachDto.setMediaAssetId(asset.getId());
        return submissionService.attachAsset(response.getId(), attachDto, user);
    }

    public SubmissionResponseDto addToDraft(UUID assetId, MediaAssetAddToDraftRequestDto dto, JwtUserDetails user) {
        MediaAsset asset = loadAsset(assetId, user);
        if (!submissionRepository.existsByIdAndContributorId(dto.getSubmissionId(), user.userId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found.");
        }

        AttachAssetDto attachDto = new AttachAssetDto();
        attachDto.setMediaAssetId(asset.getId());
        return submissionService.attachAsset(dto.getSubmissionId(), attachDto, user);
    }

    public void delete(UUID assetId, boolean force, JwtUserDetails user) {
        MediaAsset asset = loadAssetForDelete(assetId, user);
        validateDeleteReferences(assetId, force);

        asset.setDeletedAt(Instant.now());
        asset.setDeletedByUserId(user.userId());
        asset.setStatus(MediaAssetStatus.DELETED);
        mediaAssetEmbeddingRepository.deleteByAssetId(assetId);
        mediaAssetRepository.save(asset);
    }

    public MediaAssetBulkDeleteResponseDto bulkDelete(MediaAssetBulkDeleteRequestDto dto, JwtUserDetails user) {
        List<UUID> assetIds = new ArrayList<>(new LinkedHashSet<>(dto.getAssetIds()));
        if (assetIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one asset to delete.");
        }
        if (assetIds.size() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You can delete up to 100 assets at once.");
        }

        List<MediaAsset> assets = assetIds.stream()
                .map(id -> loadAssetForDelete(id, user))
                .toList();

        for (UUID assetId : assetIds) {
            validateDeleteReferences(assetId, dto.isForce());
        }

        Instant deletedAt = Instant.now();
        for (MediaAsset asset : assets) {
            asset.setDeletedAt(deletedAt);
            asset.setDeletedByUserId(user.userId());
            asset.setStatus(MediaAssetStatus.DELETED);
            mediaAssetEmbeddingRepository.deleteByAssetId(asset.getId());
        }
        mediaAssetRepository.saveAll(assets);
        return new MediaAssetBulkDeleteResponseDto(assetIds);
    }

    private void validateDeleteReferences(UUID assetId, boolean force) {
        long blockingCount = submissionMediaAssetRepository.countBlockingSubmissionsByAssetId(assetId);
        if (blockingCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Asset is referenced by active submissions and cannot be deleted.");
        }

        long warningCount = submissionMediaAssetRepository.countDraftSubmissionsByAssetId(assetId);
        if (warningCount > 0 && !force) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Asset is referenced by drafts. Use force=true to delete.");
        }
    }

    public MediaAssetDetailDto upload(MediaAssetUploadRequestDto dto, JwtUserDetails user) {
        MediaFileType fileType;
        try {
            fileType = MediaFileType.valueOf(dto.getFileType().toLowerCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported file type: " + dto.getFileType());
        }
        if (dto.getFileSizeBytes() > 50L * 1024L * 1024L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File exceeds the 50 MB limit.");
        }

        List<String> manualTags = normalizeTags(dto.getTags());
        if (manualTags.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one media tag is required.");
        }

        UUID institutionId = resolveTargetInstitutionId(dto.getInstitutionId(), user);
        MediaAlbum album = resolveAlbum(dto, institutionId, user.userId());

        MediaAsset asset = new MediaAsset();
        asset.setInstitution(entityManager.getReference(Institution.class, institutionId));
        asset.setUploader(entityManager.getReference(User.class, user.userId()));
        asset.setMediaAlbum(album);
        asset.setAssetCode(generateAssetCode());
        asset.setStorageUrl(dto.getStorageUrl());
        asset.setFileName(dto.getFileName());
        asset.setFileType(fileType);
        asset.setFileSizeBytes(dto.getFileSizeBytes());
        asset.setStatus(MediaAssetStatus.PROCESSING);
        asset = mediaAssetRepository.save(asset);
        List<AssetTagDto> savedTags = saveManualTags(asset, manualTags);

        // Trigger async classification + embedding — never blocks the upload response
        final UUID savedId = asset.getId();
        final String savedUrl = asset.getStorageUrl();
        final MediaFileType savedType = asset.getFileType();
        try {
            if (savedType.isImage()) {
                aiClassificationService.classifyAndEmbed(savedId, savedUrl);
            }
        } catch (Exception e) {
            log.warn("Failed to trigger AI classification for asset {}: {}", savedId, e.getMessage());
        }

        return MediaAssetDetailDto.from(asset, List.of(), savedTags);
    }

    @Transactional(readOnly = true)
    public List<MediaAlbumDto> listAlbums(UUID requestedInstitutionId, JwtUserDetails user) {
        // Admin with no institution filter → every institution's albums in one list,
        // so the Media Repository "All institutions" root can show them side by side.
        if (isAdmin(user) && requestedInstitutionId == null) {
            java.util.Map<UUID, Long> childCounts = toCountMap(mediaAlbumRepository.countChildAlbumsByParentAllInstitutions());
            java.util.Map<UUID, Long> assetCounts = toCountMap(mediaAssetRepository.countActiveAssetsByAlbumAllInstitutions());
            return mediaAlbumRepository.findAll()
                    .stream()
                    .sorted(java.util.Comparator.comparing(a -> a.getName().toLowerCase()))
                    .map(album -> MediaAlbumDto.from(
                    album,
                    childCounts.getOrDefault(album.getId(), 0L),
                    assetCounts.getOrDefault(album.getId(), 0L),
                    canDeleteAlbum(album, user)))
                    .toList();
        }

        // Admin filtered to one institution → just that institution's albums.
        if (isAdmin(user) && requestedInstitutionId != null) {
            java.util.Map<UUID, Long> childCounts = toCountMap(mediaAlbumRepository.countChildAlbumsByParent(requestedInstitutionId));
            java.util.Map<UUID, Long> assetCounts = toCountMap(mediaAssetRepository.countActiveAssetsByAlbum(requestedInstitutionId));
            return mediaAlbumRepository.findByInstitutionIdOrderByName(requestedInstitutionId)
                    .stream()
                    .map(album -> MediaAlbumDto.from(album,
                    childCounts.getOrDefault(album.getId(), 0L),
                    assetCounts.getOrDefault(album.getId(), 0L),
                    canDeleteAlbum(album, user)))
                    .toList();
        }

        // Non-admin: own institution + the shared default institution.
        java.util.Set<UUID> ids = visibleInstitutionIds(user);
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Institution scope is required for media albums.");
        }
        java.util.Map<UUID, Long> childCounts = new java.util.HashMap<>();
        java.util.Map<UUID, Long> assetCounts = new java.util.HashMap<>();
        for (UUID id : ids) {
            childCounts.putAll(toCountMap(mediaAlbumRepository.countChildAlbumsByParent(id)));
            assetCounts.putAll(toCountMap(mediaAssetRepository.countActiveAssetsByAlbum(id)));
        }
        return mediaAlbumRepository.findByInstitutionIdInOrderByName(ids)
                .stream()
                .map(album -> MediaAlbumDto.from(album,
                childCounts.getOrDefault(album.getId(), 0L),
                assetCounts.getOrDefault(album.getId(), 0L),
                canDeleteAlbum(album, user)))
                .toList();
    }

    private static java.util.Map<UUID, Long> toCountMap(List<Object[]> rows) {
        java.util.Map<UUID, Long> map = new java.util.HashMap<>();
        for (Object[] row : rows) {
            map.put((UUID) row[0], (Long) row[1]);
        }
        return map;
    }

    public MediaAlbumDto createAlbum(MediaAlbumRequestDto dto, JwtUserDetails user) {
        UUID institutionId = resolveTargetInstitutionId(dto.getInstitutionId(), user);
        MediaAlbum parent = loadParentAlbumOrNull(dto.getParentAlbumId(), institutionId);
        String name = normalizeRequiredAlbumName(dto.getName());
        assertAlbumNameFree(institutionId, parent, name, null);

        MediaAlbum album = new MediaAlbum();
        album.setInstitution(entityManager.getReference(Institution.class, institutionId));
        album.setParentAlbum(parent);
        album.setName(name);
        album.setCreatedBy(user.userId());
        return MediaAlbumDto.from(mediaAlbumRepository.save(album));
    }

    public MediaAlbumDto renameAlbum(UUID albumId, MediaAlbumRequestDto dto, JwtUserDetails user) {
        MediaAlbum album = loadAlbumForManage(albumId, user);
        UUID institutionId = album.getInstitution().getId();
        String name = normalizeRequiredAlbumName(dto.getName());
        assertAlbumNameFree(institutionId, album.getParentAlbum(), name, albumId);
        album.setName(name);
        return MediaAlbumDto.from(mediaAlbumRepository.save(album));
    }

    /**
     * Re-parent an album. {@code newParentId} null moves it to a root; the target
     * institution comes from the destination parent, else {@code requestedInstitutionId},
     * else it stays put. Moving a folder into another institution (only the shared
     * default for non-admins) re-homes the whole subtree and its assets.
     */
    public MediaAlbumDto moveAlbum(UUID albumId, UUID newParentId, UUID requestedInstitutionId, JwtUserDetails user) {
        MediaAlbum album = loadAlbumForManage(albumId, user);
        UUID currentInstitutionId = album.getInstitution().getId();

        MediaAlbum newParent = newParentId == null ? null
                : mediaAlbumRepository.findById(newParentId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Destination folder not found."));

        UUID targetInstitutionId = newParent != null
                ? newParent.getInstitution().getId()
                : requestedInstitutionId != null ? requestedInstitutionId : currentInstitutionId;

        boolean institutionChanges = !targetInstitutionId.equals(currentInstitutionId);
        if (institutionChanges && !isAdmin(user) && !targetInstitutionId.equals(sharedInstitutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only move folders within your institution or into the shared library.");
        }

        if (newParentId != null
                && (newParentId.equals(albumId)
                    || mediaAlbumRepository.findDescendantIds(albumId).contains(newParentId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An album cannot be moved inside itself or one of its sub-albums.");
        }

        assertAlbumNameFree(targetInstitutionId, newParent, album.getName(), albumId);

        if (institutionChanges) {
            List<UUID> descendants = mediaAlbumRepository.findDescendantIds(albumId);
            List<UUID> subtree = new ArrayList<>(descendants);
            subtree.add(albumId);
            if (!descendants.isEmpty()) {
                mediaAlbumRepository.rehomeAlbums(targetInstitutionId, descendants);
            }
            mediaAssetRepository.rehomeAssetsInAlbums(targetInstitutionId, subtree);
            album.setInstitution(entityManager.getReference(Institution.class, targetInstitutionId));
        }

        album.setParentAlbum(newParent);
        return MediaAlbumDto.from(mediaAlbumRepository.save(album), 0L, 0L, canDeleteAlbum(album, user));
    }

    /**
     * Delete an empty album. Blocks (409) while it still has sub-albums or
     * assets.
     */
    public void deleteAlbum(UUID albumId, JwtUserDetails user) {
        MediaAlbum album = loadAlbumForDelete(albumId, user);
        if (mediaAlbumRepository.countByParentAlbumId(albumId) > 0
                || mediaAssetRepository.countByMediaAlbumIdAndDeletedAtIsNull(albumId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Move or delete everything inside this album before deleting it.");
        }
        mediaAlbumRepository.delete(album);
    }

    /**
     * Load an album for deletion. Admins may delete any folder; validators any
     * in their institution; contributors only folders they created themselves.
     */
    private MediaAlbum loadAlbumForDelete(UUID albumId, JwtUserDetails user) {
        MediaAlbum album = mediaAlbumRepository.findById(albumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Album not found."));
        if (canDeleteAlbum(album, user)) {
            return album;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Contributors can only delete folders they created.");
    }

    /**
     * Admin → any; validator → own institution; contributor → own institution
     * AND creator.
     */
    private boolean canDeleteAlbum(MediaAlbum album, JwtUserDetails user) {
        if (isAdmin(user)) {
            return true;
        }
        boolean sameInstitution = album.getInstitution().getId().equals(user.institutionId());
        boolean owner = album.getCreatedBy() != null && album.getCreatedBy().equals(user.userId());
        return sameInstitution && owner;
    }

    /**
     * Load an album for rename/move/delete. Admins may manage any institution's
     * folders; everyone else only their own — not the shared default
     * institution's, not other institutions' (they may still add folders/files
     * there).
     */
    private MediaAlbum loadAlbumForManage(UUID albumId, JwtUserDetails user) {
        MediaAlbum album = mediaAlbumRepository.findById(albumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Album not found."));
        if (!isAdmin(user) && !album.getInstitution().getId().equals(user.institutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only manage folders in your own institution.");
        }
        return album;
    }

    /**
     * Walk a folder path (["Event", "Day 1"]), creating any missing segment
     * under the previous one, and return the leaf album. Backs "Upload folder".
     */
    public MediaAlbumDto ensureAlbumPath(UUID requestedInstitutionId, List<String> segments, JwtUserDetails user) {
        UUID institutionId = resolveTargetInstitutionId(requestedInstitutionId, user);
        if (segments == null || segments.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A folder path is required.");
        }
        MediaAlbum current = null;
        for (String rawSegment : segments) {
            String name = normalizeRequiredAlbumName(rawSegment);
            MediaAlbum parent = current;
            current = mediaAlbumRepository
                    .findByParentAndNameIgnoreCase(institutionId, parent == null ? null : parent.getId(), name)
                    .orElseGet(() -> {
                        MediaAlbum album = new MediaAlbum();
                        album.setInstitution(entityManager.getReference(Institution.class, institutionId));
                        album.setParentAlbum(parent);
                        album.setName(name);
                        album.setCreatedBy(user.userId());
                        return mediaAlbumRepository.save(album);
                    });
        }
        return MediaAlbumDto.from(current);
    }

    private MediaAlbum loadAlbumInInstitution(UUID albumId, UUID institutionId) {
        return mediaAlbumRepository.findById(albumId)
                .filter(a -> a.getInstitution().getId().equals(institutionId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Album not found."));
    }

    private MediaAlbum loadParentAlbumOrNull(UUID parentAlbumId, UUID institutionId) {
        return parentAlbumId == null ? null : loadAlbumInInstitution(parentAlbumId, institutionId);
    }

    private void assertAlbumNameFree(UUID institutionId, MediaAlbum parent, String name, UUID ignoreAlbumId) {
        mediaAlbumRepository
                .findByParentAndNameIgnoreCase(institutionId, parent == null ? null : parent.getId(), name)
                .filter(existing -> ignoreAlbumId == null || !existing.getId().equals(ignoreAlbumId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "An album named \"" + name + "\" already exists in this folder.");
                });
    }

    public MediaAssetDetailDto updateAlbum(UUID assetId, MediaAssetAlbumRequestDto dto, JwtUserDetails user) {
        MediaAsset asset = loadAsset(assetId, user);
        if (dto.getAlbumId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An album is required.");
        }

        MediaAlbum album = mediaAlbumRepository.findById(dto.getAlbumId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected album was not found."));

        UUID assetInstitutionId = asset.getInstitution().getId();
        UUID targetInstitutionId = album.getInstitution().getId();
        if (!targetInstitutionId.equals(assetInstitutionId)) {
            // Moving the asset into another institution — only its uploader or an
            // admin, and (for non-admins) only into the shared default library.
            boolean owner = asset.getUploader() != null && asset.getUploader().getId().equals(user.userId());
            if (!isAdmin(user) && !owner) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only move assets you uploaded.");
            }
            if (!isAdmin(user) && !targetInstitutionId.equals(sharedInstitutionId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "You can only move assets within your institution or into the shared library.");
            }
            asset.setInstitution(album.getInstitution());
        }
        asset.setMediaAlbum(album);
        return MediaAssetDetailDto.from(mediaAssetRepository.save(asset), List.of(), currentTags(assetId));
    }

    public AssetTagDto addTag(UUID assetId, AddAssetTagRequestDto dto, JwtUserDetails user) {
        MediaAsset asset = loadAsset(assetId, user);
        String trimmedLabel = dto.getLabel().trim();

        if (assetTagRepository.existsByMediaAssetIdAndLabel(asset.getId(), trimmedLabel)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tag already exists on this asset.");
        }

        AssetTag tag = new AssetTag();
        tag.setMediaAsset(asset);
        tag.setLabel(trimmedLabel);
        tag.setSource("manual");
        return AssetTagDto.from(assetTagRepository.save(tag));
    }

    public void removeTag(UUID assetId, UUID tagId, JwtUserDetails user) {
        loadAsset(assetId, user);
        AssetTag tag = assetTagRepository.findById(tagId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tag not found."));
        if (!tag.getMediaAsset().getId().equals(assetId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tag not found.");
        }
        assetTagRepository.delete(tag);
    }

    public MediaAssetUploadUrlResponseDto createUploadUrl(MediaAssetUploadUrlRequestDto dto, JwtUserDetails user) {
        String safeFileName = dto.getFileName().replaceAll("[^a-zA-Z0-9._-]", "-");
        String objectPath = user.institutionId() + "/" + UUID.randomUUID() + "-" + safeFileName;
        String signedUrl = supabaseStorageService.createSignedUploadUrl(objectPath);
        String publicUrl = supabaseStorageService.getPublicUrl(objectPath);
        return new MediaAssetUploadUrlResponseDto(signedUrl, publicUrl, objectPath);
    }

    private String generateAssetCode() {
        String code;
        do {
            code = "ASSET-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (mediaAssetRepository.existsByAssetCode(code));
        return code;
    }

    private boolean isAdmin(JwtUserDetails user) {
        return user.role() != null && user.role().toLowerCase().contains("admin");
    }

    private UUID resolveTargetInstitutionId(UUID requestedInstitutionId, JwtUserDetails user) {
        if (isAdmin(user) && requestedInstitutionId != null) {
            return requestedInstitutionId;
        }
        // Non-admins may also add folders/files to the shared default institution.
        if (requestedInstitutionId != null && requestedInstitutionId.equals(sharedInstitutionId())) {
            return requestedInstitutionId;
        }
        if (user.institutionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Institution scope is required for media library uploads.");
        }
        return user.institutionId();
    }

    private MediaAlbum resolveAlbum(MediaAssetUploadRequestDto dto, UUID institutionId, UUID createdBy) {
        if (dto.getAlbumId() != null) {
            return mediaAlbumRepository.findById(dto.getAlbumId())
                    .filter(album -> album.getInstitution().getId().equals(institutionId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected album was not found."));
        }

        if (dto.isAutoMatchAlbum()) {
            return autoMatchAlbum(dto, institutionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No confident album match found. Select an album or create a new one."));
        }

        String albumName = normalizeRequiredAlbumName(dto.getAlbumName());
        return mediaAlbumRepository.findByParentAndNameIgnoreCase(institutionId, null, albumName)
                .orElseGet(() -> {
                    MediaAlbum album = new MediaAlbum();
                    album.setInstitution(entityManager.getReference(Institution.class, institutionId));
                    album.setName(albumName);
                    album.setCreatedBy(createdBy);
                    return mediaAlbumRepository.save(album);
                });
    }

    private java.util.Optional<MediaAlbum> autoMatchAlbum(MediaAssetUploadRequestDto dto, UUID institutionId) {
        List<MediaAlbum> albums = mediaAlbumRepository.findByInstitutionIdOrderByName(institutionId);
        if (albums.isEmpty()) {
            return java.util.Optional.empty();
        }
        Set<String> cues = new LinkedHashSet<>(normalizeTags(dto.getTags()).stream().map(String::toLowerCase).toList());
        String fileName = dto.getFileName() == null ? "" : dto.getFileName().toLowerCase();
        return albums.stream()
                .filter(album -> {
                    String name = album.getName().toLowerCase();
                    return cues.stream().anyMatch(tag -> !tag.isBlank() && (name.contains(tag) || tag.contains(name)))
                            || (!name.isBlank() && fileName.contains(name));
                })
                .findFirst();
    }

    private String normalizeRequiredAlbumName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Album is required.");
        }
        return name;
    }

    private List<String> normalizeTags(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        return raw.stream()
                .map(tag -> tag == null ? "" : tag.trim())
                .filter(tag -> !tag.isBlank())
                .distinct()
                .limit(20)
                .toList();
    }

    private List<AssetTagDto> saveManualTags(MediaAsset asset, List<String> labels) {
        List<AssetTagDto> saved = new ArrayList<>();
        for (String label : labels) {
            AssetTag tag = new AssetTag();
            tag.setMediaAsset(asset);
            tag.setLabel(label);
            tag.setSource("manual");
            saved.add(AssetTagDto.from(assetTagRepository.save(tag)));
        }
        return saved;
    }

    private List<AssetTagDto> currentTags(UUID assetId) {
        return assetTagRepository
                .findByMediaAssetIdOrderByCreatedAtAsc(assetId)
                .stream()
                .map(AssetTagDto::from)
                .toList();
    }

    /**
     * Media Repository visibility: an asset that is exclusively attached to an
     * unsubmitted draft never appears in the Media Repository — not even to its
     * uploader. It stays visible only inside that draft submission's own media
     * picker until the submission leaves draft status, at which point it
     * publishes into the repository for everyone in scope. Standalone assets
     * (never attached to any submission) are unaffected.
     */
    private boolean isPublishedToRepository(MediaAsset asset) {
        Set<UUID> singleAssetId = Set.of(asset.getId());
        boolean attached = !submissionMediaAssetRepository.findAssetIdsWithAnySubmissionLink(singleAssetId).isEmpty();
        if (!attached) {
            return true;
        }
        return !submissionMediaAssetRepository.findAssetIdsUsedBeyondDraft(singleAssetId).isEmpty();
    }

    private boolean isPublishedToRepository(
            MediaAsset asset, Set<UUID> attachedAssetIds, Set<UUID> assetIdsUsedBeyondDraft) {
        if (!attachedAssetIds.contains(asset.getId())) {
            return true;
        }
        return assetIdsUsedBeyondDraft.contains(asset.getId());
    }

    private boolean isContributor(JwtUserDetails user) {
        return user.role() != null && user.role().toLowerCase().contains("contributor");
    }

    private MediaAsset loadAsset(UUID assetId, JwtUserDetails user) {
        MediaAsset asset = mediaAssetRepository.findActiveById(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media asset not found."));
        if (!isAdmin(user) && !asset.getInstitution().getId().equals(user.institutionId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media asset not found.");
        }
        if (!isPublishedToRepository(asset)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media asset not found.");
        }
        return asset;
    }

    private MediaAsset loadAssetForDelete(UUID assetId, JwtUserDetails user) {
        MediaAsset asset = mediaAssetRepository.findActiveById(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media asset not found."));

        if (isAdmin(user)) {
            return asset;
        }
        if (isContributor(user)) {
            boolean sameInstitution = asset.getInstitution().getId().equals(user.institutionId());
            boolean owner = asset.getUploader() != null && asset.getUploader().getId().equals(user.userId());
            if (sameInstitution && owner) {
                return asset;
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Contributors can only delete assets they uploaded.");
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to delete media assets.");
    }

    private static boolean containsIgnoreCase(String value, String query) {
        if (value == null) {
            return false;
        }
        return value.toLowerCase().contains(query);
    }

    private static Comparator<MediaAsset> resolveSort(String sort) {
        if (sort == null || sort.isBlank() || sort.equalsIgnoreCase("newest")) {
            return Comparator.comparing(MediaAsset::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                    .reversed();
        }
        if (sort.equalsIgnoreCase("oldest")) {
            return Comparator.comparing(MediaAsset::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        }
        if (sort.equalsIgnoreCase("name")) {
            return Comparator.comparing(MediaAsset::getFileName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        }
        if (sort.equalsIgnoreCase("size")) {
            return Comparator.comparingLong(MediaAsset::getFileSizeBytes).reversed();
        }
        return Comparator.comparing(MediaAsset::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed();
    }
}
