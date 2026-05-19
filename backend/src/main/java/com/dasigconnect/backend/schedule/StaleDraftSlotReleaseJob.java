package com.dasigconnect.backend.schedule;

import com.dasigconnect.backend.service.SlotReservationService;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * GR-T2 Cron Job: Releases stale held slot reservations.
 *
 * A slot reservation is considered stale when: - The linked submission is still
 * in DRAFT state - The submission has not been updated in the last 7 days
 *
 * Per SRS UC-1.3: stale draft slots are automatically released daily so the
 * time slot becomes available for other Contributors.
 *
 * Schedule: runs daily at 02:00 UTC (off-peak, minimizes contention).
 *
 * Place this class in:
 * backend/src/main/java/com/dasigconnect/backend/schedule/StaleDraftSlotReleaseJob.java
 */
@Component
public class StaleDraftSlotReleaseJob {

    private static final Logger log = LoggerFactory.getLogger(StaleDraftSlotReleaseJob.class);

    private final SlotReservationService slotReservationService;

    public StaleDraftSlotReleaseJob(SlotReservationService slotReservationService) {
        this.slotReservationService = slotReservationService;
    }

    /**
     * Runs daily at 02:00 UTC. Finds all held slot reservations linked to stale
     * drafts (not updated in 7+ days) and releases them so the slots become
     * available again.
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    public void releaseStaleSlots() {
        log.info("GR-T2: Starting stale draft slot release job");
        try {
            List<UUID> released = slotReservationService.releaseStaleHeldReservations();
            if (released.isEmpty()) {
                log.info("GR-T2: No stale slots found.");
            } else {
                log.info("GR-T2: Released {} stale slot(s) for submissions: {}", released.size(), released);
            }
        } catch (Exception ex) {
            log.error("GR-T2: Slot release job failed with exception", ex);
        }
    }
}
