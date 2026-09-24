package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.external.ClaudeVisionClient;
import com.dasigconnect.backend.model.dto.ai.ProofreadAppliedRequestDto;
import com.dasigconnect.backend.model.dto.ai.ProofreadIssueDto;
import com.dasigconnect.backend.model.dto.ai.ProofreadRequestDto;
import com.dasigconnect.backend.model.dto.ai.ProofreadResponseDto;
import com.dasigconnect.backend.model.dto.common.ApiResponse;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.AiAdoptionTrackingService;
import com.dasigconnect.backend.service.ProofreadService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Advisory caption proofreading (UC-2.4 edit safeguards). Suggestions only —
 * nothing here changes a submission.
 */
@RestController
@RequestMapping("/api/v1/ai")
public class ProofreadController {

    /** Same budget as the caption tool: short text in, short JSON out. */
    private static final int RATE_LIMIT_PER_HOUR = 30;

    private final ProofreadService proofreadService;
    private final AiAdoptionTrackingService adoptionTracking;

    /** In-memory per-user sliding-window rate limiter (same approach as CaptionController). */
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<Instant>> userRequests = new ConcurrentHashMap<>();

    public ProofreadController(ProofreadService proofreadService, AiAdoptionTrackingService adoptionTracking) {
        this.proofreadService = proofreadService;
        this.adoptionTracking = adoptionTracking;
    }

    @PostMapping("/proofread")
    @PreAuthorize("hasAnyRole('CONTRIBUTOR', 'MODERATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<ProofreadResponseDto>> proofread(
            @RequestBody @Valid ProofreadRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        Long resetEpoch = claimRateLimitSlot(user.userId());
        if (resetEpoch != null) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("X-RateLimit-Remaining", "0")
                    .header("X-RateLimit-Reset", String.valueOf(resetEpoch))
                    .build();
        }
        try {
            List<ProofreadIssueDto> issues = proofreadService.proofread(dto.getText(), dto.getOriginalText());
            try {
                adoptionTracking.recordProofreadCheck(dto.getSubmissionId(), user.institutionId(), issues.size());
            } catch (RuntimeException ignored) {
                // Tracking must never fail the check itself.
            }
            return ResponseEntity.ok(ApiResponse.success(new ProofreadResponseDto(issues)));
        } catch (ClaudeVisionClient.ClaudeApiException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("timed out")) {
                throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, msg);
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, msg);
        }
    }

    /** AI Feature Adoption: the user applied one suggested fix. Fire-and-forget from the client. */
    @PostMapping("/proofread/applied")
    @PreAuthorize("hasAnyRole('CONTRIBUTOR', 'MODERATOR', 'ADMIN')")
    public ResponseEntity<Void> fixApplied(
            @RequestBody ProofreadAppliedRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        try {
            adoptionTracking.recordProofreadFixApplied(dto.submissionId(), user.institutionId());
        } catch (RuntimeException ignored) {
            // Best effort — a lost tracking row is not worth an error for the user.
        }
        return ResponseEntity.noContent().build();
    }

    /** Records a request and returns null, or returns the reset epoch-second when the user is over the limit. */
    private Long claimRateLimitSlot(UUID userId) {
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(3600);
        CopyOnWriteArrayList<Instant> timestamps = userRequests.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>());
        synchronized (timestamps) {
            timestamps.removeIf(t -> t.isBefore(windowStart));
            if (timestamps.size() >= RATE_LIMIT_PER_HOUR) {
                return timestamps.get(0).plusSeconds(3600).getEpochSecond();
            }
            timestamps.add(now);
        }
        return null;
    }
}
