package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.model.dto.media.FolderCreateRequestDto;
import com.dasigconnect.backend.model.dto.media.FolderMoveRequestDto;
import com.dasigconnect.backend.model.dto.media.FolderRenameRequestDto;
import com.dasigconnect.backend.model.dto.media.FolderResponseDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.MediaFolderService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for UC-4.1 folder management. Base path: /api/v1/media-folders.
 * Institution scope is enforced in the service layer (+ RLS at the DB level).
 */
@RestController
@RequestMapping("/api/v1/media-folders")
public class MediaFolderController {

    private final MediaFolderService mediaFolderService;

    public MediaFolderController(MediaFolderService mediaFolderService) {
        this.mediaFolderService = mediaFolderService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<FolderResponseDto>> list(@RequestParam(required = false) UUID institutionId,
                                                        @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaFolderService.list(institutionId, user));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FolderResponseDto> get(@PathVariable UUID id,
                                                 @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaFolderService.get(id, user));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FolderResponseDto> create(@Valid @RequestBody FolderCreateRequestDto dto,
                                                    @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.status(201).body(mediaFolderService.create(dto, user));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FolderResponseDto> rename(@PathVariable UUID id,
                                                    @Valid @RequestBody FolderRenameRequestDto dto,
                                                    @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaFolderService.rename(id, dto, user));
    }

    @PatchMapping("/{id}/move")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FolderResponseDto> move(@PathVariable UUID id,
                                                  @Valid @RequestBody FolderMoveRequestDto dto,
                                                  @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(mediaFolderService.move(id, dto, user));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                       @AuthenticationPrincipal JwtUserDetails user) {
        mediaFolderService.delete(id, user);
        return ResponseEntity.noContent().build();
    }
}
