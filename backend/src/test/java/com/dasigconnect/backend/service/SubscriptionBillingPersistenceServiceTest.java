package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.config.PaymongoProperties;
import com.dasigconnect.backend.model.entity.SubscriptionInvoice;
import com.dasigconnect.backend.model.entity.SubscriptionInvoiceStatus;
import com.dasigconnect.backend.model.entity.SubscriptionPlan;
import com.dasigconnect.backend.repository.OrganizationSubscriptionRepository;
import com.dasigconnect.backend.repository.SubscriptionInvoiceRepository;
import com.dasigconnect.backend.repository.SubscriptionPaymentLinkRepository;
import com.dasigconnect.backend.repository.SubscriptionPaymentRepository;
import com.dasigconnect.backend.repository.SubscriptionPlanRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SubscriptionBillingPersistenceServiceTest {

    @Mock OrganizationSubscriptionRepository subscriptionRepository;
    @Mock SubscriptionPlanRepository planRepository;
    @Mock SubscriptionInvoiceRepository invoiceRepository;
    @Mock SubscriptionPaymentLinkRepository linkRepository;
    @Mock SubscriptionPaymentRepository paymentRepository;
    @Mock UserRepository userRepository;

    @Test
    void findOrCreateOpenInvoice_rejectsDifferentPlanWhilePaymentIsPending() {
        SubscriptionPlan requestedPlan = paidPlan(UUID.randomUUID(), 200);
        SubscriptionPlan pendingPlan = paidPlan(UUID.randomUUID(), 100);
        SubscriptionInvoice pendingInvoice = new SubscriptionInvoice();
        pendingInvoice.setPlan(pendingPlan);
        pendingInvoice.setAmountCentavos(100);
        pendingInvoice.setStatus(SubscriptionInvoiceStatus.OPEN);

        when(planRepository.findByIdAndActiveTrue(requestedPlan.getId()))
                .thenReturn(Optional.of(requestedPlan));
        when(invoiceRepository.findFirstByStatusOrderByCreatedAtDesc(SubscriptionInvoiceStatus.OPEN))
                .thenReturn(Optional.of(pendingInvoice));

        assertThatThrownBy(() -> service().findOrCreateOpenInvoice(requestedPlan.getId(), owner()))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.getReason()).contains("payment is already pending");
                });

        verify(invoiceRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    private SubscriptionBillingPersistenceService service() {
        PaymongoProperties properties = new PaymongoProperties(
                true, "TEST", "https://api.paymongo.com/v1",
                "sk_test_example", "", "whsk_test_example", "", 300);
        return new SubscriptionBillingPersistenceService(
                subscriptionRepository, planRepository, invoiceRepository, linkRepository,
                paymentRepository, userRepository, properties);
    }

    private static SubscriptionPlan paidPlan(UUID id, long amountCentavos) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(id);
        plan.setMonthlyPriceCentavos(amountCentavos);
        return plan;
    }

    private static JwtUserDetails owner() {
        return new JwtUserDetails(UUID.randomUUID(), "owner@dasig.test", "admin", null, true);
    }
}
