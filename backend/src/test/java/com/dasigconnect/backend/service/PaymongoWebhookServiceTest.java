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
import java.time.Instant;
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
        PaymongoWebhookReceipt receipt = receipt("evt_refund_1", "payment.refund.updated");
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

    @Test
    void accept_paidLink_settlesLatestPayment() {
        String payload = """
                {"data":{"id":"evt_paid_1","attributes":{"type":"link.payment.paid",
                "livemode":false,"data":{"id":"link_1","type":"link","attributes":{
                "amount":749900,"currency":"PHP","status":"paid","payments":[
                {"id":"pay_1","type":"payment","attributes":{"amount":749900,
                "currency":"PHP","status":"paid","paid_at":1792324800}}]}}}}}
                """;
        PaymongoWebhookReceipt receipt = receipt("evt_paid_1", "link.payment.paid");
        when(receiptRepository.insertIfAbsent(eq("evt_paid_1"), eq("link.payment.paid"),
                eq(false), anyString(), any())).thenReturn(1);
        when(receiptRepository.findByExternalEventId("evt_paid_1")).thenReturn(Optional.of(receipt));
        when(settlementService.settle(eq("link_1"), any(), eq("WEBHOOK")))
                .thenReturn(SubscriptionPaymentSettlementService.SettlementResult.SETTLED);

        assertThat(service.accept(payload.getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(PaymongoWebhookService.WebhookResult.PROCESSED);
        verify(settlementService).settle(eq("link_1"), argThat(payment ->
                payment.id().equals("pay_1")
                        && payment.amountCentavos() == 749900
                        && payment.currency().equals("PHP")
                        && payment.paidAt().equals(Instant.ofEpochSecond(1792324800))), eq("WEBHOOK"));
        assertThat(receipt.getStatus()).isEqualTo(PaymongoWebhookReceiptStatus.PROCESSED);
    }

    @Test
    void accept_liveEventInTestMode_isRejectedWithoutSettlement() {
        String payload = """
                {"data":{"id":"evt_live_1","attributes":{"type":"link.payment.paid",
                "livemode":true,"data":{}}}}
                """;
        PaymongoWebhookReceipt receipt = receipt("evt_live_1", "link.payment.paid");
        when(receiptRepository.insertIfAbsent(eq("evt_live_1"), eq("link.payment.paid"),
                eq(true), anyString(), any())).thenReturn(1);
        when(receiptRepository.findByExternalEventId("evt_live_1")).thenReturn(Optional.of(receipt));

        assertThat(service.accept(payload.getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(PaymongoWebhookService.WebhookResult.REJECTED);
        assertThat(receipt.getStatus()).isEqualTo(PaymongoWebhookReceiptStatus.REJECTED);
        assertThat(receipt.getErrorDetail()).contains("mode does not match");
        verifyNoInteractions(settlementService);
    }

    @Test
    void accept_unhandledEvent_isAcknowledgedAndIgnored() {
        String payload = """
                {"data":{"id":"evt_other_1","attributes":{"type":"payment.failed",
                "livemode":false,"data":{}}}}
                """;
        PaymongoWebhookReceipt receipt = receipt("evt_other_1", "payment.failed");
        when(receiptRepository.insertIfAbsent(eq("evt_other_1"), eq("payment.failed"),
                eq(false), anyString(), any())).thenReturn(1);
        when(receiptRepository.findByExternalEventId("evt_other_1")).thenReturn(Optional.of(receipt));

        assertThat(service.accept(payload.getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(PaymongoWebhookService.WebhookResult.IGNORED);
        assertThat(receipt.getStatus()).isEqualTo(PaymongoWebhookReceiptStatus.PROCESSED);
        verifyNoInteractions(settlementService, refundPersistenceService, eventPublisher);
    }

    private static PaymongoWebhookReceipt receipt(String eventId, String eventType) {
        PaymongoWebhookReceipt receipt = new PaymongoWebhookReceipt();
        receipt.setExternalEventId(eventId);
        receipt.setEventType(eventType);
        receipt.setStatus(PaymongoWebhookReceiptStatus.RECEIVED);
        return receipt;
    }
}
