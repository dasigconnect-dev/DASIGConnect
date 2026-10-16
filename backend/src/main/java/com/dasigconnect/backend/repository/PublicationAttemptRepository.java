package com.dasigconnect.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.dasigconnect.backend.model.entity.PublicationAttempt;

public interface PublicationAttemptRepository extends JpaRepository<PublicationAttempt, UUID> {

    Optional<PublicationAttempt> findTopBySubmissionIdOrderByAttemptedAtDesc(UUID submissionId);

    @Query("""
        SELECT pa FROM PublicationAttempt pa
        JOIN FETCH pa.submission
        WHERE pa.submission.id IN :submissionIds
          AND pa.attemptNumber = (
              SELECT MAX(latest.attemptNumber)
              FROM PublicationAttempt latest
              WHERE latest.submission.id = pa.submission.id
          )
        ORDER BY pa.submission.id, pa.attemptedAt DESC
        """)
    List<PublicationAttempt> findLatestBySubmissionIds(
            @Param("submissionIds") List<UUID> submissionIds);

    long countBySubmissionId(UUID submissionId);

    boolean existsBySubmissionIdAndErrorDetailStartingWith(UUID submissionId, String errorPrefix);
}
