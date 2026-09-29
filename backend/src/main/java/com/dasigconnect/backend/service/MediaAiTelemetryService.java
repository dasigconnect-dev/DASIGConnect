package com.dasigconnect.backend.service;

import com.dasigconnect.backend.external.VoyageAIClient;
import com.dasigconnect.backend.model.dto.systemhealth.MediaAiStageMetricDto;
import com.dasigconnect.backend.model.dto.systemhealth.MediaEmbeddingCoverageDto;
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
                        round(rs.getDouble("semantic_coverage"))),
                voyageAIClient.multimodalModelName(),
                MediaProcessingQueueService.IMAGE_EMBEDDING_VERSION,
                voyageAIClient.modelName(),
                MediaProcessingQueueService.SEMANTIC_EMBEDDING_VERSION);
    }

    public static long elapsedMillis(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
