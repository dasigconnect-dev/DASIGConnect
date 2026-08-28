package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.entity.AuditLog;
import com.dasigconnect.backend.repository.AuditLogRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolates the audit-entry INSERT in its own {@code REQUIRES_NEW} transaction.
 *
 * <p>This is a separate bean so that {@link AuditLogService#record} can wrap the
 * call in a try/catch that actually catches: if the catch lived inside a
 * {@code @Transactional(REQUIRES_NEW)} method, a failed flush would still surface
 * as an {@code UnexpectedRollbackException} when the proxy tried to commit the
 * already-rollback-only inner transaction, defeating the point.
 */
@Component
class AuditLogWriter {

    private final AuditLogRepository auditLogRepository;

    AuditLogWriter(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    AuditLog save(AuditLog entry) {
        return auditLogRepository.saveAndFlush(entry);
    }
}
