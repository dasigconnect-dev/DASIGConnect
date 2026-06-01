package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.media.FolderCreateRequestDto;
import com.dasigconnect.backend.model.dto.media.FolderMoveRequestDto;
import com.dasigconnect.backend.model.dto.media.FolderRenameRequestDto;
import com.dasigconnect.backend.model.dto.media.FolderResponseDto;
import com.dasigconnect.backend.model.entity.MediaFolder;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.InstitutionRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.MediaFolderRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * UC-4.1: single-parent folder management (Google Drive model). Folders are
 * institution-scoped; an asset lives in one folder via {@code media_assets.folder_id}.
 * Curated multi-membership is {@link MediaAlbumService}, not folders (ADR-0004). Every
 * state change writes an audit record (UC-4.11 habit).
 */
@Service
public class MediaFolderService {

    /** Max nesting depth (soft cap, tunable — see Phase 1 plan §7). */
    static final int MAX_DEPTH = 8;
    private static final int WALK_GUARD = 64;

    private final MediaFolderRepository folderRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final UserRepository userRepository;
    private final InstitutionRepository institutionRepository;
    private final AuditLogService auditLogService;

    public MediaFolderService(MediaFolderRepository folderRepository,
                              MediaAssetRepository mediaAssetRepository,
                              UserRepository userRepository,
                              InstitutionRepository institutionRepository,
                              AuditLogService auditLogService) {
        this.folderRepository = folderRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.userRepository = userRepository;
        this.institutionRepository = institutionRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public FolderResponseDto create(FolderCreateRequestDto dto, JwtUserDetails user) {
        UUID institutionId = resolveTargetInstitution(user, dto.getInstitutionId());
        User actor = loadActor(user);

        MediaFolder parent = null;
        if (dto.getParentFolderId() != null) {
            parent = requireFolder(dto.getParentFolderId(), institutionId);
            if (depthOf(parent) + 1 > MAX_DEPTH) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Folder nesting too deep (max " + MAX_DEPTH + " levels).");
            }
        }

        MediaFolder folder = new MediaFolder();
        folder.setInstitution(institutionRepository.getReferenceById(institutionId));
        folder.setParentFolder(parent);
        folder.setName(dto.getName().trim());
        folder.setCreatedBy(actor);
        folder = folderRepository.save(folder);

        audit(actor, "FOLDER_CREATED", folder,
                Map.of("name", folder.getName(),
                       "parentFolderId", String.valueOf(dto.getParentFolderId())));
        return toDto(folder);
    }

    @Transactional
    public FolderResponseDto rename(UUID id, FolderRenameRequestDto dto, JwtUserDetails user) {
        User actor = loadActor(user);
        MediaFolder folder = requireFolder(id, user);
        folder.setName(dto.getName().trim());
        folder = folderRepository.save(folder);
        audit(actor, "FOLDER_RENAMED", folder, Map.of("name", folder.getName()));
        return toDto(folder);
    }

    @Transactional
    public FolderResponseDto move(UUID id, FolderMoveRequestDto dto, JwtUserDetails user) {
        User actor = loadActor(user);
        MediaFolder folder = requireFolder(id, user);
        UUID institutionId = operationInstitution(folder, user);

        MediaFolder newParent = null;
        if (dto.getParentFolderId() != null) {
            if (dto.getParentFolderId().equals(id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A folder cannot be its own parent.");
            }
            newParent = requireFolder(dto.getParentFolderId(), institutionId);
            // Reject a cycle: the new parent must not be the folder itself or one of its descendants.
            MediaFolder cursor = newParent;
            int guard = 0;
            while (cursor != null && guard++ < WALK_GUARD) {
                if (cursor.getId().equals(id)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Cannot move a folder into its own descendant.");
                }
                cursor = cursor.getParentFolder();
            }
            if (depthOf(newParent) + 1 > MAX_DEPTH) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Folder nesting too deep (max " + MAX_DEPTH + " levels).");
            }
        }

        folder.setParentFolder(newParent);
        folder = folderRepository.save(folder);
        audit(actor, "FOLDER_MOVED", folder,
                Map.of("parentFolderId", String.valueOf(dto.getParentFolderId())));
        return toDto(folder);
    }

    /**
     * Deletes a folder. The DB foreign keys handle the rest: subfolders cascade-delete
     * (parent_folder_id ON DELETE CASCADE) and contained assets are unfiled
     * (media_assets.folder_id ON DELETE SET NULL) — assets are never deleted.
     */
    @Transactional
    public void delete(UUID id, JwtUserDetails user) {
        User actor = loadActor(user);
        MediaFolder folder = requireFolder(id, user);
        String name = folder.getName();
        folderRepository.delete(folder);
        audit(actor, "FOLDER_DELETED", folder, Map.of("name", name));
    }

    @Transactional(readOnly = true)
    public List<FolderResponseDto> list(UUID requestedInstitutionId, JwtUserDetails user) {
        UUID institutionId = resolveTargetInstitution(user, requestedInstitutionId);
        return folderRepository.findByInstitution(institutionId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public FolderResponseDto get(UUID id, JwtUserDetails user) {
        return toDto(requireFolder(id, user));
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private UUID resolveTargetInstitution(JwtUserDetails user, UUID requestedInstitutionId) {
        if (isAdmin(user)) {
            if (requestedInstitutionId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Select an institution before managing folders.");
            }
            return requestedInstitutionId;
        }
        if (user.institutionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Folder operations require an institution-scoped user.");
        }
        if (requestedInstitutionId != null && !requestedInstitutionId.equals(user.institutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You cannot manage folders for another institution.");
        }
        return user.institutionId();
    }

    private boolean isAdmin(JwtUserDetails user) {
        return user.role() != null && user.role().toLowerCase().contains("admin");
    }

    private User loadActor(JwtUserDetails user) {
        return userRepository.findById(user.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Authenticated user not found."));
    }

    private MediaFolder requireFolder(UUID id, UUID institutionId) {
        return folderRepository.findByIdAndInstitution(id, institutionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found."));
    }

    private MediaFolder requireFolder(UUID id, JwtUserDetails user) {
        if (isAdmin(user)) {
            return folderRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found."));
        }
        return requireFolder(id, resolveTargetInstitution(user, null));
    }

    private UUID operationInstitution(MediaFolder folder, JwtUserDetails user) {
        if (!isAdmin(user)) {
            return resolveTargetInstitution(user, null);
        }
        if (folder.getInstitution() == null || folder.getInstitution().getId() == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Folder has no institution.");
        }
        return folder.getInstitution().getId();
    }

    private int depthOf(MediaFolder folder) {
        int depth = 1;
        MediaFolder cursor = folder.getParentFolder();
        int guard = 0;
        while (cursor != null && guard++ < WALK_GUARD) {
            depth++;
            cursor = cursor.getParentFolder();
        }
        return depth;
    }

    private FolderResponseDto toDto(MediaFolder folder) {
        long assetCount = mediaAssetRepository.countByFolderIdAndDeletedAtIsNull(folder.getId());
        long subfolderCount = folderRepository.countByParentFolderId(folder.getId());
        return FolderResponseDto.from(folder, assetCount, subfolderCount);
    }

    private void audit(User actor, String action, MediaFolder folder, Map<String, ?> metadata) {
        auditLogService.record(actor, action, null, null, folder.getId(), metadata);
    }
}
