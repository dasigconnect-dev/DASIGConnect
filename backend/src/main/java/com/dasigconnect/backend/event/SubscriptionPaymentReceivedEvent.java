package com.dasigconnect.backend.event;

import java.time.Instant;

public record SubscriptionPaymentReceivedEvent(
        String invoiceNumber,
        long amountCentavos,
        String currency,
        Instant paidAt) {}
