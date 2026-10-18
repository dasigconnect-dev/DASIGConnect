package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.dasigconnect.backend.config.PaymongoProperties;
import com.dasigconnect.backend.model.entity.PaymongoWebhookReceipt;
import com.dasigconnect.backend.model.entity.PaymongoWebhookReceiptStatus;
import com.dasigconnect.backend.repository.PaymongoWebhookReceiptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PaymongoWebhookServiceTest {
    @Mock PaymongoWebhookReceiptRepository receiptRepository;
    @Mock SubscriptionPaymentSettlementService settlementService;
    @Mock SubscriptionRefundPersistenceService refundPersistenceService;
    @Mock ApplicationEventPublisher eventPublisher;
    PaymongoWebhookService service;

    @BeforeEach
    void setUp() {
        PaymongoProperties properties = new PaymongoProperties(
                true, "TEST", "https://api.paymongo.com/v1",
                "sk_test_example", "", "whsk_test_example", "", 300);
        service = new PaymongoWebhookService(
                new ObjectMapper(), properties, receiptRepository, settlementService,
                refundPersistenceService, eventPublisher);
    }

    @Test
    void accept_refundUpdated_synchronizesKnownRefund() {
        String payload = """
                {"data":{"id":"evt_refund_1","attributes":{"type":"payment.refund.updated",
                "livemode":false,"data":{"id":"ref_1","attributes":{"payment_id":"pay_1",
                "amount":12500,"status":"succeeded"}}}}}
                """;
        PaymongoWebhookReceipt receipt = receipt();
        when(receiptRepository.insertIfAbsent(eq("evt_refund_1"), eq("payment.refund.updated"),
                eq(false), anyString(), any())).thenReturn(1);
        when(receiptRepository.findByExternalEventId("evt_refund_1")).thenReturn(Optional.of(receipt));

        assertThat(service.accept(payload.getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(PaymongoWebhookService.WebhookResult.PROCESSED);
        verify(refundPersistenceService).synchronize("ref_1", "pay_1", 12500, "succeeded");
        assertThat(receipt.getStatus()).isEqualTo(PaymongoWebhookReceiptStatus.PROCESSED);
    }

    @Test
    void accept_duplicateEvent_doesNotRepeatSideEffects() {
        String payload = """
                {"data":{"id":"evt_duplicate","attributes":{"type":"link.payment.paid",
                "livemode":false,"data":{}}}}
                """;
        when(receiptRepository.insertIfAbsent(eq("evt_duplicate"), eq("link.payment.paid"),
                eq(false), anyString(), any())).thenReturn(0);

        assertThat(service.accept(payload.getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(PaymongoWebhookService.WebhookResult.DUPLICATE);
        verifyNoInteractions(settlementService, refundPersistenceService, eventPublisher);
    }

    private static PaymongoWebhookReceipt receipt() {
        PaymongoWebhookReceipt receipt = new PaymongoWebhookReceipt();
        receipt.setExternalEventId("evt_refund_1");
        receipt.setEventType("payment.refund.updated");
        receipt.setStatus(PaymongoWebhookReceiptStatus.RECEIVED);
        return receipt;
    }
}
