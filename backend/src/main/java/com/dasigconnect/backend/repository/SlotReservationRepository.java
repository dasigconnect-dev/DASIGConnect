package com.dasigconnect.backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dasigconnect.backend.model.entity.SlotReservation;

/**
 * Extends the base SlotReservationRepository created by M1.
 *
 * M1 created the base JpaRepository stub. M4 owns the custom query methods
 * needed by GuardRailService and SlotReservationService.
 *
 * NOTE: SlotReservation has NO pageId — uniqueness is enforced at the DB level
 * by the unique index on (scheduled_at, institution_id) WHERE status !=
 * 'released'. Guard rail GR-H1 checks across ALL institutions (network-wide
 * slot conflict), not just the same institution.
 */
public interface SlotReservationRepository extends JpaRepository<SlotReservation, UUID> {

    /**
     * GR-H1: Check if any active (non-released) reservation exists within ±30
     * minutes of the requested slot, across ALL institutions (network-wide
     * check).
     *
     * Uses native SQL because EXTRACT(EPOCH FROM interval) is
     * PostgreSQL-specific and not supported in Hibernate 7 JPQL. The window is
     * passed as two Instant bounds (slot - 30min, slot + 30min).
     */
    @Query(value = """
        SELECT COUNT(*) > 0 FROM slot_reservations
        WHERE status != 'released'
        AND scheduled_at BETWEEN :windowStart AND :windowEnd
    """, nativeQuery = true)
    boolean existsActiveWithin30Minutes(
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd
    );

    /**
     * GR-H1 alternative slots: find active reservations near a time window so
     * we can suggest gaps. Returns reservations within ±2 hours of the slot.
     */
    @Query("""
        SELECT r FROM SlotReservation r
        WHERE r.status <> com.dasigconnect.backend.model.entity.SlotReservationStatus.released
        AND r.scheduledAt BETWEEN :from AND :to
        ORDER BY r.scheduledAt ASC
    """)
    List<SlotReservation> findActiveInWindow(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * GR-S2: Count all active (non-released) reservations on a specific
     * calendar day. Uses native SQL — DATE() with AT TIME ZONE is
     * PostgreSQL-specific.
     */
    @Query(value = """
        SELECT COUNT(*) FROM slot_reservations
        WHERE status != 'released'
        AND scheduled_at >= :dayStart
        AND scheduled_at < :dayEnd
    """, nativeQuery = true)
    long countActiveOnDay(@Param("dayStart") Instant dayStart, @Param("dayEnd") Instant dayEnd);

    /**
     * Find the active reservation for a specific submission. Used by
     * SlotReservationService to update or release a reservation.
     */
    @Query("""
        SELECT r FROM SlotReservation r
        WHERE r.submission.id = :submissionId
        AND r.status <> com.dasigconnect.backend.model.entity.SlotReservationStatus.released
    """)
    Optional<SlotReservation> findActiveBySubmissionId(@Param("submissionId") UUID submissionId);

    /**
     * GR-T2 cron job support: find all held reservations whose linked
     * submission has not been updated in the last 7 days (stale drafts).
     */
    @Query("""
        SELECT r FROM SlotReservation r
        WHERE r.status = com.dasigconnect.backend.model.entity.SlotReservationStatus.held
        AND r.submission.updatedAt < :cutoff
        AND r.submission.status = com.dasigconnect.backend.model.entity.SubmissionStatus.draft
    """)
    List<SlotReservation> findStaleHeldReservations(@Param("cutoff") Instant cutoff);

    /**
     * Release a reservation by setting status to 'released'. Used when a
     * submission is rejected, revised, or abandoned.
     */
    @Modifying
    @Query("""
        UPDATE SlotReservation r SET r.status = com.dasigconnect.backend.model.entity.SlotReservationStatus.released
        WHERE r.submission.id = :submissionId
        AND r.status <> com.dasigconnect.backend.model.entity.SlotReservationStatus.released
    """)
    int releaseBySubmissionId(@Param("submissionId") UUID submissionId);

    @Modifying
    @Query("DELETE FROM SlotReservation r WHERE r.submission.id = :submissionId")
    void deleteBySubmissionId(@Param("submissionId") UUID submissionId);
}
