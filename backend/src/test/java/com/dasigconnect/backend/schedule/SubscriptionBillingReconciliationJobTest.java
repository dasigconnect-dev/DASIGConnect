package com.dasigconnect.backend.schedule;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.dasigconnect.backend.service.ScheduledJobHealthService;
import com.dasigconnect.backend.service.SubscriptionBillingReconciliationService;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SubscriptionBillingReconciliationJobTest {
    @Test
    void reconcile_whenEnabled_recordsSuccess() {
        SubscriptionBillingReconciliationService service = mock(SubscriptionBillingReconciliationService.class);
        ScheduledJobHealthService health = mock(ScheduledJobHealthService.class);
        when(service.reconcile()).thenReturn(
                new SubscriptionBillingReconciliationService.ReconciliationResult(2, 1, 0));

        new SubscriptionBillingReconciliationJob(service, health, true).reconcile();

        verify(service).reconcile();
        verify(health).recordSuccess(eq("SubscriptionBillingReconciliationJob"), any(Instant.class));
    }

    @Test
    void reconcile_whenDisabled_skipsProviderButReportsHealthyTick() {
        SubscriptionBillingReconciliationService service = mock(SubscriptionBillingReconciliationService.class);
        ScheduledJobHealthService health = mock(ScheduledJobHealthService.class);

        new SubscriptionBillingReconciliationJob(service, health, false).reconcile();

        verifyNoInteractions(service);
        verify(health).recordSuccess(eq("SubscriptionBillingReconciliationJob"), any(Instant.class));
    }
}
