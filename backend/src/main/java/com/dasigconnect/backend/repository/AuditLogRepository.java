package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.AuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByResourceIdOrderByCreatedAtDesc(UUID resourceId);
}
