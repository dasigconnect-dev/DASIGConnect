package com.dasigconnect.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dasigconnect.backend.model.entity.ValidationLog;

public interface ValidationLogRepository extends JpaRepository<ValidationLog, UUID> {

    List<ValidationLog> findBySubmissionIdOrderByCreatedAtDesc(UUID submissionId);
}
