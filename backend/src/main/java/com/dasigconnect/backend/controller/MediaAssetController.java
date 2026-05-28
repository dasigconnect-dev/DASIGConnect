package com.dasigconnect.backend.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaAssetService.list(query, aiCategory, mediaType, uploaderId, institutionId, sort, page, pageSize, scope, user));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MediaAssetDetailDto> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaAssetService.get(id, user));
    }

    @PostMapping("/{id}/use-in-new-post")
    @PreAuthorize("hasRole('CONTRIBUTOR')")
    public ResponseEntity<SubmissionResponseDto> useInNewPost(
            @PathVariable UUID id,
            @Valid @RequestBody MediaAssetUseInNewPostRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaAssetService.useInNewPost(id, dto, user));
    }

    @PostMapping("/{id}/add-to-draft")
    @PreAuthorize("hasRole('CONTRIBUTOR')")
    public ResponseEntity<SubmissionResponseDto> addToDraft(
            @PathVariable UUID id,
            @Valid @RequestBody MediaAssetAddToDraftRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaAssetService.addToDraft(id, dto, user));
    }

    @PostMapping("/upload-url")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MediaAssetUploadUrlResponseDto> getUploadUrl(
            @Valid @RequestBody MediaAssetUploadUrlRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaAssetService.createUploadUrl(dto, user));
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
