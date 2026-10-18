package com.dasigconnect.backend.model.dto.subscription;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionPaymentLinkDto(
        UUID invoiceId,
        String invoiceNumber,
        long amountCentavos,
        String currency,
        String checkoutUrl,
        String providerReferenceNumber,
        String status,
        boolean liveMode,
        Instant dueAt) {}
