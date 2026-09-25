package com.dasigconnect.backend.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.external.VoyageAIClient;
import com.dasigconnect.backend.model.dto.ai.AlbumMatchRequestDto;
import com.dasigconnect.backend.model.dto.ai.AlbumMatchResponseDto;
import com.dasigconnect.backend.model.dto.ai.MediaSuggestRequestDto;
import com.dasigconnect.backend.model.dto.ai.MediaSuggestResultDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaAlbum;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetEmbeddingType;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionMediaContext;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.AiInteractionLogRepository;
import com.dasigconnect.backend.repository.AssetTagRepository;
import com.dasigconnect.backend.repository.MediaAlbumRepository;
import com.dasigconnect.backend.repository.MediaAssetEmbeddingRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionMediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionMediaContextRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.security.JwtUserDetails;

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
    void temporalEligibility_excludesExpiredButPreservesUnknownAndEvergreenAssets() {
        Instant now = Instant.parse("2026-09-25T00:00:00Z");
        MediaAsset expired = new MediaAsset();
        expired.setTemporalClassification("expired");
        MediaAsset pastDate = new MediaAsset();
        pastDate.setTemporalClassification("time_bound");
        pastDate.setPossibleExpiration("2026-09-20");
        MediaAsset unknownLegacy = new MediaAsset();
        unknownLegacy.setPossibleExpiration("after the annual event");
        MediaAsset evergreen = new MediaAsset();
        evergreen.setTemporalClassification("evergreen");

        assertTrue(!AIRecommendationService.isTemporallyEligible(expired, now));
        assertTrue(!AIRecommendationService.isTemporallyEligible(pastDate, now));
        assertTrue(AIRecommendationService.isTemporallyEligible(unknownLegacy, now));
        assertTrue(AIRecommendationService.isTemporallyEligible(evergreen, now));
    }

    @Test
    void freshnessAndUsage_preserveOldEvergreenMediaAndPenalizeRecentOveruse() {
        Instant now = Instant.now();
        MediaAsset evergreen = new MediaAsset();
        evergreen.setTemporalClassification("evergreen");
        setCreatedAt(evergreen, now.minusSeconds(800L * 24 * 60 * 60));
        MediaAsset oldUnknown = new MediaAsset();
        setCreatedAt(oldUnknown, now.minusSeconds(800L * 24 * 60 * 60));

        assertEquals(1.0, AIRecommendationService.freshnessScore(evergreen));
        assertTrue(AIRecommendationService.freshnessScore(evergreen)
                > AIRecommendationService.freshnessScore(oldUnknown));
        assertTrue(AIRecommendationService.usageDiversityScore(0, null, now)
                > AIRecommendationService.usageDiversityScore(5, now.minusSeconds(24 * 60 * 60), now));
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
        when(mediaAssetRepository.findVisibleReadyByInstitution(eq(institutionId), any()))
                .thenReturn(List.of(attached, fallback));

        AIRecommendationService service = new AIRecommendationService(
                submissionRepository,
                submissionMediaAssetRepository,
                mediaAssetRepository,
                mediaAssetEmbeddingRepository,
                assetTagRepository,
                aiInteractionLogRepository,
                voyageAIClient,
                mock(MediaAlbumRepository.class),
                mock(SubmissionMediaContextRepository.class),
                true,
                false,
                true
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

        List<MediaSuggestResultDto> moderatorResults = service.suggestMedia(
                submissionId,
                dto,
                new JwtUserDetails(UUID.randomUUID(), "moderator@test.edu", "moderator", null)
        );

        assertEquals(1, moderatorResults.size());
        assertEquals(fallbackId, moderatorResults.getFirst().getId());
    }

    // ── suggestAlbum() — album Auto-Match (UC-1.7) ──────────────────────────────
    @Test
    void suggestMedia_usesEveryAttachedImageWithoutSpendingTextEmbeddingTokens() {
        UUID institutionId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        UUID contributorId = UUID.randomUUID();
        UUID firstAttachedId = UUID.randomUUID();
        UUID secondAttachedId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();

        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        SubmissionMediaAssetRepository submissionMediaAssetRepository = mock(SubmissionMediaAssetRepository.class);
        MediaAssetRepository mediaAssetRepository = mock(MediaAssetRepository.class);
        MediaAssetEmbeddingRepository mediaAssetEmbeddingRepository = mock(MediaAssetEmbeddingRepository.class);
        AssetTagRepository assetTagRepository = mock(AssetTagRepository.class);
        VoyageAIClient voyageAIClient = mock(VoyageAIClient.class);

        Institution institution = new Institution();
        institution.setId(institutionId);
        User contributor = new User();
        contributor.setId(contributorId);
        Submission submission = new Submission();
        submission.setId(submissionId);
        submission.setInstitution(institution);
        submission.setContributor(contributor);

        MediaAsset firstAttached = asset(firstAttachedId, "team-with-laptops.jpg", "Technology");
        MediaAsset secondAttached = asset(secondAttachedId, "awarding.jpg", "Event");
        MediaAsset candidate = asset(candidateId, "hackathon-audience.jpg", "Event");

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(submissionMediaAssetRepository.findMediaAssetsBySubmissionId(submissionId))
                .thenReturn(List.of(firstAttached, secondAttached));
        when(assetTagRepository.findLabelsAndSourcesByMediaAssetIds(anyList())).thenReturn(List.of());
        when(mediaAssetEmbeddingRepository.findTopSimilarToAssetsWithScore(
                eq(institutionId),
                eq(submissionId),
                eq(MediaAssetEmbeddingType.IMAGE),
                argThat(ids -> ids.size() == 2
                        && ids.contains(firstAttachedId)
                        && ids.contains(secondAttachedId)),
                eq(12),
                eq(30)))
                .thenReturn(List.<Object[]>of(new Object[]{candidateId.toString(), 0.82}));
        when(mediaAssetEmbeddingRepository.findTopSimilarToAssetsWithScore(
                eq(institutionId),
                eq(submissionId),
                eq(MediaAssetEmbeddingType.SEMANTIC),
                argThat(ids -> ids.size() == 2
                        && ids.contains(firstAttachedId)
                        && ids.contains(secondAttachedId)),
                eq(12),
                eq(30)))
                .thenReturn(List.<Object[]>of(new Object[]{candidateId.toString(), 0.76}));
        when(mediaAssetRepository.findActiveByIds(List.of(candidateId))).thenReturn(List.of(candidate));

        AIRecommendationService service = new AIRecommendationService(
                submissionRepository,
                submissionMediaAssetRepository,
                mediaAssetRepository,
                mediaAssetEmbeddingRepository,
                assetTagRepository,
                mock(AiInteractionLogRepository.class),
                voyageAIClient,
                mock(MediaAlbumRepository.class),
                mock(SubmissionMediaContextRepository.class),
                true,
                true,
                true
        );

        List<MediaSuggestResultDto> results = service.suggestMedia(
                submissionId,
                new MediaSuggestRequestDto(),
                new JwtUserDetails(contributorId, "contributor@test.edu", "contributor", institutionId)
        );

        assertEquals(1, results.size());
        assertEquals(candidateId, results.getFirst().getId());
        assertEquals("legacy-v1", results.getFirst().getRankingVersion());
        assertTrue(results.getFirst().getMatchReasons().stream()
                .anyMatch(reason -> reason.toLowerCase().contains("visual")));
        verify(voyageAIClient, never()).embedQuery(anyString());
    }

    @Test
    void suggestMedia_requestedMixedSelectionUsesOnlyAttachedSelectedImages() {
        UUID institutionId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        UUID contributorId = UUID.randomUUID();
        MediaAsset stagedUpload = asset(UUID.randomUUID(), "staged-upload.jpg", "Event");
        MediaAsset libraryPick = asset(UUID.randomUUID(), "library-pick.jpg", "Event");
        MediaAsset unselected = asset(UUID.randomUUID(), "unselected.jpg", "Event");
        MediaAsset candidate = asset(UUID.randomUUID(), "recommended.jpg", "Event");

        SubmissionRepository submissions = mock(SubmissionRepository.class);
        SubmissionMediaAssetRepository submissionMedia = mock(SubmissionMediaAssetRepository.class);
        MediaAssetRepository mediaAssets = mock(MediaAssetRepository.class);
        MediaAssetEmbeddingRepository embeddings = mock(MediaAssetEmbeddingRepository.class);
        AssetTagRepository tags = mock(AssetTagRepository.class);

        when(submissions.findById(submissionId)).thenReturn(Optional.of(
                submission(submissionId, institutionId, contributorId)));
        when(submissionMedia.findMediaAssetsBySubmissionId(submissionId))
                .thenReturn(List.of(stagedUpload, libraryPick, unselected));
        when(tags.findLabelsAndSourcesByMediaAssetIds(anyList())).thenReturn(List.of());
        when(embeddings.findTopSimilarToAssetsWithScore(
                eq(institutionId),
                eq(submissionId),
                eq(MediaAssetEmbeddingType.IMAGE),
                argThat(ids -> ids.size() == 2
                        && ids.contains(stagedUpload.getId())
                        && ids.contains(libraryPick.getId())),
                eq(12),
                eq(30)))
                .thenReturn(List.<Object[]>of(new Object[]{candidate.getId().toString(), 0.82}));
        when(embeddings.findTopSimilarToAssetsWithScore(
                eq(institutionId),
                eq(submissionId),
                eq(MediaAssetEmbeddingType.SEMANTIC),
                argThat(ids -> ids.size() == 2
                        && ids.contains(stagedUpload.getId())
                        && ids.contains(libraryPick.getId())),
                eq(12),
                eq(30)))
                .thenReturn(List.of());
        when(mediaAssets.findActiveByIds(List.of(candidate.getId()))).thenReturn(List.of(candidate));

        AIRecommendationService service = new AIRecommendationService(
                submissions, submissionMedia, mediaAssets, embeddings, tags,
                mock(AiInteractionLogRepository.class), mock(VoyageAIClient.class),
                mock(MediaAlbumRepository.class), mock(SubmissionMediaContextRepository.class),
                true, false, false);
        MediaSuggestRequestDto dto = new MediaSuggestRequestDto();
        dto.setSelectedAssetIds(List.of(stagedUpload.getId(), libraryPick.getId()));

        List<MediaSuggestResultDto> results = service.suggestMedia(
                submissionId,
                dto,
                new JwtUserDetails(contributorId, "contributor@test.edu", "contributor", institutionId));

        assertEquals(List.of(candidate.getId()), results.stream().map(MediaSuggestResultDto::getId).toList());
        verify(embeddings, never()).findTopSimilarToAssetsWithScore(
                eq(institutionId),
                eq(submissionId),
                any(MediaAssetEmbeddingType.class),
                eq(List.of(unselected.getId())),
                eq(12),
                eq(30));
    }

    @Test
    void suggestMedia_rejectsSelectedAssetThatIsNotAttachedToSubmission() {
        UUID institutionId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        UUID contributorId = UUID.randomUUID();
        MediaAsset attached = asset(UUID.randomUUID(), "attached.jpg", "Event");

        SubmissionRepository submissions = mock(SubmissionRepository.class);
        SubmissionMediaAssetRepository submissionMedia = mock(SubmissionMediaAssetRepository.class);
        MediaAssetEmbeddingRepository embeddings = mock(MediaAssetEmbeddingRepository.class);
        when(submissions.findById(submissionId)).thenReturn(Optional.of(
                submission(submissionId, institutionId, contributorId)));
        when(submissionMedia.findMediaAssetsBySubmissionId(submissionId)).thenReturn(List.of(attached));

        AIRecommendationService service = new AIRecommendationService(
                submissions, submissionMedia, mock(MediaAssetRepository.class), embeddings,
                mock(AssetTagRepository.class), mock(AiInteractionLogRepository.class),
                mock(VoyageAIClient.class), mock(MediaAlbumRepository.class),
                mock(SubmissionMediaContextRepository.class), true, false, false);
        MediaSuggestRequestDto dto = new MediaSuggestRequestDto();
        dto.setSelectedAssetIds(List.of(UUID.randomUUID()));

        assertThatThrownBy(() -> service.suggestMedia(
                submissionId,
                dto,
                new JwtUserDetails(contributorId, "contributor@test.edu", "contributor", institutionId)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("selectedAssetIds must reference media attached to this submission");
        verify(embeddings, never()).findTopSimilarToAssetsWithScore(
                any(), any(), any(MediaAssetEmbeddingType.class), anyList(), anyInt(), anyInt());
    }

    @Test
    void suggestMedia_visualOnlyWhileEmbeddingsArePending_doesNotReturnGenericFallback() {
        UUID institutionId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        UUID contributorId = UUID.randomUUID();
        MediaAsset attached = asset(UUID.randomUUID(), "new-upload.jpg", "Event");

        SubmissionRepository submissions = mock(SubmissionRepository.class);
        SubmissionMediaAssetRepository submissionMedia = mock(SubmissionMediaAssetRepository.class);
        MediaAssetRepository mediaAssets = mock(MediaAssetRepository.class);
        MediaAssetEmbeddingRepository embeddings = mock(MediaAssetEmbeddingRepository.class);
        AssetTagRepository tags = mock(AssetTagRepository.class);
        VoyageAIClient voyage = mock(VoyageAIClient.class);

        Institution institution = new Institution();
        institution.setId(institutionId);
        User contributor = new User();
        contributor.setId(contributorId);
        Submission submission = new Submission();
        submission.setId(submissionId);
        submission.setInstitution(institution);
        submission.setContributor(contributor);

        when(submissions.findById(submissionId)).thenReturn(Optional.of(submission));
        when(submissionMedia.findMediaAssetsBySubmissionId(submissionId)).thenReturn(List.of(attached));
        when(tags.findLabelsAndSourcesByMediaAssetIds(anyList())).thenReturn(List.of());
        when(embeddings.countEmbeddingsForAssets(
                List.of(attached.getId()), MediaAssetEmbeddingType.IMAGE)).thenReturn(0L);
        when(embeddings.findTopSimilarToAssetsWithScore(
                eq(institutionId), eq(submissionId), eq(MediaAssetEmbeddingType.IMAGE), anyList(), eq(12), eq(30)))
                .thenReturn(List.of());
        when(embeddings.findTopSimilarToAssetsWithScore(
                eq(institutionId), eq(submissionId), eq(MediaAssetEmbeddingType.SEMANTIC), anyList(), eq(12), eq(30)))
                .thenReturn(List.of());

        AIRecommendationService service = new AIRecommendationService(
                submissions, submissionMedia, mediaAssets, embeddings, tags,
                mock(AiInteractionLogRepository.class), voyage, mock(MediaAlbumRepository.class),
                mock(SubmissionMediaContextRepository.class), true, false, true);

        AIRecommendationService.MediaSuggestionBatch batch = service.suggestMediaBatch(
                submissionId,
                new MediaSuggestRequestDto(),
                new JwtUserDetails(contributorId, "contributor@test.edu", "contributor", institutionId));

        assertTrue(batch.results().isEmpty());
        assertTrue(batch.processing());
        assertTrue(service.areSelectedImagesProcessing(
                submissionId,
                new MediaSuggestRequestDto(),
                new JwtUserDetails(contributorId, "contributor@test.edu", "contributor", institutionId)));
        verify(mediaAssets, never()).findVisibleReadyByInstitution(eq(institutionId), any());
        verify(voyage, never()).embedQuery(anyString());
    }

    @Test
    void suggestMedia_visualFlagDisabled_preservesTextRecommendationPath() {
        UUID institutionId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        UUID contributorId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        MediaAsset attached = asset(UUID.randomUUID(), "selected.jpg", "Event");
        MediaAsset candidate = asset(candidateId, "candidate.jpg", "Event");

        SubmissionRepository submissions = mock(SubmissionRepository.class);
        SubmissionMediaAssetRepository submissionMedia = mock(SubmissionMediaAssetRepository.class);
        MediaAssetRepository mediaAssets = mock(MediaAssetRepository.class);
        MediaAssetEmbeddingRepository embeddings = mock(MediaAssetEmbeddingRepository.class);
        AssetTagRepository tags = mock(AssetTagRepository.class);
        VoyageAIClient voyage = mock(VoyageAIClient.class);

        Institution institution = new Institution();
        institution.setId(institutionId);
        User contributor = new User();
        contributor.setId(contributorId);
        Submission submission = new Submission();
        submission.setId(submissionId);
        submission.setInstitution(institution);
        submission.setContributor(contributor);

        when(submissions.findById(submissionId)).thenReturn(Optional.of(submission));
        when(submissionMedia.findMediaAssetsBySubmissionId(submissionId)).thenReturn(List.of(attached));
        when(tags.findLabelsAndSourcesByMediaAssetIds(anyList())).thenReturn(List.of());
        when(voyage.embedQuery(anyString())).thenReturn("[0.1,0.2]");
        when(embeddings.findTopSimilarWithScore(
                institutionId, MediaAssetEmbeddingType.SEMANTIC, "[0.1,0.2]", 30))
                .thenReturn(List.<Object[]>of(new Object[]{candidateId.toString(), 0.82}));
        when(mediaAssets.findActiveByIds(List.of(candidateId))).thenReturn(List.of(candidate));

        AIRecommendationService service = new AIRecommendationService(
                submissions, submissionMedia, mediaAssets, embeddings, tags,
                mock(AiInteractionLogRepository.class), voyage, mock(MediaAlbumRepository.class),
                mock(SubmissionMediaContextRepository.class), false, false, true);
        MediaSuggestRequestDto dto = new MediaSuggestRequestDto();
        dto.setEventTitle("Campus innovation event");

        List<MediaSuggestResultDto> results = service.suggestMedia(
                submissionId,
                dto,
                new JwtUserDetails(contributorId, "contributor@test.edu", "contributor", institutionId));

        assertEquals(1, results.size());
        assertEquals(candidateId, results.getFirst().getId());
        verify(embeddings, never()).findTopSimilarToAssetsWithScore(
                eq(institutionId), eq(submissionId), eq(MediaAssetEmbeddingType.IMAGE), anyList(), eq(12), eq(30));
    }

    @Test
    void suggestMedia_hybridRanking_usesEventContextQualityAndSequenceSignals() {
        UUID institutionId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        UUID contributorId = UUID.randomUUID();
        MediaAsset attached = asset(UUID.randomUUID(), "selected-stage.jpg", "Event");
        attached.setObservedScenes(new String[]{"auditorium"});
        attached.setObservedActivities(new String[]{"presentation"});

        MediaAsset contextual = asset(UUID.randomUUID(), "coding-team.jpg", "Technology");
        contextual.setObservedScenes(new String[]{"computer laboratory"});
        contextual.setObservedActivities(new String[]{"coding"});
        contextual.setEquipmentSignals(new String[]{"laptops"});
        contextual.setCompositionSignals(new String[]{"wide group shot"});
        contextual.setVisualQualitySignals(new String[]{"sharp", "well lit"});
        MediaAsset generic = asset(UUID.randomUUID(), "generic-stage.jpg", "Event");
        generic.setObservedScenes(new String[]{"auditorium"});
        generic.setObservedActivities(new String[]{"presentation"});
        generic.setVisualQualitySignals(new String[]{"blurry"});

        SubmissionRepository submissions = mock(SubmissionRepository.class);
        SubmissionMediaAssetRepository submissionMedia = mock(SubmissionMediaAssetRepository.class);
        MediaAssetRepository mediaAssets = mock(MediaAssetRepository.class);
        MediaAssetEmbeddingRepository embeddings = mock(MediaAssetEmbeddingRepository.class);
        AssetTagRepository tags = mock(AssetTagRepository.class);
        SubmissionMediaContextRepository contexts = mock(SubmissionMediaContextRepository.class);

        when(submissions.findById(submissionId)).thenReturn(Optional.of(
                submission(submissionId, institutionId, contributorId)));
        when(submissionMedia.findMediaAssetsBySubmissionId(submissionId)).thenReturn(List.of(attached));
        when(tags.findLabelsAndSourcesByMediaAssetIds(anyList())).thenReturn(List.of());
        when(embeddings.findTopSimilarToAssetsWithScore(
                eq(institutionId), eq(submissionId), eq(MediaAssetEmbeddingType.IMAGE), anyList(), eq(12), eq(30)))
                .thenReturn(List.of(
                        new Object[]{generic.getId().toString(), 0.78},
                        new Object[]{contextual.getId().toString(), 0.75}));
        when(embeddings.findTopSimilarToAssetsWithScore(
                eq(institutionId), eq(submissionId), eq(MediaAssetEmbeddingType.SEMANTIC), anyList(), eq(12), eq(30)))
                .thenReturn(List.of());
        when(mediaAssets.findActiveByIds(anyList())).thenReturn(List.of(generic, contextual));

        SubmissionMediaContext context = new SubmissionMediaContext();
        context.setSubmissionId(submissionId);
        context.setInstitutionId(institutionId);
        context.setReadyAssetCount(1);
        context.setContextText("scenes: computer laboratory. activities: coding. equipment: laptops. event: hackathon.");
        when(contexts.findById(submissionId)).thenReturn(Optional.of(context));

        AIRecommendationService service = new AIRecommendationService(
                submissions, submissionMedia, mediaAssets, embeddings, tags,
                mock(AiInteractionLogRepository.class), mock(VoyageAIClient.class),
                mock(MediaAlbumRepository.class), contexts, true, true, true);

        List<MediaSuggestResultDto> results = service.suggestMedia(
                submissionId,
                new MediaSuggestRequestDto(),
                new JwtUserDetails(contributorId, "contributor@test.edu", "contributor", institutionId));

        assertEquals(contextual.getId(), results.getFirst().getId());
        assertEquals("hybrid-v1", results.getFirst().getRankingVersion());
        assertTrue(results.getFirst().getMatchReasons().stream()
                .anyMatch(reason -> reason.toLowerCase().contains("event context")));
        assertTrue(results.getFirst().getMatchReasons().stream()
                .anyMatch(reason -> reason.toLowerCase().contains("quality")));
        assertTrue(results.getFirst().getMatchReasons().stream()
                .anyMatch(reason -> reason.toLowerCase().contains("complementary")));
    }

    @Test
    void suggestMedia_hybridRanking_diversifiesNearDuplicateResults() {
        UUID institutionId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        UUID contributorId = UUID.randomUUID();
        MediaAsset attached = asset(UUID.randomUUID(), "selected.jpg", "Event");
        attached.setObservedScenes(new String[]{"stage"});

        MediaAsset best = asset(UUID.fromString("00000000-0000-0000-0000-000000000001"), "award-1.jpg", "Recognition");
        best.setContentHash("duplicate-hash");
        best.setObservedScenes(new String[]{"award ceremony"});
        MediaAsset duplicate = asset(UUID.fromString("00000000-0000-0000-0000-000000000002"), "award-2.jpg", "Recognition");
        duplicate.setContentHash("duplicate-hash");
        duplicate.setObservedScenes(new String[]{"award ceremony"});
        MediaAsset diverse = asset(UUID.fromString("00000000-0000-0000-0000-000000000003"), "outreach.jpg", "Community");
        diverse.setObservedScenes(new String[]{"outdoor community outreach"});

        SubmissionRepository submissions = mock(SubmissionRepository.class);
        SubmissionMediaAssetRepository submissionMedia = mock(SubmissionMediaAssetRepository.class);
        MediaAssetRepository mediaAssets = mock(MediaAssetRepository.class);
        MediaAssetEmbeddingRepository embeddings = mock(MediaAssetEmbeddingRepository.class);
        AssetTagRepository tags = mock(AssetTagRepository.class);

        when(submissions.findById(submissionId)).thenReturn(Optional.of(
                submission(submissionId, institutionId, contributorId)));
        when(submissionMedia.findMediaAssetsBySubmissionId(submissionId)).thenReturn(List.of(attached));
        when(tags.findLabelsAndSourcesByMediaAssetIds(anyList())).thenReturn(List.of());
        when(embeddings.findTopSimilarToAssetsWithScore(
                eq(institutionId), eq(submissionId), eq(MediaAssetEmbeddingType.IMAGE), anyList(), eq(12), eq(30)))
                .thenReturn(List.of(
                        new Object[]{best.getId().toString(), 0.95},
                        new Object[]{duplicate.getId().toString(), 0.94},
                        new Object[]{diverse.getId().toString(), 0.80}));
        when(embeddings.findTopSimilarToAssetsWithScore(
                eq(institutionId), eq(submissionId), eq(MediaAssetEmbeddingType.SEMANTIC), anyList(), eq(12), eq(30)))
                .thenReturn(List.of());
        when(mediaAssets.findActiveByIds(anyList())).thenReturn(List.of(best, duplicate, diverse));

        AIRecommendationService service = new AIRecommendationService(
                submissions, submissionMedia, mediaAssets, embeddings, tags,
                mock(AiInteractionLogRepository.class), mock(VoyageAIClient.class),
                mock(MediaAlbumRepository.class), mock(SubmissionMediaContextRepository.class),
                true, true, false);

        List<MediaSuggestResultDto> results = service.suggestMedia(
                submissionId,
                new MediaSuggestRequestDto(),
                new JwtUserDetails(contributorId, "contributor@test.edu", "contributor", institutionId));

        assertEquals(List.of(best.getId(), diverse.getId(), duplicate.getId()),
                results.stream().map(MediaSuggestResultDto::getId).toList());
        assertTrue(results.stream().allMatch(result -> "hybrid-v1".equals(result.getRankingVersion())));
    }

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
            MediaAssetEmbeddingRepository mediaAssetEmbeddingRepository) {

    }

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
                mediaAlbumRepository,
                mock(SubmissionMediaContextRepository.class),
                true,
                false,
                true
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

    private static Submission submission(UUID submissionId, UUID institutionId, UUID contributorId) {
        Institution institution = new Institution();
        institution.setId(institutionId);
        institution.setAiMediaHybridRankingEnabled(true);
        User contributor = new User();
        contributor.setId(contributorId);
        Submission submission = new Submission();
        submission.setId(submissionId);
        submission.setInstitution(institution);
        submission.setContributor(contributor);
        return submission;
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
