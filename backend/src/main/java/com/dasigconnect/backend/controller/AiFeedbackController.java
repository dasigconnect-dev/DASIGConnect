package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.model.dto.ai.AiFeedbackMetricsDto;
import com.dasigconnect.backend.model.dto.ai.SearchFeedbackRequestDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.AiFeedbackService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC-4.6 AI feedback loop. Records feedback on AI surfaces and reports precision metrics.
 * Base path: /api/v1/ai/feedback
 */
@RestController
@RequestMapping("/api/v1/ai/feedback")
public class AiFeedbackController {

    private final AiFeedbackService aiFeedbackService;

    public AiFeedbackController(AiFeedbackService aiFeedbackService) {
        this.aiFeedbackService = aiFeedbackService;
    }

    /** Record feedback (thumbs/selected/applied/dismissed) on a media-search result. Best-effort. */
    @PostMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> recordSearchFeedback(
            @Valid @RequestBody SearchFeedbackRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        aiFeedbackService.recordSearchFeedback(dto, user);
        return ResponseEntity.noContent().build();
    }

    /** Acceptance/thumbs precision metrics per AI surface (administrator = network, others = own). */
    @GetMapping("/metrics")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AiFeedbackMetricsDto> metrics(@AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(aiFeedbackService.getMetrics(user));
    }
}
