package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.model.dto.common.ApiResponse;
import com.dasigconnect.backend.model.dto.subscription.SubscriptionPlanDto;
import com.dasigconnect.backend.model.dto.subscription.SubscriptionStatusDto;
import com.dasigconnect.backend.service.SubscriptionQueryService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subscription")
@PreAuthorize("hasRole('ADMIN')")
public class SubscriptionController {

    private final SubscriptionQueryService subscriptionQueryService;

    public SubscriptionController(SubscriptionQueryService subscriptionQueryService) {
        this.subscriptionQueryService = subscriptionQueryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<SubscriptionStatusDto>> currentSubscription() {
        return ResponseEntity.ok(ApiResponse.success(subscriptionQueryService.currentSubscription()));
    }

    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<SubscriptionPlanDto>>> activePlans() {
        return ResponseEntity.ok(ApiResponse.success(subscriptionQueryService.activePlans()));
    }
}
