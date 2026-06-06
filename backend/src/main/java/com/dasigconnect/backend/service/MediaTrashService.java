package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.media.MediaAssetSummaryDto;
import com.dasigconnect.backend.model.dto.media.TrashItemDto;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetStatus;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * UC-2.2 Trash: list, restore, and on-demand "Delete forever" for soft-deleted media. Scope
 * mirrors the Media Library — contributors see their own trashed uploads, validators their
 * institution, administrators the whole network (or one institution). Anyone who can see a
 * trashed item may restore it or purge it immediately. Items not purged here are removed
 * automatically by the retention job after the retention window.
 */
@Service
public class MediaTrashService {

    private static final Logger log = LoggerFactory.getLogger(MediaTrashService.class);

    private final MediaAssetRepository mediaAssetRepository;
    private final MediaAssetRetentionService retentionService;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;

    public MediaTrashService(MediaAssetRepository mediaAssetRepository,
                             MediaAssetRetentionService retentionService,
                             AuditLogService auditLogService,
                             UserRepository userRepository) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.retentionService = retentionService;
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<TrashItemDto> listTrash(UUID requestedInstitutionId, JwtUserDetails user) {
        List<MediaAsset> assets;
        if (isContributor(user)) {
            assets = mediaAssetRepository.findTrashByUploader(user.userId());
        } else if (isAdmin(user)) {
            assets = mediaAssetRepository.findTrash(requestedInstitutionId); // null = all institutions
        } else {
            // Validator (and any other institution-scoped role): own institution only.
            if (user.institutionId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Institution-scoped user required to view the trash.");
            }
            if (requestedInstitutionId != null && !requestedInstitutionId.equals(user.institutionId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "You cannot view another institution's trash.");
            }
            assets = mediaAssetRepository.findTrash(user.institutionId());
        }
        int retentionDays = retentionService.getRetentionDays();
        Instant now = Instant.now();
        return assets.stream().map(a -> TrashItemDto.from(a, retentionDays, now)).toList();
    }

    @Transactional
    public MediaAssetSummaryDto restore(UUID assetId, JwtUserDetails user) {
        MediaAsset asset = loadTrashedAsset(assetId, user);
        asset.setDeletedAt(null);
        asset.setDeletedByUserId(null);
        asset.setStatus(MediaAssetStatus.READY);
        mediaAssetRepository.save(asset);
        audit("MEDIA_ASSET_RESTORED", assetId, user, Map.of(
                "assetCode", String.valueOf(asset.getAssetCode())));
        return MediaAssetSummaryDto.from(asset);
    }

    /**
     * On-demand "Delete forever": permanently purges the asset's content/derived data now instead
     * of waiting for the retention window. Storage delete happens outside any DB transaction.
     */
    public void purgeNow(UUID assetId, JwtUserDetails user) {
        MediaAsset asset = loadTrashedAsset(assetId, user);
        String assetCode = asset.getAssetCode();
        boolean storageDeleted = retentionService.purgeAssetData(asset);
        audit("MEDIA_ASSET_PURGED", assetId, user, Map.of(
                "assetCode", String.valueOf(assetCode),
                "onDemand", "true",
                "storageDeleted", String.valueOf(storageDeleted)));
    }

    private MediaAsset loadTrashedAsset(UUID assetId, JwtUserDetails user) {
        MediaAsset asset = mediaAssetRepository.findTrashedById(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trashed asset not found."));
        if (isAdmin(user)) {
            return asset;
        }
        if (isContributor(user)) {
            boolean owner = asset.getUploader() != null && asset.getUploader().getId().equals(user.userId());
            if (owner && asset.getInstitution().getId().equals(user.institutionId())) {
                return asset;
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Contributors can only manage their own trashed uploads.");
        }
        if (asset.getInstitution().getId().equals(user.institutionId())) {
            return asset;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trashed asset not found.");
    }

    private void audit(String action, UUID assetId, JwtUserDetails user, Map<String, ?> metadata) {
        try {
            User actor = userRepository.findById(user.userId()).orElse(null);
            auditLogService.record(actor, action, null, null, assetId, metadata);
        } catch (Exception ex) {
            log.warn("Failed to write {} audit for {}: {}", action, assetId, ex.getMessage());
        }
    }

    private boolean isAdmin(JwtUserDetails user) {
        return user.role() != null && user.role().toLowerCase(Locale.ROOT).contains("admin");
    }

    private boolean isContributor(JwtUserDetails user) {
        return user.role() != null && user.role().toLowerCase(Locale.ROOT).contains("contributor");
    }
}
