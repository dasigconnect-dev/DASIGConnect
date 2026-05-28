package com.dasigconnect.backend.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dasigconnect.backend.model.dto.analytics.ContributorBreakdownDto;
import com.dasigconnect.backend.model.dto.analytics.CategoryPerformanceDto;
import com.dasigconnect.backend.model.dto.analytics.ContentIssueDto;
import com.dasigconnect.backend.model.dto.analytics.DailyAnalyticsPointDto;
import com.dasigconnect.backend.model.dto.analytics.InstitutionPostsDto;
import com.dasigconnect.backend.model.dto.analytics.InstitutionFilterOptionDto;
import com.dasigconnect.backend.model.dto.analytics.StatusBreakdownDto;
import com.dasigconnect.backend.model.dto.analytics.SubmissionAnalyticsRowDto;

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
            SELECT COALESCE(AVG(EXTRACT(EPOCH FROM (s.published_at - %1$s)) / 86400.0), 0) AS avg_days,
                   COUNT(*) AS sample_size
            FROM submissions s
            WHERE s.status IN (%2$s)
              AND s.published_at >= :start
              AND s.published_at < :end
              AND %1$s IS NOT NULL
              %3$s
            """.formatted(firstPendingExpression("s"), PUBLISHED_STATES, scope.submissionFilter("s"));
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
            SELECT COALESCE(SUM(CASE WHEN s.status IN (%s) THEN 1 ELSE 0 END), 0) AS workflow_count,
                   COALESCE(SUM(CASE WHEN s.status = 'published' THEN 1 ELSE 0 END), 0) AS automated_count,
                   COALESCE(SUM(CASE WHEN s.status = 'published_manual' THEN 1 ELSE 0 END), 0) AS manual_count,
                   COALESCE(SUM(CASE WHEN s.status = 'admin_direct_post' THEN 1 ELSE 0 END), 0) AS admin_direct_count
            FROM submissions s
            WHERE s.status IN (%s)
              AND s.published_at >= :start
              AND s.published_at < :end
              %s
            """.formatted(PUBLISHED_STATES, REPORTING_STATES, scope.submissionFilter("s"));
        return jdbc.queryForObject(sql, params(start, end, scope), (rs, rowNum) ->
                new PublishedPostStats(
                        rs.getLong("workflow_count"),
                        rs.getLong("automated_count"),
                        rs.getLong("manual_count"),
                        rs.getLong("admin_direct_count")));
    }

    public List<InstitutionPostsDto> postsByInstitution(Instant start, Instant end, AnalyticsScope scope) {
        String sql = """
            SELECT i.id AS institution_id,
                   i.name AS institution_name,
                   COALESCE(SUM(CASE WHEN s.status IN (%s) THEN 1 ELSE 0 END), 0) AS workflow_count,
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
            ORDER BY workflow_count DESC, i.name ASC
            """.formatted(PUBLISHED_STATES, REPORTING_STATES, scope.submissionFilter("s"));
        return jdbc.query(sql, params(start, end, scope), (rs, rowNum) ->
                new InstitutionPostsDto(
                        rs.getObject("institution_id", UUID.class),
                        rs.getString("institution_name"),
                        rs.getLong("workflow_count"),
                        rs.getLong("automated_count"),
                        rs.getLong("manual_count"),
                        rs.getLong("admin_direct_count")));
    }

    public List<ContributorBreakdownDto> contributorBreakdown(Instant start, Instant end, AnalyticsScope scope) {
        String sql = """
            SELECT u.id AS contributor_id,
                   COALESCE(NULLIF(TRIM(CONCAT(COALESCE(u.first_name, ''), ' ', COALESCE(u.last_name, ''))), ''), u.email) AS contributor_name,
                   COALESCE(SUM(CASE WHEN s.status <> 'draft' OR s.submitted_at IS NOT NULL THEN 1 ELSE 0 END), 0) AS posts_submitted,
                   COALESCE(SUM(CASE WHEN s.status IN (%1$s) THEN 1 ELSE 0 END), 0) AS posts_published,
                   COALESCE(SUM(CASE WHEN s.status = 'needs_revision' THEN 1 ELSE 0 END), 0) AS needs_revision_count,
                   (SELECT COUNT(*) FROM validation_logs vl
                    JOIN submissions vs ON vs.id = vl.submission_id
                    WHERE vs.contributor_id = u.id
                      AND vl.action = 'needs_revision'
                      AND vl.created_at >= :start
                      AND vl.created_at < :end) AS revision_cycles,
                   COALESCE(AVG(CASE WHEN
                       s.event_title IS NOT NULL
                       AND s.event_date IS NOT NULL
                       AND s.caption IS NOT NULL
                       AND LENGTH(TRIM(s.caption)) > 0
                       AND EXISTS (
                           SELECT 1 FROM submission_media_assets sma
                           WHERE sma.submission_id = s.id
                       )
                   THEN 100.0 ELSE 0.0 END), 0) AS completeness_rate,
                   COALESCE(AVG(CASE WHEN s.status IN (%1$s) AND s.published_at IS NOT NULL AND %2$s IS NOT NULL
                       THEN EXTRACT(EPOCH FROM (s.published_at - %2$s)) / 86400.0
                       ELSE NULL END), 0) AS avg_delay_days
            FROM submissions s
            JOIN users u ON u.id = s.contributor_id
            WHERE COALESCE(s.submitted_at, s.created_at) >= :start
              AND COALESCE(s.submitted_at, s.created_at) < :end
              %3$s
            GROUP BY u.id, contributor_name
            ORDER BY posts_submitted DESC, contributor_name ASC
            """.formatted(PUBLISHED_STATES, firstPendingExpression("s"), scope.contributorBreakdownFilter("s"));
        return jdbc.query(sql, params(start, end, scope), (rs, rowNum) ->
                new ContributorBreakdownDto(
                        rs.getObject("contributor_id", UUID.class),
                        rs.getString("contributor_name"),
                        rs.getLong("posts_submitted"),
                        rs.getLong("posts_published"),
                        rs.getLong("needs_revision_count"),
                        rs.getLong("revision_cycles"),
                        round(rs.getDouble("completeness_rate")),
                        round(rs.getDouble("avg_delay_days"))));
    }

    public ContributorStats contributorStats(Instant start, Instant end, AnalyticsScope scope) {
        String sql = """
            SELECT COALESCE(SUM(CASE WHEN s.status <> 'draft' OR s.submitted_at IS NOT NULL THEN 1 ELSE 0 END), 0) AS submitted_count,
                   COALESCE(SUM(CASE WHEN s.status IN (%1$s) THEN 1 ELSE 0 END), 0) AS published_count,
                   COALESCE(SUM(CASE WHEN s.status = 'needs_revision' THEN 1 ELSE 0 END), 0) AS needs_revision_count,
                   COALESCE(SUM(CASE WHEN s.status IN ('needs_revision', 'rejected') THEN 1 ELSE 0 END), 0) AS rejected_or_revision_count,
                   (SELECT COUNT(*) FROM validation_logs vl
                    JOIN submissions vs ON vs.id = vl.submission_id
                    WHERE vl.action = 'needs_revision'
                      AND vl.created_at >= :start
                      AND vl.created_at < :end
                      %2$s) AS revision_request_count
            FROM submissions s
            WHERE COALESCE(s.submitted_at, s.created_at) >= :start
              AND COALESCE(s.submitted_at, s.created_at) < :end
              %3$s
            """.formatted(PUBLISHED_STATES, scope.validationSubmissionFilter("vs"), scope.submissionFilter("s"));
        return jdbc.queryForObject(sql, params(start, end, scope), (rs, rowNum) ->
                new ContributorStats(
                        rs.getLong("submitted_count"),
                        rs.getLong("published_count"),
                        rs.getLong("revision_request_count"),
                        rs.getLong("rejected_or_revision_count")));
    }

    public ValidatorStats validatorStats(Instant start, Instant end, Instant now, AnalyticsScope scope) {
        MapSqlParameterSource params = params(start, end, scope)
                .addValue("agingCutoff", Timestamp.from(now.minusSeconds(86_400)));
        String sql = """
            SELECT
                (SELECT COUNT(*) FROM submissions s
                 WHERE COALESCE(s.submitted_at, s.created_at) >= :start
                   AND COALESCE(s.submitted_at, s.created_at) < :end
                   AND (s.status <> 'draft' OR s.submitted_at IS NOT NULL)
                   %1$s) AS submission_volume,
                (SELECT COUNT(*) FROM submissions s
                 WHERE s.status = 'pending' %1$s) AS pending_count,
                (SELECT COUNT(*) FROM submissions s
                 WHERE s.status = 'in_review' %1$s) AS in_review_count,
                (SELECT COALESCE(AVG(EXTRACT(EPOCH FROM (vl.created_at - %2$s)) / 86400.0), 0)
                 FROM validation_logs vl
                 JOIN submissions s ON s.id = vl.submission_id
                 WHERE vl.action IN ('approved', 'needs_revision', 'rejected')
                   AND vl.created_at >= :start
                   AND vl.created_at < :end
                   AND %2$s IS NOT NULL
                   %1$s) AS avg_turnaround_days,
                (SELECT COUNT(*) FROM submissions s
                 WHERE s.status IN ('pending', 'in_review')
                   AND COALESCE(s.submitted_at, s.created_at) <= :agingCutoff
                   %1$s) AS queue_aging_count
            """.formatted(scope.submissionFilter("s"), firstPendingExpression("s"));
        return jdbc.queryForObject(sql, params, (rs, rowNum) ->
                new ValidatorStats(
                        rs.getLong("submission_volume"),
                        rs.getLong("pending_count"),
                        rs.getLong("in_review_count"),
                        round(rs.getDouble("avg_turnaround_days")),
                        rs.getLong("queue_aging_count")));
    }

    public List<StatusBreakdownDto> statusBreakdown(AnalyticsScope scope) {
        String sql = """
            SELECT s.status, COUNT(*) AS status_count
            FROM submissions s
            WHERE 1 = 1 %s
            GROUP BY s.status
            ORDER BY s.status ASC
            """.formatted(scope.submissionFilter("s"));
        return jdbc.query(sql, params(Instant.EPOCH, Instant.now(), scope), (rs, rowNum) ->
                new StatusBreakdownDto(rs.getString("status"), rs.getLong("status_count")));
    }

    public List<ContentIssueDto> contentIssues(Instant start, Instant end, AnalyticsScope scope) {
        String sql = """
            SELECT issue, SUM(issue_count) AS issue_count
            FROM (
                SELECT 'Missing event title' AS issue, COUNT(*) AS issue_count FROM submissions s
                WHERE COALESCE(s.submitted_at, s.created_at) >= :start AND COALESCE(s.submitted_at, s.created_at) < :end
                  AND (s.event_title IS NULL OR LENGTH(TRIM(s.event_title)) = 0) %1$s
                UNION ALL
                SELECT 'Missing event date' AS issue, COUNT(*) AS issue_count FROM submissions s
                WHERE COALESCE(s.submitted_at, s.created_at) >= :start AND COALESCE(s.submitted_at, s.created_at) < :end
                  AND s.event_date IS NULL %1$s
                UNION ALL
                SELECT 'Missing caption' AS issue, COUNT(*) AS issue_count FROM submissions s
                WHERE COALESCE(s.submitted_at, s.created_at) >= :start AND COALESCE(s.submitted_at, s.created_at) < :end
                  AND (s.caption IS NULL OR LENGTH(TRIM(s.caption)) = 0) %1$s
                UNION ALL
                SELECT 'Missing media' AS issue, COUNT(*) AS issue_count FROM submissions s
                WHERE COALESCE(s.submitted_at, s.created_at) >= :start AND COALESCE(s.submitted_at, s.created_at) < :end
                  AND NOT EXISTS (SELECT 1 FROM submission_media_assets sma WHERE sma.submission_id = s.id) %1$s
            ) issues
            GROUP BY issue
            HAVING SUM(issue_count) > 0
            ORDER BY issue_count DESC, issue ASC
            """.formatted(scope.submissionFilter("s"));
        return jdbc.query(sql, params(start, end, scope), (rs, rowNum) ->
                new ContentIssueDto(rs.getString("issue"), rs.getLong("issue_count")));
    }

    public List<CategoryPerformanceDto> topCategories(Instant start, Instant end, AnalyticsScope scope) {
        String sql = """
            SELECT COALESCE(NULLIF(TRIM(s.category), ''), 'Uncategorized') AS category,
                   COUNT(*) AS post_count,
                   COALESCE(AVG(CASE WHEN
                       s.event_title IS NOT NULL
                       AND s.event_date IS NOT NULL
                       AND s.caption IS NOT NULL
                       AND LENGTH(TRIM(s.caption)) > 0
                       AND EXISTS (SELECT 1 FROM submission_media_assets sma WHERE sma.submission_id = s.id)
                   THEN 100.0 ELSE 0.0 END), 0) AS completeness_rate
            FROM submissions s
            WHERE s.status IN (%s)
              AND s.published_at >= :start
              AND s.published_at < :end
              %s
            GROUP BY category
            ORDER BY post_count DESC, completeness_rate DESC
            LIMIT 5
            """.formatted(PUBLISHED_STATES, scope.submissionFilter("s"));
        return jdbc.query(sql, params(start, end, scope), (rs, rowNum) ->
                new CategoryPerformanceDto(
                        rs.getString("category"),
                        rs.getLong("post_count"),
                        round(rs.getDouble("completeness_rate"))));
    }

    public List<InstitutionFilterOptionDto> institutionFilterOptions() {
        String sql = """
            SELECT i.id, i.name
            FROM institutions i
            ORDER BY i.name ASC
            """;
        return jdbc.query(sql, Map.of(), (rs, rowNum) ->
                new InstitutionFilterOptionDto(
                        rs.getObject("id", UUID.class),
                        rs.getString("name")));
    }

    public List<Double> postingDelaySparkline(Instant start, Instant end, AnalyticsScope scope) {
        String sql = """
            SELECT week_start,
                   COALESCE(AVG(EXTRACT(EPOCH FROM (s.published_at - %1$s)) / 86400.0), 0) AS value
            FROM generate_series(
                date_trunc('week', CAST(:end AS timestamptz)) - interval '11 weeks',
                date_trunc('week', CAST(:end AS timestamptz)),
                interval '1 week'
            ) AS week_start
            LEFT JOIN submissions s ON date_trunc('week', s.published_at) = week_start
                AND s.status IN (%2$s)
                AND s.published_at >= :start
                AND s.published_at < :end
                AND %1$s IS NOT NULL
                %3$s
            GROUP BY week_start
            ORDER BY week_start ASC
            """.formatted(firstPendingExpression("s"), PUBLISHED_STATES, scope.joinSubmissionFilter("s"));
        return jdbc.query(sql, params(start, end, scope), (rs, rowNum) -> round(rs.getDouble("value")));
    }

    public List<Double> completenessSparkline(Instant start, Instant end, AnalyticsScope scope) {
        String sql = """
            SELECT week_start,
                   CASE WHEN COUNT(s.id) = 0 THEN 0
                        ELSE COALESCE(SUM(CASE WHEN
                            s.event_title IS NOT NULL
                            AND s.event_date IS NOT NULL
                            AND s.caption IS NOT NULL
                            AND LENGTH(TRIM(s.caption)) > 0
                            AND EXISTS (
                                SELECT 1 FROM submission_media_assets sma
                                WHERE sma.submission_id = s.id
                            )
                        THEN 1 ELSE 0 END), 0) * 100.0 / COUNT(s.id)
                   END AS value
            FROM generate_series(
                date_trunc('week', CAST(:end AS timestamptz)) - interval '11 weeks',
                date_trunc('week', CAST(:end AS timestamptz)),
                interval '1 week'
            ) AS week_start
            LEFT JOIN submissions s ON date_trunc('week', s.published_at) = week_start
                AND s.status IN (%s)
                AND s.published_at >= :start
                AND s.published_at < :end
                %s
            GROUP BY week_start
            ORDER BY week_start ASC
            """.formatted(PUBLISHED_STATES, scope.joinSubmissionFilter("s"));
        return jdbc.query(sql, params(start, end, scope), (rs, rowNum) -> round(rs.getDouble("value")));
    }

    public List<Double> publishedPostsSparkline(Instant start, Instant end, AnalyticsScope scope) {
        String sql = """
            SELECT week_start,
                   COUNT(s.id)::double precision AS value
            FROM generate_series(
                date_trunc('week', CAST(:end AS timestamptz)) - interval '11 weeks',
                date_trunc('week', CAST(:end AS timestamptz)),
                interval '1 week'
            ) AS week_start
            LEFT JOIN submissions s ON date_trunc('week', s.published_at) = week_start
                AND s.status IN (%s)
                AND s.published_at >= :start
                AND s.published_at < :end
                %s
            GROUP BY week_start
            ORDER BY week_start ASC
            """.formatted(PUBLISHED_STATES, scope.joinSubmissionFilter("s"));
        return jdbc.query(sql, params(start, end, scope), (rs, rowNum) -> round(rs.getDouble("value")));
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
            """.formatted(scope.aiFilter("s"));
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

    public List<DailyAnalyticsPointDto> dailyBreakdown(String metric, Instant start, Instant end, AnalyticsScope scope) {
        return switch (metric) {
            case "posting-delay" -> dailyPostingDelay(start, end, scope);
            case "content-completeness" -> dailyCompleteness(start, end, scope);
            case "posts-by-institution" -> dailyPosts(start, end, scope);
            case "ai-performance" -> dailyAiPerformance(start, end, scope);
            case "operational-health" -> dailyOperationalHealth(start, end, scope);
            default -> throw new IllegalArgumentException("Unsupported analytics report metric: " + metric);
        };
    }

    public List<SubmissionAnalyticsRowDto> submissionReportRows(Instant start, Instant end, AnalyticsScope scope) {
        boolean showContributor = !"contributor".equals(scope.role());
        boolean showInstitution = "administrator".equals(scope.role());
        boolean showRevisionCycles = !"contributor".equals(scope.role());
        String sql = """
            SELECT s.id AS submission_id,
                   s.event_title,
                   %1$s AS first_submitted_at,
                   s.published_at,
                   s.status,
                   ROUND(EXTRACT(EPOCH FROM (s.published_at - %1$s)) / 86400.0, 4) AS delay_days,
                   CASE WHEN
                       s.event_title IS NOT NULL
                       AND s.event_date IS NOT NULL
                       AND s.caption IS NOT NULL
                       AND LENGTH(TRIM(s.caption)) > 0
                       AND EXISTS (
                           SELECT 1 FROM submission_media_assets sma
                           WHERE sma.submission_id = s.id
                       )
                   THEN true ELSE false END AS complete,
                   COALESCE(NULLIF(TRIM(CONCAT(COALESCE(u.first_name, ''), ' ', COALESCE(u.last_name, ''))), ''), u.email) AS contributor_name,
                   i.name AS institution_name,
                   (SELECT COUNT(*) FROM audit_log revision
                    WHERE revision.resource_id = s.id
                      AND UPPER(revision.action) LIKE '%%REVISION%%') AS revision_cycles
            FROM submissions s
            JOIN users u ON u.id = s.contributor_id
            JOIN institutions i ON i.id = s.institution_id
            WHERE s.status IN (%2$s)
              AND s.published_at >= :start
              AND s.published_at < :end
              AND %1$s IS NOT NULL
              %3$s
            ORDER BY s.published_at DESC
            """.formatted(firstPendingExpression("s"), PUBLISHED_STATES, scope.submissionFilter("s"));
        return jdbc.query(sql, params(start, end, scope), (rs, rowNum) ->
                new SubmissionAnalyticsRowDto(
                        rs.getObject("submission_id", UUID.class),
                        rs.getString("event_title"),
                        instantOrNull(rs.getTimestamp("first_submitted_at")),
                        instantOrNull(rs.getTimestamp("published_at")),
                        rs.getString("status"),
                        round(rs.getDouble("delay_days")),
                        rs.getBoolean("complete"),
                        showContributor ? rs.getString("contributor_name") : null,
                        showInstitution ? rs.getString("institution_name") : null,
                        showRevisionCycles ? rs.getLong("revision_cycles") : null));
    }

    private List<DailyAnalyticsPointDto> dailyPostingDelay(Instant start, Instant end, AnalyticsScope scope) {
        String sql = """
            SELECT CAST(day_start AS date) AS day,
                   COALESCE(AVG(EXTRACT(EPOCH FROM (s.published_at - %1$s)) / 86400.0), 0) AS value,
                   COUNT(s.id) AS secondary_value
            FROM generate_series(CAST(:start AS timestamptz), CAST(:end AS timestamptz), interval '1 day') AS day_start
            LEFT JOIN submissions s ON CAST(s.published_at AS date) = CAST(day_start AS date)
                AND s.status IN (%2$s)
                AND s.published_at >= :start
                AND s.published_at < :end
                AND %1$s IS NOT NULL
                %3$s
            GROUP BY day_start
            ORDER BY day_start ASC
            """.formatted(firstPendingExpression("s"), PUBLISHED_STATES, scope.joinSubmissionFilter("s"));
        return queryDaily(sql, start, end, scope);
    }

    private List<DailyAnalyticsPointDto> dailyCompleteness(Instant start, Instant end, AnalyticsScope scope) {
        String sql = """
            SELECT CAST(day_start AS date) AS day,
                   CASE WHEN COUNT(s.id) = 0 THEN 0
                        ELSE COALESCE(SUM(CASE WHEN
                            s.event_title IS NOT NULL
                            AND s.event_date IS NOT NULL
                            AND s.caption IS NOT NULL
                            AND LENGTH(TRIM(s.caption)) > 0
                            AND EXISTS (
                                SELECT 1 FROM submission_media_assets sma
                                WHERE sma.submission_id = s.id
                            )
                        THEN 1 ELSE 0 END), 0) * 100.0 / COUNT(s.id)
                   END AS value,
                   COUNT(s.id) AS secondary_value
            FROM generate_series(CAST(:start AS timestamptz), CAST(:end AS timestamptz), interval '1 day') AS day_start
            LEFT JOIN submissions s ON CAST(s.published_at AS date) = CAST(day_start AS date)
                AND s.status IN (%s)
                AND s.published_at >= :start
                AND s.published_at < :end
                %s
            GROUP BY day_start
            ORDER BY day_start ASC
            """.formatted(PUBLISHED_STATES, scope.joinSubmissionFilter("s"));
        return queryDaily(sql, start, end, scope);
    }

    private List<DailyAnalyticsPointDto> dailyPosts(Instant start, Instant end, AnalyticsScope scope) {
        String sql = """
            SELECT CAST(day_start AS date) AS day,
                   COUNT(CASE WHEN s.status IN (%1$s) THEN 1 END)::double precision AS value,
                   COUNT(CASE WHEN s.status = 'admin_direct_post' THEN 1 END) AS secondary_value
            FROM generate_series(CAST(:start AS timestamptz), CAST(:end AS timestamptz), interval '1 day') AS day_start
            LEFT JOIN submissions s ON CAST(s.published_at AS date) = CAST(day_start AS date)
                AND s.status IN (%2$s)
                AND s.published_at >= :start
                AND s.published_at < :end
                %3$s
            GROUP BY day_start
            ORDER BY day_start ASC
            """.formatted(PUBLISHED_STATES, REPORTING_STATES, scope.joinSubmissionFilter("s"));
        return queryDaily(sql, start, end, scope);
    }

    private List<DailyAnalyticsPointDto> dailyAiPerformance(Instant start, Instant end, AnalyticsScope scope) {
        String sql = """
            SELECT CAST(day_start AS date) AS day,
                   COUNT(CASE WHEN ail.interaction_type = 'caption_suggestion'
                       AND ail.action_taken IN ('use', 'use_then_edited') THEN 1 END)::double precision AS value,
                   COUNT(CASE WHEN ail.interaction_type = 'caption_suggestion' THEN 1 END) AS secondary_value
            FROM generate_series(CAST(:start AS timestamptz), CAST(:end AS timestamptz), interval '1 day') AS day_start
            LEFT JOIN ai_interaction_log ail ON CAST(ail.created_at AS date) = CAST(day_start AS date)
                AND ail.created_at >= :start
                AND ail.created_at < :end
            LEFT JOIN submissions s ON s.id = ail.submission_id
            WHERE ail.id IS NULL OR 1 = 1 %s
            GROUP BY day_start
            ORDER BY day_start ASC
            """.formatted(scope.aiFilter("s"));
        return queryDaily(sql, start, end, scope);
    }

    private List<DailyAnalyticsPointDto> dailyOperationalHealth(Instant start, Instant end, AnalyticsScope scope) {
        String sql = """
            SELECT CAST(day_start AS date) AS day,
                   CASE WHEN COUNT(pa.id) = 0 THEN 0
                        ELSE COUNT(CASE WHEN pa.result = 'success' THEN 1 END) * 100.0 / COUNT(pa.id)
                   END AS value,
                   COUNT(pa.id) AS secondary_value
            FROM generate_series(CAST(:start AS timestamptz), CAST(:end AS timestamptz), interval '1 day') AS day_start
            LEFT JOIN publication_attempts pa ON CAST(pa.attempted_at AS date) = CAST(day_start AS date)
                AND pa.attempted_at >= :start
                AND pa.attempted_at < :end
            LEFT JOIN submissions s ON s.id = pa.submission_id
            WHERE pa.id IS NULL OR 1 = 1 %s
            GROUP BY day_start
            ORDER BY day_start ASC
            """.formatted(scope.submissionFilter("s"));
        return queryDaily(sql, start, end, scope);
    }

    private List<DailyAnalyticsPointDto> queryDaily(String sql, Instant start, Instant end, AnalyticsScope scope) {
        return jdbc.query(sql, params(start, end, scope), (rs, rowNum) ->
                new DailyAnalyticsPointDto(
                        rs.getDate("day").toLocalDate(),
                        round(rs.getDouble("value")),
                        rs.getLong("secondary_value")));
    }

    private List<Map<String, Object>> exportPostingDelay(Instant start, Instant end, AnalyticsScope scope) {
        String sql = """
            SELECT s.id AS submission_id, i.name AS institution_name, s.status,
                   %1$s AS first_submitted_at, s.published_at,
                   ROUND(EXTRACT(EPOCH FROM (s.published_at - %1$s)) / 86400.0, 4) AS delay_days
            FROM submissions s
            JOIN institutions i ON i.id = s.institution_id
            WHERE s.status IN (%2$s)
              AND s.published_at >= :start AND s.published_at < :end
              AND %1$s IS NOT NULL
              %3$s
            ORDER BY s.published_at DESC
            """.formatted(firstPendingExpression("s"), PUBLISHED_STATES, scope.submissionFilter("s"));
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
            """.formatted(scope.aiFilter("s"));
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

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String firstPendingExpression(String alias) {
        return "COALESCE((SELECT MIN(al.created_at) FROM audit_log al "
                + "WHERE al.resource_id = " + alias + ".id AND al.action = 'SUBMISSION_SUBMITTED'), "
                + alias + ".submitted_at)";
    }

    private Instant instantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record AnalyticsScope(String role, UUID institutionId, UUID userId) {
        public String submissionFilter(String alias) {
            if ("administrator".equals(role) && institutionId == null) {
                return "";
            }
            if ("validator".equals(role) || "contributor".equals(role)) {
                if ("contributor".equals(role)) {
                    return " AND " + alias + ".contributor_id = :userId ";
                }
                return " AND " + alias + ".institution_id = :institutionId ";
            }
            return " AND " + alias + ".institution_id = :institutionId ";
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

        public String joinSubmissionFilter(String alias) {
            String filter = submissionFilter(alias);
            return filter.isBlank() ? "" : filter.replaceFirst("^ AND ", " AND ");
        }

        public String contributorBreakdownFilter(String alias) {
            if ("administrator".equals(role) || "validator".equals(role)) {
                return submissionFilter(alias);
            }
            return " AND " + alias + ".contributor_id = :userId ";
        }

        public String aiFilter(String alias) {
            if ("contributor".equals(role)) {
                return " AND " + alias + ".contributor_id = :userId ";
            }
            return submissionFilter(alias);
        }

        public String validationSubmissionFilter(String submissionAlias) {
            return submissionFilter(submissionAlias);
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
    public record ContributorStats(long submittedCount, long publishedCount, long revisionRequestCount,
                                   long rejectedOrRevisionCount) {}
    public record ValidatorStats(long submissionVolume, long pendingCount, long inReviewCount,
                                 double averageTurnaroundDays, long queueAgingCount) {}
}
