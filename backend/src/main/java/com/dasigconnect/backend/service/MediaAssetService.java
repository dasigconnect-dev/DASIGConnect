package com.dasigconnect.backend.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.model.dto.media.AddAssetTagRequestDto;
import com.dasigconnect.backend.model.dto.media.AssetTagDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetAddToDraftRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetBulkDeleteRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetBulkDeleteResponseDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetCurationEditDto;
import com.dasigconnect.backend.model.dto.media.MediaBatchCurationResponseDto;
import com.dasigconnect.backend.model.dto.media.MediaBatchCurationRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetDetailDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetListResponseDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetSummaryDto;
import com.dasigconnect.backend.model.dto.media.MediaAuditEntryDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetUploadRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetVisibilityRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetUsageDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetUseInNewPostRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaImportBatchCreateRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaImportBatchResponseDto;
import com.dasigconnect.backend.model.dto.submission.AttachAssetDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionCreateDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionResponseDto;
import com.dasigconnect.backend.model.entity.AssetTag;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.MediaAssetStatus;
import com.dasigconnect.backend.model.entity.MediaImportBatch;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.dto.media.MediaAssetUploadUrlRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetUploadUrlResponseDto;
import com.dasigconnect.backend.repository.AssetTagRepository;
import com.dasigconnect.backend.repository.AuditLogRepository;
import com.dasigconnect.backend.repository.MediaAssetEmbeddingRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.MediaAssetRightsRepository;
import com.dasigconnect.backend.repository.MediaImportBatchRepository;
import com.dasigconnect.backend.repository.SubmissionMediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.repository.UserRepository;
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
    private final MediaImportBatchRepository mediaImportBatchRepository;
    private final SubmissionService submissionService;
    private final SupabaseStorageService supabaseStorageService;
    private final MediaIngestionQueueService mediaIngestionQueueService;
    private final MediaIntegrityQueueService mediaIntegrityQueueService;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final AuditLogRepository auditLogRepository;
    private final MediaAssetRightsRepository mediaAssetRightsRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public MediaAssetService(
            MediaAssetRepository mediaAssetRepository,
            SubmissionRepository submissionRepository,
            SubmissionMediaAssetRepository submissionMediaAssetRepository,
            AssetTagRepository assetTagRepository,
            MediaAssetEmbeddingRepository mediaAssetEmbeddingRepository,
            MediaImportBatchRepository mediaImportBatchRepository,
            SubmissionService submissionService,
            SupabaseStorageService supabaseStorageService,
            MediaIngestionQueueService mediaIngestionQueueService,
            MediaIntegrityQueueService mediaIntegrityQueueService,
            UserRepository userRepository,
            AuditLogService auditLogService,
            AuditLogRepository auditLogRepository,
            MediaAssetRightsRepository mediaAssetRightsRepository) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.submissionRepository = submissionRepository;
        this.submissionMediaAssetRepository = submissionMediaAssetRepository;
        this.assetTagRepository = assetTagRepository;
        this.mediaAssetEmbeddingRepository = mediaAssetEmbeddingRepository;
        this.mediaImportBatchRepository = mediaImportBatchRepository;
        this.submissionService = submissionService;
        this.supabaseStorageService = supabaseStorageService;
        this.mediaIngestionQueueService = mediaIngestionQueueService;
        this.mediaIntegrityQueueService = mediaIntegrityQueueService;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.auditLogRepository = auditLogRepository;
        this.mediaAssetRightsRepository = mediaAssetRightsRepository;
    }

    /**
     * UC-4.11: the provenance trail for an asset (newest first). Visibility mirrors the asset
     * detail endpoint — {@link #loadAsset} enforces admin/institution scope and 404s otherwise.
     */
    @Transactional(readOnly = true)
    public List<MediaAuditEntryDto> getHistory(UUID assetId, JwtUserDetails user) {
        loadAsset(assetId, user);
        return auditLogRepository.findByResourceIdWithActor(assetId).stream()
                .map(MediaAuditEntryDto::from)
                .toList();
    }

    /**
     * UC-4.11 provenance: write an immutable audit row for a state-changing media operation.
     * Best-effort — a logging failure must never roll back the business action (same discipline
     * as the T1 notification block). {@code AuditLogService.record} is REQUIRES_NEW, so the row
     * commits independently of the caller's transaction.
     */
    private void auditMedia(JwtUserDetails user, String action, UUID resourceId, Map<String, ?> metadata) {
        try {
            User actor = user == null ? null : userRepository.findById(user.userId()).orElse(null);
            auditLogService.record(actor, action, null, null, resourceId, metadata);
        } catch (Exception e) {
            log.warn("Failed to write media audit [{}] for {}: {}", action, resourceId, e.getMessage());
        }
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
            String health,
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
                .filter(asset -> matchesHealthFilter(asset, health))
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
        // Embeddings are retained through the trash window so Restore is lossless; they are
        // removed only at purge (retention job or on-demand "Delete forever"). Search/recommend
        // queries already exclude soft-deleted assets via the deleted_at filter.
        mediaAssetRepository.save(asset);

        auditMedia(user, "MEDIA_ASSET_DELETED", assetId, Map.of(
                "assetCode", String.valueOf(asset.getAssetCode()),
                "institutionId", String.valueOf(asset.getInstitution().getId()),
                "force", String.valueOf(force)));
    }

    /**
     * Resilient bulk soft-delete. Instead of failing the whole batch when one asset is
     * blocked, it deletes every asset it can and reports the rest in
     * {@link MediaAssetBulkDeleteResponseDto#getSkipped()}. An asset is skipped when it is
     * referenced by an active (pending/in-review/scheduled) submission, already gone, or
     * outside the caller's delete scope. Only a malformed request (empty / over the cap)
     * fails outright.
     */
    public MediaAssetBulkDeleteResponseDto bulkDelete(MediaAssetBulkDeleteRequestDto dto, JwtUserDetails user) {
        List<UUID> assetIds = new ArrayList<>(new LinkedHashSet<>(dto.getAssetIds()));
        if (assetIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one asset to delete.");
        }
        if (assetIds.size() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You can delete up to 100 assets at once.");
        }

        List<MediaAsset> toDelete = new ArrayList<>();
        List<MediaAssetBulkDeleteResponseDto.SkippedAsset> skipped = new ArrayList<>();

        for (UUID assetId : assetIds) {
            MediaAsset asset;
            try {
                asset = loadAssetForDelete(assetId, user);
            } catch (ResponseStatusException ex) {
                skipped.add(skip(assetId, null, scopeSkipReason(ex.getStatusCode().value())));
                continue;
            }

            String blockReason = deletionBlockReason(assetId, dto.isForce());
            if (blockReason != null) {
                skipped.add(skip(assetId, asset.getAssetCode(), blockReason));
                continue;
            }
            toDelete.add(asset);
        }

        if (!toDelete.isEmpty()) {
            Instant deletedAt = Instant.now();
            for (MediaAsset asset : toDelete) {
                asset.setDeletedAt(deletedAt);
                asset.setDeletedByUserId(user.userId());
                asset.setStatus(MediaAssetStatus.DELETED);
                // Embeddings retained through the trash window (see delete()); removed at purge.
            }
            mediaAssetRepository.saveAll(toDelete);

            List<UUID> deletedIds = toDelete.stream().map(MediaAsset::getId).toList();
            auditMedia(user, "MEDIA_ASSETS_BULK_DELETED", null, Map.of(
                    "count", String.valueOf(deletedIds.size()),
                    "force", String.valueOf(dto.isForce()),
                    "skippedCount", String.valueOf(skipped.size()),
                    "assetIds", String.join(",", deletedIds.stream().map(UUID::toString).toList())));
        }

        return new MediaAssetBulkDeleteResponseDto(
                toDelete.stream().map(MediaAsset::getId).toList(), skipped);
    }

    private void validateDeleteReferences(UUID assetId, boolean force) {
        long blockingCount = submissionMediaAssetRepository.countBlockingSubmissionsByAssetId(assetId);
        if (blockingCount > 0 && !force) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Asset is referenced by active submissions. Use force=true to delete.");
        }

        long warningCount = submissionMediaAssetRepository.countDraftSubmissionsByAssetId(assetId);
        if (warningCount > 0 && !force) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Asset is referenced by drafts. Use force=true to delete.");
        }
    }

    /** Non-throwing variant of {@link #validateDeleteReferences} for the bulk path. */
    private String deletionBlockReason(UUID assetId, boolean force) {
        if (!force && submissionMediaAssetRepository.countBlockingSubmissionsByAssetId(assetId) > 0) {
            return "in_use";
        }
        if (!force && submissionMediaAssetRepository.countDraftSubmissionsByAssetId(assetId) > 0) {
            return "has_drafts";
        }
        return null;
    }

    private static String scopeSkipReason(int status) {
        if (status == HttpStatus.NOT_FOUND.value()) {
            return "missing";
        }
        if (status == HttpStatus.FORBIDDEN.value()) {
            return "not_allowed";
        }
        return "error";
    }

    private static MediaAssetBulkDeleteResponseDto.SkippedAsset skip(UUID assetId, String assetCode, String reason) {
        return new MediaAssetBulkDeleteResponseDto.SkippedAsset(assetId, assetCode, reason, skipMessage(reason));
    }

    private static String skipMessage(String reason) {
        return switch (reason) {
            case "in_use" -> "Referenced by a pending, in-review, or scheduled submission.";
            case "has_drafts" -> "Referenced by a draft submission.";
            case "missing" -> "No longer in the library.";
            case "not_allowed" -> "Outside your delete scope.";
            default -> "Could not be deleted.";
        };
    }

    public MediaAssetDetailDto upload(MediaAssetUploadRequestDto dto, JwtUserDetails user) {
        UUID institutionId = resolveTargetInstitution(user, dto.getInstitutionId(), "upload assets");
        validateImportBatchScope(dto.getImportBatchId(), institutionId);
        MediaFileType fileType;
        try {
            fileType = MediaFileType.valueOf(dto.getFileType().toLowerCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported file type: " + dto.getFileType());
        }

        MediaAsset asset = new MediaAsset();
        asset.setInstitution(entityManager.getReference(Institution.class, institutionId));
        asset.setUploader(entityManager.getReference(User.class, user.userId()));
        asset.setAssetCode(generateAssetCode());
        asset.setStorageUrl(dto.getStorageUrl());
        asset.setFileName(dto.getFileName());
        asset.setFileType(fileType);
        asset.setFileSizeBytes(dto.getFileSizeBytes());
        asset.setImportBatchId(dto.getImportBatchId());
        asset.setStatus(MediaAssetStatus.PROCESSING);
        asset = mediaAssetRepository.save(asset);

        // Enqueue classification + embedding on the bounded ingestion pool — never blocks
        // the upload response (UC-4.2, ADR-0002).
        final UUID savedId = asset.getId();
        final String savedUrl = asset.getStorageUrl();
        final MediaFileType savedType = asset.getFileType();
        afterCommit(() -> {
            try {
                mediaIntegrityQueueService.enqueueIngestCheck(savedId);
            } catch (Exception e) {
                log.warn("Failed to enqueue integrity check for asset {}: {}", savedId, e.getMessage());
            }
            try {
                if (savedType.isImage()) {
                    mediaIngestionQueueService.enqueue(savedId, savedUrl);
                }
            } catch (Exception e) {
                log.warn("Failed to enqueue AI classification for asset {}: {}", savedId, e.getMessage());
            }
        });

        auditMedia(user, "MEDIA_ASSET_UPLOADED", savedId, Map.of(
                "assetCode", String.valueOf(asset.getAssetCode()),
                "institutionId", String.valueOf(institutionId),
                "fileType", fileType.name(),
                "fileName", String.valueOf(dto.getFileName())));

        return MediaAssetDetailDto.from(asset, List.of(), List.of());
    }

    public MediaImportBatchResponseDto createImportBatch(MediaImportBatchCreateRequestDto dto, JwtUserDetails user) {
        UUID institutionId = resolveTargetInstitution(user, dto.getInstitutionId(), "create import batches");
        MediaImportBatch batch = new MediaImportBatch();
        batch.setInstitution(entityManager.getReference(Institution.class, institutionId));
        batch.setUploadedBy(entityManager.getReference(User.class, user.userId()));
        batch.setAssetCount(dto.getAssetCount());
        return MediaImportBatchResponseDto.from(mediaImportBatchRepository.save(batch));
    }

    @Transactional(readOnly = true)
    public List<MediaImportBatchResponseDto> listImportBatches(UUID requestedInstitutionId, JwtUserDetails user) {
        UUID institutionId = resolveTargetInstitution(user, requestedInstitutionId, "review import batches");
        return mediaImportBatchRepository.findByInstitution(institutionId)
                .stream()
                .map(batch -> {
                    List<MediaAsset> assets = mediaAssetRepository.findActiveByImportBatch(batch.getId(), institutionId);
                    int readyCount = (int) assets.stream()
                            .filter(asset -> asset.getStatus() == MediaAssetStatus.READY)
                            .count();
                    int curatedCount = (int) assets.stream()
                            .filter(asset -> asset.getCuratedAt() != null)
                            .count();
                    return MediaImportBatchResponseDto.from(batch, assets.size(), readyCount, curatedCount);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MediaAssetDetailDto> listImportBatchAssets(UUID importBatchId, UUID requestedInstitutionId, JwtUserDetails user) {
        UUID institutionId = resolveTargetInstitution(user, requestedInstitutionId, "review import batches");
        mediaImportBatchRepository.findByIdAndInstitution(importBatchId, institutionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import batch not found."));

        return mediaAssetRepository.findActiveByImportBatch(importBatchId, institutionId)
                .stream()
                .map(asset -> MediaAssetDetailDto.from(asset, List.of(), tagsForAsset(asset.getId())))
                .toList();
    }

    public MediaBatchCurationResponseDto markImportBatchCurated(
            UUID importBatchId,
            UUID requestedInstitutionId,
            MediaBatchCurationRequestDto dto,
            JwtUserDetails user) {
        UUID institutionId = resolveTargetInstitution(user, requestedInstitutionId, "curate import batches");
        mediaImportBatchRepository.findByIdAndInstitution(importBatchId, institutionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import batch not found."));
        List<MediaAsset> assets = mediaAssetRepository.findActiveByImportBatch(importBatchId, institutionId);
        applyCurationEdits(assets, dto);
        Instant curatedAt = Instant.now();
        for (MediaAsset asset : assets) {
            if (asset.getTitle() == null || asset.getTitle().isBlank()) {
                asset.setTitle(titleFromFileName(asset.getFileName()));
            }
            asset.setCuratedAt(curatedAt);
        }
        mediaAssetRepository.saveAll(assets);

        auditMedia(user, "MEDIA_BATCH_CURATED", importBatchId, Map.of(
                "institutionId", String.valueOf(institutionId),
                "curatedCount", String.valueOf(assets.size())));
        return new MediaBatchCurationResponseDto(assets.size());
    }

    public MediaBatchCurationResponseDto markImportBatchCurated(
            UUID importBatchId,
            UUID requestedInstitutionId,
            JwtUserDetails user) {
        return markImportBatchCurated(importBatchId, requestedInstitutionId, null, user);
    }

    /**
     * UC-4.2: delete an import batch grouping. Un-groups the batch's assets (clears
     * import_batch_id) and removes the batch row — the actual media assets are NOT deleted.
     */
    public void deleteImportBatch(UUID importBatchId, UUID requestedInstitutionId, JwtUserDetails user) {
        UUID institutionId = resolveTargetInstitution(user, requestedInstitutionId, "delete import batches");
        MediaImportBatch batch = mediaImportBatchRepository.findByIdAndInstitution(importBatchId, institutionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import batch not found."));
        int cleared = mediaAssetRepository.clearImportBatch(importBatchId, institutionId);
        mediaImportBatchRepository.delete(batch);
        auditMedia(user, "MEDIA_IMPORT_BATCH_DELETED", importBatchId, Map.of(
                "institutionId", String.valueOf(institutionId),
                "ungroupedAssets", String.valueOf(cleared)));
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
        AssetTag saved = assetTagRepository.save(tag);

        auditMedia(user, "MEDIA_ASSET_TAG_ADDED", assetId, Map.of(
                "label", trimmedLabel,
                "tagId", String.valueOf(saved.getId())));
        return AssetTagDto.from(saved);
    }

    public void removeTag(UUID assetId, UUID tagId, JwtUserDetails user) {
        loadAsset(assetId, user);
        AssetTag tag = assetTagRepository.findById(tagId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tag not found."));
        if (!tag.getMediaAsset().getId().equals(assetId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tag not found.");
        }
        String removedLabel = tag.getLabel();
        assetTagRepository.delete(tag);

        auditMedia(user, "MEDIA_ASSET_TAG_REMOVED", assetId, Map.of(
                "label", String.valueOf(removedLabel),
                "tagId", String.valueOf(tagId)));
    }

    /**
     * UC-4.x: change an asset's consent/visibility (internal_only | cleared_for_public).
     * Institution-scoped like the other asset mutations; the change is audited (UC-4.11).
     */
    public MediaAssetDetailDto changeVisibility(UUID assetId, MediaAssetVisibilityRequestDto dto, JwtUserDetails user) {
        MediaAsset asset = loadAsset(assetId, user);
        String newVisibility = dto.getVisibility() == null ? "" : dto.getVisibility().trim();
        if (!newVisibility.equals("internal_only") && !newVisibility.equals("cleared_for_public")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "visibility must be internal_only or cleared_for_public.");
        }

        String previous = asset.getVisibility();
        // UC-4.12 Phase 7D: a NEW clearance into cleared_for_public requires a complete,
        // non-expired rights record. Already-cleared assets are grandfathered — the gate only
        // fires on the transition, so re-saving an already-cleared asset never re-checks.
        if (newVisibility.equals("cleared_for_public") && !"cleared_for_public".equals(previous)) {
            boolean permitted = mediaAssetRightsRepository
                    .findByAssetIdAndInstitutionId(assetId, asset.getInstitution().getId())
                    .map(r -> r.permitsPublicClearance(java.time.LocalDate.now()))
                    .orElse(false);
            if (!permitted) {
                throw new ResponseStatusException(org.springframework.http.HttpStatusCode.valueOf(422),
                        "Asset " + asset.getAssetCode() + " needs a complete, non-expired rights "
                        + "record before it can be cleared for public use.");
            }
        }
        asset.setVisibility(newVisibility);
        mediaAssetRepository.save(asset);

        auditMedia(user, "MEDIA_ASSET_VISIBILITY_CHANGED", assetId, Map.of(
                "from", String.valueOf(previous),
                "to", newVisibility));
        return get(assetId, user);
    }

    /**
     * Upload-time exact-duplicate check (SHA-256). Returns, for each supplied hash that already
     * exists in the target institution, the existing asset — so the uploader can choose to use it,
     * keep both, or skip. Tenant-scoped via {@link #resolveTargetInstitution}.
     */
    @Transactional(readOnly = true)
    public java.util.List<com.dasigconnect.backend.model.dto.media.MediaDuplicateMatchDto> checkDuplicates(
            com.dasigconnect.backend.model.dto.media.MediaDuplicateCheckRequestDto dto, JwtUserDetails user) {
        UUID institutionId = resolveTargetInstitution(user, dto.getInstitutionId(), "check for duplicates");
        java.util.List<String> hashes = dto.getSha256s().stream()
                .filter(h -> h != null && !h.isBlank())
                .map(h -> h.trim().toLowerCase(java.util.Locale.ROOT))
                .distinct()
                .toList();
        if (hashes.isEmpty()) {
            return java.util.List.of();
        }
        java.util.Map<String, com.dasigconnect.backend.model.dto.media.MediaDuplicateMatchDto> firstBySha =
                new java.util.LinkedHashMap<>();
        for (MediaAsset asset : mediaAssetRepository.findActiveByInstitutionAndContentSha256In(institutionId, hashes)) {
            firstBySha.putIfAbsent(asset.getContentSha256(),
                    new com.dasigconnect.backend.model.dto.media.MediaDuplicateMatchDto(
                            asset.getContentSha256(),
                            com.dasigconnect.backend.model.dto.media.MediaAssetSummaryDto.from(asset)));
        }
        return java.util.List.copyOf(firstBySha.values());
    }

    public MediaAssetUploadUrlResponseDto createUploadUrl(MediaAssetUploadUrlRequestDto dto, JwtUserDetails user) {
        UUID institutionId = resolveTargetInstitution(user, dto.getInstitutionId(), "upload assets");
        String safeFileName = dto.getFileName().replaceAll("[^a-zA-Z0-9._-]", "-");
        String objectPath = institutionId + "/" + UUID.randomUUID() + "-" + safeFileName;
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

    /**
     * UC-4.12 Phase 7C drill-down: each Repository Health tile links to the affected asset set
     * via {@code ?health=...}. Unknown/blank values are a no-op so existing list calls are
     * unaffected.
     */
    private boolean matchesHealthFilter(MediaAsset asset, String health) {
        if (health == null || health.isBlank()) {
            return true;
        }
        return switch (health.trim().toLowerCase()) {
            case "integrity_failures" -> asset.getIntegrityStatus() == com.dasigconnect.backend.model.entity.MediaIntegrityStatus.MISMATCH
                    || asset.getIntegrityStatus() == com.dasigconnect.backend.model.entity.MediaIntegrityStatus.MISSING
                    || asset.getIntegrityStatus() == com.dasigconnect.backend.model.entity.MediaIntegrityStatus.ERROR;
            case "review_open" -> asset.getIntegrityReviewStatus() == com.dasigconnect.backend.model.entity.MediaIntegrityReviewStatus.OPEN;
            case "unorganized" -> asset.getFolderId() == null;
            case "uncurated" -> asset.getStatus() == com.dasigconnect.backend.model.entity.MediaAssetStatus.READY
                    && asset.getCuratedAt() == null;
            case "internal_only" -> "internal_only".equalsIgnoreCase(asset.getVisibility());
            case "duplicates" -> asset.getDuplicateOfId() != null;
            case "processing_failed" -> asset.getStatus() == com.dasigconnect.backend.model.entity.MediaAssetStatus.FAILED;
            default -> true;
        };
    }

    private boolean isValidator(JwtUserDetails user) {
        return user.role() != null && user.role().toLowerCase().contains("validator");
    }

    private boolean isContributor(JwtUserDetails user) {
        return user.role() != null && user.role().toLowerCase().contains("contributor");
    }

    private UUID resolveTargetInstitution(JwtUserDetails user, UUID requestedInstitutionId, String action) {
        if (isAdmin(user)) {
            if (requestedInstitutionId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Select an institution before you " + action + ".");
            }
            return requestedInstitutionId;
        }
        if (user.institutionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Institution-scoped user required to " + action + ".");
        }
        if (requestedInstitutionId != null && !requestedInstitutionId.equals(user.institutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You cannot " + action + " for another institution.");
        }
        return user.institutionId();
    }

    private void validateImportBatchScope(UUID importBatchId, UUID institutionId) {
        if (importBatchId == null) {
            return;
        }
        mediaImportBatchRepository.findByIdAndInstitution(importBatchId, institutionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Import batch does not belong to the selected institution."));
    }

    private List<AssetTagDto> tagsForAsset(UUID assetId) {
        return assetTagRepository
                .findByMediaAssetIdOrderByCreatedAtAsc(assetId)
                .stream()
                .map(AssetTagDto::from)
                .toList();
    }

    private void applyCurationEdits(List<MediaAsset> assets, MediaBatchCurationRequestDto dto) {
        if (dto == null || dto.getEdits() == null || dto.getEdits().isEmpty()) {
            return;
        }

        Map<UUID, MediaAsset> assetsById = new HashMap<>();
        for (MediaAsset asset : assets) {
            assetsById.put(asset.getId(), asset);
        }

        for (MediaAssetCurationEditDto edit : dto.getEdits()) {
            MediaAsset asset = assetsById.get(edit.getAssetId());
            if (asset == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Curation edit contains an asset outside this import batch.");
            }
            String title = trimToNull(edit.getTitle());
            if (title != null) {
                asset.setTitle(title);
            }
            if (edit.getTags() != null) {
                replaceTagsWithManualCuration(asset, edit.getTags());
            }
        }
    }

    private void replaceTagsWithManualCuration(MediaAsset asset, List<String> tags) {
        assetTagRepository.deleteByMediaAssetId(asset.getId());
        tags.stream()
                .map(this::trimToNull)
                .filter(tag -> tag != null)
                .distinct()
                .limit(20)
                .forEach(label -> {
                    AssetTag tag = new AssetTag();
                    tag.setMediaAsset(asset);
                    tag.setLabel(label);
                    tag.setSource("manual");
                    assetTagRepository.save(tag);
                });
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        return trimmed.isBlank() ? null : trimmed;
    }

    private String titleFromFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "Untitled media";
        }
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String cleaned = base.replace('_', ' ').replace('-', ' ').replaceAll("\\s+", " ").trim();
        return cleaned.isBlank() ? fileName : cleaned;
    }

    private void afterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
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
