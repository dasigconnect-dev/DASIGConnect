package com.dasigconnect.backend.service;

import com.dasigconnect.backend.exception.SubscriptionLimitExceededException;
import com.dasigconnect.backend.model.entity.OrganizationSubscription;
import com.dasigconnect.backend.model.entity.StorageQuotaReservation;
import com.dasigconnect.backend.model.entity.StorageQuotaReservationStatus;
import com.dasigconnect.backend.model.entity.SubscriptionLifecycleStatus;
import com.dasigconnect.backend.repository.OrganizationSubscriptionRepository;
import com.dasigconnect.backend.repository.StorageQuotaReservationRepository;
import com.dasigconnect.backend.repository.SubscriptionUsageEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StorageQuotaService {

    private final StorageQuotaReservationRepository reservationRepository;
    private final OrganizationSubscriptionRepository subscriptionRepository;
    private final SubscriptionUsageEventRepository usageRepository;
    private final boolean enforcementEnabled;
    private final double warningThreshold;
    private final Duration reservationTtl;

    public StorageQuotaService(
            StorageQuotaReservationRepository reservationRepository,
            OrganizationSubscriptionRepository subscriptionRepository,
            SubscriptionUsageEventRepository usageRepository,
            @Value("${app.subscription.enforcement-enabled:false}") boolean enforcementEnabled,
            @Value("${app.subscription.storage-warning-threshold:0.8}") double warningThreshold,
            @Value("${app.subscription.storage-reservation-ttl-minutes:20}") long reservationTtlMinutes) {
        this.reservationRepository = reservationRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.usageRepository = usageRepository;
        this.enforcementEnabled = enforcementEnabled;
        this.warningThreshold = Math.min(Math.max(warningThreshold, 0.0), 1.0);
        this.reservationTtl = Duration.ofMinutes(Math.max(reservationTtlMinutes, 1));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReservationResult reserve(
            UUID organizationId,
            UUID institutionId,
            UUID userId,
            String objectKey,
            long requestedBytes) {
        if (organizationId == null || institutionId == null || userId == null) {
            throw new IllegalArgumentException("Organization, institution, and user are required.");
        }
        if (objectKey == null || objectKey.isBlank() || objectKey.length() > 1024) {
            throw new IllegalArgumentException("A valid storage object key is required.");
        }
        if (requestedBytes <= 0) {
            throw new IllegalArgumentException("Reserved bytes must be positive.");
        }

        OrganizationSubscription subscription = lockSubscription(organizationId);
        Instant now = Instant.now();
        long usedBytes = usageRepository.currentMediaStorageBytes(organizationId);
        long reservedBytes = reservationRepository.sumActiveReservedBytes(organizationId, now);
        long limitBytes = subscription.getPlan().getMediaStorageBytes();
        long projectedBytes = Math.addExact(Math.addExact(usedBytes, reservedBytes), requestedBytes);
        boolean overLimit = projectedBytes > limitBytes;

        if (enforcementEnabled && overLimit) {
            throw new SubscriptionLimitExceededException(
                    "mediaStorage", usedBytes, reservedBytes, requestedBytes,
                    limitBytes, subscription.getCurrentPeriodEnd());
        }

        StorageQuotaReservation reservation = new StorageQuotaReservation();
        reservation.setOrganizationId(organizationId);
        reservation.setInstitutionId(institutionId);
        reservation.setUserId(userId);
        reservation.setObjectKey(objectKey.trim());
        reservation.setReservedBytes(requestedBytes);
        reservation.setStatus(StorageQuotaReservationStatus.RESERVED);
        reservation.setExpiresAt(now.plus(reservationTtl));
        reservation = reservationRepository.save(reservation);

        long warningAt = (long) Math.floor(limitBytes * warningThreshold);
        boolean warning = projectedBytes >= warningAt;
        return new ReservationResult(
                reservation.getId(), reservation.getExpiresAt(), requestedBytes,
                usedBytes, reservedBytes, limitBytes, warning, overLimit, enforcementEnabled);
    }

    @Transactional
    public void consume(
            UUID reservationId,
            UUID organizationId,
            UUID institutionId,
            UUID userId,
            long actualBytes,
            UUID mediaAssetId) {
        if (reservationId == null) {
            if (enforcementEnabled) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "A storage quota reservation is required.");
            }
            return;
        }

        StorageQuotaReservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Storage quota reservation was not found."));
        lockSubscription(reservation.getOrganizationId());

        if (reservation.getStatus() == StorageQuotaReservationStatus.CONSUMED
                && mediaAssetId != null
                && mediaAssetId.equals(reservation.getMediaAssetId())) {
            return;
        }
        if (reservation.getStatus() != StorageQuotaReservationStatus.RESERVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Storage quota reservation is no longer active.");
        }
        if (!reservation.getExpiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Storage quota reservation has expired.");
        }
        if (!reservation.getOrganizationId().equals(organizationId)
                || !reservation.getInstitutionId().equals(institutionId)
                || !reservation.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Storage quota reservation scope mismatch.");
        }
        if (actualBytes != reservation.getReservedBytes()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Uploaded file size does not match the storage reservation.");
        }

        reservation.setStatus(StorageQuotaReservationStatus.CONSUMED);
        reservation.setMediaAssetId(mediaAssetId);
        reservation.setConsumedAt(Instant.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(UUID reservationId) {
        if (reservationId == null) {
            return;
        }
        reservationRepository.findByIdForUpdate(reservationId).ifPresent(reservation -> {
            if (reservation.getStatus() == StorageQuotaReservationStatus.RESERVED) {
                reservation.setStatus(StorageQuotaReservationStatus.RELEASED);
                reservation.setReleasedAt(Instant.now());
            }
        });
    }

    @Transactional(readOnly = true)
    public List<ExpiredReservation> expiredReservations(int limit) {
        return reservationRepository
                .findByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                        StorageQuotaReservationStatus.RESERVED,
                        Instant.now(),
                        PageRequest.of(0, Math.min(Math.max(limit, 1), 500)))
                .stream()
                .map(reservation -> new ExpiredReservation(
                        reservation.getId(), reservation.getObjectKey()))
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claimExpired(UUID reservationId) {
        StorageQuotaReservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElse(null);
        if (reservation == null
                || reservation.getStatus() != StorageQuotaReservationStatus.RESERVED
                || reservation.getExpiresAt().isAfter(Instant.now())) {
            return false;
        }
        reservation.setStatus(StorageQuotaReservationStatus.EXPIRING);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean completeExpiry(UUID reservationId) {
        StorageQuotaReservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElse(null);
        if (reservation == null || reservation.getStatus() != StorageQuotaReservationStatus.EXPIRING) {
            return false;
        }
        reservation.setStatus(StorageQuotaReservationStatus.EXPIRED);
        reservation.setReleasedAt(Instant.now());
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryExpiry(UUID reservationId) {
        reservationRepository.findByIdForUpdate(reservationId).ifPresent(reservation -> {
            if (reservation.getStatus() == StorageQuotaReservationStatus.EXPIRING) {
                reservation.setStatus(StorageQuotaReservationStatus.RESERVED);
            }
        });
    }

    private OrganizationSubscription lockSubscription(UUID organizationId) {
        OrganizationSubscription subscription = subscriptionRepository.findByOrganizationIdForUpdate(organizationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "No subscription is configured for this organization."));
        if (enforcementEnabled
                && (subscription.getStatus() == SubscriptionLifecycleStatus.RESTRICTED
                    || subscription.getStatus() == SubscriptionLifecycleStatus.CANCELLED)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "The organization subscription is restricted. Contact the Admin Owner.");
        }
        return subscription;
    }

    public record ReservationResult(
            UUID reservationId,
            Instant expiresAt,
            long reservedBytes,
            long usedBytes,
            long existingReservedBytes,
            long limitBytes,
            boolean warningThresholdReached,
            boolean projectedOverLimit,
            boolean enforcementEnabled) {
    }

    public record ExpiredReservation(UUID id, String objectKey) {
    }
}
