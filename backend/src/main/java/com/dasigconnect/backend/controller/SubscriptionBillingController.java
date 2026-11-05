package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.model.dto.common.ApiResponse;
import com.dasigconnect.backend.model.dto.subscription.*;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/subscription/billing")
@PreAuthorize("hasRole('ADMIN')")
public class SubscriptionBillingController {
    private final SubscriptionBillingPersistenceService persistence;
    private final SubscriptionBillingService billingService;
    private final SubscriptionRefundService refundService;
    private final SubscriptionBillingReconciliationService reconciliationService;
    private final boolean enabled;

    public SubscriptionBillingController(
            SubscriptionBillingPersistenceService persistence,
            SubscriptionBillingService billingService,
            SubscriptionRefundService refundService,
            SubscriptionBillingReconciliationService reconciliationService,
            @Value("${app.subscription.enabled:false}") boolean enabled) {
        this.persistence = persistence;
        this.billingService = billingService;
        this.refundService = refundService;
        this.reconciliationService = reconciliationService;
        this.enabled = enabled;
    }

    private void checkEnabled() {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Subscription and billing features are currently disabled.");
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<SubscriptionBillingDto>> history(
            @AuthenticationPrincipal JwtUserDetails actor) {
        checkEnabled();
        SubscriptionBillingPersistenceService.requireOwner(actor);
        return ResponseEntity.ok(ApiResponse.success(persistence.history()));
    }

    @PostMapping("/payment-link")
    public ResponseEntity<ApiResponse<SubscriptionPaymentLinkDto>> createPaymentLink(
            @RequestBody @Valid CreatePaymentLinkRequestDto request,
            @AuthenticationPrincipal JwtUserDetails actor) {
        checkEnabled();
        return ResponseEntity.ok(ApiResponse.success(
                billingService.createOrReusePaymentLink(request.planId(), request.payerEmail(), actor)));
    }

    @PostMapping("/payments/{paymentId}/refund")
    public ResponseEntity<ApiResponse<SubscriptionRefundDto>> refund(
            @PathVariable UUID paymentId,
            @RequestBody @Valid CreateRefundRequestDto request,
            @AuthenticationPrincipal JwtUserDetails actor) {
        checkEnabled();
        return ResponseEntity.ok(ApiResponse.success(refundService.create(
                paymentId, request.amountCentavos(), request.reason(), request.notes(), actor)));
    }

    @PostMapping("/reconcile")
    public ResponseEntity<ApiResponse<SubscriptionBillingReconciliationService.ReconciliationResult>> reconcile(
            @AuthenticationPrincipal JwtUserDetails actor) {
        checkEnabled();
        SubscriptionBillingPersistenceService.requireOwner(actor);
        return ResponseEntity.ok(ApiResponse.success(reconciliationService.reconcile()));
    }
}
