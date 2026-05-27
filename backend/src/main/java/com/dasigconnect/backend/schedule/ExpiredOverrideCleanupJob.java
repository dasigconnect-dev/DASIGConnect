package com.dasigconnect.backend.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dasigconnect.backend.service.OverrideRequestService;

/**
 * UC-3.5 Category C — auto-dismisses expired override requests.
 *
 * Runs every 5 minutes. Any pending request whose requested slot has already
 * passed is transitioned to {@code expired} and the Contributor is notified
 * per UC-2.3 T10 (override denied / expired).
 */
@Component
public class ExpiredOverrideCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiredOverrideCleanupJob.class);

    private final OverrideRequestService overrideRequestService;

    public ExpiredOverrideCleanupJob(OverrideRequestService overrideRequestService) {
        this.overrideRequestService = overrideRequestService;
    }

    @Scheduled(cron = "0 */5 * * * *", zone = "UTC")
    public void run() {
        int dismissed = overrideRequestService.dismissExpiredRequests();
        if (dismissed > 0) {
            log.info("ExpiredOverrideCleanupJob: dismissed {} expired override request(s).", dismissed);
        }
    }
}
