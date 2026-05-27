package com.dasigconnect.backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dasigconnect.backend.model.entity.OverrideRequest;
import com.dasigconnect.backend.model.entity.OverrideRequestDecision;

public interface OverrideRequestRepository extends JpaRepository<OverrideRequest, UUID> {

    /** Active override requests sorted by urgency: soonest requested slot first. */
    @Query("""
        SELECT r FROM OverrideRequest r
        WHERE r.decision = com.dasigconnect.backend.model.entity.OverrideRequestDecision.pending
        ORDER BY r.requestedSlot ASC
        """)
    List<OverrideRequest> findPendingOrderBySlot();

    /** Pending requests whose requested slot has already passed. */
    @Query("""
        SELECT r FROM OverrideRequest r
        WHERE r.decision = com.dasigconnect.backend.model.entity.OverrideRequestDecision.pending
        AND r.requestedSlot < :now
        """)
    List<OverrideRequest> findExpiredRequests(@Param("now") Instant now);

    List<OverrideRequest> findBySubmissionIdAndDecision(UUID submissionId, OverrideRequestDecision decision);

    @Query("""
        SELECT COUNT(r) FROM OverrideRequest r
        WHERE r.submission.id = :submissionId
        AND r.decision != com.dasigconnect.backend.model.entity.OverrideRequestDecision.expired
        """)
    long countNonExpiredBySubmissionId(@Param("submissionId") UUID submissionId);

    long countByDecision(OverrideRequestDecision decision);
}
