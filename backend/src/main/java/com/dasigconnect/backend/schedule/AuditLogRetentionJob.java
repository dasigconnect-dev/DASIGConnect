package com.dasigconnect.backend.schedule;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dasigconnect.backend.repository.AuditLogRepository;
import com.dasigconnect.backend.service.ScheduledJobHealthService;

/**
 * Tiered retention for {@code audit_log}. High-volume operational actions
 * (sign-ins, uploads, routine submission edits, background-job markers) are
 * pruned after {@code app.audit.retention.operational-days}. Everything else —
 * security, account/role changes, personal-data erasure, institution changes,
 * override decisions, exports — is retained indefinitely because the table is
 * append-only and those rows are the compliance trail.
 *
 * <p>Runs daily at 03:45 UTC (after the job-run prune at 03:15).
 */
@Component
public class AuditLogRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AuditLogRetentionJob.class);

    /** Actions safe to age out — pure operational volume, no compliance value past the window. */
    private static final List<String> PRUNABLE_ACTIONS = List.of(
            "LOGIN_SUCCESS",
            "LOGIN_FAILED",
            "LOGOUT",
            "MEDIA_ASSET_UPLOADED",
            "SUBMISSION_UPDATED",
            "BACKGROUND_JOB_RUN");

    private final AuditLogRepository auditLogRepository;
    private final ScheduledJobHealthService scheduledJobHealthService;

    @Value("${app.audit.retention.operational-days:730}")
    private int operationalRetentionDays = 730;

    public AuditLogRetentionJob(
            AuditLogRepository auditLogRepository,
            ScheduledJobHealthService scheduledJobHealthService) {
        this.auditLogRepository = auditLogRepository;
        this.scheduledJobHealthService = scheduledJobHealthService;
    }

    @Scheduled(cron = "${app.audit.retention.prune-cron:0 45 3 * * *}", zone = "UTC")
    @Transactional
    public void pruneOperationalRows() {
        Instant startedAt = Instant.now();
        try {
            Instant cutoff = startedAt.minus(operationalRetentionDays, ChronoUnit.DAYS);
            int removed = auditLogRepository.deleteByActionInAndCreatedAtBefore(PRUNABLE_ACTIONS, cutoff);
            if (removed > 0) {
                log.info("AuditLogRetentionJob: pruned {} operational audit row(s) older than {} days.",
                        removed, operationalRetentionDays);
            }
            scheduledJobHealthService.recordSuccess("AuditLogRetentionJob", startedAt);
        } catch (Exception ex) {
            log.error("AuditLogRetentionJob failed: {}", ex.getMessage(), ex);
            scheduledJobHealthService.recordFailure("AuditLogRetentionJob", startedAt, ex);
        }
    }
}
