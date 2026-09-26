package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.systemhealth.MediaAiStageMetricDto;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Best-effort, content-free production telemetry for the media AI pipeline. */
@Service
public class MediaAiTelemetryService {

    private static final Logger log = LoggerFactory.getLogger(MediaAiTelemetryService.class);
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate writeTransaction;

    public MediaAiTelemetryService(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.writeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(String stage, long durationMs, String outcome, UUID assetId,
            UUID submissionId, int attemptNumber, int providerCalls, int duplicateCallsAvoided) {
        try {
            writeTransaction.executeWithoutResult(status -> jdbcTemplate.update("""
                        INSERT INTO media_ai_processing_metrics
                            (stage, outcome, duration_ms, asset_id, submission_id, attempt_number,
                             provider_call_count, duplicate_call_avoided_count)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """, stage, outcome, Math.max(0, durationMs), assetId, submissionId,
                        Math.max(1, attemptNumber), Math.max(0, providerCalls),
                        Math.max(0, duplicateCallsAvoided)));
        } catch (RuntimeException error) {
            log.debug("Media AI telemetry write skipped for stage {}: {}", stage, error.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<MediaAiStageMetricDto> aggregate(int requestedDays) {
        int days = Math.max(1, Math.min(requestedDays, 90));
        Instant cutoff = Instant.now().minus(Duration.ofDays(days));
        return jdbcTemplate.query("""
                SELECT stage,
                       COUNT(*) AS sample_size,
                       AVG(duration_ms) AS average_ms,
                       percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_ms) AS p95_ms,
                       100.0 * COUNT(*) FILTER (WHERE outcome = 'FAILURE') / COUNT(*) AS failure_rate,
                       100.0 * COUNT(*) FILTER (WHERE attempt_number > 1) / COUNT(*) AS retry_rate,
                       COUNT(DISTINCT asset_id) AS distinct_assets,
                       COALESCE(SUM(provider_call_count), 0) AS provider_calls,
                       CASE WHEN COUNT(DISTINCT asset_id) = 0 THEN 0
                            ELSE 1.0 * SUM(provider_call_count) / COUNT(DISTINCT asset_id)
                       END AS provider_calls_per_asset,
                       COALESCE(SUM(duplicate_call_avoided_count), 0) AS duplicate_calls_avoided
                FROM media_ai_processing_metrics
                WHERE created_at >= ?
                GROUP BY stage
                ORDER BY stage
                """, (rs, rowNum) -> new MediaAiStageMetricDto(
                        rs.getString("stage"),
                        rs.getLong("sample_size"),
                        round(rs.getDouble("average_ms")),
                        round(rs.getDouble("p95_ms")),
                        round(rs.getDouble("failure_rate")),
                        round(rs.getDouble("retry_rate")),
                        rs.getLong("distinct_assets"),
                        rs.getLong("provider_calls"),
                        round(rs.getDouble("provider_calls_per_asset")),
                        rs.getLong("duplicate_calls_avoided")),
                Timestamp.from(cutoff));
    }

    public static long elapsedMillis(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
