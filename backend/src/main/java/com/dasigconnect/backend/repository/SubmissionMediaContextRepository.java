package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.SubmissionMediaContext;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionMediaContextRepository extends JpaRepository<SubmissionMediaContext, UUID> {
}
