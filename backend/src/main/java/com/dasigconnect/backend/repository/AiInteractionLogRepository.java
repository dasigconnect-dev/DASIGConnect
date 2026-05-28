package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.AiInteractionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AiInteractionLogRepository extends JpaRepository<AiInteractionLog, UUID> {
}
