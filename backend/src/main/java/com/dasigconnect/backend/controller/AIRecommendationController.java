package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.model.dto.ai.AiInteractionLogRequestDto;
import com.dasigconnect.backend.model.dto.ai.AlbumMatchRequestDto;
import com.dasigconnect.backend.model.dto.ai.AlbumMatchResponseDto;
import com.dasigconnect.backend.model.dto.ai.MediaSuggestRequestDto;
import com.dasigconnect.backend.model.dto.ai.MediaSuggestResultDto;
import com.dasigconnect.backend.model.dto.common.ApiResponse;
import com.dasigconnect.backend.model.dto.media.MediaAssetSummaryDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.AIRecommendationService;
import com.dasigconnect.backend.service.AiAdoptionTrackingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * UC-3.3: AI-driven category/tag suggestions and similar-media recommendations.
 * Base path: /api/v1/ai/submissions/{id}
 */
@RestController
@RequestMapping("/api/v1/ai/submissions")
public class AIRecommendationController {

    public static final String MEDIA_SUGGESTIONS_PROCESSING_HEADER = "X-Media-Suggestions-Processing";

    private final AIRecommendationService aiRecommendationService;
    private final AiAdoptionTrackingService adoptionTracking;

    public AIRecommendationController(AIRecommendationService aiRecommendationService,
                                      AiAdoptionTrackingService adoptionTracking) {
        this.aiRecommendationService = aiRecommendationService;
        this.adoptionTracking = adoptionTracking;
    }

    /** Returns up to 5 similar media assets from the library using pgvector cosine search. */
    @GetMapping("/{id}/similar-media")
    @PreAuthorize("hasAnyRole('CONTRIBUTOR', 'MODERATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<MediaAssetSummaryDto>>> getSimilarMedia(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(aiRecommendationService.getSimilarMedia(id, user)));
    }

    /**
     * Suggests up to 8 library assets based on text context (title + caption + tags)
     * via a synchronous Voyage AI embedding call. Returns ranked results with similarity scores.
     */
    @PostMapping("/{id}/suggest-media")
    @PreAuthorize("hasAnyRole('CONTRIBUTOR', 'MODERATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<MediaSuggestResultDto>>> suggestMedia(
            @PathVariable UUID id,
            @RequestBody @Valid MediaSuggestRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        AIRecommendationService.MediaSuggestionBatch batch =
                aiRecommendationService.suggestMediaBatch(id, dto, user);
        return ResponseEntity.ok()
                .header(MEDIA_SUGGESTIONS_PROCESSING_HEADER, Boolean.toString(batch.processing()))
                .body(ApiResponse.success(batch.results()));
    }

    /** Lightweight readiness check used by the composer's bounded background refresh. */
    @PostMapping("/{id}/suggest-media-status")
    @PreAuthorize("hasAnyRole('CONTRIBUTOR', 'MODERATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Boolean>> getSuggestMediaStatus(
            @PathVariable UUID id,
            @RequestBody @Valid MediaSuggestRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(
                aiRecommendationService.areSelectedImagesProcessing(id, dto, user)));
    }

    /**
     * Album Auto-Match (UC-1.7): ranks the institution's existing root albums against
     * the draft's current event title/caption/tags. See {@link AlbumMatchResponseDto}
     * for how the "confident"/"ambiguous"/"none" status maps to the composer's UI.
     */
    @PostMapping("/{id}/suggest-album")
    @PreAuthorize("hasAnyRole('CONTRIBUTOR', 'MODERATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<AlbumMatchResponseDto>> suggestAlbum(
            @PathVariable UUID id,
            @RequestBody AlbumMatchRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        AlbumMatchResponseDto result = aiRecommendationService.suggestAlbum(id, dto, user);
        if (result.getStatus() != AlbumMatchResponseDto.Status.none) {
            try {
                // AI Feature Adoption: the outcome (kept/changed) is judged on submit.
                adoptionTracking.recordAlbumSuggestion(id, user.institutionId(), result.getStatus().name(),
                        result.getCandidates().stream().map(candidate -> candidate.getAlbumName()).toList());
            } catch (RuntimeException ignored) {
                // Tracking must never fail the suggestion itself.
            }
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /** Records a user action (accepted/dismissed) for tag_classification or media_recommendation. */
    @PostMapping("/{id}/log-interaction")
    @PreAuthorize("hasAnyRole('CONTRIBUTOR', 'MODERATOR', 'ADMIN')")
    public ResponseEntity<Void> logInteraction(
            @PathVariable UUID id,
            @RequestBody @Valid AiInteractionLogRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        aiRecommendationService.logInteraction(
                id, user.institutionId(), dto.getType(), dto.getActionTaken());
        return ResponseEntity.noContent().build();
    }
}
