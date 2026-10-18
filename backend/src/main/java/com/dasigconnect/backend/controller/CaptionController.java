package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.external.ClaudeVisionClient;
import com.dasigconnect.backend.model.dto.ai.CaptionLogRequestDto;
import com.dasigconnect.backend.model.dto.common.ApiResponse;
import com.dasigconnect.backend.model.dto.ai.CaptionRequestDto;
import com.dasigconnect.backend.model.dto.ai.CaptionResponseDto;
import com.dasigconnect.backend.model.entity.SubscriptionUsageEventType;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.CaptionGenerationService;
import com.dasigconnect.backend.service.SubscriptionEntitlementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/v1/ai")
public class CaptionController {

    private final CaptionGenerationService captionGenerationService;
    private final SubscriptionEntitlementService subscriptionEntitlementService;

    public CaptionController(
            CaptionGenerationService captionGenerationService,
            SubscriptionEntitlementService subscriptionEntitlementService) {
        this.captionGenerationService = captionGenerationService;
        this.subscriptionEntitlementService = subscriptionEntitlementService;
    }

    @PostMapping("/caption")
    @PreAuthorize("hasAnyRole('CONTRIBUTOR', 'MODERATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<CaptionResponseDto>> generateCaption(
            @RequestBody @Valid CaptionRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return generate(dto, user, idempotencyKey, CaptionGenerationService.ACTION_GENERATED_ON_REQUEST);
    }

    /**
     * RP-03 (REQ-320): the composer's automatic suggestion after the first image + title.
     * Same limits as {@code /caption}; logged as {@code auto_generated} so GO3 coverage can
     * be told apart from on-request generations.
     */
    @PostMapping("/caption/background")
    @PreAuthorize("hasAnyRole('CONTRIBUTOR', 'MODERATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<CaptionResponseDto>> generateBackgroundCaption(
            @RequestBody @Valid CaptionRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return generate(dto, user, idempotencyKey, CaptionGenerationService.ACTION_GENERATED_IN_BACKGROUND);
    }

    private ResponseEntity<ApiResponse<CaptionResponseDto>> generate(
            CaptionRequestDto dto, JwtUserDetails user, String idempotencyKey, String generationAction) {
        UUID userId = user.userId();
        SubscriptionEntitlementService.ActionReservation reservation =
                subscriptionEntitlementService.reserveForSubmission(
                        dto.getSubmissionId(), userId, SubscriptionUsageEventType.AI_CAPTION,
                        idempotencyKey, "AI_CAPTION_REQUEST");

        try {
            CaptionResponseDto response = captionGenerationService.generateCaptions(
                    dto.getSubmissionId(), userId, user.institutionId(),
                    dto.getExistingCaption(), dto.getPrompt(), dto.getTone(), generationAction);
            subscriptionEntitlementService.consume(reservation.reservationId());

            return ResponseEntity.ok()
                    .header("X-RateLimit-Remaining", String.valueOf(reservation.remaining()))
                    .header("X-RateLimit-Reset", String.valueOf(reservation.resetAt().getEpochSecond()))
                    .body(ApiResponse.success(response));
        } catch (ClaudeVisionClient.ClaudeApiException e) {
            subscriptionEntitlementService.release(reservation.reservationId());
            String msg = e.getMessage();
            if (msg != null && msg.contains("timed out")) {
                throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, msg);
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, msg);
        } catch (RuntimeException e) {
            subscriptionEntitlementService.release(reservation.reservationId());
            throw e;
        }
    }

    @PostMapping("/caption/log")
    @PreAuthorize("hasAnyRole('CONTRIBUTOR', 'MODERATOR', 'ADMIN')")
    public ResponseEntity<Void> logCaptionInteraction(
            @RequestBody @Valid CaptionLogRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {

        captionGenerationService.logInteraction(
                dto.getSubmissionId(),
                user.institutionId(),
                dto.getActionTaken(),
                dto.getToneSelected(),
                dto.getQualityRating(),
                dto.getDraftingDurationMs());
        return ResponseEntity.noContent().build();
    }

}
