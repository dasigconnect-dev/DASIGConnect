package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.media.AlbumAddAssetsRequestDto;
import com.dasigconnect.backend.model.dto.media.AlbumCreateRequestDto;
import com.dasigconnect.backend.model.dto.media.AlbumDetailDto;
import com.dasigconnect.backend.model.dto.media.AlbumResponseDto;
import com.dasigconnect.backend.model.dto.media.AlbumSetCoverRequestDto;
import com.dasigconnect.backend.model.dto.media.AlbumUpdateRequestDto;
import com.dasigconnect.backend.model.entity.AlbumAsset;
import com.dasigconnect.backend.model.entity.MediaAlbum;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.AlbumAssetRepository;
import com.dasigconnect.backend.repository.InstitutionRepository;
import com.dasigconnect.backend.repository.MediaAlbumRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
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
 * UC-4.1b: many-to-many album (curated collection) management — Google Photos model
 * (ADR-0004). An asset can belong to several albums without moving from its folder.
 * Albums created here are {@code source = manual}; Phase 2 AI auto-grouping writes
 * {@code ai_suggested} albums into the same structure. Every state change is audited.
 */
@Service
public class MediaAlbumService {

    private final MediaAlbumRepository albumRepository;
    private final AlbumAssetRepository albumAssetRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final UserRepository userRepository;
    private final InstitutionRepository institutionRepository;
    private final AuditLogService auditLogService;

    public MediaAlbumService(MediaAlbumRepository albumRepository,
                             AlbumAssetRepository albumAssetRepository,
                             MediaAssetRepository mediaAssetRepository,
                             UserRepository userRepository,
                             InstitutionRepository institutionRepository,
                             AuditLogService auditLogService) {
        this.albumRepository = albumRepository;
        this.albumAssetRepository = albumAssetRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.userRepository = userRepository;
        this.institutionRepository = institutionRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public AlbumResponseDto create(AlbumCreateRequestDto dto, JwtUserDetails user) {
        UUID institutionId = requireInstitution(user);
        User actor = loadActor(user);

        MediaAlbum album = new MediaAlbum();
        album.setInstitution(institutionRepository.getReferenceById(institutionId));
        album.setName(dto.getName().trim());
        album.setDescription(trimToNull(dto.getDescription()));
        album.setSource(MediaAlbum.SOURCE_MANUAL);
        album.setCreatedBy(actor);
        album = albumRepository.save(album);

        audit(actor, "ALBUM_CREATED", album.getId(), Map.of("name", album.getName()));
        return AlbumResponseDto.from(album, 0);
    }

    @Transactional
    public AlbumResponseDto update(UUID id, AlbumUpdateRequestDto dto, JwtUserDetails user) {
        UUID institutionId = requireInstitution(user);
        User actor = loadActor(user);
        MediaAlbum album = requireAlbum(id, institutionId);
        album.setName(dto.getName().trim());
        album.setDescription(trimToNull(dto.getDescription()));
        album = albumRepository.save(album);
        audit(actor, "ALBUM_UPDATED", album.getId(), Map.of("name", album.getName()));
        return AlbumResponseDto.from(album, albumAssetRepository.countByAlbumId(album.getId()));
    }

    @Transactional
    public void delete(UUID id, JwtUserDetails user) {
        UUID institutionId = requireInstitution(user);
        User actor = loadActor(user);
        MediaAlbum album = requireAlbum(id, institutionId);
        String name = album.getName();
        // album_assets rows are removed by the FK ON DELETE CASCADE; the assets themselves are untouched.
        albumRepository.delete(album);
        audit(actor, "ALBUM_DELETED", id, Map.of("name", name));
    }

    @Transactional
    public AlbumDetailDto addAssets(UUID id, AlbumAddAssetsRequestDto dto, JwtUserDetails user) {
        UUID institutionId = requireInstitution(user);
        User actor = loadActor(user);
        MediaAlbum album = requireAlbum(id, institutionId);

        long order = albumAssetRepository.countByAlbumId(id);
        int added = 0;
        for (UUID assetId : dto.getAssetIds()) {
            if (albumAssetRepository.existsByAlbumIdAndAssetId(id, assetId)) {
                continue; // idempotent: already in the album
            }
            MediaAsset asset = requireInstitutionAsset(assetId, institutionId);
            AlbumAsset link = new AlbumAsset();
            link.setAlbum(album);
            link.setAsset(asset);
            link.setDisplayOrder((int) (order + added));
            albumAssetRepository.save(link);
            added++;
        }
        audit(actor, "ALBUM_ASSETS_ADDED", id, Map.of("added", String.valueOf(added)));
        return detail(album);
    }

    @Transactional
    public void removeAsset(UUID id, UUID assetId, JwtUserDetails user) {
        UUID institutionId = requireInstitution(user);
        User actor = loadActor(user);
        requireAlbum(id, institutionId); // scope + existence check
        albumAssetRepository.deleteByAlbumIdAndAssetId(id, assetId);
        audit(actor, "ALBUM_ASSET_REMOVED", id, Map.of("assetId", assetId.toString()));
    }

    @Transactional
    public AlbumResponseDto setCover(UUID id, AlbumSetCoverRequestDto dto, JwtUserDetails user) {
        UUID institutionId = requireInstitution(user);
        User actor = loadActor(user);
        MediaAlbum album = requireAlbum(id, institutionId);
        if (dto.getCoverAssetId() == null) {
            album.setCoverAsset(null);
        } else {
            album.setCoverAsset(requireInstitutionAsset(dto.getCoverAssetId(), institutionId));
        }
        album = albumRepository.save(album);
        audit(actor, "ALBUM_COVER_SET", id, Map.of("coverAssetId", String.valueOf(dto.getCoverAssetId())));
        return AlbumResponseDto.from(album, albumAssetRepository.countByAlbumId(id));
    }

    @Transactional(readOnly = true)
    public List<AlbumResponseDto> list(JwtUserDetails user) {
        UUID institutionId = requireInstitution(user);
        return albumRepository.findByInstitution(institutionId).stream()
                .map(a -> AlbumResponseDto.from(a, albumAssetRepository.countByAlbumId(a.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public AlbumDetailDto get(UUID id, JwtUserDetails user) {
        UUID institutionId = requireInstitution(user);
        return detail(requireAlbum(id, institutionId));
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private AlbumDetailDto detail(MediaAlbum album) {
        List<MediaAsset> assets = albumAssetRepository.findByAlbumOrdered(album.getId()).stream()
                .map(AlbumAsset::getAsset)
                .toList();
        return AlbumDetailDto.from(album, assets);
    }

    private UUID requireInstitution(JwtUserDetails user) {
        if (user.institutionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Album operations require an institution-scoped user.");
        }
        return user.institutionId();
    }

    private User loadActor(JwtUserDetails user) {
        return userRepository.findById(user.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Authenticated user not found."));
    }

    private MediaAlbum requireAlbum(UUID id, UUID institutionId) {
        return albumRepository.findByIdAndInstitution(id, institutionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Album not found."));
    }

    private MediaAsset requireInstitutionAsset(UUID assetId, UUID institutionId) {
        MediaAsset asset = mediaAssetRepository.findActiveById(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found."));
        if (!asset.getInstitution().getId().equals(institutionId)) {
            // Don't leak cross-tenant existence — treat as not found.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found.");
        }
        return asset;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void audit(User actor, String action, UUID resourceId, Map<String, ?> metadata) {
        auditLogService.record(actor, action, null, null, resourceId, metadata);
    }
}
