package com.dasigconnect.backend.schedule;

import com.dasigconnect.backend.service.ScheduledJobHealthService;
import com.dasigconnect.backend.service.SubscriptionBillingReconciliationService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionBillingReconciliationJob {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionBillingReconciliationJob.class);
    private final SubscriptionBillingReconciliationService service;
    private final ScheduledJobHealthService healthService;
    private final boolean subscriptionEnabled;
    private final boolean reconciliationEnabled;

    public SubscriptionBillingReconciliationJob(
            SubscriptionBillingReconciliationService service,
            ScheduledJobHealthService healthService,
            @Value("${app.subscription.enabled:false}") boolean subscriptionEnabled,
            @Value("${app.paymongo.reconciliation-enabled:false}") boolean reconciliationEnabled) {
        this.service = service;
        this.healthService = healthService;
        this.subscriptionEnabled = subscriptionEnabled;
        this.reconciliationEnabled = reconciliationEnabled;
    }

    @Scheduled(cron = "${app.paymongo.reconciliation-cron:0 35 * * * *}", zone = "UTC")
    public void reconcile() {
        Instant startedAt = Instant.now();
        try {
            if (subscriptionEnabled && reconciliationEnabled) {
                var result = service.reconcile();
                log.info("PayMongo reconciliation checked {}, settled {}, failed {}.",
                        result.checked(), result.settled(), result.failed());
            }
            healthService.recordSuccess("SubscriptionBillingReconciliationJob", startedAt);
        } catch (Exception error) {
            log.error("SubscriptionBillingReconciliationJob failed: {}", error.getMessage(), error);
            healthService.recordFailure("SubscriptionBillingReconciliationJob", startedAt, error);
        }
    }
}
