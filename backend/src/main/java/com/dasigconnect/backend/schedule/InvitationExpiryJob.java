package com.dasigconnect.backend.schedule;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dasigconnect.backend.service.InvitationService;
import com.dasigconnect.backend.service.ScheduledJobHealthService;

/**
 * Runs hourly. See {@link InvitationService#expireOverdueInvitations} for why
 * this exists: an invitation token's own 72h expiry never used to update the
 * invited user's account_state, so a pending row could sit indefinitely
 * showing "Pending" with no way to tell it apart from a fresh invite.
 * Hourly granularity is fine here -- this only affects a display status, not
 * anything time-sensitive like publishing.
 */
@Component
public class InvitationExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(InvitationExpiryJob.class);

    private final InvitationService invitationService;
    private final ScheduledJobHealthService scheduledJobHealthService;

    public InvitationExpiryJob(
            InvitationService invitationService,
            ScheduledJobHealthService scheduledJobHealthService) {
        this.invitationService = invitationService;
        this.scheduledJobHealthService = scheduledJobHealthService;
    }

    @Scheduled(cron = "0 15 * * * *", zone = "UTC")
    public void run() {
        Instant startedAt = Instant.now();
        try {
            int expired = invitationService.expireOverdueInvitations();
            if (expired > 0) {
                log.info("InvitationExpiryJob: marked {} overdue invitation(s) as expired.", expired);
            }
            scheduledJobHealthService.recordSuccess("InvitationExpiryJob", startedAt);
        } catch (Exception ex) {
            log.error("InvitationExpiryJob failed: {}", ex.getMessage(), ex);
            scheduledJobHealthService.recordFailure("InvitationExpiryJob", startedAt, ex);
        }
    }
}
