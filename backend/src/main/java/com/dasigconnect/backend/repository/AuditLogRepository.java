package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.AuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findByResourceIdOrderByCreatedAtDesc(UUID resourceId);

    /** True if the user is the actor on any audit row — i.e. they have "acted" and cannot be row-deleted. */
    boolean existsByActorId(UUID actorId);

    /** True if an audit row with this action already targets the resource — used to keep removal idempotent. */
    boolean existsByActionAndResourceId(String action, UUID resourceId);
}
