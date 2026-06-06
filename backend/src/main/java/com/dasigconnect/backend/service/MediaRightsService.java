package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.media.MediaAssetRightsDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetRightsUpdateRequestDto;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetRights;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.MediaAssetRightsRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * UC-4.12 Phase 7D: structured rights/consent records. Human governance is authoritative here —
 * the service only records and validates what a user asserts; it never auto-determines copyright
 * or consent. Tenant-scoped: administrators are unrestricted, everyone else is confined to their
 * own institution's assets.
 */
@Service
public class MediaRightsService {

    private static final Logger log = LoggerFactory.getLogger(MediaRightsService.class);
    private static final Set<String> ALLOWED_BASES =
            Set.of("owned", "consent", "licensed", "public_domain", "unknown");

    private final MediaAssetRepository mediaAssetRepository;
    private final MediaAssetRightsRepository rightsRepository;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;

    public MediaRightsService(MediaAssetRepository mediaAssetRepository,
                              MediaAssetRightsRepository rightsRepository,
                              AuditLogService auditLogService,
                              UserRepository userRepository) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.rightsRepository = rightsRepository;
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public MediaAssetRightsDto getRights(UUID assetId, JwtUserDetails user) {
        MediaAsset asset = loadAsset(assetId, user);
        return rightsRepository
                .findByAssetIdAndInstitutionId(assetId, asset.getInstitution().getId())
                .map(r -> MediaAssetRightsDto.from(r, LocalDate.now()))
                .orElseGet(() -> MediaAssetRightsDto.empty(assetId));
    }

    @Transactional
    public MediaAssetRightsDto updateRights(UUID assetId,
                                            MediaAssetRightsUpdateRequestDto dto,
                                            JwtUserDetails user) {
        MediaAsset asset = loadAsset(assetId, user);
        UUID institutionId = asset.getInstitution().getId();

        String basis = dto.getRightsBasis() == null
                ? "unknown" : dto.getRightsBasis().trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_BASES.contains(basis)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "rightsBasis must be one of owned, consent, licensed, public_domain, unknown.");
        }
        if (dto.getClearanceDate() != null && dto.getExpiresAt() != null
                && dto.getExpiresAt().isBefore(dto.getClearanceDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "expiresAt cannot be before clearanceDate.");
        }

        MediaAssetRights rights = rightsRepository
                .findByAssetIdAndInstitutionId(assetId, institutionId)
                .orElseGet(() -> {
                    MediaAssetRights fresh = new MediaAssetRights();
                    fresh.setAssetId(assetId);
                    fresh.setInstitutionId(institutionId);
                    return fresh;
                });

        rights.setRightsHolder(trimToNull(dto.getRightsHolder()));
        rights.setRightsBasis(basis);
        rights.setLicenseUri(trimToNull(dto.getLicenseUri()));
        rights.setConsentReference(trimToNull(dto.getConsentReference()));
        rights.setClearanceDate(dto.getClearanceDate());
        rights.setExpiresAt(dto.getExpiresAt());
        rights.setPermittedChannels(toArray(dto.getPermittedChannels()));
        rights.setRestrictions(trimToNull(dto.getRestrictions()));
        rights.setSupportingDocumentUrl(trimToNull(dto.getSupportingDocumentUrl()));
        rights.setVerifiedBy(user.userId());
        rights.setVerifiedAt(Instant.now());

        MediaAssetRights saved = rightsRepository.save(rights);
        audit(assetId, user, saved);
        return MediaAssetRightsDto.from(saved, LocalDate.now());
    }

    private void audit(UUID assetId, JwtUserDetails user, MediaAssetRights rights) {
        try {
            User actor = userRepository.findById(user.userId()).orElse(null);
            Map<String, Object> metadata = Map.of(
                    "rightsBasis", rights.getRightsBasis(),
                    "derivedState", rights.derivedState(LocalDate.now()),
                    "expiresAt", String.valueOf(rights.getExpiresAt()));
            auditLogService.record(actor, "MEDIA_ASSET_RIGHTS_UPDATED", null, null, assetId, metadata);
        } catch (Exception ex) {
            log.warn("Failed to audit rights update for asset {}: {}", assetId, ex.getMessage());
        }
    }

    private MediaAsset loadAsset(UUID assetId, JwtUserDetails user) {
        MediaAsset asset = mediaAssetRepository.findActiveById(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media asset not found."));
        boolean admin = user.role() != null && user.role().toLowerCase(Locale.ROOT).contains("admin");
        if (!admin && !asset.getInstitution().getId().equals(user.institutionId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media asset not found.");
        }
        return asset;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String[] toArray(List<String> values) {
        if (values == null || values.isEmpty()) return null;
        return values.stream()
                .map(MediaRightsService::trimToNull)
                .filter(v -> v != null)
                .toArray(String[]::new);
    }
}
