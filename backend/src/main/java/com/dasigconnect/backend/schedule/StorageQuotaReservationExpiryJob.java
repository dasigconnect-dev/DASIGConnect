package com.dasigconnect.backend.schedule;

import com.dasigconnect.backend.service.MediaStorageService;
import com.dasigconnect.backend.service.ScheduledJobHealthService;
import com.dasigconnect.backend.service.StorageQuotaService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StorageQuotaReservationExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(StorageQuotaReservationExpiryJob.class);
    private static final int BATCH_SIZE = 100;

    private final StorageQuotaService storageQuotaService;
    private final MediaStorageService mediaStorageService;
    private final ScheduledJobHealthService scheduledJobHealthService;

    public StorageQuotaReservationExpiryJob(
            StorageQuotaService storageQuotaService,
            MediaStorageService mediaStorageService,
            ScheduledJobHealthService scheduledJobHealthService) {
        this.storageQuotaService = storageQuotaService;
        this.mediaStorageService = mediaStorageService;
        this.scheduledJobHealthService = scheduledJobHealthService;
    }

    @Scheduled(fixedDelayString = "${app.subscription.storage-reservation-expiry-delay-ms:300000}")
    public void releaseExpiredReservations() {
        Instant startedAt = Instant.now();
        try {
            int released = 0;
            for (StorageQuotaService.ExpiredReservation reservation
                    : storageQuotaService.expiredReservations(BATCH_SIZE)) {
                if (!storageQuotaService.claimExpired(reservation.id())) {
                    continue;
                }
                if (mediaStorageService.deleteObject(reservation.objectKey())) {
                    if (storageQuotaService.completeExpiry(reservation.id())) {
                        released++;
                    }
                } else {
                    storageQuotaService.retryExpiry(reservation.id());
                }
            }
            if (released > 0) {
                log.info("StorageQuotaReservationExpiryJob: released {} expired reservation(s).", released);
            }
            scheduledJobHealthService.recordSuccess("StorageQuotaReservationExpiryJob", startedAt);
        } catch (Exception ex) {
            log.error("StorageQuotaReservationExpiryJob failed: {}", ex.getMessage(), ex);
            scheduledJobHealthService.recordFailure("StorageQuotaReservationExpiryJob", startedAt, ex);
        }
    }
}
