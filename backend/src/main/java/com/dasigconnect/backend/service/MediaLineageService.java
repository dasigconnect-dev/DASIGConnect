package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.media.MediaAssetLineageDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetRelationCreateRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetSummaryDto;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetRelation;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.MediaAssetRelationRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * UC-4.12 Phase 7E: asset lineage (original → version/derivative). Relations are links between
 * independent assets — the service never overwrites a stored object in place. Tenant-scoped:
 * administrators are unrestricted, everyone else is confined to their own institution. Self-links,
 * cross-institution links, duplicates, and cycles are rejected.
 */
@Service
public class MediaLineageService {

    private static final Logger log = LoggerFactory.getLogger(MediaLineageService.class);
    private static final Set<String> ALLOWED_TYPES =
            Set.of("new_version", "derived_from", "replacement_for", "component_of");
    private static final int CYCLE_WALK_GUARD = 10_000;

    private final MediaAssetRepository mediaAssetRepository;
    private final MediaAssetRelationRepository relationRepository;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;

    public MediaLineageService(MediaAssetRepository mediaAssetRepository,
                               MediaAssetRelationRepository relationRepository,
                               AuditLogService auditLogService,
                               UserRepository userRepository) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.relationRepository = relationRepository;
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public MediaAssetLineageDto getLineage(UUID assetId, JwtUserDetails user) {
        MediaAsset asset = loadAsset(assetId, user);
        UUID institutionId = asset.getInstitution().getId();

        List<MediaAssetLineageDto.Edge> parents = new ArrayList<>();
        for (MediaAssetRelation r : relationRepository.findByChild(assetId, institutionId)) {
            summary(r.getParentAssetId()).ifPresent(s ->
                    parents.add(new MediaAssetLineageDto.Edge(r.getId(), r.getRelationType(), r.getCreatedAt(), s)));
        }
        List<MediaAssetLineageDto.Edge> children = new ArrayList<>();
        for (MediaAssetRelation r : relationRepository.findByParent(assetId, institutionId)) {
            summary(r.getChildAssetId()).ifPresent(s ->
                    children.add(new MediaAssetLineageDto.Edge(r.getId(), r.getRelationType(), r.getCreatedAt(), s)));
        }
        return new MediaAssetLineageDto(assetId, parents, children);
    }

    @Transactional
    public MediaAssetLineageDto createRelation(UUID parentId,
                                               MediaAssetRelationCreateRequestDto dto,
                                               JwtUserDetails user) {
        MediaAsset parent = loadAsset(parentId, user);
        UUID institutionId = parent.getInstitution().getId();

        String type = dto.getRelationType() == null
                ? "" : dto.getRelationType().trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "relationType must be one of new_version, derived_from, replacement_for, component_of.");
        }
        UUID childId = dto.getChildAssetId();
        if (childId == null || childId.equals(parentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A relation must link two different assets.");
        }

        MediaAsset child = mediaAssetRepository.findActiveById(childId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Related asset not found."));
        if (!child.getInstitution().getId().equals(institutionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Assets in a relation must belong to the same institution.");
        }
        if (relationRepository.existsByParentAssetIdAndChildAssetIdAndRelationType(parentId, childId, type)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That relation already exists.");
        }
        if (createsCycle(parentId, childId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "That link would create a lineage cycle.");
        }

        MediaAssetRelation relation = new MediaAssetRelation();
        relation.setInstitutionId(institutionId);
        relation.setParentAssetId(parentId);
        relation.setChildAssetId(childId);
        relation.setRelationType(type);
        relation.setCreatedBy(user.userId());
        relationRepository.save(relation);

        audit(parentId, childId, type, user);
        return getLineage(parentId, user);
    }

    /** Adding parent→child closes a cycle iff parent is already reachable forward from child. */
    private boolean createsCycle(UUID parentId, UUID childId) {
        Deque<UUID> queue = new ArrayDeque<>();
        Set<UUID> visited = new HashSet<>();
        queue.add(childId);
        int guard = 0;
        while (!queue.isEmpty() && guard++ < CYCLE_WALK_GUARD) {
            UUID current = queue.poll();
            if (current.equals(parentId)) {
                return true;
            }
            if (!visited.add(current)) {
                continue;
            }
            queue.addAll(relationRepository.findChildIds(current));
        }
        return false;
    }

    private Optional<MediaAssetSummaryDto> summary(UUID assetId) {
        return mediaAssetRepository.findActiveById(assetId).map(MediaAssetSummaryDto::from);
    }

    private void audit(UUID parentId, UUID childId, String type, JwtUserDetails user) {
        try {
            User actor = userRepository.findById(user.userId()).orElse(null);
            Map<String, Object> metadata = Map.of(
                    "parentAssetId", String.valueOf(parentId),
                    "childAssetId", String.valueOf(childId),
                    "relationType", type);
            auditLogService.record(actor, "MEDIA_ASSET_RELATION_ADDED", null, null, parentId, metadata);
        } catch (Exception ex) {
            log.warn("Failed to audit relation for asset {}: {}", parentId, ex.getMessage());
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
}
