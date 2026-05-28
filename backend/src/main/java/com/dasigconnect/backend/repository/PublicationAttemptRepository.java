package com.dasigconnect.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dasigconnect.backend.model.entity.PublicationAttempt;

public interface PublicationAttemptRepository extends JpaRepository<PublicationAttempt, UUID> {

    Optional<PublicationAttempt> findTopBySubmissionIdOrderByAttemptedAtDesc(UUID submissionId);

    long countBySubmissionId(UUID submissionId);
}
