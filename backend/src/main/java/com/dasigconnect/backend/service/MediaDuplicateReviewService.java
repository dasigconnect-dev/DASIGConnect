package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.media.DuplicateCandidatePairDto;
import com.dasigconnect.backend.model.dto.media.DuplicateDecisionRequestDto;
import com.dasigconnect.backend.model.dto.media.DuplicateDecisionResultDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetSummaryDto;
import com.dasigconnect.backend.model.entity.AssetTag;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaDuplicateReview;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.AssetTagRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.MediaDuplicateReviewRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * UC-4.12 Phase 7F: human duplicate adjudication. Candidates come from the deterministic
 * perceptual-hash flag ({@code duplicate_of_id}); decisions are append-only evidence and never
 * delete an asset. Tenant-scoped: administrators see the network (or one institution), validators
 * see their own.
 */
@Service
public class MediaDuplicateReviewService {

    private static final Logger log = LoggerFactory.getLogger(MediaDuplicateReviewService.class);
    private static final Set<String> ALLOWED_DECISIONS =
            Set.of("duplicate", "keep_both", "not_duplicate");
    private static final int MAX_PAIRS = 100;

    private final MediaAssetRepository mediaAssetRepository;
    private final MediaDuplicateReviewRepository reviewRepository;
    private final AssetTagRepository assetTagRepository;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;

    public MediaDuplicateReviewService(MediaAssetRepository mediaAssetRepository,
                                       MediaDuplicateReviewRepository reviewRepository,
                                       AssetTagRepository assetTagRepository,
                                       AuditLogService auditLogService,
                                       UserRepository userRepository) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.reviewRepository = reviewRepository;
        this.assetTagRepository = assetTagRepository;
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<DuplicateCandidatePairDto> listCandidates(UUID requestedInstitutionId,
                                                          String status,
                                                          JwtUserDetails user) {
        UUID institutionId = resolveScope(requestedInstitutionId, user);
        return "reviewed".equalsIgnoreCase(status)
                ? listReviewed(institutionId)
                : listPending(institutionId);
    }

    private List<DuplicateCandidatePairDto> listPending(UUID institutionId) {
        Set<UUID> reviewed = new HashSet<>(reviewRepository.findReviewedCandidateIds(institutionId));
        List<DuplicateCandidatePairDto> pairs = new ArrayList<>();
        for (MediaAsset candidate : mediaAssetRepository.findDuplicateCandidates(institutionId)) {
            if (reviewed.contains(candidate.getId()) || pairs.size() >= MAX_PAIRS) {
                continue;
            }
            MediaAsset compared = mediaAssetRepository.findActiveById(candidate.getDuplicateOfId()).orElse(null);
            pairs.add(new DuplicateCandidatePairDto(
                    candidate.getId(),
                    MediaAssetSummaryDto.from(candidate),
                    compared == null ? null : MediaAssetSummaryDto.from(compared),
                    hamming(candidate, compared),
                    null, null, false, null));
        }
        return pairs;
    }

    private List<DuplicateCandidatePairDto> listReviewed(UUID institutionId) {
        Set<UUID> seen = new HashSet<>();
        List<DuplicateCandidatePairDto> pairs = new ArrayList<>();
        for (MediaDuplicateReview r : reviewRepository.findRecent(institutionId, PageRequest.of(0, MAX_PAIRS * 2))) {
            if (!seen.add(r.getCandidateAssetId()) || pairs.size() >= MAX_PAIRS) {
                continue;
            }
            MediaAsset candidate = mediaAssetRepository.findActiveById(r.getCandidateAssetId()).orElse(null);
            if (candidate == null) {
                continue;
            }
            MediaAsset compared = r.getComparedAssetId() == null
                    ? null : mediaAssetRepository.findActiveById(r.getComparedAssetId()).orElse(null);
            pairs.add(new DuplicateCandidatePairDto(
                    candidate.getId(),
                    MediaAssetSummaryDto.from(candidate),
                    compared == null ? null : MediaAssetSummaryDto.from(compared),
                    hamming(candidate, compared),
                    r.getDecision(), r.getCanonicalAssetId(), r.isMergedTags(), r.getReviewedAt()));
        }
        return pairs;
    }

