package com.dasigconnect.backend.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dasigconnect.backend.model.dto.analytics.InstitutionPostsDto;

@Repository
public class AnalyticsRepository {

    private static final String PUBLISHED_STATES = "'published', 'published_manual'";
    private static final String REPORTING_STATES = "'published', 'published_manual', 'admin_direct_post'";

    private final NamedParameterJdbcTemplate jdbc;

    public AnalyticsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PostingDelayStats averagePostingDelay(Instant start, Instant end, AnalyticsScope scope) {
        String sql = """
            SELECT COALESCE(AVG(EXTRACT(EPOCH FROM (s.published_at - s.submitted_at)) / 86400.0), 0) AS avg_days,
                   COUNT(*) AS sample_size
            FROM submissions s
            WHERE s.status IN (%s)
              AND s.published_at >= :start
              AND s.published_at < :end
              AND s.submitted_at IS NOT NULL
              %s
            """.formatted(PUBLISHED_STATES, scope.submissionFilter("s"));
        return jdbc.queryForObject(sql, params(start, end, scope), (rs, rowNum) ->
                new PostingDelayStats(rs.getDouble("avg_days"), rs.getLong("sample_size")));
    }

    public CompletenessStats contentCompleteness(Instant start, Instant end, AnalyticsScope scope) {
        String sql = """
            SELECT COUNT(*) AS total_count,
                   COALESCE(SUM(CASE WHEN
                       s.event_title IS NOT NULL
                       AND s.event_date IS NOT NULL
                       AND s.caption IS NOT NULL
                       AND LENGTH(TRIM(s.caption)) > 0
                       AND EXISTS (
                           SELECT 1 FROM submission_media_assets sma
                           WHERE sma.submission_id = s.id
                       )
                   THEN 1 ELSE 0 END), 0) AS complete_count
            FROM submissions s
            WHERE s.status IN (%s)
              AND s.published_at >= :start
              AND s.published_at < :end
              %s
            """.formatted(PUBLISHED_STATES, scope.submissionFilter("s"));
        return jdbc.queryForObject(sql, params(start, end, scope), (rs, rowNum) ->
                new CompletenessStats(rs.getLong("complete_count"), rs.getLong("total_count")));
    }

    public PublishedPostStats publishedPostStats(Instant start, Instant end, AnalyticsScope scope) {
        String sql = """
            SELECT COUNT(*) AS total_count,
                   COALESCE(SUM(CASE WHEN s.status = 'published' THEN 1 ELSE 0 END), 0) AS automated_count,
                   COALESCE(SUM(CASE WHEN s.status = 'published_manual' THEN 1 ELSE 0 END), 0) AS manual_count,
                   COALESCE(SUM(CASE WHEN s.status = 'admin_direct_post' THEN 1 ELSE 0 END), 0) AS admin_direct_count
            FROM submissions s
            WHERE s.status IN (%s)
              AND s.published_at >= :start
              AND s.published_at < :end
              %s
            """.formatted(REPORTING_STATES, scope.submissionFilter("s"));
        return jdbc.queryForObject(sql, params(start, end, scope), (rs, rowNum) ->
                new PublishedPostStats(
                        rs.getLong("total_count"),
                        rs.getLong("automated_count"),
                        rs.getLong("manual_count"),
                        rs.getLong("admin_direct_count")));
    }

