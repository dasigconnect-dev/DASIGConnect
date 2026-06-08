package com.dasigconnect.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dasigconnect.backend.model.dto.media.AddAssetTagRequestDto;
import com.dasigconnect.backend.model.dto.media.AssetTagDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetAddToDraftRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetBulkDeleteRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetBulkDeleteResponseDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetDetailDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetListResponseDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetSearchRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetVisibilityRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAuditEntryDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetSearchResponseDto;
import com.dasigconnect.backend.model.dto.media.MediaSearchSuggestionsResponseDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetUploadRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetUploadUrlRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetUploadUrlResponseDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetUseInNewPostRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaBatchCurationRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaBatchCurationResponseDto;
import com.dasigconnect.backend.model.dto.media.MediaImportBatchCreateRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaImportBatchResponseDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetLineageDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetRelationCreateRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetRightsDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetRightsUpdateRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaIntegrityCheckDto;
import com.dasigconnect.backend.model.dto.media.MediaIntegrityResultDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionResponseDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.MediaIntegrityService;
import com.dasigconnect.backend.service.MediaAssetSearchService;
import com.dasigconnect.backend.service.MediaAssetService;
import com.dasigconnect.backend.service.MediaLineageService;
import com.dasigconnect.backend.service.MediaRightsService;

import jakarta.validation.Valid;

/**
 * REST endpoints for UC-2.2: Media library browsing. Base path:
 * /api/v1/media-assets
 */
@RestController
@RequestMapping("/api/v1/media-assets")
public class MediaAssetController {

    private final MediaAssetService mediaAssetService;
    private final MediaAssetSearchService mediaAssetSearchService;
    private final MediaIntegrityService mediaIntegrityService;
    private final MediaRightsService mediaRightsService;
    private final MediaLineageService mediaLineageService;
    private final com.dasigconnect.backend.service.MediaTrashService mediaTrashService;

    public MediaAssetController(MediaAssetService mediaAssetService,
                                MediaAssetSearchService mediaAssetSearchService,
                                MediaIntegrityService mediaIntegrityService,
                                MediaRightsService mediaRightsService,
                                MediaLineageService mediaLineageService,
                                com.dasigconnect.backend.service.MediaTrashService mediaTrashService) {
        this.mediaAssetService = mediaAssetService;
        this.mediaAssetSearchService = mediaAssetSearchService;
        this.mediaIntegrityService = mediaIntegrityService;
        this.mediaRightsService = mediaRightsService;
        this.mediaLineageService = mediaLineageService;
        this.mediaTrashService = mediaTrashService;
    }