    @Transactional
    public DuplicateDecisionResultDto decide(UUID candidateId,
                                             DuplicateDecisionRequestDto dto,
                                             JwtUserDetails user) {
        MediaAsset candidate = loadAsset(candidateId, user);
        UUID institutionId = candidate.getInstitution().getId();
        UUID comparedId = candidate.getDuplicateOfId();

        String decision = dto.getDecision() == null ? "" : dto.getDecision().trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_DECISIONS.contains(decision)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "decision must be one of duplicate, keep_both, not_duplicate.");
        }

        UUID canonicalId = null;
        boolean mergedTags = false;
        if (decision.equals("duplicate")) {
            if (comparedId == null) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(422),
                        "This asset has no compared duplicate to adjudicate.");
            }
            canonicalId = dto.getCanonicalAssetId();
            if (canonicalId == null || (!canonicalId.equals(candidateId) && !canonicalId.equals(comparedId))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "canonicalAssetId must be one of the two assets in the pair.");
            }
            if (dto.isMergeTags()) {
                MediaAsset compared = mediaAssetRepository.findActiveById(comparedId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compared asset not found."));
                MediaAsset canonical = canonicalId.equals(candidateId) ? candidate : compared;
                MediaAsset duplicate = canonicalId.equals(candidateId) ? compared : candidate;
                mergeTags(duplicate, canonical);
                mergedTags = true;
            }
        }

        MediaDuplicateReview review = new MediaDuplicateReview();
        review.setInstitutionId(institutionId);
        review.setCandidateAssetId(candidateId);
        review.setComparedAssetId(comparedId);
        review.setCanonicalAssetId(canonicalId);
        review.setDecision(decision);
        review.setMergedTags(mergedTags);
        review.setReviewedBy(user.userId());
        MediaDuplicateReview saved = reviewRepository.save(review);

        audit(candidateId, decision, canonicalId, comparedId, user);
        return DuplicateDecisionResultDto.from(saved);
    }

    private void mergeTags(MediaAsset from, MediaAsset into) {
        for (AssetTag tag : assetTagRepository.findByMediaAssetIdOrderByCreatedAtAsc(from.getId())) {
            if (!assetTagRepository.existsByMediaAssetIdAndLabel(into.getId(), tag.getLabel())) {
                AssetTag copy = new AssetTag();
                copy.setMediaAsset(into);
                copy.setLabel(tag.getLabel());
                copy.setSource(tag.getSource());
                assetTagRepository.save(copy);
            }
        }
    }

    private static Integer hamming(MediaAsset a, MediaAsset b) {
        if (a == null || b == null || a.getPerceptualHash() == null || b.getPerceptualHash() == null) {
            return null;
        }
        return Long.bitCount(a.getPerceptualHash() ^ b.getPerceptualHash());
    }

    private void audit(UUID candidateId, String decision, UUID canonicalId, UUID comparedId, JwtUserDetails user) {
        try {
            User actor = userRepository.findById(user.userId()).orElse(null);
            Map<String, Object> metadata = Map.of(
                    "decision", decision,
                    "canonicalAssetId", String.valueOf(canonicalId),
                    "comparedAssetId", String.valueOf(comparedId));
            auditLogService.record(actor, "MEDIA_DUPLICATE_REVIEWED", null, null, candidateId, metadata);
        } catch (Exception ex) {
            log.warn("Failed to audit duplicate review for asset {}: {}", candidateId, ex.getMessage());
        }
    }

    private UUID resolveScope(UUID requestedInstitutionId, JwtUserDetails user) {
        if (isAdmin(user)) {
            return requestedInstitutionId; // null => network-wide
        }
        if (user.institutionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Institution-scoped user required to review duplicates.");
        }
        if (requestedInstitutionId != null && !requestedInstitutionId.equals(user.institutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You cannot review duplicates for another institution.");
        }
        return user.institutionId();
    }

    private MediaAsset loadAsset(UUID assetId, JwtUserDetails user) {
        MediaAsset asset = mediaAssetRepository.findActiveById(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media asset not found."));
        if (!isAdmin(user) && !asset.getInstitution().getId().equals(user.institutionId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media asset not found.");
        }
        return asset;
    }

    private boolean isAdmin(JwtUserDetails user) {
        return user.role() != null && user.role().toLowerCase(Locale.ROOT).contains("admin");
    }
}
