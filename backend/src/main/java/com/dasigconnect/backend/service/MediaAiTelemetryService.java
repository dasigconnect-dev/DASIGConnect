package com.dasigconnect.backend.service;

import com.dasigconnect.backend.external.VoyageAIClient;
import com.dasigconnect.backend.model.dto.systemhealth.MediaAiStageMetricDto;
import com.dasigconnect.backend.model.dto.systemhealth.MediaEmbeddingCoverageDto;
import com.dasigconnect.backend.model.dto.systemhealth.HealthStatus;
import com.dasigconnect.backend.model.dto.systemhealth.OperationalMetricDto;
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
    private final VoyageAIClient voyageAIClient;

    public MediaAiTelemetryService(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            VoyageAIClient voyageAIClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.voyageAIClient = voyageAIClient;
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

    public void recordRankingShadow(
            UUID institutionId,
            UUID submissionId,
            int legacyResultCount,
            int hybridResultCount,
            int overlapCount,
            boolean topResultChanged) {
        try {
            writeTransaction.executeWithoutResult(status -> jdbcTemplate.update("""
                    INSERT INTO media_ai_ranking_shadow_metrics
                        (institution_id, submission_id, legacy_result_count,
                         hybrid_result_count, overlap_count, top_result_changed)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, institutionId, submissionId,
                    Math.max(0, legacyResultCount), Math.max(0, hybridResultCount),
                    Math.max(0, overlapCount), topResultChanged));
        } catch (RuntimeException error) {
            log.debug("Media AI ranking shadow metric write skipped: {}", error.getMessage());
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
                       100.0 * COUNT(*) FILTER (
                           WHERE outcome IN ('FAILURE', 'ERROR', 'EMBEDDING_FAILED')
                       ) / COUNT(*) AS failure_rate,
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

    @Transactional(readOnly = true)
    public List<MediaEmbeddingCoverageDto> embeddingCoverage() {
        return jdbcTemplate.query("""
                WITH asset_scope AS (
                    SELECT asset.id,
                           asset.status,
                           asset.storage_url,
                           asset.semantic_revision,
                           COALESCE(asset.institution_id,
                                    MIN(submission.institution_id::text)::uuid) AS institution_id
                    FROM media_assets asset
                    LEFT JOIN submission_media_assets selected_media
                      ON selected_media.media_asset_id = asset.id
                    LEFT JOIN submissions submission
                      ON submission.id = selected_media.submission_id
                    WHERE asset.deleted_at IS NULL
                      AND asset.file_type IN ('jpeg', 'png', 'webp', 'gif')
                    GROUP BY asset.id, asset.status, asset.institution_id,
                             asset.storage_url, asset.semantic_revision
                ), embedding_flags AS (
                    SELECT scoped.id,
                           scoped.status,
                           scoped.institution_id,
                           BOOL_OR(embedding.embedding_type = 'image'
                               AND embedding.model = ?
                               AND embedding.processing_version = ?
                               AND embedding.source_revision = 0
                               AND embedding.source_input_hash =
                                   encode(digest(btrim(scoped.storage_url), 'sha256'), 'hex')) AS has_image,
                           BOOL_OR(embedding.embedding_type = 'semantic'
                               AND embedding.model = ?
                               AND embedding.processing_version = ?
                               AND embedding.source_revision = scoped.semantic_revision) AS has_semantic
                    FROM asset_scope scoped
                    LEFT JOIN media_asset_embeddings embedding ON embedding.asset_id = scoped.id
                    GROUP BY scoped.id, scoped.status, scoped.institution_id,
                             scoped.storage_url, scoped.semantic_revision
                )
                SELECT flags.institution_id,
                       COALESCE(institution.name, 'Unassigned') AS institution_name,
                       flags.status AS asset_status,
                       COUNT(*) AS eligible_assets,
                       COUNT(*) FILTER (WHERE flags.has_image) AS image_embeddings,
                       COUNT(*) FILTER (WHERE flags.has_semantic) AS semantic_embeddings,
                       100.0 * COUNT(*) FILTER (WHERE flags.has_image) / COUNT(*) AS image_coverage,
                       100.0 * COUNT(*) FILTER (WHERE flags.has_semantic) / COUNT(*) AS semantic_coverage
                FROM embedding_flags flags
                LEFT JOIN institutions institution ON institution.id = flags.institution_id
                GROUP BY flags.institution_id, institution.name, flags.status
                ORDER BY institution_name, asset_status
                """, (rs, rowNum) -> new MediaEmbeddingCoverageDto(
                        rs.getObject("institution_id", UUID.class),
                        rs.getString("institution_name"),
                        rs.getString("asset_status"),
                        rs.getLong("eligible_assets"),
                        rs.getLong("image_embeddings"),
                        rs.getLong("semantic_embeddings"),
                        round(rs.getDouble("image_coverage")),
                        round(rs.getDouble("semantic_coverage")),
                        voyageAIClient.multimodalModelName(),
                        MediaProcessingQueueService.IMAGE_EMBEDDING_VERSION,
                        voyageAIClient.modelName(),
                        MediaProcessingQueueService.SEMANTIC_EMBEDDING_VERSION),
                voyageAIClient.multimodalModelName(),
                MediaProcessingQueueService.IMAGE_EMBEDDING_VERSION,
                voyageAIClient.modelName(),
                MediaProcessingQueueService.SEMANTIC_EMBEDDING_VERSION);
    }

    /** Content-free metrics rendered by the existing System Health performance cards. */
    public List<OperationalMetricDto> operationalMetrics(int requestedDays) {
        int days = Math.max(1, Math.min(requestedDays, 90));
        Instant cutoff = Instant.now().minus(Duration.ofDays(days));
        List<OperationalMetricDto> metrics = new java.util.ArrayList<>();
        metrics.addAll(queueMetrics(cutoff));
        metrics.add(voyageFailureMetric(cutoff));
        metrics.add(embeddingCoverageMetric());
        metrics.add(suggestionNoResultMetric(cutoff));
        metrics.addAll(suggestionAdoptionMetrics(cutoff));
        metrics.add(shadowComparisonMetric(cutoff));
        return List.copyOf(metrics);
    }

    private List<OperationalMetricDto> queueMetrics(Instant cutoff) {
        try {
            java.util.Map<String, Object> row = jdbcTemplate.queryForMap("""
                    SELECT COUNT(*) FILTER (WHERE status IN ('PENDING', 'PROCESSING', 'RETRY')) AS active,
                           COUNT(*) FILTER (WHERE status = 'PENDING') AS pending,
                           COUNT(*) FILTER (WHERE status = 'PROCESSING') AS processing,
                           COUNT(*) FILTER (WHERE status = 'RETRY') AS retrying,
                           COUNT(*) FILTER (WHERE status = 'DEAD') AS dead,
                           COUNT(*) FILTER (
                               WHERE status = 'PROCESSING' AND lease_until < NOW()
                           ) AS stalled,
                           COALESCE(EXTRACT(EPOCH FROM (
                               NOW() - MIN(created_at) FILTER (
                                   WHERE status IN ('PENDING', 'PROCESSING', 'RETRY')
                               )
                           )), 0) AS oldest_age_seconds,
                           COUNT(*) FILTER (
                               WHERE status = 'COMPLETED' AND completed_at >= ?
                           ) AS completed_window,
                           COUNT(*) FILTER (
                               WHERE status = 'DEAD' AND updated_at >= ?
                           ) AS dead_window
                    FROM media_processing_jobs
                    """, Timestamp.from(cutoff), Timestamp.from(cutoff));
            long active = number(row, "active");
            long pending = number(row, "pending");
            long processing = number(row, "processing");
            long retrying = number(row, "retrying");
            long dead = number(row, "dead");
            long stalled = number(row, "stalled");
            double oldestAge = decimal(row, "oldest_age_seconds");
            long completed = number(row, "completed_window");
            long deadWindow = number(row, "dead_window");
            HealthStatus queueStatus = dead > 0 || stalled > 0
                    ? HealthStatus.UNHEALTHY
                    : active > 50 || oldestAge > 300
                            ? HealthStatus.WARNING : HealthStatus.HEALTHY;
            OperationalMetricDto queue = metric(
                    "media_ai_queue_depth", "Media AI queue depth", active, "jobs", active,
                    queueStatus,
                    "Pending " + pending + ", processing " + processing + ", retrying " + retrying
                            + ", dead letters " + dead + ", stalled leases " + stalled
                            + "; oldest active job " + Math.round(oldestAge) + " seconds.");

            long terminal = completed + deadWindow;
            double completionRate = terminal == 0 ? 0.0 : completed * 100.0 / terminal;
            OperationalMetricDto completion = terminal == 0
                    ? metric("media_ai_queue_completion_rate", "Media AI queue completion rate",
                            0, "percent", 0, HealthStatus.HEALTHY,
                            "No media AI jobs reached a terminal state in the selected window.")
                    : metric("media_ai_queue_completion_rate", "Media AI queue completion rate",
                            completionRate, "percent", terminal,
                            completionRate < 95 ? HealthStatus.WARNING : HealthStatus.HEALTHY,
                            "Completed jobs divided by completed plus dead-lettered jobs in the selected window.");
            return List.of(queue, completion);
        } catch (RuntimeException error) {
            return List.of(unavailable("media_ai_queue_depth", "Media AI queue depth", "jobs"),
                    unavailable("media_ai_queue_completion_rate", "Media AI queue completion rate", "percent"));
        }
    }

    private OperationalMetricDto voyageFailureMetric(Instant cutoff) {
        try {
            java.util.Map<String, Object> row = jdbcTemplate.queryForMap("""
                    SELECT COUNT(*) AS samples,
                           COUNT(*) FILTER (
                               WHERE outcome IN ('FAILURE', 'ERROR', 'EMBEDDING_FAILED')
                           ) AS failures,
                           COALESCE(SUM(provider_call_count), 0) AS provider_calls,
                           COALESCE(SUM(duplicate_call_avoided_count), 0) AS reused
                    FROM media_ai_processing_metrics
                    WHERE stage IN ('VOYAGE_IMAGE_EMBEDDING', 'VOYAGE_SEMANTIC_EMBEDDING')
                      AND created_at >= ?
                    """, Timestamp.from(cutoff));
            long samples = number(row, "samples");
            long failures = number(row, "failures");
            long calls = number(row, "provider_calls");
            long reused = number(row, "reused");
            double rate = samples == 0 ? 0.0 : failures * 100.0 / samples;
            return metric("media_ai_voyage_failure_rate", "Voyage embedding failure rate",
                    rate, "percent", samples,
                    rate > 10 ? HealthStatus.WARNING : HealthStatus.HEALTHY,
                    calls + " provider calls, " + reused + " duplicate calls avoided, "
                            + failures + " failed measurements.");
        } catch (RuntimeException error) {
            return unavailable("media_ai_voyage_failure_rate", "Voyage embedding failure rate", "percent");
        }
    }

    private OperationalMetricDto embeddingCoverageMetric() {
        try {
            java.util.Map<String, Object> row = jdbcTemplate.queryForMap("""
                    WITH eligible AS (
                        SELECT id, storage_url, semantic_revision
                        FROM media_assets
                        WHERE deleted_at IS NULL
                          AND status = 'READY'
                          AND file_type IN ('jpeg', 'png', 'webp', 'gif')
                    )
                    SELECT COUNT(*) AS eligible,
                           COUNT(*) FILTER (WHERE EXISTS (
                               SELECT 1 FROM media_asset_embeddings embedding
                               WHERE embedding.asset_id = eligible.id
                                 AND embedding.embedding_type = 'image'
                                 AND embedding.model = ?
                                 AND embedding.processing_version = ?
                                 AND embedding.source_revision = 0
                                 AND embedding.source_input_hash = encode(
                                     digest(COALESCE(BTRIM(eligible.storage_url), ''), 'sha256'), 'hex')
                           )) AS image_ready,
                           COUNT(*) FILTER (WHERE EXISTS (
                               SELECT 1 FROM media_asset_embeddings embedding
                               WHERE embedding.asset_id = eligible.id
                                 AND embedding.embedding_type = 'semantic'
                                 AND embedding.model = ?
                                 AND embedding.processing_version = ?
                                 AND embedding.source_revision = eligible.semantic_revision
                           )) AS semantic_ready
                    FROM eligible
                    """, voyageAIClient.multimodalModelName(),
                    MediaProcessingQueueService.IMAGE_EMBEDDING_VERSION,
                    voyageAIClient.modelName(),
                    MediaProcessingQueueService.SEMANTIC_EMBEDDING_VERSION);
            long eligible = number(row, "eligible");
            long image = number(row, "image_ready");
            long semantic = number(row, "semantic_ready");
            double imageRate = eligible == 0 ? 0.0 : image * 100.0 / eligible;
            double semanticRate = eligible == 0 ? 0.0 : semantic * 100.0 / eligible;
            double minimumRate = Math.min(imageRate, semanticRate);
            return metric("media_ai_embedding_coverage", "Current embedding coverage",
                    minimumRate, "percent", eligible,
                    eligible > 0 && minimumRate < 90 ? HealthStatus.WARNING : HealthStatus.HEALTHY,
                    "Image " + round(imageRate) + "% (" + voyageAIClient.multimodalModelName()
                            + "), semantic " + round(semanticRate) + "% (" + voyageAIClient.modelName() + ").");
        } catch (RuntimeException error) {
            return unavailable("media_ai_embedding_coverage", "Current embedding coverage", "percent");
        }
    }

    private OperationalMetricDto suggestionNoResultMetric(Instant cutoff) {
        try {
            java.util.Map<String, Object> row = jdbcTemplate.queryForMap("""
                    SELECT COUNT(*) AS samples,
                           COUNT(*) FILTER (WHERE outcome = 'NO_RELEVANT_MATCHES') AS no_match,
                           COUNT(*) FILTER (WHERE outcome = 'NO_INDEXED_CANDIDATES') AS no_index,
                           COUNT(*) FILTER (
                               WHERE outcome IN ('ERROR', 'EMBEDDING_FAILED', 'FAILURE')
                           ) AS errors
                    FROM media_ai_processing_metrics
                    WHERE stage = 'AI_SUGGESTION_QUERY' AND created_at >= ?
                    """, Timestamp.from(cutoff));
            long samples = number(row, "samples");
            long noMatch = number(row, "no_match");
            long noIndex = number(row, "no_index");
            long errors = number(row, "errors");
            double rate = samples == 0 ? 0.0 : noMatch * 100.0 / samples;
            HealthStatus status = errors > 0 || noIndex > 0 ? HealthStatus.WARNING : HealthStatus.HEALTHY;
            return metric("media_ai_suggestion_no_result_rate", "AI suggestion no-result rate",
                    rate, "percent", samples, status,
                    noMatch + " genuine no-match responses, " + noIndex
                            + " requests without indexed candidates, " + errors + " failures.");
        } catch (RuntimeException error) {
            return unavailable("media_ai_suggestion_no_result_rate", "AI suggestion no-result rate", "percent");
        }
    }

    private List<OperationalMetricDto> suggestionAdoptionMetrics(Instant cutoff) {
        try {
            java.util.Map<String, Object> row = jdbcTemplate.queryForMap("""
                    SELECT COUNT(*) FILTER (WHERE action_taken = 'shown') AS shown,
                           COUNT(*) FILTER (WHERE action_taken = 'accepted') AS accepted,
                           COUNT(*) FILTER (WHERE action_taken = 'dismissed') AS dismissed
                    FROM ai_interaction_log
                    WHERE interaction_type = 'media_recommendation' AND created_at >= ?
                    """, Timestamp.from(cutoff));
            long shown = number(row, "shown");
            long accepted = number(row, "accepted");
            long dismissed = number(row, "dismissed");
            double acceptance = shown == 0 ? 0.0 : Math.min(100.0, accepted * 100.0 / shown);
            double dismissal = shown == 0 ? 0.0 : Math.min(100.0, dismissed * 100.0 / shown);
            return List.of(
                    metric("media_ai_suggestion_acceptance_rate", "AI suggestion acceptance rate",
                            acceptance, "percent", shown, HealthStatus.HEALTHY,
                            accepted + " accepted interactions from " + shown + " shown result sets."),
                    metric("media_ai_suggestion_dismissal_rate", "AI suggestion dismissal rate",
                            dismissal, "percent", shown, HealthStatus.HEALTHY,
                            dismissed + " dismissed interactions from " + shown + " shown result sets."));
        } catch (RuntimeException error) {
            return List.of(
                    unavailable("media_ai_suggestion_acceptance_rate", "AI suggestion acceptance rate", "percent"),
                    unavailable("media_ai_suggestion_dismissal_rate", "AI suggestion dismissal rate", "percent"));
        }
    }

    private OperationalMetricDto shadowComparisonMetric(Instant cutoff) {
        try {
            java.util.Map<String, Object> row = jdbcTemplate.queryForMap("""
                    SELECT COUNT(*) AS samples,
                           100.0 * COUNT(*) FILTER (WHERE top_result_changed) / NULLIF(COUNT(*), 0)
                               AS top_change_rate,
                           100.0 * AVG(
                               CASE WHEN GREATEST(legacy_result_count, hybrid_result_count) = 0 THEN 1.0
                                    ELSE overlap_count::double precision
                                         / GREATEST(legacy_result_count, hybrid_result_count)
                               END
                           ) AS overlap_rate
                    FROM media_ai_ranking_shadow_metrics
                    WHERE created_at >= ?
                    """, Timestamp.from(cutoff));
            long samples = number(row, "samples");
            double changeRate = decimal(row, "top_change_rate");
            double overlapRate = decimal(row, "overlap_rate");
            return metric("media_ai_shadow_top_change_rate", "Hybrid ranking shadow comparison",
                    changeRate, "percent", samples, HealthStatus.HEALTHY,
                    "Top result changed in " + round(changeRate) + "% of shadow runs; top-set overlap "
                            + round(overlapRate) + "%.");
        } catch (RuntimeException error) {
            return unavailable("media_ai_shadow_top_change_rate", "Hybrid ranking shadow comparison", "percent");
        }
    }

    private static OperationalMetricDto metric(
            String key, String label, double value, String unit, long sampleSize,
            HealthStatus status, String detail) {
        return new OperationalMetricDto(key, label, status, round(value), unit, sampleSize, detail);
    }

    private static OperationalMetricDto unavailable(String key, String label, String unit) {
        return metric(key, label, 0, unit, 0, HealthStatus.UNAVAILABLE,
                "Media AI monitoring data could not be retrieved.");
    }

    private static long number(java.util.Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static double decimal(java.util.Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    public static long elapsedMillis(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
