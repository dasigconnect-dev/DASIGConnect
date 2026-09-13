package com.dasigconnect.backend.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Single source of truth for "publish success rate": successful Facebook
 * publication attempts over total attempts in a time window.
 *
 * <p>Both the System Health dashboard (network-wide, fixed last 30 days) and the
 * Analytics operational-health block (its selected range, optionally scoped to
 * one institution / category) read the number from here, so the definition of
 * "an attempt" and "a success" can never drift between the two screens.
 */
@Repository
public class PublishSuccessRateRepository {

    /** Pass/warn threshold, in percent. Shared by every caller. */
    public static final double TARGET_PERCENT = 95.0;

    private final NamedParameterJdbcTemplate jdbc;

    public PublishSuccessRateRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Network-wide counts for {@code [start, end)}. */
    public Stats networkWide(Instant start, Instant end) {
        return between(start, end, null);
    }

    /**
     * Counts for {@code [start, end)}, optionally narrowed to one institution.
     * The submissions table is joined only when an institution scope is supplied.
     */
    public Stats between(Instant start, Instant end, UUID institutionId) {
        StringBuilder where = new StringBuilder("pa.attempted_at >= :start AND pa.attempted_at < :end");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("start", Timestamp.from(start))
                .addValue("end", Timestamp.from(end));
        if (institutionId != null) {
            where.append(" AND s.institution_id = :institutionId");
            params.addValue("institutionId", institutionId);
        }

        String sql = """
            SELECT COUNT(*) AS attempts,
                   COUNT(*) FILTER (WHERE pa.result = 'success') AS successes
            FROM publication_attempts pa
            %s
            WHERE %s
            """.formatted(institutionId != null ? "JOIN submissions s ON s.id = pa.submission_id" : "", where);

        return jdbc.queryForObject(sql, params, (rs, rowNum) ->
                new Stats(rs.getLong("attempts"), rs.getLong("successes")));
    }

    public record Stats(long attempts, long successes) {
        /** Percentage 0–100, rounded to 2 dp; {@code 0} when there were no attempts. */
        public double ratePercent() {
            return attempts == 0 ? 0.0 : Math.round(successes * 10000.0 / attempts) / 100.0;
        }
    }
}
