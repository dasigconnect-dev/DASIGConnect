package com.dasigconnect.backend.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.model.dto.media.AddAssetTagRequestDto;
import com.dasigconnect.backend.model.dto.media.AssetTagDto;
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
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.MediaAssetStatus;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.dto.media.MediaAssetUploadUrlRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetUploadUrlResponseDto;
import com.dasigconnect.backend.repository.AssetTagRepository;
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
            MediaAssetEmbeddingRepository mediaAssetEmbeddingRepository,
            SubmissionService submissionService,
            SupabaseStorageService supabaseStorageService,
            AIClassificationService aiClassificationService) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.submissionRepository = submissionRepository;
        this.submissionMediaAssetRepository = submissionMediaAssetRepository;
        this.assetTagRepository = assetTagRepository;
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

        boolean admin = isAdmin(user);
        boolean networkScope = admin && "network".equalsIgnoreCase(scope);
        List<MediaAsset> source;
        if (admin && institutionId != null) {
            source = mediaAssetRepository.findActiveByInstitution(institutionId);
        } else if (admin || networkScope) {
            source = mediaAssetRepository.findAllActive();
        } else {
            source = mediaAssetRepository.findActiveByInstitution(user.institutionId());
        }

        List<MediaAsset> filtered = source
                .stream()
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

        MediaAsset asset = new MediaAsset();
        asset.setInstitution(entityManager.getReference(Institution.class, user.institutionId()));
        asset.setUploader(entityManager.getReference(User.class, user.userId()));
        asset.setAssetCode(generateAssetCode());
        asset.setStorageUrl(dto.getStorageUrl());
        asset.setFileName(dto.getFileName());
        asset.setFileType(fileType);
        asset.setFileSizeBytes(dto.getFileSizeBytes());
        asset.setStatus(MediaAssetStatus.PROCESSING);
        asset = mediaAssetRepository.save(asset);

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

        return MediaAssetDetailDto.from(asset, List.of(), List.of());
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

    private boolean isValidator(JwtUserDetails user) {
        return user.role() != null && user.role().toLowerCase().contains("validator");
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
