package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.dasigconnect.backend.event.SubscriptionPaymentReceivedEvent;
import com.dasigconnect.backend.external.PaymongoClient;
import com.dasigconnect.backend.model.entity.*;
import com.dasigconnect.backend.repository.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class SubscriptionPaymentSettlementServiceTest {
    @Mock SubscriptionPaymentLinkRepository linkRepository;
    @Mock SubscriptionPaymentRepository paymentRepository;
    @Mock SubscriptionInvoiceRepository invoiceRepository;
    @Mock OrganizationSubscriptionRepository subscriptionRepository;
    @Mock AuditLogService auditLogService;
    @Mock ApplicationEventPublisher eventPublisher;
    SubscriptionPaymentSettlementService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionPaymentSettlementService(
                linkRepository, paymentRepository, invoiceRepository, subscriptionRepository,
                auditLogService, eventPublisher);
    }

    @Test
    void settle_paidMatchingPayment_renewsAndRecordsPayment() {
        Fixture fixture = fixture();
        PaymongoClient.Payment payment = new PaymongoClient.Payment(
                "pay_1", 749900, "PHP", "paid", Instant.parse("2026-10-18T04:00:00Z"));
        when(paymentRepository.existsByExternalPaymentId("pay_1")).thenReturn(false);
        when(linkRepository.findByExternalLinkIdForUpdate("link_1")).thenReturn(Optional.of(fixture.link));
        when(subscriptionRepository.findByOrganizationIdForUpdate(fixture.organization.getId()))
                .thenReturn(Optional.of(fixture.subscription));
        when(paymentRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.settle("link_1", payment, "WEBHOOK"))
                .isEqualTo(SubscriptionPaymentSettlementService.SettlementResult.SETTLED);
        assertThat(fixture.invoice.getStatus()).isEqualTo(SubscriptionInvoiceStatus.PAID);
        assertThat(fixture.link.getStatus()).isEqualTo(SubscriptionPaymentLinkStatus.PAID);
        assertThat(fixture.subscription.getStatus()).isEqualTo(SubscriptionLifecycleStatus.ACTIVE);
        assertThat(fixture.subscription.getCurrentPeriodEnd()).isEqualTo(fixture.invoice.getBillingPeriodEnd());
        verify(eventPublisher).publishEvent(any(SubscriptionPaymentReceivedEvent.class));
        verify(auditLogService).recordByActorId(
                isNull(), eq("SUBSCRIPTION_PAYMONGO_PAYMENT_CONFIRMED"), isNull(), isNull(),
                eq(fixture.invoice.getId()), anyMap());
    }

    @Test
    void settle_wrongAmount_rejectsWithoutSaving() {
        Fixture fixture = fixture();
        when(paymentRepository.existsByExternalPaymentId("pay_bad")).thenReturn(false);
        when(linkRepository.findByExternalLinkIdForUpdate("link_1")).thenReturn(Optional.of(fixture.link));

        assertThatThrownBy(() -> service.settle("link_1", new PaymongoClient.Payment(
                "pay_bad", 100, "PHP", "paid", Instant.now()), "WEBHOOK"))
                .isInstanceOf(SubscriptionPaymentSettlementService.PaymentValidationException.class)
                .hasMessageContaining("amount");
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void settle_duplicatePayment_isIdempotent() {
        Fixture fixture = fixture();
        when(linkRepository.findByExternalLinkIdForUpdate("link_1")).thenReturn(Optional.of(fixture.link));
        when(paymentRepository.existsByExternalPaymentId("pay_1")).thenReturn(true);

        assertThat(service.settle("link_1", new PaymongoClient.Payment(
                "pay_1", 749900, "PHP", "paid", Instant.now()), "WEBHOOK"))
                .isEqualTo(SubscriptionPaymentSettlementService.SettlementResult.DUPLICATE);
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    private static Fixture fixture() {
        Organization organization = new Organization();
        organization.setId(UUID.randomUUID());
        organization.setName("DASIG Central Visayas");
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(UUID.randomUUID());
        plan.setMonthlyPriceCentavos(749900);
        OrganizationSubscription subscription = new OrganizationSubscription();
        subscription.setId(UUID.randomUUID());
        subscription.setOrganization(organization);
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionLifecycleStatus.PAST_DUE);
        subscription.setCurrentPeriodStart(Instant.parse("2026-09-01T00:00:00Z"));
        subscription.setCurrentPeriodEnd(Instant.parse("2026-10-01T00:00:00Z"));
        SubscriptionInvoice invoice = new SubscriptionInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setSubscription(subscription);
        invoice.setInvoiceNumber("DC-20261018-TEST");
        invoice.setAmountCentavos(749900);
        invoice.setCurrency("PHP");
        invoice.setStatus(SubscriptionInvoiceStatus.OPEN);
        invoice.setBillingPeriodStart(Instant.parse("2026-10-18T00:00:00Z"));
        invoice.setBillingPeriodEnd(Instant.parse("2026-11-18T00:00:00Z"));
        SubscriptionPaymentLink link = new SubscriptionPaymentLink();
        link.setId(UUID.randomUUID());
        link.setInvoice(invoice);
        link.setExternalLinkId("link_1");
        link.setStatus(SubscriptionPaymentLinkStatus.ACTIVE);
        return new Fixture(organization, subscription, invoice, link);
    }

    private record Fixture(
            Organization organization,
            OrganizationSubscription subscription,
            SubscriptionInvoice invoice,
            SubscriptionPaymentLink link) {}
}
