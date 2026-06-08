package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.ai.MediaSuggestRequestDto;
import com.dasigconnect.backend.model.dto.ai.MediaSuggestResultDto;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetEmbeddingType;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.AiInteractionLogRepository;
import com.dasigconnect.backend.repository.AssetTagRepository;
import com.dasigconnect.backend.repository.MediaAssetEmbeddingRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.MediaAssetSearchHit;
import com.dasigconnect.backend.repository.MediaAssetSearchRepository;
import com.dasigconnect.backend.repository.SubmissionMediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.external.VoyageAIClient;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
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
        MediaAssetSearchRepository mediaAssetSearchRepository = mock(MediaAssetSearchRepository.class);
        AssetTagRepository assetTagRepository = mock(AssetTagRepository.class);
        AiInteractionLogRepository aiInteractionLogRepository = mock(AiInteractionLogRepository.class);
        VoyageAIClient voyageAIClient = mock(VoyageAIClient.class);

        Institution institution = new Institution();
        institution.setId(institutionId);
        Submission submission = new Submission();
        submission.setId(submissionId);
        submission.setInstitution(institution);

        MediaAsset attached = asset(attachedId, "cookie-selected.jpg", "Event");
        MediaAsset fallback = asset(fallbackId, "cookie-library.jpg", "Event");

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(submissionMediaAssetRepository.findMediaAssetsBySubmissionId(submissionId)).thenReturn(List.of(attached));
        when(voyageAIClient.embedQuery(org.mockito.ArgumentMatchers.anyString())).thenReturn("[0.1,0.2]");
        when(mediaAssetEmbeddingRepository.findTopSimilarWithScore(
                org.mockito.ArgumentMatchers.eq(institutionId),
                org.mockito.ArgumentMatchers.eq(MediaAssetEmbeddingType.SEMANTIC),
                org.mockito.ArgumentMatchers.eq("[0.1,0.2]"),
                org.mockito.ArgumentMatchers.eq(60)))
                .thenReturn(List.<Object[]>of(new Object[]{attachedId.toString(), 0.92}));
        when(mediaAssetRepository.findActiveByIds(List.of(attachedId))).thenReturn(List.of(attached));
        when(assetTagRepository.findLabelsAndSourcesByMediaAssetIds(anyList())).thenReturn(List.of());
        when(mediaAssetRepository.findReadyByInstitution(institutionId)).thenReturn(List.of(attached, fallback));

        AIRecommendationService service = new AIRecommendationService(
                submissionRepository,
                submissionMediaAssetRepository,
                mediaAssetRepository,
                mediaAssetEmbeddingRepository,
                mediaAssetSearchRepository,
                assetTagRepository,
                aiInteractionLogRepository,
                voyageAIClient
        );

        MediaSuggestRequestDto dto = new MediaSuggestRequestDto();
        dto.setEventTitle("Cookie so good");
        dto.setCaption("Passed Capstone Cutie should be good");
        dto.setCategory("Event");

        List<MediaSuggestResultDto> results = service.suggestMedia(
                submissionId,
                dto,
                new JwtUserDetails(UUID.randomUUID(), "contributor@test.edu", "contributor", institutionId)
        );

        assertEquals(1, results.size());
        assertEquals(fallbackId, results.getFirst().getId());
        assertTrue(results.getFirst().getMatchReasons().stream()
                .anyMatch(reason -> reason.toLowerCase().contains("category")));
    }

    @Test
    void suggestMedia_lexicalMetadataMatchCanBeatVagueSemanticMatch() {
        UUID institutionId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        UUID exactId = UUID.randomUUID();
        UUID vagueId = UUID.randomUUID();

        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        SubmissionMediaAssetRepository submissionMediaAssetRepository = mock(SubmissionMediaAssetRepository.class);
        MediaAssetRepository mediaAssetRepository = mock(MediaAssetRepository.class);
        MediaAssetEmbeddingRepository mediaAssetEmbeddingRepository = mock(MediaAssetEmbeddingRepository.class);
        MediaAssetSearchRepository mediaAssetSearchRepository = mock(MediaAssetSearchRepository.class);
        AssetTagRepository assetTagRepository = mock(AssetTagRepository.class);
        AiInteractionLogRepository aiInteractionLogRepository = mock(AiInteractionLogRepository.class);
        VoyageAIClient voyageAIClient = mock(VoyageAIClient.class);

        Institution institution = new Institution();
        institution.setId(institutionId);
        Submission submission = new Submission();
        submission.setId(submissionId);
        submission.setInstitution(institution);

        MediaAsset exact = asset(exactId, "robotics-bootcamp-winners.jpg", "Training");
        exact.setTitle("Regional Robotics Bootcamp Winners");
        exact.setAiDescription("Students presenting robotics prototypes with mentors.");
        exact.setSpecificSubjects(new String[] {"students", "robotics prototypes"});

        MediaAsset vague = asset(vagueId, "general-campus-event.jpg", "Event");
        vague.setTitle("Campus activity");

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(submissionMediaAssetRepository.findMediaAssetsBySubmissionId(submissionId)).thenReturn(List.of());
        when(voyageAIClient.embedQuery(org.mockito.ArgumentMatchers.anyString())).thenReturn("[0.1,0.2]");
        when(voyageAIClient.embedMultimodalTextQuery(org.mockito.ArgumentMatchers.anyString())).thenReturn("[0.3,0.4]");
        when(mediaAssetSearchRepository.searchLexical(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(institutionId),
                org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(60),
                org.mockito.ArgumentMatchers.eq(0)))
                .thenReturn(List.of(new MediaAssetSearchHit(exactId, 0.95, 1)));
        when(mediaAssetEmbeddingRepository.findTopSimilarWithScore(
                org.mockito.ArgumentMatchers.eq(institutionId),
                org.mockito.ArgumentMatchers.eq(MediaAssetEmbeddingType.SEMANTIC),
                org.mockito.ArgumentMatchers.eq("[0.1,0.2]"),
                org.mockito.ArgumentMatchers.eq(60)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{vagueId.toString(), 0.82},
                        new Object[]{exactId.toString(), 0.62}));
        when(mediaAssetEmbeddingRepository.findTopSimilarWithScore(
                org.mockito.ArgumentMatchers.eq(institutionId),
                org.mockito.ArgumentMatchers.eq(MediaAssetEmbeddingType.IMAGE),
                org.mockito.ArgumentMatchers.eq("[0.3,0.4]"),
                org.mockito.ArgumentMatchers.eq(60)))
                .thenReturn(List.of());
        when(mediaAssetRepository.findActiveByIds(anyList())).thenReturn(List.of(exact, vague));
        when(assetTagRepository.findLabelsAndSourcesByMediaAssetIds(anyList())).thenReturn(List.of());

        AIRecommendationService service = new AIRecommendationService(
                submissionRepository,
                submissionMediaAssetRepository,
                mediaAssetRepository,
                mediaAssetEmbeddingRepository,
                mediaAssetSearchRepository,
                assetTagRepository,
                aiInteractionLogRepository,
                voyageAIClient
        );

        MediaSuggestRequestDto dto = new MediaSuggestRequestDto();
        dto.setEventTitle("Regional robotics bootcamp");
        dto.setCaption("Students presented prototypes with DOST mentors.");
        dto.setCategory("Training");
        dto.setTags(List.of("students", "innovation"));

        List<MediaSuggestResultDto> results = service.suggestMedia(
                submissionId,
                dto,
                new JwtUserDetails(UUID.randomUUID(), "contributor@test.edu", "contributor", institutionId)
        );

        assertEquals(exactId, results.getFirst().getId());
        assertTrue(results.getFirst().getMatchReasons().stream()
                .anyMatch(reason -> reason.toLowerCase().contains("metadata")
                        || reason.toLowerCase().contains("title")));
    }

    @Test
    void getSimilarMedia_filtersCookiesAndMeetingsForSelectedSoccerPhoto() {
        UUID institutionId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        UUID selectedId = UUID.randomUUID();
        UUID soccerId = UUID.randomUUID();
        UUID cookieId = UUID.randomUUID();
        UUID meetingId = UUID.randomUUID();

        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        SubmissionMediaAssetRepository submissionMediaAssetRepository = mock(SubmissionMediaAssetRepository.class);
        MediaAssetRepository mediaAssetRepository = mock(MediaAssetRepository.class);
        MediaAssetEmbeddingRepository mediaAssetEmbeddingRepository = mock(MediaAssetEmbeddingRepository.class);
        MediaAssetSearchRepository mediaAssetSearchRepository = mock(MediaAssetSearchRepository.class);
        AssetTagRepository assetTagRepository = mock(AssetTagRepository.class);
        AiInteractionLogRepository aiInteractionLogRepository = mock(AiInteractionLogRepository.class);
        VoyageAIClient voyageAIClient = mock(VoyageAIClient.class);

        Institution institution = new Institution();
        institution.setId(institutionId);
        Submission submission = new Submission();
        submission.setId(submissionId);
        submission.setInstitution(institution);

        MediaAsset selected = asset(selectedId, "soccer-varsity-game.jpg", "Sports");
        selected.setTitle("Soccer varsity match");
        selected.setAiDescription("Soccer players kicking the ball on a sports field.");
        selected.setSpecificSubjects(new String[] {"soccer players", "football match"});
        selected.setVisibleObjects(new String[] {"soccer ball", "goal post"});

        MediaAsset soccer = asset(soccerId, "football-field-goal.jpg", "Sports");
        soccer.setTitle("Football goal attempt");
        soccer.setAiDescription("Soccer athletes playing on the field.");
        soccer.setVisibleObjects(new String[] {"soccer ball", "goal"});

        MediaAsset cookie = asset(cookieId, "cookie-sale.jpg", "Food");
        cookie.setTitle("Cookie booth sale");
        cookie.setAiDescription("Cookies displayed on a food table.");

        MediaAsset meeting = asset(meetingId, "staff-meeting.jpg", "Meeting");
        meeting.setTitle("Planning meeting");
        meeting.setAiDescription("A group of people seated around a meeting table.");

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(submissionMediaAssetRepository.findMediaAssetsBySubmissionId(submissionId)).thenReturn(List.of(selected));
        when(mediaAssetEmbeddingRepository.findEmbedding(selectedId, MediaAssetEmbeddingType.IMAGE))
                .thenReturn(Optional.of("[0.9,0.1]"));
        when(mediaAssetEmbeddingRepository.findTopSimilarWithScore(
                org.mockito.ArgumentMatchers.eq(institutionId),
                org.mockito.ArgumentMatchers.eq(MediaAssetEmbeddingType.IMAGE),
                org.mockito.ArgumentMatchers.eq("[0.9,0.1]"),
                org.mockito.ArgumentMatchers.eq(60)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{cookieId.toString(), 0.40},
                        new Object[]{meetingId.toString(), 0.38},
                        new Object[]{soccerId.toString(), 0.37}));
        when(mediaAssetSearchRepository.searchLexical(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(institutionId),
                org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(60),
                org.mockito.ArgumentMatchers.eq(0)))
                .thenReturn(List.of(new MediaAssetSearchHit(soccerId, 0.90, 1)));
        when(mediaAssetRepository.findActiveByIds(anyList())).thenReturn(List.of(cookie, meeting, soccer));
        when(assetTagRepository.findLabelsAndSourcesByMediaAssetIds(anyList())).thenReturn(List.of());

        AIRecommendationService service = new AIRecommendationService(
                submissionRepository,
                submissionMediaAssetRepository,
                mediaAssetRepository,
                mediaAssetEmbeddingRepository,
                mediaAssetSearchRepository,
                assetTagRepository,
                aiInteractionLogRepository,
                voyageAIClient
        );

        var results = service.getSimilarMedia(
                submissionId,
                new JwtUserDetails(UUID.randomUUID(), "contributor@test.edu", "contributor", institutionId)
        );

        assertEquals(1, results.size());
        assertEquals(soccerId, results.getFirst().getId());
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
        Institution institution = new Institution();
        institution.setId(UUID.randomUUID());
        institution.setName("CIT-U");
        User uploader = new User();
        uploader.setId(UUID.randomUUID());
        uploader.setEmail("contributor@test.edu");
        asset.setInstitution(institution);
        asset.setUploader(uploader);
        setCreatedAt(asset, Instant.now());
        return asset;
    }
}
