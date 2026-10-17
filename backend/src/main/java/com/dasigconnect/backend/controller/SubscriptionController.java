package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.model.dto.common.ApiResponse;
import com.dasigconnect.backend.model.dto.subscription.SubscriptionPlanDto;
import com.dasigconnect.backend.model.dto.subscription.InstitutionUsageDto;
import com.dasigconnect.backend.model.dto.subscription.SubscriptionReconciliationDto;
import com.dasigconnect.backend.model.dto.subscription.SubscriptionStatusDto;
import com.dasigconnect.backend.model.dto.subscription.SubscriptionUsageEventPageDto;
import com.dasigconnect.backend.model.dto.subscription.SubscriptionUsageSummaryDto;
import com.dasigconnect.backend.service.SubscriptionQueryService;
import com.dasigconnect.backend.service.SubscriptionUsageService;
import java.time.YearMonth;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subscription")
@PreAuthorize("hasRole('ADMIN')")
public class SubscriptionController {

    private final SubscriptionQueryService subscriptionQueryService;
    private final SubscriptionUsageService subscriptionUsageService;

    public SubscriptionController(
            SubscriptionQueryService subscriptionQueryService,
            SubscriptionUsageService subscriptionUsageService) {
        this.subscriptionQueryService = subscriptionQueryService;
        this.subscriptionUsageService = subscriptionUsageService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<SubscriptionStatusDto>> currentSubscription() {
        return ResponseEntity.ok(ApiResponse.success(subscriptionQueryService.currentSubscription()));
    }

    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<SubscriptionPlanDto>>> activePlans() {
        return ResponseEntity.ok(ApiResponse.success(subscriptionQueryService.activePlans()));
    }

    @GetMapping("/usage")
    public ResponseEntity<ApiResponse<SubscriptionUsageSummaryDto>> usage(
            @RequestParam(required = false) YearMonth month) {
        YearMonth resolvedMonth = month == null ? YearMonth.now(java.time.ZoneId.of("Asia/Manila")) : month;
        return ResponseEntity.ok(ApiResponse.success(subscriptionUsageService.monthlySummary(resolvedMonth)));
    }

    @GetMapping("/usage/institutions")
    public ResponseEntity<ApiResponse<List<InstitutionUsageDto>>> institutionUsage(
            @RequestParam(required = false) YearMonth month) {
        YearMonth resolvedMonth = month == null ? YearMonth.now(java.time.ZoneId.of("Asia/Manila")) : month;
        return ResponseEntity.ok(ApiResponse.success(subscriptionUsageService.institutionUsage(resolvedMonth)));
    }

    @GetMapping("/usage/events")
    public ResponseEntity<ApiResponse<SubscriptionUsageEventPageDto>> usageEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        return ResponseEntity.ok(ApiResponse.success(subscriptionUsageService.events(page, pageSize)));
    }

    @GetMapping("/usage/reconciliation")
    public ResponseEntity<ApiResponse<SubscriptionReconciliationDto>> reconciliation() {
        return ResponseEntity.ok(ApiResponse.success(subscriptionUsageService.reconciliation()));
    }
}
