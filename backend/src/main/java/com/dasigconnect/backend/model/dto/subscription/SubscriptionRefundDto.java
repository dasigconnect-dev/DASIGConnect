package com.dasigconnect.backend.model.dto.subscription;

import java.util.UUID;

public record SubscriptionRefundDto(
        UUID id,
        UUID paymentId,
        long amountCentavos,
        String status,
        String reason) {}