    /** UC-2.2 Trash: soft-deleted (not yet purged) assets, scoped by role. */
    @GetMapping("/trash")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<java.util.List<com.dasigconnect.backend.model.dto.media.TrashItemDto>> trash(
            @RequestParam(required = false) UUID institutionId,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaTrashService.listTrash(institutionId, user));
    }

    /** UC-2.2 Trash: restore a soft-deleted asset (lossless within the retention window). */
    @PostMapping("/{id:[0-9a-fA-F\\-]{36}}/restore")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<com.dasigconnect.backend.model.dto.media.MediaAssetSummaryDto> restore(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaTrashService.restore(id, user));
    }

    /** UC-2.2 Trash: "Delete forever" — purge the asset's content/derived data immediately. */
    @PostMapping("/{id:[0-9a-fA-F\\-]{36}}/purge")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> purge(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails user) {
        mediaTrashService.purgeNow(id, user);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MediaAssetListResponseDto> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String aiCategory,
            @RequestParam(required = false) String mediaType,
            @RequestParam(required = false) UUID uploaderId,
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "25") int pageSize,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String health,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaAssetService.list(query, aiCategory, mediaType, uploaderId, institutionId, sort, page, pageSize, scope, health, user));
    }

    @PostMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MediaAssetSearchResponseDto> search(
            @Valid @RequestBody MediaAssetSearchRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaAssetSearchService.search(dto, user));
    }

    @GetMapping("/search/suggestions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MediaSearchSuggestionsResponseDto> suggest(
            @RequestParam("q") String q,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaAssetSearchService.suggest(q, scope, institutionId, limit, user));
    }

    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MediaAssetDetailDto> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaAssetService.get(id, user));
    }

    /** UC-4.11: the asset's provenance/audit trail (newest first). */
    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MediaAuditEntryDto>> history(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaAssetService.getHistory(id, user));
    }

    /** UC-4.12 Phase 7A: manually verify the stored object's SHA-256 fixity. */
    @PostMapping("/{id:[0-9a-fA-F\\-]{36}}/integrity-check")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MediaIntegrityResultDto> verifyIntegrity(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaIntegrityService.verifyManual(id, user));
    }

    /** UC-4.12 Phase 7A: append-only integrity verification history. */
    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}/integrity-history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MediaIntegrityCheckDto>> integrityHistory(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaIntegrityService.history(id, user));
    }

    /** UC-4.12 Phase 7B: acknowledge an active mismatch, missing object, or check error. */
    @PostMapping("/{id:[0-9a-fA-F\\-]{36}}/integrity-review/acknowledge")
    @PreAuthorize("hasAnyRole('VALIDATOR', 'ADMINISTRATOR')")
    public ResponseEntity<MediaIntegrityResultDto> acknowledgeIntegrityReview(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaIntegrityService.acknowledgeReview(id, user));
    }

    /** UC-4.x: change an asset's consent/visibility (internal_only | cleared_for_public). */
    @PatchMapping("/{id:[0-9a-fA-F\\-]{36}}/visibility")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MediaAssetDetailDto> changeVisibility(
            @PathVariable UUID id,
            @Valid @RequestBody MediaAssetVisibilityRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaAssetService.changeVisibility(id, dto, user));
    }

    /** UC-4.12 Phase 7D: the asset's current rights/consent record and derived governance state. */
    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}/rights")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MediaAssetRightsDto> getRights(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaRightsService.getRights(id, user));
    }

    /** UC-4.12 Phase 7D: upsert the asset's rights/consent record (audited). */
    @PutMapping("/{id:[0-9a-fA-F\\-]{36}}/rights")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MediaAssetRightsDto> updateRights(
            @PathVariable UUID id,
            @Valid @RequestBody MediaAssetRightsUpdateRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaRightsService.updateRights(id, dto, user));
    }

    /** UC-4.12 Phase 7E: the asset's lineage — what it derives from, and its versions/derivatives. */
    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}/relations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MediaAssetLineageDto> getRelations(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaLineageService.getLineage(id, user));
    }

    /** UC-4.12 Phase 7E: link an existing asset as a version/derivative of this one (audited). */
    @PostMapping("/{id:[0-9a-fA-F\\-]{36}}/relations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MediaAssetLineageDto> createRelation(
            @PathVariable UUID id,
            @Valid @RequestBody MediaAssetRelationCreateRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.status(201).body(mediaLineageService.createRelation(id, dto, user));
    }

    @PostMapping("/{id}/use-in-new-post")
    @PreAuthorize("hasAnyRole('CONTRIBUTOR', 'VALIDATOR')")
    public ResponseEntity<SubmissionResponseDto> useInNewPost(
            @PathVariable UUID id,
            @Valid @RequestBody MediaAssetUseInNewPostRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaAssetService.useInNewPost(id, dto, user));
    }

    @PostMapping("/{id}/add-to-draft")
    @PreAuthorize("hasAnyRole('CONTRIBUTOR', 'VALIDATOR')")
    public ResponseEntity<SubmissionResponseDto> addToDraft(
            @PathVariable UUID id,
            @Valid @RequestBody MediaAssetAddToDraftRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaAssetService.addToDraft(id, dto, user));
    }

    /** Upload-time exact-duplicate (SHA-256) check; returns existing assets for matching hashes. */
    @PostMapping("/check-duplicates")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<java.util.List<com.dasigconnect.backend.model.dto.media.MediaDuplicateMatchDto>> checkDuplicates(
            @Valid @RequestBody com.dasigconnect.backend.model.dto.media.MediaDuplicateCheckRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaAssetService.checkDuplicates(dto, user));
    }

    @PostMapping("/upload-url")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MediaAssetUploadUrlResponseDto> getUploadUrl(
            @Valid @RequestBody MediaAssetUploadUrlRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaAssetService.createUploadUrl(dto, user));
    }

    @PostMapping("/import-batches")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MediaImportBatchResponseDto> createImportBatch(
            @Valid @RequestBody MediaImportBatchCreateRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.status(201).body(mediaAssetService.createImportBatch(dto, user));
    }

    @GetMapping("/import-batches")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<java.util.List<MediaImportBatchResponseDto>> listImportBatches(
            @RequestParam(required = false) UUID institutionId,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaAssetService.listImportBatches(institutionId, user));
    }

    @GetMapping("/import-batches/{id}/assets")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<java.util.List<MediaAssetDetailDto>> listImportBatchAssets(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaAssetService.listImportBatchAssets(id, institutionId, user));
    }

    @PostMapping("/import-batches/{id}/curate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MediaBatchCurationResponseDto> markImportBatchCurated(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody(required = false) MediaBatchCurationRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaAssetService.markImportBatchCurated(id, institutionId, dto, user));
    }

    /** UC-4.2: delete a batch grouping (un-groups its assets; the media itself is kept). */
    @DeleteMapping("/import-batches/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteImportBatch(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId,
            @AuthenticationPrincipal JwtUserDetails user) {
        mediaAssetService.deleteImportBatch(id, institutionId, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MediaAssetDetailDto> upload(
            @Valid @RequestBody MediaAssetUploadRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.status(201).body(mediaAssetService.upload(dto, user));
    }

    @PostMapping("/{id}/tags")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AssetTagDto> addTag(
            @PathVariable UUID id,
            @Valid @RequestBody AddAssetTagRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.status(201).body(mediaAssetService.addTag(id, dto, user));
    }

    @DeleteMapping("/{id}/tags/{tagId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> removeTag(
            @PathVariable UUID id,
            @PathVariable UUID tagId,
            @AuthenticationPrincipal JwtUserDetails user) {
        mediaAssetService.removeTag(id, tagId, user);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean force,
            @AuthenticationPrincipal JwtUserDetails user) {
        mediaAssetService.delete(id, force, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk-delete")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MediaAssetBulkDeleteResponseDto> bulkDelete(
            @Valid @RequestBody MediaAssetBulkDeleteRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaAssetService.bulkDelete(dto, user));
    }
}
