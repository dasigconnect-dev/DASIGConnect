package com.dasigconnect.backend.service;

import com.dasigconnect.backend.config.PaymongoProperties;
import com.dasigconnect.backend.event.SubscriptionPaymentAttentionEvent;
import com.dasigconnect.backend.external.PaymongoClient;
import com.dasigconnect.backend.model.entity.PaymongoWebhookReceipt;
import com.dasigconnect.backend.model.entity.PaymongoWebhookReceiptStatus;
import com.dasigconnect.backend.repository.PaymongoWebhookReceiptRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class PaymongoWebhookService {
    private final ObjectMapper objectMapper;
    private final PaymongoProperties properties;
    private final PaymongoWebhookReceiptRepository receiptRepository;
    private final SubscriptionPaymentSettlementService settlementService;
    private final SubscriptionRefundPersistenceService refundPersistenceService;
    private final ApplicationEventPublisher eventPublisher;

    public PaymongoWebhookService(
            ObjectMapper objectMapper,
            PaymongoProperties properties,
            PaymongoWebhookReceiptRepository receiptRepository,
            SubscriptionPaymentSettlementService settlementService,
            SubscriptionRefundPersistenceService refundPersistenceService,
            ApplicationEventPublisher eventPublisher) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.receiptRepository = receiptRepository;
        this.settlementService = settlementService;
        this.refundPersistenceService = refundPersistenceService;
        this.eventPublisher = eventPublisher;
    }

    public WebhookResult accept(byte[] payload) {
        try {
            JsonNode event = objectMapper.readTree(payload).path("data");
            String eventId = required(event, "id");
            JsonNode attributes = event.path("attributes");
            String eventType = required(attributes, "type");
            boolean liveMode = attributes.path("livemode").asBoolean(false);
            int inserted = receiptRepository.insertIfAbsent(
                    eventId, eventType, liveMode, sha256(payload), Instant.now());
            if (inserted == 0) return WebhookResult.DUPLICATE;
            PaymongoWebhookReceipt receipt = receiptRepository.findByExternalEventId(eventId).orElseThrow();

            if (liveMode != properties.liveMode()) {
                reject(receipt, "Webhook mode does not match the configured PayMongo mode.");
                return WebhookResult.REJECTED;
            }
            if ("link.payment.paid".equals(eventType)) {
                processPaidLink(attributes.path("data"));
            } else if ("payment.refund.updated".equals(eventType)) {
                processRefund(attributes.path("data"));
            } else if ("payment.refunded".equals(eventType)) {
                processRefundedPayment(attributes.path("data"));
            } else {
                receipt.setStatus(PaymongoWebhookReceiptStatus.PROCESSED);
                receipt.setProcessedAt(Instant.now());
                receiptRepository.save(receipt);
                return WebhookResult.IGNORED;
            }

            receipt.setStatus(PaymongoWebhookReceiptStatus.PROCESSED);
            receipt.setProcessedAt(Instant.now());
            receiptRepository.save(receipt);
            return WebhookResult.PROCESSED;
        } catch (SubscriptionPaymentSettlementService.PaymentValidationException
                | SubscriptionRefundPersistenceService.RefundValidationException error) {
            markLatestFailure(payload, PaymongoWebhookReceiptStatus.REJECTED, error.getMessage());
            eventPublisher.publishEvent(new SubscriptionPaymentAttentionEvent(error.getMessage()));
            return WebhookResult.REJECTED;
        } catch (Exception error) {
            markLatestFailure(payload, PaymongoWebhookReceiptStatus.FAILED, summarize(error));
            eventPublisher.publishEvent(new SubscriptionPaymentAttentionEvent(
                    "A PayMongo webhook could not be processed and requires reconciliation."));
            return WebhookResult.FAILED;
        }
    }

    private void processPaidLink(JsonNode link) {
        String externalLinkId = required(link, "id");
        JsonNode linkAttributes = link.path("attributes");
        JsonNode payment = latestPayment(linkAttributes.path("payments"));
        JsonNode paymentAttributes = payment.path("attributes").isMissingNode()
                ? payment : payment.path("attributes");
        PaymongoClient.Payment providerPayment = new PaymongoClient.Payment(
                required(payment, "id"),
                paymentAttributes.path("amount").asLong(),
                paymentAttributes.path("currency").asText("PHP"),
                paymentAttributes.path("status").asText("paid"),
                providerInstant(paymentAttributes));
        settlementService.settle(externalLinkId, providerPayment, "WEBHOOK");
    }

    private void processRefund(JsonNode refund) {
        JsonNode attributes = refund.path("attributes");
        refundPersistenceService.synchronize(
                required(refund, "id"),
                attributes.path("payment_id").asText(null),
                attributes.path("amount").asLong(),
                required(attributes, "status"));
    }

    private void processRefundedPayment(JsonNode payment) {
        JsonNode attributes = payment.path("attributes");
        String externalPaymentId = required(payment, "id");
        JsonNode refunds = attributes.path("refunds");
        if (!refunds.isArray() || refunds.isEmpty()) {
            throw new SubscriptionRefundPersistenceService.RefundValidationException(
                    "Refunded payment event did not contain refund records.");
        }
        for (JsonNode refund : refunds) {
            JsonNode refundAttributes = refund.path("attributes").isMissingNode()
                    ? refund : refund.path("attributes");
            refundPersistenceService.synchronize(
                    required(refund, "id"),
                    refundAttributes.path("payment_id").asText(externalPaymentId),
                    refundAttributes.path("amount").asLong(),
                    refundAttributes.path("status").asText("succeeded"));
        }
    }

    private void markLatestFailure(byte[] payload, PaymongoWebhookReceiptStatus status, String message) {
        try {
            String eventId = objectMapper.readTree(payload).path("data").path("id").asText("");
            if (eventId.isBlank()) return;
            receiptRepository.findByExternalEventId(eventId).ifPresent(receipt -> {
                receipt.setStatus(status);
                receipt.setProcessedAt(Instant.now());
                receipt.setErrorDetail(truncate(message));
                receiptRepository.save(receipt);
            });
        } catch (Exception ignored) {
            // Malformed payloads have no reliable provider event identifier to persist.
        }
    }

    private static JsonNode latestPayment(JsonNode payments) {
        if (!payments.isArray() || payments.isEmpty()) {
            throw new SubscriptionPaymentSettlementService.PaymentValidationException(
                    "Paid link event did not contain a payment record.");
        }
        return payments.get(payments.size() - 1);
    }
    private static Instant providerInstant(JsonNode attributes) {
        long epoch = attributes.path("paid_at").asLong(attributes.path("created_at").asLong(0));
        return epoch > 0 ? Instant.ofEpochSecond(epoch) : Instant.now();
    }
    private static String required(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) throw new IllegalArgumentException("Webhook is missing " + field + ".");
        return value;
    }
    private static String sha256(byte[] payload) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
    }
    private static String summarize(Exception error) {
        return error.getClass().getSimpleName() + (error.getMessage() == null ? "" : ": " + error.getMessage());
    }
    private static String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
    private void reject(PaymongoWebhookReceipt receipt, String message) {
        receipt.setStatus(PaymongoWebhookReceiptStatus.REJECTED);
        receipt.setProcessedAt(Instant.now());
        receipt.setErrorDetail(message);
        receiptRepository.save(receipt);
    }

    public enum WebhookResult { PROCESSED, DUPLICATE, IGNORED, REJECTED, FAILED }
}
