package com.dasigconnect.backend.service;

import com.dasigconnect.backend.external.PaymongoClient;
import com.dasigconnect.backend.model.dto.subscription.SubscriptionRefundDto;
import com.dasigconnect.backend.model.entity.*;
import com.dasigconnect.backend.repository.SubscriptionPaymentRepository;
import com.dasigconnect.backend.repository.SubscriptionRefundRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SubscriptionRefundPersistenceService {
    private final SubscriptionPaymentRepository paymentRepository;
    private final SubscriptionRefundRepository refundRepository;

    public SubscriptionRefundPersistenceService(
            SubscriptionPaymentRepository paymentRepository,
            SubscriptionRefundRepository refundRepository) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
    }

    @Transactional(readOnly = true)
    public RefundContext validate(UUID paymentId, long amountCentavos) {
        SubscriptionPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found."));
        long alreadyCommitted = refundRepository.sumCommittedRefunds(paymentId);
        long available = payment.getAmountCentavos() - alreadyCommitted;
        if (amountCentavos <= 0 || amountCentavos > available) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Refund amount exceeds the refundable payment balance.");
        }
        return new RefundContext(payment.getId(), payment.getExternalPaymentId(), amountCentavos);
    }

    @Transactional
    public SubscriptionRefundDto save(
            RefundContext context,
            PaymongoClient.Refund providerRefund,
            String reason) {
        SubscriptionPayment payment = paymentRepository.findById(context.paymentId()).orElseThrow();
        SubscriptionRefund refund = new SubscriptionRefund();
        refund.setPayment(payment);
        refund.setExternalRefundId(providerRefund.id());
        refund.setAmountCentavos(providerRefund.amountCentavos());
        refund.setReason(reason);
        refund.setStatus(mapStatus(providerRefund.status()));
        refund = refundRepository.saveAndFlush(refund);
        updatePaymentAndInvoice(payment);
        return new SubscriptionRefundDto(
                refund.getId(), payment.getId(), refund.getAmountCentavos(),
                refund.getStatus().name(), refund.getReason());
    }

    @Transactional
    public void synchronize(
            String externalRefundId,
            String externalPaymentId,
            long amountCentavos,
            String providerStatus) {
        SubscriptionRefund refund = refundRepository.findByExternalRefundId(externalRefundId)
                .orElseThrow(() -> new RefundValidationException("Unknown PayMongo refund."));
        SubscriptionPayment payment = refund.getPayment();
        if (externalPaymentId != null
                && !externalPaymentId.isBlank()
                && !externalPaymentId.equals(payment.getExternalPaymentId())) {
            throw new RefundValidationException("PayMongo refund payment does not match the recorded payment.");
        }
        if (amountCentavos > 0 && amountCentavos != refund.getAmountCentavos()) {
            throw new RefundValidationException("PayMongo refund amount does not match the requested amount.");
        }
        refund.setStatus(mapStatus(providerStatus));
        refundRepository.save(refund);
        updatePaymentAndInvoice(payment);
    }

    private void updatePaymentAndInvoice(SubscriptionPayment payment) {
        long refunded = refundRepository.sumCommittedRefunds(payment.getId());
        if (refunded >= payment.getAmountCentavos()) {
            payment.setStatus(SubscriptionPaymentStatus.REFUNDED);
            payment.getInvoice().setStatus(SubscriptionInvoiceStatus.REFUNDED);
        } else if (refunded > 0) {
            payment.setStatus(SubscriptionPaymentStatus.PARTIALLY_REFUNDED);
            payment.getInvoice().setStatus(SubscriptionInvoiceStatus.PARTIALLY_REFUNDED);
        } else {
            payment.setStatus(SubscriptionPaymentStatus.PAID);
            payment.getInvoice().setStatus(SubscriptionInvoiceStatus.PAID);
        }
        paymentRepository.save(payment);
    }

    private static SubscriptionRefundStatus mapStatus(String status) {
        if ("succeeded".equalsIgnoreCase(status) || "refunded".equalsIgnoreCase(status)) {
            return SubscriptionRefundStatus.SUCCEEDED;
        }
        if ("failed".equalsIgnoreCase(status)) return SubscriptionRefundStatus.FAILED;
        return SubscriptionRefundStatus.PENDING;
    }

    public record RefundContext(UUID paymentId, String externalPaymentId, long amountCentavos) {}

    public static class RefundValidationException extends RuntimeException {
        public RefundValidationException(String message) { super(message); }
    }
}
