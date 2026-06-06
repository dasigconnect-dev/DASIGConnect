package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.model.dto.media.DuplicateCandidatePairDto;
import com.dasigconnect.backend.model.dto.media.DuplicateDecisionRequestDto;
import com.dasigconnect.backend.model.dto.media.DuplicateDecisionResultDto;
import com.dasigconnect.backend.model.entity.AssetTag;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaDuplicateReview;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.AssetTagRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.MediaDuplicateReviewRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MediaDuplicateReviewServiceTest {

    @Mock
    private MediaAssetRepository mediaAssetRepository;
    @Mock
    private MediaDuplicateReviewRepository reviewRepository;
    @Mock
    private AssetTagRepository assetTagRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private UserRepository userRepository;

    private MediaDuplicateReviewService service;

    @BeforeEach
    void setUp() {
        service = new MediaDuplicateReviewService(
                mediaAssetRepository, reviewRepository, assetTagRepository, auditLogService, userRepository);
        lenientSave();
    }

    private void lenientSave() {
        org.mockito.Mockito.lenient()
                .when(reviewRepository.save(any(MediaDuplicateReview.class))).thenAnswer(inv -> {
                    MediaDuplicateReview r = inv.getArgument(0);
                    if (r.getId() == null) r.setId(UUID.randomUUID());
                    return r;
                });
    }

    @Test
    void listPending_buildsPairsWithHamming_excludingReviewed() {
        UUID institutionId = UUID.randomUUID();
        UUID comparedId = UUID.randomUUID();
        MediaAsset candidate = asset(UUID.randomUUID(), institutionId, comparedId, 0b1011L);
        MediaAsset compared = asset(comparedId, institutionId, null, 0b1001L);
        when(mediaAssetRepository.findDuplicateCandidates(institutionId)).thenReturn(List.of(candidate));
        when(reviewRepository.findReviewedCandidateIds(institutionId)).thenReturn(List.of());
        when(mediaAssetRepository.findActiveById(comparedId)).thenReturn(Optional.of(compared));

        List<DuplicateCandidatePairDto> pairs = service.listCandidates(null, "pending", user("validator", institutionId));

        assertThat(pairs).hasSize(1);
        assertThat(pairs.get(0).candidateAssetId()).isEqualTo(candidate.getId());
        assertThat(pairs.get(0).compared().getId()).isEqualTo(comparedId);
        assertThat(pairs.get(0).hammingDistance()).isEqualTo(1); // 1011 ^ 1001 = 0010
        assertThat(pairs.get(0).decision()).isNull();
    }

    @Test
    void decide_duplicate_recordsCanonicalAndAudits() {
        UUID institutionId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        UUID comparedId = UUID.randomUUID();
        MediaAsset candidate = asset(candidateId, institutionId, comparedId, 1L);
        when(mediaAssetRepository.findActiveById(candidateId)).thenReturn(Optional.of(candidate));

        DuplicateDecisionRequestDto dto = new DuplicateDecisionRequestDto();
        dto.setDecision("duplicate");
        dto.setCanonicalAssetId(comparedId);

        DuplicateDecisionResultDto result = service.decide(candidateId, dto, user("validator", institutionId));

        assertThat(result.decision()).isEqualTo("duplicate");
        assertThat(result.canonicalAssetId()).isEqualTo(comparedId);
        assertThat(result.mergedTags()).isFalse();
        verify(reviewRepository).save(any(MediaDuplicateReview.class));
        verify(auditLogService).record(any(), eq("MEDIA_DUPLICATE_REVIEWED"), any(), any(), eq(candidateId), any());
    }

    @Test
    void decide_duplicate_withMergeTags_copiesMissingTagsToCanonical() {
        UUID institutionId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        UUID comparedId = UUID.randomUUID();
        MediaAsset candidate = asset(candidateId, institutionId, comparedId, 1L);
        MediaAsset compared = asset(comparedId, institutionId, null, 1L);
        when(mediaAssetRepository.findActiveById(candidateId)).thenReturn(Optional.of(candidate));
        when(mediaAssetRepository.findActiveById(comparedId)).thenReturn(Optional.of(compared));
        // canonical = candidate, so the duplicate (compared) tags are merged into candidate.
        when(assetTagRepository.findByMediaAssetIdOrderByCreatedAtAsc(comparedId))
                .thenReturn(List.of(tag(compared, "awarding")));
        when(assetTagRepository.existsByMediaAssetIdAndLabel(candidateId, "awarding")).thenReturn(false);

        DuplicateDecisionRequestDto dto = new DuplicateDecisionRequestDto();
        dto.setDecision("duplicate");
        dto.setCanonicalAssetId(candidateId);
        dto.setMergeTags(true);

        DuplicateDecisionResultDto result = service.decide(candidateId, dto, user("validator", institutionId));

        assertThat(result.mergedTags()).isTrue();
        verify(assetTagRepository).save(any(AssetTag.class));
    }

    @Test
    void decide_duplicate_canonicalNotInPair_returns400() {
        UUID institutionId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        MediaAsset candidate = asset(candidateId, institutionId, UUID.randomUUID(), 1L);
        when(mediaAssetRepository.findActiveById(candidateId)).thenReturn(Optional.of(candidate));

        DuplicateDecisionRequestDto dto = new DuplicateDecisionRequestDto();
        dto.setDecision("duplicate");
        dto.setCanonicalAssetId(UUID.randomUUID());

        assertThatThrownBy(() -> service.decide(candidateId, dto, user("validator", institutionId)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void decide_keepBoth_recordsWithoutCanonical() {
        UUID institutionId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        MediaAsset candidate = asset(candidateId, institutionId, UUID.randomUUID(), 1L);
        when(mediaAssetRepository.findActiveById(candidateId)).thenReturn(Optional.of(candidate));

        DuplicateDecisionRequestDto dto = new DuplicateDecisionRequestDto();
        dto.setDecision("keep_both");

        DuplicateDecisionResultDto result = service.decide(candidateId, dto, user("admin", null));

        assertThat(result.decision()).isEqualTo("keep_both");
        assertThat(result.canonicalAssetId()).isNull();
    }

    @Test
    void decide_invalidDecision_returns400() {
        UUID institutionId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        when(mediaAssetRepository.findActiveById(candidateId))
                .thenReturn(Optional.of(asset(candidateId, institutionId, UUID.randomUUID(), 1L)));

        DuplicateDecisionRequestDto dto = new DuplicateDecisionRequestDto();
        dto.setDecision("merge");

        assertThatThrownBy(() -> service.decide(candidateId, dto, user("validator", institutionId)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void decide_crossTenant_isHidden() {
        UUID candidateId = UUID.randomUUID();
        when(mediaAssetRepository.findActiveById(candidateId))
                .thenReturn(Optional.of(asset(candidateId, UUID.randomUUID(), UUID.randomUUID(), 1L)));

        DuplicateDecisionRequestDto dto = new DuplicateDecisionRequestDto();
        dto.setDecision("keep_both");

        assertThatThrownBy(() -> service.decide(candidateId, dto, user("validator", UUID.randomUUID())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        verify(reviewRepository, never()).save(any());
    }

    private static MediaAsset asset(UUID id, UUID institutionId, UUID duplicateOfId, Long pHash) {
        MediaAsset asset = new MediaAsset();
        asset.setId(id);
        asset.setAssetCode("ASSET-" + id.toString().substring(0, 8));
        asset.setStorageUrl("https://storage.example/" + id + ".jpg");
        asset.setFileName(id + ".jpg");
        asset.setFileType(MediaFileType.jpeg);
        asset.setFileSizeBytes(2048);
        asset.setDuplicateOfId(duplicateOfId);
        asset.setPerceptualHash(pHash);
        Institution institution = new Institution();
        institution.setId(institutionId);
        institution.setName("Institution");
        institution.setCode("INST");
        asset.setInstitution(institution);
        User uploader = new User();
        uploader.setId(UUID.randomUUID());
        uploader.setEmail("uploader@example.edu");
        asset.setUploader(uploader);
        return asset;
    }

    private static AssetTag tag(MediaAsset asset, String label) {
        AssetTag t = new AssetTag();
        t.setMediaAsset(asset);
        t.setLabel(label);
        t.setSource("manual");
        return t;
    }

    private static JwtUserDetails user(String role, UUID institutionId) {
        return new JwtUserDetails(UUID.randomUUID(), role + "@example.edu", role, institutionId);
    }
}
