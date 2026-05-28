package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.ai.MediaSuggestRequestDto;
import com.dasigconnect.backend.model.dto.ai.MediaSuggestResultDto;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.repository.AiInteractionLogRepository;
import com.dasigconnect.backend.repository.AssetTagRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
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
        when(voyageAIClient.embed(org.mockito.ArgumentMatchers.anyString())).thenReturn("[0.1,0.2]");
        when(mediaAssetRepository.findTopSimilarWithScore(institutionId, "[0.1,0.2]"))
                .thenReturn(List.<Object[]>of(new Object[]{attachedId.toString(), 0.92}));
        when(mediaAssetRepository.findActiveByIds(List.of(attachedId))).thenReturn(List.of(attached));
        when(assetTagRepository.findLabelsAndSourcesByMediaAssetIds(anyList())).thenReturn(List.of());
        when(mediaAssetRepository.findActiveByInstitution(institutionId)).thenReturn(List.of(attached, fallback));

        AIRecommendationService service = new AIRecommendationService(
                submissionRepository,
                submissionMediaAssetRepository,
                mediaAssetRepository,
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
