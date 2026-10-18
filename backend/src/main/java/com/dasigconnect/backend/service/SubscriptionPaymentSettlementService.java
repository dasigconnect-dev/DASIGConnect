package com.dasigconnect.backend.service;

import com.dasigconnect.backend.event.SubscriptionLifecycleChangedEvent;
import com.dasigconnect.backend.event.SubscriptionPaymentReceivedEvent;
import com.dasigconnect.backend.external.PaymongoClient;
import com.dasigconnect.backend.model.entity.*;
import com.dasigconnect.backend.repository.*;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionPaymentSettlementService {
    private final SubscriptionPaymentLinkRepository linkRepository;
    private final SubscriptionPaymentRepository paymentRepository;
    private final SubscriptionInvoiceRepository invoiceRepository;
    private final OrganizationSubscriptionRepository subscriptionRepository;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;

    public SubscriptionPaymentSettlementService(
            SubscriptionPaymentLinkRepository linkRepository,
            SubscriptionPaymentRepository paymentRepository,
            SubscriptionInvoiceRepository invoiceRepository,
            OrganizationSubscriptionRepository subscriptionRepository,
            AuditLogService auditLogService,
            ApplicationEventPublisher eventPublisher) {
        this.linkRepository = linkRepository;
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public SettlementResult settle(String externalLinkId, PaymongoClient.Payment providerPayment, String source) {
        SubscriptionPaymentLink link = linkRepository.findByExternalLinkIdForUpdate(externalLinkId)
                .orElseThrow(() -> new PaymentValidationException("Unknown PayMongo payment link."));
        if (paymentRepository.existsByExternalPaymentId(providerPayment.id())) {
            return SettlementResult.DUPLICATE;
        }
        SubscriptionInvoice invoice = link.getInvoice();
        validate(invoice, link, providerPayment);

        SubscriptionPayment payment = new SubscriptionPayment();
        payment.setInvoice(invoice);
        payment.setPaymentLink(link);
        payment.setExternalPaymentId(providerPayment.id());
        payment.setAmountCentavos(providerPayment.amountCentavos());
        payment.setCurrency(providerPayment.currency().toUpperCase());
        payment.setStatus(SubscriptionPaymentStatus.PAID);
        payment.setPaidAt(providerPayment.paidAt());
        paymentRepository.saveAndFlush(payment);

        invoice.setStatus(SubscriptionInvoiceStatus.PAID);
        invoice.setPaidAt(providerPayment.paidAt());
        invoiceRepository.save(invoice);
        link.setStatus(SubscriptionPaymentLinkStatus.PAID);
        linkRepository.save(link);

        OrganizationSubscription subscription = subscriptionRepository
                .findByOrganizationIdForUpdate(invoice.getSubscription().getOrganization().getId())
                .orElseThrow(() -> new PaymentValidationException("Organization subscription is unavailable."));
        SubscriptionLifecycleStatus previous = subscription.getStatus();
        Instant now = Instant.now();
        if (!subscription.getCurrentPeriodEnd().isAfter(now)) {
            subscription.setCurrentPeriodStart(invoice.getBillingPeriodStart());
        }
        if (invoice.getBillingPeriodEnd().isAfter(subscription.getCurrentPeriodEnd())) {
            subscription.setCurrentPeriodEnd(invoice.getBillingPeriodEnd());
        }
        subscription.setGracePeriodEndsAt(null);
        subscription.setStatus(SubscriptionLifecycleStatus.ACTIVE);
        subscription.setExternalProvider("PAYMONGO");
        subscriptionRepository.saveAndFlush(subscription);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("invoiceNumber", invoice.getInvoiceNumber());
        metadata.put("paymentId", providerPayment.id());
        metadata.put("amountCentavos", providerPayment.amountCentavos());
        metadata.put("currency", providerPayment.currency());
        metadata.put("source", source);
        auditLogService.recordByActorId(
                null, "SUBSCRIPTION_PAYMONGO_PAYMENT_CONFIRMED", null, null, invoice.getId(), metadata);

        if (previous != SubscriptionLifecycleStatus.ACTIVE) {
            eventPublisher.publishEvent(new SubscriptionLifecycleChangedEvent(
                    subscription.getId(), subscription.getOrganization().getName(), previous,
                    SubscriptionLifecycleStatus.ACTIVE, subscription.getCurrentPeriodEnd(), null));
        }
        eventPublisher.publishEvent(new SubscriptionPaymentReceivedEvent(
                invoice.getInvoiceNumber(), providerPayment.amountCentavos(),
                providerPayment.currency(), providerPayment.paidAt()));
        return SettlementResult.SETTLED;
    }

    private static void validate(
            SubscriptionInvoice invoice,
            SubscriptionPaymentLink link,
            PaymongoClient.Payment payment) {
        if (invoice.getStatus() != SubscriptionInvoiceStatus.OPEN) {
            throw new PaymentValidationException("Invoice is not open for payment.");
        }
        if (link.getStatus() != SubscriptionPaymentLinkStatus.ACTIVE) {
            throw new PaymentValidationException("Payment link is not active.");
        }
        if (payment.amountCentavos() != invoice.getAmountCentavos()) {
            throw new PaymentValidationException("Payment amount does not match the invoice.");
        }
        if (!invoice.getCurrency().equalsIgnoreCase(payment.currency())) {
            throw new PaymentValidationException("Payment currency does not match the invoice.");
        }
        if (!"paid".equalsIgnoreCase(payment.status())) {
            throw new PaymentValidationException("Provider payment is not marked paid.");
        }
    }

    public enum SettlementResult { SETTLED, DUPLICATE }
    public static class PaymentValidationException extends RuntimeException {
        public PaymentValidationException(String message) { super(message); }
    }
}
