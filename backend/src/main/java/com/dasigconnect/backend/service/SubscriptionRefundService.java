package com.dasigconnect.backend.service;

import com.dasigconnect.backend.external.PaymongoApiException;
import com.dasigconnect.backend.external.PaymongoClient;
import com.dasigconnect.backend.model.dto.subscription.SubscriptionRefundDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SubscriptionRefundService {
    private final PaymongoClient paymongoClient;
    private final SubscriptionRefundPersistenceService persistence;
    private final AuditLogService auditLogService;

    public SubscriptionRefundService(
            PaymongoClient paymongoClient,
            SubscriptionRefundPersistenceService persistence,
            AuditLogService auditLogService) {
        this.paymongoClient = paymongoClient;
        this.persistence = persistence;
        this.auditLogService = auditLogService;
    }

    public SubscriptionRefundDto create(
            UUID paymentId,
            long amountCentavos,
            String reason,
            String notes,
            JwtUserDetails actor) {
        SubscriptionBillingPersistenceService.requireOwner(actor);
        SubscriptionRefundPersistenceService.RefundContext context = persistence.validate(paymentId, amountCentavos);
        try {
            PaymongoClient.Refund refund = paymongoClient.createRefund(
                    context.externalPaymentId(), context.amountCentavos(), reason, notes);
            if (refund.amountCentavos() != context.amountCentavos()) {
                throw new PaymongoApiException("PayMongo returned an unexpected refund amount.");
            }
            SubscriptionRefundDto result = persistence.save(context, refund, reason);
            auditLogService.recordByActorId(
                    actor.userId(), "SUBSCRIPTION_REFUND_REQUESTED", null, null, result.id(),
                    Map.of("paymentId", paymentId.toString(), "amountCentavos", amountCentavos,
                            "providerStatus", result.status()));
            return result;
        } catch (PaymongoApiException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The refund could not be submitted to PayMongo.", error);
        }
    }
}
