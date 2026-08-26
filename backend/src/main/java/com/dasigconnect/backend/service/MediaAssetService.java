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
import com.dasigconnect.backend.model.dto.media.MediaAssetAlbumRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetAddToDraftRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetBulkDeleteRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetBulkDeleteResponseDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetDetailDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetListResponseDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetSummaryDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetUploadRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetUsageDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetUseInNewPostRequestDto;
import com.dasigconnect.backend.model.dto.submission.AttachAssetDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionCreateDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionResponseDto;
import com.dasigconnect.backend.model.entity.AssetTag;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAlbum;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.MediaAssetStatus;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.dto.media.MediaAssetUploadUrlRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetUploadUrlResponseDto;
import com.dasigconnect.backend.repository.AssetTagRepository;
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
    private final SubmissionService submissionService;
    private final SupabaseStorageService supabaseStorageService;
    private final AIClassificationService aiClassificationService;

    @PersistenceContext
    private EntityManager entityManager;

    public MediaAssetService(
            MediaAssetRepository mediaAssetRepository,
            SubmissionRepository submissionRepository,
            SubmissionMediaAssetRepository submissionMediaAssetRepository,
            AssetTagRepository assetTagRepository,
            MediaAlbumRepository mediaAlbumRepository,
            MediaAssetEmbeddingRepository mediaAssetEmbeddingRepository,
            SubmissionService submissionService,
            SupabaseStorageService supabaseStorageService,
            AIClassificationService aiClassificationService) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.submissionRepository = submissionRepository;
        this.submissionMediaAssetRepository = submissionMediaAssetRepository;
        this.assetTagRepository = assetTagRepository;
        this.mediaAlbumRepository = mediaAlbumRepository;
        this.mediaAssetEmbeddingRepository = mediaAssetEmbeddingRepository;
        this.submissionService = submissionService;
        this.supabaseStorageService = supabaseStorageService;
        this.aiClassificationService = aiClassificationService;
    }

    @Transactional(readOnly = true)
    public MediaAssetListResponseDto list(
            String query,
            String aiCategory,
            String mediaType,
            UUID uploaderId,
            UUID institutionId,
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
            source = mediaAssetRepository.findActiveByInstitution(user.institutionId());
        }

        List<UUID> sourceIds = source.stream().map(MediaAsset::getId).toList();
        Set<UUID> attachedAssetIds = submissionMediaAssetRepository.findAssetIdsWithAnySubmissionLink(sourceIds);
        Set<UUID> assetIdsUsedBeyondDraft = submissionMediaAssetRepository.findAssetIdsUsedBeyondDraft(sourceIds);

        List<MediaAsset> filtered = source
                .stream()
                .filter(asset -> isPublishedToRepository(asset, attachedAssetIds, assetIdsUsedBeyondDraft))
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

    @Transactional(readOnly = true)
    public MediaAssetDetailDto get(UUID id, JwtUserDetails user) {
        MediaAsset asset = mediaAssetRepository.findActiveById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media asset not found."));
        if (!isAdmin(user) && !asset.getInstitution().getId().equals(user.institutionId())) {
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
        UUID institutionId = resolveTargetInstitutionId(requestedInstitutionId, user);
        return mediaAlbumRepository.findByInstitutionIdOrderByName(institutionId)
                .stream()
                .map(MediaAlbumDto::from)
                .toList();
    }

    public MediaAlbumDto createAlbum(MediaAlbumRequestDto dto, JwtUserDetails user) {
        UUID institutionId = resolveTargetInstitutionId(dto.getInstitutionId(), user);
        String name = normalizeRequiredAlbumName(dto.getName());
        if (mediaAlbumRepository.existsByInstitutionIdAndNameIgnoreCase(institutionId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Album already exists.");
        }
        MediaAlbum album = new MediaAlbum();
        album.setInstitution(entityManager.getReference(Institution.class, institutionId));
        album.setName(name);
        album.setCreatedBy(user.userId());
        return MediaAlbumDto.from(mediaAlbumRepository.save(album));
    }

    public MediaAlbumDto renameAlbum(UUID albumId, MediaAlbumRequestDto dto, JwtUserDetails user) {
        UUID institutionId = resolveTargetInstitutionId(dto.getInstitutionId(), user);
        MediaAlbum album = mediaAlbumRepository.findById(albumId)
                .filter(a -> a.getInstitution().getId().equals(institutionId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Album not found."));
        String name = normalizeRequiredAlbumName(dto.getName());
        mediaAlbumRepository.findByInstitutionIdAndNameIgnoreCase(institutionId, name)
                .filter(existing -> !existing.getId().equals(albumId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Album already exists.");
                });
        album.setName(name);
        return MediaAlbumDto.from(mediaAlbumRepository.save(album));
    }

    public MediaAssetDetailDto updateAlbum(UUID assetId, MediaAssetAlbumRequestDto dto, JwtUserDetails user) {
        MediaAsset asset = loadAsset(assetId, user);
        if (dto.getAlbumId() == null) {
            asset.setMediaAlbum(null);
            return MediaAssetDetailDto.from(mediaAssetRepository.save(asset), List.of(), currentTags(assetId));
        }

        MediaAlbum album = mediaAlbumRepository.findById(dto.getAlbumId())
                .filter(a -> a.getInstitution().getId().equals(asset.getInstitution().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected album was not found."));
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
        return mediaAlbumRepository.findByInstitutionIdAndNameIgnoreCase(institutionId, albumName)
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
        if (raw == null) return List.of();
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
     * Media Repository visibility: an asset that is exclusively attached to
     * an unsubmitted draft never appears in the Media Repository — not even
     * to its uploader. It stays visible only inside that draft submission's
     * own media picker until the submission leaves draft status, at which
     * point it publishes into the repository for everyone in scope.
     * Standalone assets (never attached to any submission) are unaffected.
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

    private boolean isValidator(JwtUserDetails user) {
        return user.role() != null && user.role().toLowerCase().contains("administrator");
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
        if (isValidator(user)) {
            if (asset.getInstitution().getId().equals(user.institutionId())) {
                return asset;
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media asset not found.");
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
