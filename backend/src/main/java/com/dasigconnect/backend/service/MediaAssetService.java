package com.dasigconnect.backend.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.model.dto.media.AddAssetTagRequestDto;
import com.dasigconnect.backend.model.dto.media.AssetTagDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetAddToDraftRequestDto;
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
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.AssetTagRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionMediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.security.JwtUserDetails;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
@Transactional
public class MediaAssetService {

    private final MediaAssetRepository mediaAssetRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionMediaAssetRepository submissionMediaAssetRepository;
    private final AssetTagRepository assetTagRepository;
    private final SubmissionService submissionService;

    @PersistenceContext
    private EntityManager entityManager;

    public MediaAssetService(
            MediaAssetRepository mediaAssetRepository,
            SubmissionRepository submissionRepository,
            SubmissionMediaAssetRepository submissionMediaAssetRepository,
            AssetTagRepository assetTagRepository,
            SubmissionService submissionService) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.submissionRepository = submissionRepository;
        this.submissionMediaAssetRepository = submissionMediaAssetRepository;
        this.assetTagRepository = assetTagRepository;
        this.submissionService = submissionService;
    }

    @Transactional(readOnly = true)
    public MediaAssetListResponseDto list(
            String query,
            String aiCategory,
            UUID uploaderId,
            String sort,
            int page,
            int pageSize,
            String scope,
            JwtUserDetails user) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        String trimmedQuery = query == null ? "" : query.trim().toLowerCase();
        String trimmedCategory = aiCategory == null ? "" : aiCategory.trim();

        boolean networkScope = isAdmin(user) && "network".equalsIgnoreCase(scope);
        List<MediaAsset> source = networkScope
                ? mediaAssetRepository.findAllActive()
                : mediaAssetRepository.findActiveByInstitution(user.institutionId());

        List<MediaAsset> filtered = source
                .stream()
                .filter(asset -> trimmedQuery.isBlank()
                || containsIgnoreCase(asset.getFileName(), trimmedQuery)
                || containsIgnoreCase(asset.getAssetCode(), trimmedQuery))
                .filter(asset -> trimmedCategory.isBlank()
                || (asset.getAiCategory() != null && asset.getAiCategory().equalsIgnoreCase(trimmedCategory)))
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
        MediaAsset asset = loadAsset(assetId, user);

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

        asset.setDeletedAt(Instant.now());
        mediaAssetRepository.save(asset);
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
        asset = mediaAssetRepository.save(asset);

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

    private MediaAsset loadAsset(UUID assetId, JwtUserDetails user) {
        MediaAsset asset = mediaAssetRepository.findActiveById(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media asset not found."));
        if (!isAdmin(user) && !asset.getInstitution().getId().equals(user.institutionId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media asset not found.");
        }
        return asset;
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
