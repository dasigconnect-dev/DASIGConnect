package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.Submission;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {
}
