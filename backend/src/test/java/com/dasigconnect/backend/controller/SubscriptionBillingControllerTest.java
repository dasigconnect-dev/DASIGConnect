package com.dasigconnect.backend.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.model.dto.subscription.SubscriptionBillingDto;
import com.dasigconnect.backend.model.dto.subscription.SubscriptionPaymentLinkDto;
import com.dasigconnect.backend.model.dto.subscription.SubscriptionRefundDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SubscriptionBillingController.class)
@Import(SecurityConfig.class)
class SubscriptionBillingControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean SubscriptionBillingPersistenceService persistence;
    @MockitoBean SubscriptionBillingService billingService;
    @MockitoBean SubscriptionRefundService refundService;
    @MockitoBean SubscriptionBillingReconciliationService reconciliationService;
    @MockitoBean JWTService jwtService;
    @MockitoBean TenantScopeService tenantScopeService;

    @Test
    void history_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/subscription/billing"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void history_asModerator_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/subscription/billing"))
                .andExpect(status().isForbidden());
    }

    @Test
    void history_asAdminOwner_returnsBillingData() throws Exception {
        JwtUserDetails owner = owner();
        when(persistence.history()).thenReturn(new SubscriptionBillingDto(true, "TEST", List.of()));

        mockMvc.perform(get("/api/v1/subscription/billing").with(authentication(ownerAuthentication(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providerEnabled").value(true))
                .andExpect(jsonPath("$.data.providerMode").value("TEST"));
    }

    @Test
    void createPaymentLink_asAdminOwnerDelegatesValidatedRequest() throws Exception {
        JwtUserDetails owner = owner();
        SubscriptionPaymentLinkDto link = new SubscriptionPaymentLinkDto(
                UUID.randomUUID(), "DC-1", 749_900, "PHP", "https://paymongo.test/link_1",
                "REF-1", "ACTIVE", false, Instant.parse("2026-10-25T00:00:00Z"));
        UUID planId = UUID.randomUUID();
        when(billingService.createOrReusePaymentLink(planId, "payer@dasig.test", owner)).thenReturn(link);

        mockMvc.perform(post("/api/v1/subscription/billing/payment-link")
                        .with(authentication(ownerAuthentication(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":\"" + planId + "\",\"payerEmail\":\"payer@dasig.test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceNumber").value("DC-1"))
                .andExpect(jsonPath("$.data.liveMode").value(false));
    }

    @Test
    void createPaymentLink_withInvalidEmail_returnsBadRequest() throws Exception {
        UUID planId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/subscription/billing/payment-link")
                        .with(authentication(ownerAuthentication(owner())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":\"" + planId + "\",\"payerEmail\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPaymentLink_withoutPlanId_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/subscription/billing/payment-link")
                        .with(authentication(ownerAuthentication(owner())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payerEmail\":\"payer@dasig.test\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refund_asAdminOwnerReturnsProviderStatus() throws Exception {
        JwtUserDetails owner = owner();
        UUID paymentId = UUID.randomUUID();
        SubscriptionRefundDto refund = new SubscriptionRefundDto(
                UUID.randomUUID(), paymentId, 12_500, "PENDING", "requested_by_customer");
        when(refundService.create(eq(paymentId), eq(12_500L),
                eq("requested_by_customer"), eq("approved"), eq(owner))).thenReturn(refund);

        mockMvc.perform(post("/api/v1/subscription/billing/payments/{paymentId}/refund", paymentId)
                        .with(authentication(ownerAuthentication(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCentavos":12500,"reason":"requested_by_customer","notes":"approved"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void reconcile_asAdminOwnerReturnsCounts() throws Exception {
        JwtUserDetails owner = owner();
        when(reconciliationService.reconcile()).thenReturn(
                new SubscriptionBillingReconciliationService.ReconciliationResult(2, 1, 0));

        mockMvc.perform(post("/api/v1/subscription/billing/reconcile")
                        .with(authentication(ownerAuthentication(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checked").value(2))
                .andExpect(jsonPath("$.data.settled").value(1));
    }

    private static JwtUserDetails owner() {
        return new JwtUserDetails(UUID.randomUUID(), "owner@dasig.test", "admin", null, true);
    }

    private static UsernamePasswordAuthenticationToken ownerAuthentication(JwtUserDetails owner) {
        return new UsernamePasswordAuthenticationToken(
                owner, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
