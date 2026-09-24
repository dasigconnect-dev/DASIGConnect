package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.external.ClaudeVisionClient;
import com.dasigconnect.backend.model.dto.ai.ProofreadRequestDto;
import com.dasigconnect.backend.model.dto.ai.ProofreadResponseDto;
import com.dasigconnect.backend.model.dto.common.ApiResponse;
import com.dasigconnect.backend.security.JwtUserDetails;
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

    /** In-memory per-user sliding-window rate limiter (same approach as CaptionController). */
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<Instant>> userRequests = new ConcurrentHashMap<>();

    public ProofreadController(ProofreadService proofreadService) {
        this.proofreadService = proofreadService;
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
            return ResponseEntity.ok(ApiResponse.success(
                    new ProofreadResponseDto(proofreadService.proofread(dto.getText(), dto.getOriginalText()))));
        } catch (ClaudeVisionClient.ClaudeApiException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("timed out")) {
                throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, msg);
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, msg);
        }
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