    public List<InstitutionPostsDto> postsByInstitution(Instant start, Instant end, AnalyticsScope scope) {
        String sql = """
            SELECT i.id AS institution_id,
                   i.name AS institution_name,
                   COUNT(s.id) AS total_count,
                   COALESCE(SUM(CASE WHEN s.status = 'published' THEN 1 ELSE 0 END), 0) AS automated_count,
                   COALESCE(SUM(CASE WHEN s.status = 'published_manual' THEN 1 ELSE 0 END), 0) AS manual_count,
                   COALESCE(SUM(CASE WHEN s.status = 'admin_direct_post' THEN 1 ELSE 0 END), 0) AS admin_direct_count
            FROM submissions s
            JOIN institutions i ON i.id = s.institution_id
            WHERE s.status IN (%s)
              AND s.published_at >= :start
              AND s.published_at < :end
              %s
            GROUP BY i.id, i.name
            ORDER BY total_count DESC, i.name ASC
            """.formatted(REPORTING_STATES, scope.submissionFilter("s"));
        return jdbc.query(sql, params(start, end, scope), (rs, rowNum) ->
                new InstitutionPostsDto(
                        rs.getObject("institution_id", UUID.class),
                        rs.getString("institution_name"),
                        rs.getLong("total_count"),
                        rs.getLong("automated_count"),
                        rs.getLong("manual_count"),
                        rs.getLong("admin_direct_count")));
    }

    public AiStats aiPerformance(Instant start, Instant end, AnalyticsScope scope) {
        String sql = """
            SELECT
                COALESCE(SUM(CASE WHEN ail.interaction_type = 'caption_suggestion' THEN 1 ELSE 0 END), 0) AS caption_total,
                COALESCE(SUM(CASE WHEN ail.interaction_type = 'caption_suggestion'
                    AND ail.action_taken IN ('use', 'use_then_edited') THEN 1 ELSE 0 END), 0) AS caption_accepted,
                COALESCE(SUM(CASE WHEN ail.interaction_type = 'tag_classification' THEN 1 ELSE 0 END), 0) AS tag_total,
                COALESCE(SUM(CASE WHEN ail.interaction_type = 'tag_classification'
                    AND ail.action_taken IN ('manual_correction', 'edited', 'corrected') THEN 1 ELSE 0 END), 0) AS tag_corrected,
                COALESCE(SUM(CASE WHEN ail.interaction_type = 'media_recommendation' THEN 1 ELSE 0 END), 0) AS media_total,
                COALESCE(SUM(CASE WHEN ail.interaction_type = 'media_recommendation'
                    AND ail.action_taken IN ('relevant', 'highly_relevant', 'use', 'used') THEN 1 ELSE 0 END), 0) AS media_relevant
            FROM ai_interaction_log ail
            JOIN submissions s ON s.id = ail.submission_id
            WHERE ail.created_at >= :start
              AND ail.created_at < :end
              %s
            """.formatted(scope.submissionFilter("s"));
        return jdbc.queryForObject(sql, params(start, end, scope), (rs, rowNum) ->
                new AiStats(
                        rs.getLong("caption_total"),
                        rs.getLong("caption_accepted"),
                        rs.getLong("tag_total"),
                        rs.getLong("tag_corrected"),
                        rs.getLong("media_total"),
                        rs.getLong("media_relevant")));
    }

