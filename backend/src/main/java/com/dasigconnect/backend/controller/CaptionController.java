package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.external.ClaudeVisionClient;
import com.dasigconnect.backend.model.dto.ai.CaptionLogRequestDto;
import com.dasigconnect.backend.model.dto.ai.CaptionRequestDto;
import com.dasigconnect.backend.model.dto.ai.CaptionResponseDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.CaptionGenerationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api/v1/ai")
public class CaptionController {

    private static final int RATE_LIMIT_PER_HOUR = 30;

    private final CaptionGenerationService captionGenerationService;

    /** In-memory per-user sliding-window rate limiter. */
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<Instant>> userRequests =
            new ConcurrentHashMap<>();

    public CaptionController(CaptionGenerationService captionGenerationService) {
        this.captionGenerationService = captionGenerationService;
    }

    @PostMapping("/caption")
    @PreAuthorize("hasAnyRole('CONTRIBUTOR', 'ADMINISTRATOR')")
    public ResponseEntity<CaptionResponseDto> generateCaption(
            @RequestBody @Valid CaptionRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {

        UUID userId = user.userId();
        RateLimitResult limit = checkRateLimit(userId);

        if (!limit.allowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("X-RateLimit-Remaining", "0")
                    .header("X-RateLimit-Reset", String.valueOf(limit.resetEpochSeconds()))
                    .build();
        }

        try {
            CaptionResponseDto response = captionGenerationService.generateCaptions(
                    dto.getSubmissionId(), userId, user.institutionId(),
                    dto.getExistingCaption());

            int remaining = RATE_LIMIT_PER_HOUR - countRecentRequests(userId);
            return ResponseEntity.ok()
                    .header("X-RateLimit-Remaining", String.valueOf(Math.max(remaining, 0)))
                    .header("X-RateLimit-Reset", String.valueOf(limit.resetEpochSeconds()))
                    .body(response);
        } catch (ClaudeVisionClient.ClaudeApiException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("timed out")) {
                throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, msg);
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, msg);
        }
    }

    @PostMapping("/caption/log")
    @PreAuthorize("hasAnyRole('CONTRIBUTOR', 'ADMINISTRATOR')")
    public ResponseEntity<Void> logCaptionInteraction(
            @RequestBody @Valid CaptionLogRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {

        captionGenerationService.logInteraction(
                dto.getSubmissionId(),
                user.institutionId(),
                dto.getActionTaken(),
                dto.getToneSelected());
        return ResponseEntity.noContent().build();
    }

    // --- Rate limiter helpers ---

    private RateLimitResult checkRateLimit(UUID userId) {
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(3600);

        userRequests.putIfAbsent(userId, new CopyOnWriteArrayList<>());
        CopyOnWriteArrayList<Instant> timestamps = userRequests.get(userId);

        synchronized (timestamps) {
            timestamps.removeIf(t -> t.isBefore(windowStart));
            if (timestamps.size() >= RATE_LIMIT_PER_HOUR) {
                long resetEpoch = timestamps.get(0).plusSeconds(3600).getEpochSecond();
                return new RateLimitResult(false, resetEpoch);
            }
            timestamps.add(now);
        }
        long resetEpoch = now.plusSeconds(3600).getEpochSecond();
        return new RateLimitResult(true, resetEpoch);
    }

    private int countRecentRequests(UUID userId) {
        CopyOnWriteArrayList<Instant> timestamps = userRequests.get(userId);
        if (timestamps == null) return 0;
        Instant windowStart = Instant.now().minusSeconds(3600);
        synchronized (timestamps) {
            return (int) timestamps.stream().filter(t -> !t.isBefore(windowStart)).count();
        }
    }

    private record RateLimitResult(boolean allowed, long resetEpochSeconds) {}
}
