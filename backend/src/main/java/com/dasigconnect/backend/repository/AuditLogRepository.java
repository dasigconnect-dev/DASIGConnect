package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.AuditLog;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findByResourceIdOrderByCreatedAtDesc(UUID resourceId);

    /** True if the user is the actor on any audit row — i.e. they have "acted" and cannot be row-deleted. */
    boolean existsByActorId(UUID actorId);

    /** True if an audit row with this action already targets the resource — used to keep removal idempotent. */
    boolean existsByActionAndResourceId(String action, UUID resourceId);

    /**
     * Retention prune: hard-deletes high-volume operational rows past the cutoff.
     * Security / account / erasure / institution / override / export events are
     * never in the supplied action set, so they are retained indefinitely.
     *
     * <p>V78 creates no UPDATE/DELETE policy on {@code audit_log}; this and
     * {@link #scrubPersonalMetadataForUser} are the only sanctioned mutations and
     * rely on the app connecting as the table owner (RLS is ENABLE, not FORCE).
     * If a restricted DB role or FORCE ROW LEVEL SECURITY is ever introduced,
     * move this to a privileged maintenance job / pg_cron.
     */
    @Modifying
    @Query(value = "DELETE FROM audit_log WHERE action IN (:actions) AND created_at < :cutoff", nativeQuery = true)
    int deleteByActionInAndCreatedAtBefore(@Param("actions") Collection<String> actions,
                                           @Param("cutoff") Instant cutoff);

    /**
     * Right-to-be-forgotten: strips personal fields copied into audit metadata
     * for an erased user, keeping the rows themselves. JSONB {@code -} operator
     * removes the key if present and is a no-op otherwise.
     */
    @Modifying
    @Query(value = "UPDATE audit_log SET metadata = (metadata - 'email' - 'originalEmail') "
            + "WHERE actor_id = :userId OR resource_id = :userId", nativeQuery = true)
    int scrubPersonalMetadataForUser(@Param("userId") UUID userId);
}
