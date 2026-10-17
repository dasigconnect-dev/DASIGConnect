package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.StorageQuotaReservation;
import com.dasigconnect.backend.model.entity.StorageQuotaReservationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface StorageQuotaReservationRepository extends JpaRepository<StorageQuotaReservation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT reservation FROM StorageQuotaReservation reservation WHERE reservation.id = :id")
    Optional<StorageQuotaReservation> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            SELECT COALESCE(SUM(reservation.reservedBytes), 0)
            FROM StorageQuotaReservation reservation
            WHERE reservation.organizationId = :organizationId
              AND reservation.status = com.dasigconnect.backend.model.entity.StorageQuotaReservationStatus.RESERVED
              AND reservation.expiresAt > :now
            """)
    long sumActiveReservedBytes(
            @Param("organizationId") UUID organizationId,
            @Param("now") Instant now);

    List<StorageQuotaReservation> findByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
            StorageQuotaReservationStatus status,
            Instant expiresAt,
            Pageable pageable);
}
