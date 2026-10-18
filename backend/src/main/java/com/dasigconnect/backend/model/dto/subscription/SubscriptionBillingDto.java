package com.dasigconnect.backend.model.dto.subscription;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SubscriptionBillingDto(
        boolean providerEnabled,
        String providerMode,
        List<Invoice> invoices) {

    public record Invoice(
            UUID id,
            String invoiceNumber,
            long amountCentavos,
            String currency,
            String status,
            Instant billingPeriodStart,
            Instant billingPeriodEnd,
            Instant dueAt,
            Instant paidAt,
            Instant createdAt,
            PaymentLink paymentLink,
            List<Payment> payments) {}

    public record PaymentLink(
            UUID id,
            String checkoutUrl,
            String providerReferenceNumber,
            String status,
            boolean liveMode,
            Instant createdAt) {}

    public record Payment(
            UUID id,
            String externalPaymentId,
            long amountCentavos,
            String currency,
            String status,
            Instant paidAt) {}
}
