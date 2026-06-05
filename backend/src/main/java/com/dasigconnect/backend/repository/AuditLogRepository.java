package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.AuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /** UC-4.11: the full audit trail for one resource (e.g. a media asset), newest first. */
    @Query("""
        SELECT a FROM AuditLog a
        LEFT JOIN FETCH a.actor
        WHERE a.resourceId = :resourceId
        ORDER BY a.createdAt DESC
        """)
    List<AuditLog> findByResourceIdWithActor(@Param("resourceId") UUID resourceId);
}
