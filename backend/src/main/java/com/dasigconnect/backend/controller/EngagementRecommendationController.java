package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.model.dto.common.ApiResponse;
import com.dasigconnect.backend.model.dto.engagement.EngagementRecommendationDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.EngagementRecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/engagement-recommendations")
@PreAuthorize("hasAnyRole('CONTRIBUTOR', 'ADMINISTRATOR', 'SUPER_ADMINISTRATOR')")
public class EngagementRecommendationController {
    private final EngagementRecommendationService service;

    public EngagementRecommendationController(EngagementRecommendationService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<EngagementRecommendationDto>> recommendations(
            @RequestParam(required = false) java.util.UUID institutionId,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(service.recommend(user, institutionId)));
    }
}
