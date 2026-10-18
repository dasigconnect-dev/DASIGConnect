package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.dasigconnect.backend.config.PaymongoProperties;
import com.dasigconnect.backend.event.SubscriptionPaymentAttentionEvent;
import com.dasigconnect.backend.external.PaymongoClient;
import com.dasigconnect.backend.model.entity.SubscriptionInvoice;
import com.dasigconnect.backend.model.entity.SubscriptionPaymentLink;
import com.dasigconnect.backend.model.entity.SubscriptionPaymentLinkStatus;
import com.dasigconnect.backend.repository.SubscriptionPaymentLinkRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class SubscriptionBillingReconciliationServiceTest {

    @Mock PaymongoClient paymongoClient;
    @Mock SubscriptionPaymentLinkRepository linkRepository;
    @Mock SubscriptionPaymentSettlementService settlementService;
    @Mock ApplicationEventPublisher eventPublisher;

    @Test
    void reconcile_whenPaymongoDisabled_doesNothing() {
        var result = service(false).reconcile();

        assertThat(result).isEqualTo(
                new SubscriptionBillingReconciliationService.ReconciliationResult(0, 0, 0));
        verifyNoInteractions(paymongoClient, linkRepository, settlementService);
    }

    @Test
    void reconcile_settlesEachNewProviderPayment() {
        SubscriptionPaymentLink link = link(false, 749_900);
        PaymongoClient.Payment first = payment("pay_1");
        PaymongoClient.Payment duplicate = payment("pay_2");
        when(linkRepository.findByStatus(SubscriptionPaymentLinkStatus.ACTIVE)).thenReturn(List.of(link));
        when(paymongoClient.retrievePaymentLink("link_1")).thenReturn(new PaymongoClient.PaymentLink(
                "link_1", "https://paymongo.test/link_1", "REF-1", 749_900,
                "paid", false, List.of(first, duplicate)));
        when(settlementService.settle("link_1", first, "RECONCILIATION"))
                .thenReturn(SubscriptionPaymentSettlementService.SettlementResult.SETTLED);
        when(settlementService.settle("link_1", duplicate, "RECONCILIATION"))
                .thenReturn(SubscriptionPaymentSettlementService.SettlementResult.DUPLICATE);

        var result = service(true).reconcile();

        assertThat(result.checked()).isEqualTo(1);
        assertThat(result.settled()).isEqualTo(1);
        assertThat(result.failed()).isZero();
    }

    @Test
    void reconcile_rejectsMismatchedAmountAndPublishesAttention() {
        SubscriptionPaymentLink link = link(false, 749_900);
        when(linkRepository.findByStatus(SubscriptionPaymentLinkStatus.ACTIVE)).thenReturn(List.of(link));
        when(paymongoClient.retrievePaymentLink("link_1")).thenReturn(new PaymongoClient.PaymentLink(
                "link_1", "https://paymongo.test/link_1", "REF-1", 100,
                "paid", false, List.of(payment("pay_1"))));

        var result = service(true).reconcile();

        assertThat(result.failed()).isEqualTo(1);
        verifyNoInteractions(settlementService);
        verify(eventPublisher).publishEvent(any(SubscriptionPaymentAttentionEvent.class));
    }

    @Test
    void reconcile_archivedProviderLinkMarksLocalLinkArchived() {
        SubscriptionPaymentLink link = link(false, 749_900);
        when(linkRepository.findByStatus(SubscriptionPaymentLinkStatus.ACTIVE)).thenReturn(List.of(link));
        when(paymongoClient.retrievePaymentLink("link_1")).thenReturn(new PaymongoClient.PaymentLink(
                "link_1", "https://paymongo.test/link_1", "REF-1", 749_900,
                "archived", false, List.of()));
        when(linkRepository.findById(link.getId())).thenReturn(Optional.of(link));

        var result = service(true).reconcile();

        assertThat(result.failed()).isZero();
        assertThat(link.getStatus()).isEqualTo(SubscriptionPaymentLinkStatus.ARCHIVED);
        assertThat(link.getArchivedAt()).isNotNull();
        verify(linkRepository).save(link);
    }

    private SubscriptionBillingReconciliationService service(boolean enabled) {
        PaymongoProperties properties = new PaymongoProperties(
                enabled, "TEST", "https://api.paymongo.com/v1",
                "sk_test_example", "", "whsk_test_example", "", 300);
        return new SubscriptionBillingReconciliationService(
                properties, paymongoClient, linkRepository, settlementService, eventPublisher);
    }

    private static SubscriptionPaymentLink link(boolean liveMode, long amountCentavos) {
        SubscriptionInvoice invoice = new SubscriptionInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setAmountCentavos(amountCentavos);
        SubscriptionPaymentLink link = new SubscriptionPaymentLink();
        link.setId(UUID.randomUUID());
        link.setInvoice(invoice);
        link.setExternalLinkId("link_1");
        link.setStatus(SubscriptionPaymentLinkStatus.ACTIVE);
        link.setLiveMode(liveMode);
        return link;
    }

    private static PaymongoClient.Payment payment(String id) {
        return new PaymongoClient.Payment(id, 749_900, "PHP", "paid", Instant.now());
    }
}
