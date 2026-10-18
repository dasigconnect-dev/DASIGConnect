package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.exception.SubscriptionLimitExceededException;
import com.dasigconnect.backend.model.entity.OrganizationSubscription;
import com.dasigconnect.backend.model.entity.StorageQuotaReservation;
import com.dasigconnect.backend.model.entity.StorageQuotaReservationStatus;
import com.dasigconnect.backend.model.entity.SubscriptionPlan;
import com.dasigconnect.backend.model.entity.SubscriptionLifecycleStatus;
import com.dasigconnect.backend.repository.OrganizationSubscriptionRepository;
import com.dasigconnect.backend.repository.StorageQuotaReservationRepository;
import com.dasigconnect.backend.repository.SubscriptionUsageEventRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class StorageQuotaServiceTest {

    @Mock private StorageQuotaReservationRepository reservationRepository;
    @Mock private OrganizationSubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionUsageEventRepository usageRepository;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID institutionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private StorageQuotaService service;

    @BeforeEach
    void setUp() {
        service = service(false);
        when(subscriptionRepository.findByOrganizationIdForUpdate(organizationId))
                .thenReturn(Optional.of(subscription(1_000L)));
    }

    @Test
    void reserve_accountsForUsedAndPendingBytesAndReportsWarning() {
        UUID reservationId = UUID.randomUUID();
        when(usageRepository.currentMediaStorageBytes(organizationId)).thenReturn(700L);
        when(reservationRepository.sumActiveReservedBytes(any(), any())).thenReturn(100L);
        when(reservationRepository.save(any())).thenAnswer(invocation -> {
            StorageQuotaReservation reservation = invocation.getArgument(0);
            reservation.setId(reservationId);
            return reservation;
        });

        StorageQuotaService.ReservationResult result = service.reserve(
                organizationId, institutionId, userId, "media/example.jpg", 100L);

        assertThat(result.reservationId()).isEqualTo(reservationId);
        assertThat(result.warningThresholdReached()).isTrue();
        assertThat(result.projectedOverLimit()).isFalse();
        assertThat(result.enforcementEnabled()).isFalse();
        ArgumentCaptor<StorageQuotaReservation> captor = ArgumentCaptor.forClass(StorageQuotaReservation.class);
        verify(reservationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(StorageQuotaReservationStatus.RESERVED);
        assertThat(captor.getValue().getReservedBytes()).isEqualTo(100L);
    }

    @Test
    void reserve_whenEnforcedAndProjectedUsageExceedsLimit_rejectsBeforeSaving() {
        service = service(true);
        when(usageRepository.currentMediaStorageBytes(organizationId)).thenReturn(900L);
        when(reservationRepository.sumActiveReservedBytes(any(), any())).thenReturn(50L);

        assertThatThrownBy(() -> service.reserve(
                organizationId, institutionId, userId, "media/example.jpg", 100L))
                .isInstanceOf(SubscriptionLimitExceededException.class);

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void reserve_whenSubscriptionRestricted_rejectsNewUpload() {
        service = service(true);
        OrganizationSubscription restricted = subscription(1_000L);
        restricted.setStatus(SubscriptionLifecycleStatus.RESTRICTED);
        when(subscriptionRepository.findByOrganizationIdForUpdate(organizationId))
                .thenReturn(Optional.of(restricted));

        assertThatThrownBy(() -> service.reserve(
                organizationId, institutionId, userId, "media/example.jpg", 100L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void consume_matchingReservation_marksItConsumed() {
        UUID mediaAssetId = UUID.randomUUID();
        StorageQuotaReservation reservation = activeReservation(100L);
        when(reservationRepository.findByIdForUpdate(reservation.getId()))
                .thenReturn(Optional.of(reservation));

        service.consume(reservation.getId(), organizationId, institutionId, userId, 100L, mediaAssetId);

        assertThat(reservation.getStatus()).isEqualTo(StorageQuotaReservationStatus.CONSUMED);
        assertThat(reservation.getMediaAssetId()).isEqualTo(mediaAssetId);
        assertThat(reservation.getConsumedAt()).isNotNull();
    }

    @Test
    void consume_wrongInstitution_rejectsWithoutConsuming() {
        StorageQuotaReservation reservation = activeReservation(100L);
        when(reservationRepository.findByIdForUpdate(reservation.getId()))
                .thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.consume(
                reservation.getId(), organizationId, UUID.randomUUID(), userId, 100L, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(reservation.getStatus()).isEqualTo(StorageQuotaReservationStatus.RESERVED);
    }

    @Test
    void consume_sameMediaAssetTwice_isIdempotent() {
        UUID mediaAssetId = UUID.randomUUID();
        StorageQuotaReservation reservation = activeReservation(100L);
        reservation.setStatus(StorageQuotaReservationStatus.CONSUMED);
        reservation.setMediaAssetId(mediaAssetId);
        when(reservationRepository.findByIdForUpdate(reservation.getId()))
                .thenReturn(Optional.of(reservation));

        service.consume(reservation.getId(), organizationId, institutionId, userId, 100L, mediaAssetId);

        assertThat(reservation.getStatus()).isEqualTo(StorageQuotaReservationStatus.CONSUMED);
    }

    @Test
    void expiryClaim_preventsConsumptionAndSupportsRetry() {
        StorageQuotaReservation reservation = activeReservation(100L);
        reservation.setExpiresAt(Instant.now().minusSeconds(1));
        when(reservationRepository.findByIdForUpdate(reservation.getId()))
                .thenReturn(Optional.of(reservation));

        assertThat(service.claimExpired(reservation.getId())).isTrue();
        assertThat(reservation.getStatus()).isEqualTo(StorageQuotaReservationStatus.EXPIRING);
        assertThatThrownBy(() -> service.consume(
                reservation.getId(), organizationId, institutionId, userId, 100L, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class);

        service.retryExpiry(reservation.getId());
        assertThat(reservation.getStatus()).isEqualTo(StorageQuotaReservationStatus.RESERVED);
        assertThat(service.claimExpired(reservation.getId())).isTrue();
        assertThat(service.completeExpiry(reservation.getId())).isTrue();
        assertThat(reservation.getStatus()).isEqualTo(StorageQuotaReservationStatus.EXPIRED);
        assertThat(reservation.getReleasedAt()).isNotNull();
    }

    private StorageQuotaService service(boolean enforcementEnabled) {
        return new StorageQuotaService(
                reservationRepository,
                subscriptionRepository,
                usageRepository,
                enforcementEnabled,
                0.8,
                20);
    }

    private OrganizationSubscription subscription(long storageLimit) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setMediaStorageBytes(storageLimit);
        OrganizationSubscription subscription = new OrganizationSubscription();
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionLifecycleStatus.ACTIVE);
        subscription.setCurrentPeriodEnd(Instant.now().plusSeconds(3_600));
        return subscription;
    }

    private StorageQuotaReservation activeReservation(long bytes) {
        StorageQuotaReservation reservation = new StorageQuotaReservation();
        reservation.setId(UUID.randomUUID());
        reservation.setOrganizationId(organizationId);
        reservation.setInstitutionId(institutionId);
        reservation.setUserId(userId);
        reservation.setObjectKey("media/" + UUID.randomUUID() + ".jpg");
        reservation.setReservedBytes(bytes);
        reservation.setStatus(StorageQuotaReservationStatus.RESERVED);
        reservation.setExpiresAt(Instant.now().plusSeconds(300));
        return reservation;
    }
}
