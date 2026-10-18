package com.dasigconnect.backend.schedule;

import com.dasigconnect.backend.service.ScheduledJobHealthService;
import com.dasigconnect.backend.service.SubscriptionLifecycleService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionLifecycleJob {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionLifecycleJob.class);

    private final SubscriptionLifecycleService lifecycleService;
    private final ScheduledJobHealthService scheduledJobHealthService;

    public SubscriptionLifecycleJob(
            SubscriptionLifecycleService lifecycleService,
            ScheduledJobHealthService scheduledJobHealthService) {
        this.lifecycleService = lifecycleService;
        this.scheduledJobHealthService = scheduledJobHealthService;
    }

    @Scheduled(cron = "${app.subscription.lifecycle-cron:0 10 * * * *}", zone = "UTC")
    public void advanceLifecycle() {
        Instant startedAt = Instant.now();
        try {
            lifecycleService.evaluate(startedAt).ifPresent(transition ->
                    log.info("Subscription lifecycle advanced from {} to {}.",
                            transition.previousStatus(), transition.currentStatus()));
            scheduledJobHealthService.recordSuccess("SubscriptionLifecycleJob", startedAt);
        } catch (Exception ex) {
            log.error("SubscriptionLifecycleJob failed: {}", ex.getMessage(), ex);
            scheduledJobHealthService.recordFailure("SubscriptionLifecycleJob", startedAt, ex);
        }
    }
}
