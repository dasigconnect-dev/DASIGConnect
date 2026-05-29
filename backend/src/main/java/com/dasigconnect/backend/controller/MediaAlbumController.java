package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.model.dto.media.AlbumAddAssetsRequestDto;
import com.dasigconnect.backend.model.dto.media.AlbumCreateRequestDto;
import com.dasigconnect.backend.model.dto.media.AlbumDetailDto;
import com.dasigconnect.backend.model.dto.media.AlbumResponseDto;
import com.dasigconnect.backend.model.dto.media.AlbumSetCoverRequestDto;
import com.dasigconnect.backend.model.dto.media.AlbumUpdateRequestDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.MediaAlbumService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for UC-4.1b album management. Base path: /api/v1/media-albums.
 * Institution scope is enforced in the service layer (+ RLS at the DB level).
 */
@RestController
@RequestMapping("/api/v1/media-albums")
public class MediaAlbumController {

    private final MediaAlbumService mediaAlbumService;

    public MediaAlbumController(MediaAlbumService mediaAlbumService) {
        this.mediaAlbumService = mediaAlbumService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AlbumResponseDto>> list(@AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaAlbumService.list(user));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AlbumDetailDto> get(@PathVariable UUID id,
                                              @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaAlbumService.get(id, user));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AlbumResponseDto> create(@Valid @RequestBody AlbumCreateRequestDto dto,
                                                   @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.status(201).body(mediaAlbumService.create(dto, user));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AlbumResponseDto> update(@PathVariable UUID id,
                                                   @Valid @RequestBody AlbumUpdateRequestDto dto,
                                                   @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaAlbumService.update(id, dto, user));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                       @AuthenticationPrincipal JwtUserDetails user) {
        mediaAlbumService.delete(id, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/assets")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AlbumDetailDto> addAssets(@PathVariable UUID id,
                                                    @Valid @RequestBody AlbumAddAssetsRequestDto dto,
                                                    @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaAlbumService.addAssets(id, dto, user));
    }

    @DeleteMapping("/{id}/assets/{assetId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> removeAsset(@PathVariable UUID id,
                                            @PathVariable UUID assetId,
                                            @AuthenticationPrincipal JwtUserDetails user) {
        mediaAlbumService.removeAsset(id, assetId, user);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/cover")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AlbumResponseDto> setCover(@PathVariable UUID id,
                                                     @Valid @RequestBody AlbumSetCoverRequestDto dto,
                                                     @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaAlbumService.setCover(id, dto, user));
    }
}
