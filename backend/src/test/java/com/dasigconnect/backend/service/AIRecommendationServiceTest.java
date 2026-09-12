package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.ai.AlbumMatchRequestDto;
import com.dasigconnect.backend.model.dto.ai.AlbumMatchResponseDto;
import com.dasigconnect.backend.model.dto.ai.MediaSuggestRequestDto;
import com.dasigconnect.backend.model.dto.ai.MediaSuggestResultDto;
import com.dasigconnect.backend.model.entity.MediaAlbum;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetEmbeddingType;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.AiInteractionLogRepository;
import com.dasigconnect.backend.repository.AssetTagRepository;
import com.dasigconnect.backend.repository.MediaAlbumRepository;
import com.dasigconnect.backend.repository.MediaAssetEmbeddingRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionMediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.external.VoyageAIClient;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AIRecommendationServiceTest {

    @Test
    void buildQueryEmbeddingText_includesCategoryAndTags() {
        MediaSuggestRequestDto dto = new MediaSuggestRequestDto();
        dto.setEventTitle("Regional robotics bootcamp");
        dto.setCaption("Students presented prototypes with DOST mentors.");
        dto.setCategory("Training");
        dto.setTags(List.of("Students", "Innovation"));

        String text = AIRecommendationService.buildQueryEmbeddingText(dto);

        assertTrue(text.contains("event_title: Regional robotics bootcamp."));
        assertTrue(text.contains("caption: Students presented prototypes with DOST mentors."));
        assertTrue(text.contains("category: Training."));
        assertTrue(text.contains("tags: Students, Innovation."));
    }

    @Test
    void boostedScore_prioritizesCategoryAndTagMatches() {
        MediaSuggestRequestDto dto = new MediaSuggestRequestDto();
        dto.setCategory("Training");
        dto.setTags(List.of("Students", "Innovation"));

        MediaAsset matching = new MediaAsset();
        matching.setAiCategory("Training");
        setCreatedAt(matching, Instant.now());

        MediaAsset weak = new MediaAsset();
        weak.setAiCategory("Facility");
        setCreatedAt(weak, Instant.now().minusSeconds(120L * 24 * 60 * 60));

        double matchingScore = AIRecommendationService.boostedScore(
                matching, 0.72, dto, Set.of("Students", "Research"));
        double weakScore = AIRecommendationService.boostedScore(
                weak, 0.72, dto, Set.of("Community"));

        assertTrue(matchingScore > weakScore);
    }

    @Test
    void suggestMedia_fallsBackWhenSemanticCandidatesAreAlreadyAttached() {
        UUID institutionId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        UUID attachedId = UUID.randomUUID();
        UUID fallbackId = UUID.randomUUID();

        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        SubmissionMediaAssetRepository submissionMediaAssetRepository = mock(SubmissionMediaAssetRepository.class);
        MediaAssetRepository mediaAssetRepository = mock(MediaAssetRepository.class);
        MediaAssetEmbeddingRepository mediaAssetEmbeddingRepository = mock(MediaAssetEmbeddingRepository.class);
        AssetTagRepository assetTagRepository = mock(AssetTagRepository.class);
        AiInteractionLogRepository aiInteractionLogRepository = mock(AiInteractionLogRepository.class);
        VoyageAIClient voyageAIClient = mock(VoyageAIClient.class);

        Institution institution = new Institution();
        institution.setId(institutionId);
        Submission submission = new Submission();
        submission.setId(submissionId);
        submission.setInstitution(institution);
        UUID contributorId = UUID.randomUUID();
        User contributor = new User();
        contributor.setId(contributorId);
        submission.setContributor(contributor);

        MediaAsset attached = asset(attachedId, "cookie-selected.jpg", "Event");
        MediaAsset fallback = asset(fallbackId, "cookie-library.jpg", "Event");

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(submissionMediaAssetRepository.findMediaAssetsBySubmissionId(submissionId)).thenReturn(List.of(attached));
        when(voyageAIClient.embedQuery(org.mockito.ArgumentMatchers.anyString())).thenReturn("[0.1,0.2]");
        when(mediaAssetEmbeddingRepository.findTopSimilarWithScore(
                org.mockito.ArgumentMatchers.eq(institutionId),
                org.mockito.ArgumentMatchers.eq(MediaAssetEmbeddingType.SEMANTIC),
                org.mockito.ArgumentMatchers.eq("[0.1,0.2]"),
                org.mockito.ArgumentMatchers.eq(30)))
                .thenReturn(List.<Object[]>of(new Object[]{attachedId.toString(), 0.92}));
        when(mediaAssetRepository.findActiveByIds(List.of(attachedId))).thenReturn(List.of(attached));
        when(assetTagRepository.findLabelsAndSourcesByMediaAssetIds(anyList())).thenReturn(List.of());
        when(mediaAssetRepository.findReadyByInstitution(institutionId)).thenReturn(List.of(attached, fallback));

        AIRecommendationService service = new AIRecommendationService(
                submissionRepository,
                submissionMediaAssetRepository,
                mediaAssetRepository,
                mediaAssetEmbeddingRepository,
                assetTagRepository,
                aiInteractionLogRepository,
                voyageAIClient,
                mock(MediaAlbumRepository.class)
        );

        MediaSuggestRequestDto dto = new MediaSuggestRequestDto();
        dto.setEventTitle("Cookie so good");
        dto.setCaption("Passed Capstone Cutie should be good");
        dto.setCategory("Event");

        List<MediaSuggestResultDto> results = service.suggestMedia(
                submissionId,
                dto,
                new JwtUserDetails(contributorId, "contributor@test.edu", "contributor", institutionId)
        );

        assertEquals(1, results.size());
        assertEquals(fallbackId, results.getFirst().getId());
        assertTrue(results.getFirst().getMatchReasons().stream()
                .anyMatch(reason -> reason.toLowerCase().contains("category")));
    }

    // ── suggestAlbum() — album Auto-Match (UC-1.7) ──────────────────────────────

    @Test
    void suggestAlbum_confidentWhenTagsAndEmbeddingBothMatch() {
        Harness h = harness();
        MediaAlbum album = album(h.institutionId, "Hackathon 2026");
        MediaAlbum other = album(h.institutionId, "General");
        when(h.mediaAlbumRepository.findByInstitutionIdOrderByName(h.institutionId))
                .thenReturn(List.of(album, other));
        when(h.assetTagRepository.findLabelsByRootAlbumForInstitution(h.institutionId))
                .thenReturn(List.<Object[]>of(new Object[]{album.getId().toString(), "hackathon"}));
        when(h.voyageAIClient.embedQuery(anyString())).thenReturn("[0.1,0.2]");
        when(h.mediaAssetEmbeddingRepository.findMaxSimilarityByRootAlbum(
                eq(h.institutionId), eq(MediaAssetEmbeddingType.SEMANTIC), eq("[0.1,0.2]")))
                .thenReturn(List.<Object[]>of(
                        new Object[]{album.getId().toString(), 0.90},
                        new Object[]{other.getId().toString(), 0.10}));

        AlbumMatchRequestDto dto = new AlbumMatchRequestDto();
        dto.setEventTitle("Hackathon 2026 Kickoff");
        dto.setTags(List.of("Hackathon"));

        AlbumMatchResponseDto result = h.service.suggestAlbum(h.submissionId, dto, h.contributorPrincipal);

        assertEquals(AlbumMatchResponseDto.Status.confident, result.getStatus());
        assertEquals(1, result.getCandidates().size());
        assertEquals(album.getId(), result.getCandidates().get(0).getAlbumId());
        assertTrue(result.getCandidates().get(0).getScore() >= 0.55);
    }

    @Test
    void suggestAlbum_ambiguousWhenScoreClearsFloorButNotConfidentBar() {
        Harness h = harness();
        MediaAlbum album = album(h.institutionId, "Community Outreach");
        when(h.mediaAlbumRepository.findByInstitutionIdOrderByName(h.institutionId)).thenReturn(List.of(album));
        when(h.assetTagRepository.findLabelsByRootAlbumForInstitution(h.institutionId)).thenReturn(List.of());
        when(h.voyageAIClient.embedQuery(anyString())).thenReturn("[0.1,0.2]");
        when(h.mediaAssetEmbeddingRepository.findMaxSimilarityByRootAlbum(
                eq(h.institutionId), eq(MediaAssetEmbeddingType.SEMANTIC), eq("[0.1,0.2]")))
                .thenReturn(List.<Object[]>of(new Object[]{album.getId().toString(), 0.50}));

        AlbumMatchRequestDto dto = new AlbumMatchRequestDto();
        dto.setEventTitle("Some other event");

        AlbumMatchResponseDto result = h.service.suggestAlbum(h.submissionId, dto, h.contributorPrincipal);

        assertEquals(AlbumMatchResponseDto.Status.ambiguous, result.getStatus());
        assertEquals(1, result.getCandidates().size());
    }

    @Test
    void suggestAlbum_noneWhenInstitutionHasNoRootAlbums() {
        Harness h = harness();
        when(h.mediaAlbumRepository.findByInstitutionIdOrderByName(h.institutionId)).thenReturn(List.of());

        AlbumMatchRequestDto dto = new AlbumMatchRequestDto();
        dto.setEventTitle("Anything");

        AlbumMatchResponseDto result = h.service.suggestAlbum(h.submissionId, dto, h.contributorPrincipal);

        assertEquals(AlbumMatchResponseDto.Status.none, result.getStatus());
        assertTrue(result.getCandidates().isEmpty());
    }

    @Test
    void suggestAlbum_moderatorWithNullInstitutionId_bypassesOwnershipCheck() {
        Harness h = harness();
        MediaAlbum album = album(h.institutionId, "Anything");
        when(h.mediaAlbumRepository.findByInstitutionIdOrderByName(h.institutionId)).thenReturn(List.of(album));
        when(h.assetTagRepository.findLabelsByRootAlbumForInstitution(h.institutionId)).thenReturn(List.of());
        when(h.voyageAIClient.embedQuery(anyString())).thenReturn("[0.1,0.2]");
        when(h.mediaAssetEmbeddingRepository.findMaxSimilarityByRootAlbum(
                eq(h.institutionId), eq(MediaAssetEmbeddingType.SEMANTIC), eq("[0.1,0.2]")))
                .thenReturn(List.<Object[]>of());

        AlbumMatchRequestDto dto = new AlbumMatchRequestDto();
        dto.setEventTitle("Anything");
        JwtUserDetails moderator = new JwtUserDetails(UUID.randomUUID(), "mod@test.edu", "moderator", null);

        AlbumMatchResponseDto result = h.service.suggestAlbum(h.submissionId, dto, moderator);

        assertEquals(AlbumMatchResponseDto.Status.none, result.getStatus());
    }

    @Test
    void suggestAlbum_contributorFromAnotherInstitution_isForbidden() {
        Harness h = harness();
        JwtUserDetails otherContributor = new JwtUserDetails(UUID.randomUUID(), "other@test.edu", "contributor", UUID.randomUUID());

        AlbumMatchRequestDto dto = new AlbumMatchRequestDto();

        assertThatThrownBy(() -> h.service.suggestAlbum(h.submissionId, dto, otherContributor))
                .isInstanceOf(ResponseStatusException.class);
    }

    private record Harness(AIRecommendationService service, UUID submissionId, UUID institutionId,
                           JwtUserDetails contributorPrincipal, MediaAlbumRepository mediaAlbumRepository,
                           AssetTagRepository assetTagRepository, VoyageAIClient voyageAIClient,
                           MediaAssetEmbeddingRepository mediaAssetEmbeddingRepository) {}

    private static Harness harness() {
        UUID institutionId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        UUID contributorId = UUID.randomUUID();

        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        MediaAlbumRepository mediaAlbumRepository = mock(MediaAlbumRepository.class);
        AssetTagRepository assetTagRepository = mock(AssetTagRepository.class);
        VoyageAIClient voyageAIClient = mock(VoyageAIClient.class);
        MediaAssetEmbeddingRepository mediaAssetEmbeddingRepository = mock(MediaAssetEmbeddingRepository.class);

        Institution institution = new Institution();
        institution.setId(institutionId);
        Submission submission = new Submission();
        submission.setId(submissionId);
        submission.setInstitution(institution);
        User contributor = new User();
        contributor.setId(contributorId);
        submission.setContributor(contributor);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        AIRecommendationService service = new AIRecommendationService(
                submissionRepository,
                mock(SubmissionMediaAssetRepository.class),
                mock(MediaAssetRepository.class),
                mediaAssetEmbeddingRepository,
                assetTagRepository,
                mock(AiInteractionLogRepository.class),
                voyageAIClient,
                mediaAlbumRepository
        );

        JwtUserDetails contributorPrincipal = new JwtUserDetails(contributorId, "contributor@test.edu", "contributor", institutionId);
        return new Harness(service, submissionId, institutionId, contributorPrincipal,
                mediaAlbumRepository, assetTagRepository, voyageAIClient, mediaAssetEmbeddingRepository);
    }

    private static MediaAlbum album(UUID institutionId, String name) {
        Institution institution = new Institution();
        institution.setId(institutionId);
        MediaAlbum album = new MediaAlbum();
        album.setId(UUID.randomUUID());
        album.setInstitution(institution);
        album.setName(name);
        album.setCreatedBy(UUID.randomUUID());
        return album;
    }

    private static void setCreatedAt(MediaAsset asset, Instant createdAt) {
        try {
            var field = MediaAsset.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(asset, createdAt);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static MediaAsset asset(UUID id, String fileName, String category) {
        MediaAsset asset = new MediaAsset();
        asset.setId(id);
        asset.setAssetCode("ASSET-" + id.toString().substring(0, 8).toUpperCase());
        asset.setStorageUrl("https://example.com/" + fileName);
        asset.setFileName(fileName);
        asset.setFileType(MediaFileType.jpeg);
        asset.setFileSizeBytes(1024);
        asset.setAiCategory(category);
        setCreatedAt(asset, Instant.now());
        return asset;
    }
}
