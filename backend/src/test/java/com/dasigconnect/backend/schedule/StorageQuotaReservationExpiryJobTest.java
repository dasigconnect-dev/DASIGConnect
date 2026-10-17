package com.dasigconnect.backend.schedule;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.service.MediaStorageService;
import com.dasigconnect.backend.service.ScheduledJobHealthService;
import com.dasigconnect.backend.service.StorageQuotaService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StorageQuotaReservationExpiryJobTest {

    @Mock private StorageQuotaService storageQuotaService;
    @Mock private MediaStorageService mediaStorageService;
    @Mock private ScheduledJobHealthService scheduledJobHealthService;

    @Test
    void releaseExpiredReservations_claimsDeletesAndCompletes() {
        UUID reservationId = UUID.randomUUID();
        when(storageQuotaService.expiredReservations(100))
                .thenReturn(List.of(new StorageQuotaService.ExpiredReservation(reservationId, "media/a.jpg")));
        when(storageQuotaService.claimExpired(reservationId)).thenReturn(true);
        when(mediaStorageService.deleteObject("media/a.jpg")).thenReturn(true);
        when(storageQuotaService.completeExpiry(reservationId)).thenReturn(true);

        job().releaseExpiredReservations();

        verify(storageQuotaService).claimExpired(reservationId);
        verify(mediaStorageService).deleteObject("media/a.jpg");
        verify(storageQuotaService).completeExpiry(reservationId);
        verify(scheduledJobHealthService).recordSuccess(
                org.mockito.ArgumentMatchers.eq("StorageQuotaReservationExpiryJob"), any());
    }

    @Test
    void releaseExpiredReservations_whenDeleteFails_restoresForRetry() {
        UUID reservationId = UUID.randomUUID();
        when(storageQuotaService.expiredReservations(100))
                .thenReturn(List.of(new StorageQuotaService.ExpiredReservation(reservationId, "media/a.jpg")));
        when(storageQuotaService.claimExpired(reservationId)).thenReturn(true);
        when(mediaStorageService.deleteObject("media/a.jpg")).thenReturn(false);

        job().releaseExpiredReservations();

        verify(storageQuotaService).retryExpiry(reservationId);
        verify(storageQuotaService, never()).completeExpiry(reservationId);
    }

    @Test
    void releaseExpiredReservations_whenClaimLost_doesNotDeleteObject() {
        UUID reservationId = UUID.randomUUID();
        when(storageQuotaService.expiredReservations(100))
                .thenReturn(List.of(new StorageQuotaService.ExpiredReservation(reservationId, "media/a.jpg")));
        when(storageQuotaService.claimExpired(reservationId)).thenReturn(false);

        job().releaseExpiredReservations();

        verify(mediaStorageService, never()).deleteObject(any());
    }

    private StorageQuotaReservationExpiryJob job() {
        return new StorageQuotaReservationExpiryJob(
                storageQuotaService, mediaStorageService, scheduledJobHealthService);
    }
}
