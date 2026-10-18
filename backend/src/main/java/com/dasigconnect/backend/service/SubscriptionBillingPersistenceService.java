package com.dasigconnect.backend.service;

import com.dasigconnect.backend.config.PaymongoProperties;
import com.dasigconnect.backend.external.PaymongoClient;
import com.dasigconnect.backend.model.dto.subscription.SubscriptionBillingDto;
import com.dasigconnect.backend.model.dto.subscription.SubscriptionPaymentLinkDto;
import com.dasigconnect.backend.model.entity.*;
import com.dasigconnect.backend.repository.*;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SubscriptionBillingPersistenceService {
    private static final ZoneId BILLING_ZONE = ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter INVOICE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrganizationSubscriptionRepository subscriptionRepository;
    private final SubscriptionInvoiceRepository invoiceRepository;
    private final SubscriptionPaymentLinkRepository linkRepository;
    private final SubscriptionPaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final PaymongoProperties properties;

    public SubscriptionBillingPersistenceService(
            OrganizationSubscriptionRepository subscriptionRepository,
            SubscriptionInvoiceRepository invoiceRepository,
            SubscriptionPaymentLinkRepository linkRepository,
            SubscriptionPaymentRepository paymentRepository,
            UserRepository userRepository,
            PaymongoProperties properties) {
        this.subscriptionRepository = subscriptionRepository;
        this.invoiceRepository = invoiceRepository;
        this.linkRepository = linkRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.properties = properties;
    }

    @Transactional
    public SubscriptionInvoice findOrCreateOpenInvoice(JwtUserDetails actor) {
        requireOwner(actor);
        SubscriptionInvoice existing = invoiceRepository
                .findFirstByStatusOrderByCreatedAtDesc(SubscriptionInvoiceStatus.OPEN).orElse(null);
        if (existing != null) return existing;

        OrganizationSubscription subscription = subscriptionRepository.findFirstByOrderByCreatedAtAsc()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No organization subscription is configured."));
        if (subscription.getPlan().getMonthlyPriceCentavos() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "The current free plan does not require a payment link.");
        }
        Instant now = Instant.now();
        Instant periodStart = subscription.getCurrentPeriodEnd().isAfter(now)
                ? subscription.getCurrentPeriodEnd() : now;
        Instant periodEnd = periodStart.atZone(BILLING_ZONE).plusMonths(1).toInstant();
        User creator = userRepository.findById(actor.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found."));

        SubscriptionInvoice invoice = new SubscriptionInvoice();
        invoice.setSubscription(subscription);
        invoice.setInvoiceNumber("DC-" + INVOICE_DATE.format(now.atZone(BILLING_ZONE)) + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        invoice.setAmountCentavos(subscription.getPlan().getMonthlyPriceCentavos());
        invoice.setCurrency("PHP");
        invoice.setStatus(SubscriptionInvoiceStatus.OPEN);
        invoice.setBillingPeriodStart(periodStart);
        invoice.setBillingPeriodEnd(periodEnd);
        invoice.setDueAt(now.plusSeconds(7 * 86_400L));
        invoice.setCreatedBy(creator);
        return invoiceRepository.saveAndFlush(invoice);
    }

    @Transactional(readOnly = true)
    public SubscriptionPaymentLinkDto activeLink(UUID invoiceId) {
        return linkRepository.findFirstByInvoiceIdAndStatusOrderByCreatedAtDesc(
                        invoiceId, SubscriptionPaymentLinkStatus.ACTIVE)
                .map(this::toLinkDto)
                .orElse(null);
    }

    @Transactional
    public SubscriptionPaymentLinkDto saveLink(UUID invoiceId, PaymongoClient.PaymentLink providerLink) {
        SubscriptionInvoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));
        SubscriptionPaymentLink link = new SubscriptionPaymentLink();
        link.setInvoice(invoice);
        link.setProvider("PAYMONGO");
        link.setExternalLinkId(providerLink.id());
        link.setProviderReferenceNumber(providerLink.referenceNumber());
        link.setCheckoutUrl(providerLink.checkoutUrl());
        link.setStatus(SubscriptionPaymentLinkStatus.ACTIVE);
        link.setLiveMode(providerLink.liveMode());
        return toLinkDto(linkRepository.saveAndFlush(link));
    }

    @Transactional(readOnly = true)
    public SubscriptionBillingDto history() {
        List<SubscriptionBillingDto.Invoice> invoices = invoiceRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(0, 24)).stream()
                .map(this::toInvoiceDto)
                .toList();
        return new SubscriptionBillingDto(properties.enabled(), properties.mode().name(), invoices);
    }

    private SubscriptionBillingDto.Invoice toInvoiceDto(SubscriptionInvoice invoice) {
        SubscriptionBillingDto.PaymentLink link = linkRepository
                .findFirstByInvoiceIdOrderByCreatedAtDesc(invoice.getId())
                .map(value -> new SubscriptionBillingDto.PaymentLink(
                        value.getId(), value.getCheckoutUrl(), value.getProviderReferenceNumber(),
                        value.getStatus().name(), value.isLiveMode(), value.getCreatedAt()))
                .orElse(null);
        List<SubscriptionBillingDto.Payment> payments = paymentRepository
                .findByInvoiceIdOrderByPaidAtDesc(invoice.getId()).stream()
                .map(value -> new SubscriptionBillingDto.Payment(
                        value.getId(), value.getExternalPaymentId(), value.getAmountCentavos(),
                        value.getCurrency(), value.getStatus().name(), value.getPaidAt()))
                .toList();
        return new SubscriptionBillingDto.Invoice(
                invoice.getId(), invoice.getInvoiceNumber(), invoice.getAmountCentavos(), invoice.getCurrency(),
                invoice.getStatus().name(), invoice.getBillingPeriodStart(), invoice.getBillingPeriodEnd(),
                invoice.getDueAt(), invoice.getPaidAt(), invoice.getCreatedAt(), link, payments);
    }

    private SubscriptionPaymentLinkDto toLinkDto(SubscriptionPaymentLink link) {
        SubscriptionInvoice invoice = link.getInvoice();
        return new SubscriptionPaymentLinkDto(
                invoice.getId(), invoice.getInvoiceNumber(), invoice.getAmountCentavos(), invoice.getCurrency(),
                link.getCheckoutUrl(), link.getProviderReferenceNumber(), link.getStatus().name(),
                link.isLiveMode(), invoice.getDueAt());
    }

    public static void requireOwner(JwtUserDetails actor) {
        if (actor == null || !"admin".equalsIgnoreCase(actor.role()) || !actor.adminOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the Admin Owner can manage subscription billing.");
        }
    }
}
