package com.dasigconnect.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.model.dto.ai.AiInteractionLogRequestDto;
import com.dasigconnect.backend.model.dto.ai.MediaSuggestRequestDto;
import com.dasigconnect.backend.model.dto.ai.MediaSuggestResultDto;
import com.dasigconnect.backend.model.dto.common.ApiResponse;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.AIRecommendationService;
import com.dasigconnect.backend.service.AiAdoptionTrackingService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

class AIRecommendationControllerUnitTest {

    @Test
    void suggestMedia_preservesListBodyAndReportsProcessingHeader() {
        AIRecommendationService service = mock(AIRecommendationService.class);
        AIRecommendationController controller = new AIRecommendationController(
                service, mock(AiAdoptionTrackingService.class));
        UUID submissionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        JwtUserDetails user = new JwtUserDetails(
                userId, "contributor@test.edu", "contributor", institutionId);
        MediaSuggestRequestDto request = new MediaSuggestRequestDto();
        List<MediaSuggestResultDto> results = List.of();
        when(service.suggestMediaBatch(submissionId, request, user))
                .thenReturn(new AIRecommendationService.MediaSuggestionBatch(results, true));

        ResponseEntity<ApiResponse<List<MediaSuggestResultDto>>> response =
                controller.suggestMedia(submissionId, request, user);

        assertEquals("true", response.getHeaders().getFirst(
                AIRecommendationController.MEDIA_SUGGESTIONS_PROCESSING_HEADER));
        assertEquals("PROCESSING", response.getHeaders().getFirst(
                AIRecommendationController.MEDIA_SUGGESTIONS_OUTCOME_HEADER));
        assertEquals(results, response.getBody().data());

        when(service.areSelectedImagesProcessing(submissionId, request, user)).thenReturn(true);
        ResponseEntity<ApiResponse<Boolean>> statusResponse =
                controller.getSuggestMediaStatus(submissionId, request, user);
        assertEquals(true, statusResponse.getBody().data());
    }

    @Test
    void logInteraction_authorizesMatchingSubmissionThroughService() {
        AIRecommendationService service = mock(AIRecommendationService.class);
        AIRecommendationController controller = new AIRecommendationController(
                service, mock(AiAdoptionTrackingService.class));
        UUID submissionId = UUID.randomUUID();
        JwtUserDetails user = new JwtUserDetails(
                UUID.randomUUID(), "contributor@test.edu", "contributor", UUID.randomUUID());
        AiInteractionLogRequestDto request = interactionRequest(
                submissionId, "media_recommendation", "dismissed");

        ResponseEntity<Void> response = controller.logInteraction(submissionId, request, user);

        assertEquals(204, response.getStatusCode().value());
        verify(service).logInteraction(
                submissionId, "media_recommendation", "dismissed", user);
    }

    @Test
    void logInteraction_rejectsSubmissionIdMismatch() {
        AIRecommendationController controller = new AIRecommendationController(
                mock(AIRecommendationService.class), mock(AiAdoptionTrackingService.class));
        JwtUserDetails user = new JwtUserDetails(
                UUID.randomUUID(), "contributor@test.edu", "contributor", UUID.randomUUID());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.logInteraction(
                        UUID.randomUUID(),
                        interactionRequest(UUID.randomUUID(), "media_recommendation", "shown"),
                        user));

        assertEquals(400, error.getStatusCode().value());
    }

    private static AiInteractionLogRequestDto interactionRequest(
            UUID submissionId, String type, String action) {
        AiInteractionLogRequestDto request = new AiInteractionLogRequestDto();
        request.setSubmissionId(submissionId);
        request.setType(type);
        request.setActionTaken(action);
        return request;
    }
}
