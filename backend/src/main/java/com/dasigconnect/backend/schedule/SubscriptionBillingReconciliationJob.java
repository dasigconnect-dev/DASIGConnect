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
    private final boolean enabled;

    public SubscriptionBillingReconciliationJob(
            SubscriptionBillingReconciliationService service,
            ScheduledJobHealthService healthService,
            @Value("${app.paymongo.reconciliation-enabled:false}") boolean enabled) {
        this.service = service;
        this.healthService = healthService;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${app.paymongo.reconciliation-cron:0 35 * * * *}", zone = "UTC")
    public void reconcile() {
        Instant startedAt = Instant.now();
        try {
            if (enabled) {
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
