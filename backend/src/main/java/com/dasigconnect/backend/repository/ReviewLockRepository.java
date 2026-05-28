package com.dasigconnect.backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dasigconnect.backend.model.entity.ReviewLock;

public interface ReviewLockRepository extends JpaRepository<ReviewLock, UUID> {

    Optional<ReviewLock> findBySubmissionId(UUID submissionId);

    @Modifying
    @Query("DELETE FROM ReviewLock r WHERE r.submission.id = :submissionId")
    void deleteBySubmissionId(@Param("submissionId") UUID submissionId);

    List<ReviewLock> findByExpiresAtBefore(Instant now);

    @Modifying
    @Query("DELETE FROM ReviewLock r WHERE r.lockedBy.id = :userId")
    void deleteByLockedById(@Param("userId") UUID userId);
}
