package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.dasigconnect.backend.external.PaymongoApiException;
import com.dasigconnect.backend.external.PaymongoClient;
import com.dasigconnect.backend.model.dto.subscription.SubscriptionRefundDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SubscriptionRefundServiceTest {

    @Mock PaymongoClient paymongoClient;
    @Mock SubscriptionRefundPersistenceService persistence;
    @Mock AuditLogService auditLogService;

    @Test
    void create_submitsPersistsAndAuditsRefund() {
        UUID paymentId = UUID.randomUUID();
        JwtUserDetails owner = owner();
        var context = new SubscriptionRefundPersistenceService.RefundContext(paymentId, "pay_1", 12_500);
        var provider = new PaymongoClient.Refund("ref_1", 12_500, "pending", "requested_by_customer");
        var saved = new SubscriptionRefundDto(UUID.randomUUID(), paymentId, 12_500, "PENDING", "requested_by_customer");
        when(persistence.validate(paymentId, 12_500)).thenReturn(context);
        when(paymongoClient.createRefund("pay_1", 12_500, "requested_by_customer", "approved"))
                .thenReturn(provider);
        when(persistence.save(context, provider, "requested_by_customer")).thenReturn(saved);

        assertThat(service().create(
                paymentId, 12_500, "requested_by_customer", "approved", owner)).isSameAs(saved);
        verify(auditLogService).recordByActorId(eq(owner.userId()),
                eq("SUBSCRIPTION_REFUND_REQUESTED"), isNull(), isNull(), eq(saved.id()), anyMap());
    }

    @Test
    void create_rejectsProviderAmountMismatch() {
        UUID paymentId = UUID.randomUUID();
        var context = new SubscriptionRefundPersistenceService.RefundContext(paymentId, "pay_1", 12_500);
        when(persistence.validate(paymentId, 12_500)).thenReturn(context);
        when(paymongoClient.createRefund(anyString(), anyLong(), anyString(), nullable(String.class)))
                .thenReturn(new PaymongoClient.Refund("ref_1", 10_000, "pending", "other"));

        assertThatThrownBy(() -> service().create(paymentId, 12_500, "other", null, owner()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
        verify(persistence, never()).save(any(), any(), anyString());
    }

    @Test
    void create_mapsProviderFailureToBadGateway() {
        UUID paymentId = UUID.randomUUID();
        var context = new SubscriptionRefundPersistenceService.RefundContext(paymentId, "pay_1", 12_500);
        when(persistence.validate(paymentId, 12_500)).thenReturn(context);
        when(paymongoClient.createRefund(anyString(), anyLong(), anyString(), nullable(String.class)))
                .thenThrow(new PaymongoApiException("provider unavailable"));

        assertThatThrownBy(() -> service().create(paymentId, 12_500, "other", null, owner()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    private SubscriptionRefundService service() {
        return new SubscriptionRefundService(paymongoClient, persistence, auditLogService);
    }

    private static JwtUserDetails owner() {
        return new JwtUserDetails(UUID.randomUUID(), "owner@dasig.test", "admin", null, true);
    }
}
