package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.dasigconnect.backend.config.PaymongoProperties;
import com.dasigconnect.backend.external.PaymongoApiException;
import com.dasigconnect.backend.external.PaymongoClient;
import com.dasigconnect.backend.model.dto.subscription.SubscriptionPaymentLinkDto;
import com.dasigconnect.backend.model.entity.SubscriptionInvoice;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SubscriptionBillingServiceTest {

    @Mock PaymongoClient paymongoClient;
    @Mock SubscriptionBillingPersistenceService persistence;
    @Mock AuditLogService auditLogService;
    @Mock EmailService emailService;

    @Test
    void createOrReusePaymentLink_whenDisabled_returnsServiceUnavailable() {
        SubscriptionBillingService service = service(false);

        assertThatThrownBy(() -> service.createOrReusePaymentLink(null, owner()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verifyNoInteractions(persistence, paymongoClient);
    }

    @Test
    void createOrReusePaymentLink_reusesActiveLinkWithoutProviderCall() {
        SubscriptionInvoice invoice = invoice();
        SubscriptionPaymentLinkDto existing = linkDto(invoice);
        when(persistence.findOrCreateOpenInvoice(any())).thenReturn(invoice);
        when(persistence.activeLink(invoice.getId())).thenReturn(existing);

        assertThat(service(true).createOrReusePaymentLink(null, owner())).isSameAs(existing);
        verifyNoInteractions(paymongoClient);
    }

    @Test
    void createOrReusePaymentLink_createsPersistsAuditsAndEmails() {
        SubscriptionInvoice invoice = invoice();
        PaymongoClient.PaymentLink provider = new PaymongoClient.PaymentLink(
                "link_1", "https://paymongo.test/link_1", "REF-1",
                invoice.getAmountCentavos(), "unpaid", false, List.of());
        SubscriptionPaymentLinkDto saved = linkDto(invoice);
        JwtUserDetails owner = owner();
        when(persistence.findOrCreateOpenInvoice(owner)).thenReturn(invoice);
        when(persistence.activeLink(invoice.getId())).thenReturn(null);
        when(paymongoClient.createPaymentLink(anyLong(), anyString(), anyString())).thenReturn(provider);
        when(persistence.saveLink(invoice.getId(), provider)).thenReturn(saved);

        assertThat(service(true).createOrReusePaymentLink("payer@dasig.test", owner)).isSameAs(saved);

        verify(auditLogService).recordByActorId(eq(owner.userId()),
                eq("SUBSCRIPTION_PAYMENT_LINK_CREATED"), isNull(), isNull(), eq(invoice.getId()), anyMap());
        verify(emailService).sendPlainText(eq("payer@dasig.test"), anyString(), contains(saved.checkoutUrl()));
    }

    @Test
    void createOrReusePaymentLink_rejectsProviderAmountMismatch() {
        SubscriptionInvoice invoice = invoice();
        when(persistence.findOrCreateOpenInvoice(any())).thenReturn(invoice);
        when(persistence.activeLink(invoice.getId())).thenReturn(null);
        when(paymongoClient.createPaymentLink(anyLong(), anyString(), anyString())).thenReturn(
                new PaymongoClient.PaymentLink("link_1", "https://paymongo.test/link_1", null,
                        invoice.getAmountCentavos() - 1, "unpaid", false, List.of()));

        assertThatThrownBy(() -> service(true).createOrReusePaymentLink(null, owner()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
        verify(persistence, never()).saveLink(any(), any());
    }

    @Test
    void createOrReusePaymentLink_mapsProviderFailureToBadGateway() {
        SubscriptionInvoice invoice = invoice();
        when(persistence.findOrCreateOpenInvoice(any())).thenReturn(invoice);
        when(persistence.activeLink(invoice.getId())).thenReturn(null);
        when(paymongoClient.createPaymentLink(anyLong(), anyString(), anyString()))
                .thenThrow(new PaymongoApiException("timeout"));

        assertThatThrownBy(() -> service(true).createOrReusePaymentLink(null, owner()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    private SubscriptionBillingService service(boolean enabled) {
        PaymongoProperties properties = new PaymongoProperties(
                enabled, "TEST", "https://api.paymongo.com/v1",
                "sk_test_example", "", "whsk_test_example", "", 300);
        return new SubscriptionBillingService(
                properties, paymongoClient, persistence, auditLogService, emailService);
    }

    private static JwtUserDetails owner() {
        return new JwtUserDetails(UUID.randomUUID(), "owner@dasig.test", "admin", null, true);
    }

    private static SubscriptionInvoice invoice() {
        SubscriptionInvoice invoice = new SubscriptionInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setInvoiceNumber("DC-20261018-TEST");
        invoice.setAmountCentavos(749_900);
        invoice.setCurrency("PHP");
        invoice.setDueAt(Instant.parse("2026-10-25T00:00:00Z"));
        return invoice;
    }

    private static SubscriptionPaymentLinkDto linkDto(SubscriptionInvoice invoice) {
        return new SubscriptionPaymentLinkDto(
                invoice.getId(), invoice.getInvoiceNumber(), invoice.getAmountCentavos(), "PHP",
                "https://paymongo.test/link_1", "REF-1", "ACTIVE", false, invoice.getDueAt());
    }
}