    public OperationalStats operationalHealth(Instant start, Instant end, Instant now, AnalyticsScope scope) {
        MapSqlParameterSource params = params(start, end, scope).addValue("deadlineCutoff", Timestamp.from(now.plusSeconds(1800)));
        String sql = """
            SELECT
                (SELECT COUNT(*) FROM submissions s
                 WHERE s.submitted_at >= :start AND s.submitted_at < :end %1$s) AS workflow_count,
                (SELECT COUNT(*) FROM submissions s
                 WHERE s.status IN ('pending', 'in_review')
                   AND s.scheduled_at IS NOT NULL
                   AND s.scheduled_at <= :deadlineCutoff
                   %1$s) AS deadline_risk_count,
                (SELECT COUNT(*) FROM audit_log al
                 LEFT JOIN users actor ON actor.id = al.actor_id
                 WHERE al.created_at >= :start AND al.created_at < :end
                   AND UPPER(al.action) LIKE '%%OVERRIDE%%'
                   %2$s) AS override_count,
                (SELECT COUNT(*) FROM publication_attempts pa
                 JOIN submissions s ON s.id = pa.submission_id
                 WHERE pa.attempted_at >= :start AND pa.attempted_at < :end %1$s) AS attempt_count,
                (SELECT COUNT(*) FROM publication_attempts pa
                 JOIN submissions s ON s.id = pa.submission_id
                 WHERE pa.attempted_at >= :start AND pa.attempted_at < :end
                   AND pa.result = 'success'
                   %1$s) AS success_count,
                (SELECT COUNT(*) FROM submissions s
                 WHERE s.status IN ('published', 'published_manual')
                   AND s.published_at >= :start AND s.published_at < :end
                   AND s.scheduled_at IS NOT NULL
                   AND ABS(EXTRACT(EPOCH FROM (s.published_at - s.scheduled_at))) <= 300
                   %1$s) AS on_time_count,
                (SELECT COUNT(*) FROM audit_log al
                 LEFT JOIN users actor ON actor.id = al.actor_id
                 WHERE al.created_at >= :start AND al.created_at < :end
                   AND actor.role = 'administrator'
                   %2$s) AS admin_action_count
            """.formatted(scope.submissionFilter("s"), scope.auditFilter("actor"));
        return jdbc.queryForObject(sql, params, (rs, rowNum) ->
                new OperationalStats(
                        rs.getLong("workflow_count"),
                        rs.getLong("deadline_risk_count"),
                        rs.getLong("override_count"),
                        rs.getLong("attempt_count"),
                        rs.getLong("success_count"),
                        rs.getLong("on_time_count"),
                        rs.getLong("admin_action_count")));
    }

    public List<Map<String, Object>> exportRows(String metric, Instant start, Instant end, AnalyticsScope scope) {
        return switch (metric) {
            case "posting-delay" -> exportPostingDelay(start, end, scope);
            case "content-completeness" -> exportCompleteness(start, end, scope);
            case "posts-by-institution" -> exportPostsByInstitution(start, end, scope);
            case "ai-performance" -> exportAiPerformance(start, end, scope);
            case "operational-health" -> exportOperationalHealth(start, end, scope);
            default -> throw new IllegalArgumentException("Unsupported analytics export metric: " + metric);
        };
    }

    private List<Map<String, Object>> exportPostingDelay(Instant start, Instant end, AnalyticsScope scope) {
        String sql = """
            SELECT s.id AS submission_id, i.name AS institution_name, s.status,
                   s.submitted_at, s.published_at,
                   ROUND(EXTRACT(EPOCH FROM (s.published_at - s.submitted_at)) / 86400.0, 4) AS delay_days
            FROM submissions s
            JOIN institutions i ON i.id = s.institution_id
            WHERE s.status IN (%s)
              AND s.published_at >= :start AND s.published_at < :end
              AND s.submitted_at IS NOT NULL
              %s
            ORDER BY s.published_at DESC
            """.formatted(PUBLISHED_STATES, scope.submissionFilter("s"));
        return jdbc.queryForList(sql, params(start, end, scope));
    }

    private List<Map<String, Object>> exportCompleteness(Instant start, Instant end, AnalyticsScope scope) {
        String sql = """
            SELECT s.id AS submission_id, i.name AS institution_name, s.status, s.published_at,
                   CASE WHEN s.event_title IS NOT NULL THEN true ELSE false END AS has_event_title,
                   CASE WHEN s.event_date IS NOT NULL THEN true ELSE false END AS has_event_date,
                   CASE WHEN s.caption IS NOT NULL AND LENGTH(TRIM(s.caption)) > 0 THEN true ELSE false END AS has_caption,
                   CASE WHEN EXISTS (SELECT 1 FROM submission_media_assets sma WHERE sma.submission_id = s.id)
                        THEN true ELSE false END AS has_media
            FROM submissions s
            JOIN institutions i ON i.id = s.institution_id
            WHERE s.status IN (%s)
              AND s.published_at >= :start AND s.published_at < :end
              %s
            ORDER BY s.published_at DESC
            """.formatted(PUBLISHED_STATES, scope.submissionFilter("s"));
        return jdbc.queryForList(sql, params(start, end, scope));
    }

