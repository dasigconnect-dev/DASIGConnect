package com.dasigconnect.backend.controller;

import java.util.UUID;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dasigconnect.backend.model.dto.common.ApiResponse;
import com.dasigconnect.backend.model.dto.media.AddAssetTagRequestDto;
import com.dasigconnect.backend.model.dto.media.AssetTagDto;
import com.dasigconnect.backend.model.dto.media.MediaAlbumDto;
import com.dasigconnect.backend.model.dto.media.MediaAlbumMoveRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAlbumPathRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAlbumRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetAlbumRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetAddToDraftRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetBulkDeleteRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetBulkDeleteResponseDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetDetailDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetListResponseDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetUploadRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetUploadUrlRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetUploadUrlResponseDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetUseInNewPostRequestDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionResponseDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.MediaAssetService;

import jakarta.validation.Valid;

/**
 * REST endpoints for UC-2.2: Media library browsing. Base path:
 * /api/v1/media-assets
 */
@RestController
@RequestMapping("/api/v1/media-assets")
public class MediaAssetController {

    private final MediaAssetService mediaAssetService;

    public MediaAssetController(MediaAssetService mediaAssetService) {
        this.mediaAssetService = mediaAssetService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MediaAssetListResponseDto>> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String aiCategory,
            @RequestParam(required = false) String mediaType,
            @RequestParam(required = false) UUID uploaderId,
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) UUID albumId,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "25") int pageSize,
            @RequestParam(required = false) String scope,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(
                mediaAssetService.list(query, aiCategory, mediaType, uploaderId, institutionId, albumId, sort, page, pageSize, scope, user)));
    }

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MediaAssetListResponseDto>> semanticSearch(
            @RequestParam String query,
            @RequestParam(required = false) UUID institutionId,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(
                mediaAssetService.semanticSearch(query, institutionId, user)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MediaAssetDetailDto>> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(mediaAssetService.get(id, user)));
    }

    @PostMapping("/{id}/use-in-new-post")
    @PreAuthorize("hasRole('CONTRIBUTOR')")
    public ResponseEntity<ApiResponse<SubmissionResponseDto>> useInNewPost(
            @PathVariable UUID id,
            @Valid @RequestBody MediaAssetUseInNewPostRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(mediaAssetService.useInNewPost(id, dto, user)));
    }

    @PostMapping("/{id}/add-to-draft")
    @PreAuthorize("hasRole('CONTRIBUTOR')")
    public ResponseEntity<ApiResponse<SubmissionResponseDto>> addToDraft(
            @PathVariable UUID id,
            @Valid @RequestBody MediaAssetAddToDraftRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(mediaAssetService.addToDraft(id, dto, user)));
    }

    @PostMapping("/upload-url")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MediaAssetUploadUrlResponseDto>> getUploadUrl(
            @Valid @RequestBody MediaAssetUploadUrlRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(mediaAssetService.createUploadUrl(dto, user)));
    }

    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MediaAssetDetailDto>> upload(
            @Valid @RequestBody MediaAssetUploadRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.status(201).body(ApiResponse.success(mediaAssetService.upload(dto, user)));
    }

    @GetMapping("/albums")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<MediaAlbumDto>>> listAlbums(
            @RequestParam(required = false) UUID institutionId,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(mediaAssetService.listAlbums(institutionId, user)));
    }

    @PostMapping("/albums")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MediaAlbumDto>> createAlbum(
            @Valid @RequestBody MediaAlbumRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.status(201).body(ApiResponse.success(mediaAssetService.createAlbum(dto, user)));
    }

    @PostMapping("/albums/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MediaAlbumDto>> renameAlbum(
            @PathVariable UUID id,
            @Valid @RequestBody MediaAlbumRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(mediaAssetService.renameAlbum(id, dto, user)));
    }

    @PostMapping("/albums/ensure-path")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MediaAlbumDto>> ensureAlbumPath(
            @Valid @RequestBody MediaAlbumPathRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.status(201).body(ApiResponse.success(
                mediaAssetService.ensureAlbumPath(dto.getInstitutionId(), dto.getSegments(), user)));
    }

    @PatchMapping("/albums/{id}/parent")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MediaAlbumDto>> moveAlbum(
            @PathVariable UUID id,
            @RequestBody MediaAlbumMoveRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(
                mediaAssetService.moveAlbum(id, dto.getParentAlbumId(), dto.getInstitutionId(), user)));
    }

    @DeleteMapping("/albums/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteAlbum(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails user) {
        mediaAssetService.deleteAlbum(id, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/album")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MediaAssetDetailDto>> updateAlbum(
            @PathVariable UUID id,
            @Valid @RequestBody MediaAssetAlbumRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(mediaAssetService.updateAlbum(id, dto, user)));
    }

    @PostMapping("/{id}/tags")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AssetTagDto>> addTag(
            @PathVariable UUID id,
            @Valid @RequestBody AddAssetTagRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.status(201).body(ApiResponse.success(mediaAssetService.addTag(id, dto, user)));
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
    public ResponseEntity<ApiResponse<MediaAssetBulkDeleteResponseDto>> bulkDelete(
            @Valid @RequestBody MediaAssetBulkDeleteRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(mediaAssetService.bulkDelete(dto, user)));
    }
}
