package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.SubscriptionActionReservation;
import com.dasigconnect.backend.model.entity.SubscriptionUsageEventType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface SubscriptionActionReservationRepository
        extends JpaRepository<SubscriptionActionReservation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT reservation FROM SubscriptionActionReservation reservation WHERE reservation.id = :id")
    Optional<SubscriptionActionReservation> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT reservation FROM SubscriptionActionReservation reservation "
            + "WHERE reservation.idempotencyKey = :idempotencyKey")
    Optional<SubscriptionActionReservation> findByIdempotencyKeyForUpdate(
            @Param("idempotencyKey") String idempotencyKey);

    @Query("""
            SELECT COALESCE(SUM(reservation.quantity), 0)
            FROM SubscriptionActionReservation reservation
            WHERE reservation.organizationId = :organizationId
              AND reservation.eventType = :eventType
              AND reservation.status = com.dasigconnect.backend.model.entity.SubscriptionActionReservationStatus.RESERVED
              AND reservation.expiresAt > :now
              AND reservation.reservedAt >= :periodStart
              AND reservation.reservedAt < :periodEnd
            """)
    long sumActiveOrganizationReservations(
            @Param("organizationId") UUID organizationId,
            @Param("eventType") SubscriptionUsageEventType eventType,
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd,
            @Param("now") Instant now);

    @Query("""
            SELECT COALESCE(SUM(reservation.quantity), 0)
            FROM SubscriptionActionReservation reservation
            WHERE reservation.organizationId = :organizationId
              AND reservation.userId = :userId
              AND reservation.eventType = :eventType
              AND reservation.status = com.dasigconnect.backend.model.entity.SubscriptionActionReservationStatus.RESERVED
              AND reservation.expiresAt > :now
              AND reservation.reservedAt >= :periodStart
              AND reservation.reservedAt < :periodEnd
            """)
    long sumActiveUserReservations(
            @Param("organizationId") UUID organizationId,
            @Param("userId") UUID userId,
            @Param("eventType") SubscriptionUsageEventType eventType,
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd,
            @Param("now") Instant now);

    @Modifying
    @Query("""
            UPDATE SubscriptionActionReservation reservation
            SET reservation.status = com.dasigconnect.backend.model.entity.SubscriptionActionReservationStatus.EXPIRED,
                reservation.releasedAt = :now
            WHERE reservation.organizationId = :organizationId
              AND reservation.status = com.dasigconnect.backend.model.entity.SubscriptionActionReservationStatus.RESERVED
              AND reservation.expiresAt <= :now
            """)
    int expireStaleReservations(
            @Param("organizationId") UUID organizationId,
            @Param("now") Instant now);
}