    private List<Map<String, Object>> exportPostsByInstitution(Instant start, Instant end, AnalyticsScope scope) {
        String sql = """
            SELECT i.name AS institution_name, s.status, COUNT(*) AS post_count
            FROM submissions s
            JOIN institutions i ON i.id = s.institution_id
            WHERE s.status IN (%s)
              AND s.published_at >= :start AND s.published_at < :end
              %s
            GROUP BY i.name, s.status
            ORDER BY i.name ASC, s.status ASC
            """.formatted(REPORTING_STATES, scope.submissionFilter("s"));
        return jdbc.queryForList(sql, params(start, end, scope));
    }

    private List<Map<String, Object>> exportAiPerformance(Instant start, Instant end, AnalyticsScope scope) {
        String sql = """
            SELECT ail.interaction_type, ail.action_taken, COUNT(*) AS event_count
            FROM ai_interaction_log ail
            JOIN submissions s ON s.id = ail.submission_id
            WHERE ail.created_at >= :start AND ail.created_at < :end
              %s
            GROUP BY ail.interaction_type, ail.action_taken
            ORDER BY ail.interaction_type ASC, ail.action_taken ASC
            """.formatted(scope.submissionFilter("s"));
        return jdbc.queryForList(sql, params(start, end, scope));
    }

    private List<Map<String, Object>> exportOperationalHealth(Instant start, Instant end, AnalyticsScope scope) {
        OperationalStats stats = operationalHealth(start, end, Instant.now(), scope);
        return List.of(
                Map.of("metric", "submissions_entered_workflow", "value", stats.workflowCount()),
                Map.of("metric", "validation_deadline_risks", "value", stats.deadlineRiskCount()),
                Map.of("metric", "override_audit_events", "value", stats.overrideCount()),
                Map.of("metric", "publication_attempts", "value", stats.attemptCount()),
                Map.of("metric", "successful_publication_attempts", "value", stats.successCount()),
                Map.of("metric", "on_time_publications", "value", stats.onTimeCount()),
                Map.of("metric", "administrator_actions", "value", stats.adminActionCount()));
    }

    private MapSqlParameterSource params(Instant start, Instant end, AnalyticsScope scope) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("start", Timestamp.from(start))
                .addValue("end", Timestamp.from(end));
        if (scope.institutionId() != null) {
            params.addValue("institutionId", scope.institutionId());
        }
        if (scope.userId() != null) {
            params.addValue("userId", scope.userId());
        }
        return params;
    }

    public record AnalyticsScope(String role, UUID institutionId, UUID userId) {
        public String submissionFilter(String alias) {
            if ("administrator".equals(role)) {
                return "";
            }
            if ("validator".equals(role)) {
                return " AND " + alias + ".institution_id = :institutionId ";
            }
            return " AND " + alias + ".contributor_id = :userId ";
        }

        public String auditFilter(String actorAlias) {
            if ("administrator".equals(role)) {
                return "";
            }
            if ("validator".equals(role)) {
                return " AND " + actorAlias + ".institution_id = :institutionId ";
            }
            return " AND " + actorAlias + ".id = :userId ";
        }
    }

    public record PostingDelayStats(double averageDays, long sampleSize) {}
    public record CompletenessStats(long completeCount, long totalCount) {}
    public record PublishedPostStats(long totalCount, long automatedCount, long manualCount, long adminDirectCount) {}
    public record AiStats(long captionTotal, long captionAccepted, long tagTotal, long tagCorrected,
                          long mediaTotal, long mediaRelevant) {}
    public record OperationalStats(long workflowCount, long deadlineRiskCount, long overrideCount,
                                   long attemptCount, long successCount, long onTimeCount,
                                   long adminActionCount) {}
}
