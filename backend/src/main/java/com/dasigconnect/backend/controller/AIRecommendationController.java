package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.model.dto.ai.AiInteractionLogRequestDto;
import com.dasigconnect.backend.model.dto.ai.MediaSuggestRequestDto;
import com.dasigconnect.backend.model.dto.ai.MediaSuggestResultDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetSummaryDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.AIRecommendationService;
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

    private final AIRecommendationService aiRecommendationService;

    public AIRecommendationController(AIRecommendationService aiRecommendationService) {
        this.aiRecommendationService = aiRecommendationService;
    }

    /** Returns up to 5 similar media assets from the library using pgvector cosine search. */
    @GetMapping("/{id}/similar-media")
    @PreAuthorize("hasAnyRole('CONTRIBUTOR', 'ADMINISTRATOR')")
    public ResponseEntity<List<MediaAssetSummaryDto>> getSimilarMedia(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(aiRecommendationService.getSimilarMedia(id, user));
    }

    /**
     * Suggests up to 8 library assets based on text context (title + caption + tags)
     * via a synchronous Voyage AI embedding call. Returns ranked results with similarity scores.
     */
    @PostMapping("/{id}/suggest-media")
    @PreAuthorize("hasAnyRole('CONTRIBUTOR', 'ADMINISTRATOR')")
    public ResponseEntity<List<MediaSuggestResultDto>> suggestMedia(
            @PathVariable UUID id,
            @RequestBody MediaSuggestRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(aiRecommendationService.suggestMedia(id, dto, user));
    }

    /** Records a user action (accepted/dismissed) for tag_classification or media_recommendation. */
    @PostMapping("/{id}/log-interaction")
    @PreAuthorize("hasAnyRole('CONTRIBUTOR', 'ADMINISTRATOR')")
    public ResponseEntity<Void> logInteraction(
            @PathVariable UUID id,
            @RequestBody @Valid AiInteractionLogRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        aiRecommendationService.logInteraction(
                id, user.institutionId(), dto.getType(), dto.getActionTaken());
        return ResponseEntity.noContent().build();
    }
}
