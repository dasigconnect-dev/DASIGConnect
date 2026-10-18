package com.dasigconnect.backend.service;

import com.dasigconnect.backend.config.PaymongoProperties;
import com.dasigconnect.backend.external.PaymongoApiException;
import com.dasigconnect.backend.external.PaymongoClient;
import com.dasigconnect.backend.model.dto.subscription.SubscriptionPaymentLinkDto;
import com.dasigconnect.backend.model.entity.SubscriptionInvoice;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SubscriptionBillingService {
    private final PaymongoProperties properties;
    private final PaymongoClient paymongoClient;
    private final SubscriptionBillingPersistenceService persistence;
    private final AuditLogService auditLogService;
    private final EmailService emailService;

    public SubscriptionBillingService(
            PaymongoProperties properties,
            PaymongoClient paymongoClient,
            SubscriptionBillingPersistenceService persistence,
            AuditLogService auditLogService,
            EmailService emailService) {
        this.properties = properties;
        this.paymongoClient = paymongoClient;
        this.persistence = persistence;
        this.auditLogService = auditLogService;
        this.emailService = emailService;
    }

    public SubscriptionPaymentLinkDto createOrReusePaymentLink(String payerEmail, JwtUserDetails actor) {
        SubscriptionBillingPersistenceService.requireOwner(actor);
        if (!properties.enabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "PayMongo QRPh billing is not enabled.");
        }
        SubscriptionInvoice invoice = persistence.findOrCreateOpenInvoice(actor);
        SubscriptionPaymentLinkDto existing = persistence.activeLink(invoice.getId());
        if (existing != null) return existing;

        try {
            PaymongoClient.PaymentLink providerLink = paymongoClient.createPaymentLink(
                    invoice.getAmountCentavos(),
                    "DASIGConnect " + invoice.getInvoiceNumber(),
                    "Organization subscription invoice " + invoice.getInvoiceNumber());
            if (providerLink.amountCentavos() != invoice.getAmountCentavos()) {
                throw new PaymongoApiException("PayMongo returned an unexpected payment-link amount.");
            }
            SubscriptionPaymentLinkDto result = persistence.saveLink(invoice.getId(), providerLink);
            auditLogService.recordByActorId(
                    actor.userId(), "SUBSCRIPTION_PAYMENT_LINK_CREATED", null, null, invoice.getId(),
                    Map.of("invoiceNumber", invoice.getInvoiceNumber(), "liveMode", providerLink.liveMode()));
            sendPayerNotice(payerEmail, result);
            return result;
        } catch (PaymongoApiException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The QRPh payment link could not be created. Try again later.", error);
        }
    }

    private void sendPayerNotice(String payerEmail, SubscriptionPaymentLinkDto link) {
        if (payerEmail == null || payerEmail.isBlank()) return;
        try {
            emailService.sendPlainText(
                    payerEmail,
                    "DASIGConnect subscription payment " + link.invoiceNumber(),
                    "A QRPh payment link is ready for invoice " + link.invoiceNumber()
                            + ". Amount: PHP " + String.format("%,.2f", link.amountCentavos() / 100.0)
                            + "\n\nOpen the secure PayMongo page: " + link.checkoutUrl());
        } catch (Exception ignored) {
            // Link creation succeeds even when optional payer email delivery fails.
        }
    }
}
