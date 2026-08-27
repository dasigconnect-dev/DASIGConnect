package com.dasigconnect.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dasigconnect.backend.model.entity.ValidationLog;

public interface ValidationLogRepository extends JpaRepository<ValidationLog, UUID> {

    @Query("SELECT vl FROM ValidationLog vl JOIN FETCH vl.validator WHERE vl.submission.id = :submissionId ORDER BY vl.createdAt DESC")
    List<ValidationLog> findBySubmissionIdOrderByCreatedAtDesc(@Param("submissionId") UUID submissionId);

    @Query("SELECT vl FROM ValidationLog vl WHERE vl.submission.id = :submissionId ORDER BY vl.createdAt ASC")
    List<ValidationLog> findBySubmissionIdOrderByCreatedAtAsc(@Param("submissionId") UUID submissionId);

    boolean existsByValidatorId(UUID validatorId);
}
