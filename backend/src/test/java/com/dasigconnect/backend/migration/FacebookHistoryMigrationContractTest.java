package com.dasigconnect.backend.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FacebookHistoryMigrationContractTest {

    private static final String MIGRATION =
            "db/migration/V109__facebook_history_domain_foundation.sql";

    @Test
    void migrationEnforcesPageScopedIdempotencyAndProvenance() throws IOException {
        String sql = migrationSql();

        assertThat(sql).contains("UNIQUE (page_id, graph_post_id)");
        assertThat(sql).contains("UNIQUE (facebook_historical_post_id, graph_media_id)");
        assertThat(sql).contains("FOREIGN KEY (facebook_historical_post_id, page_id)");
        assertThat(sql).contains("FOREIGN KEY (facebook_page_token_id, page_id)");
        assertThat(sql).contains("source_type = 'FACEBOOK_IMPORT'");
        assertThat(sql).contains("source_page_id IS NOT NULL");
        assertThat(sql).contains("system_managed = TRUE");
    }

    @Test
    void migrationRestrictsRawHistoryTablesToAdminAndBackgroundSystem() throws IOException {
        String sql = migrationSql();

        assertThat(sql).contains("facebook_import_jobs_admin_system_access");
        assertThat(sql).contains("facebook_historical_posts_admin_system_access");
        assertThat(sql).contains("facebook_post_media_admin_system_access");
        assertThat(sql).contains("facebook_engagement_snapshots_admin_system_access");
        assertThat(sql).contains("IN ('', 'admin')");
        assertThat(sql).doesNotContain("'moderator'");
        assertThat(sql).doesNotContain("'contributor'");
        assertThat(sql).contains("REVOKE ALL ON TABLE facebook_import_jobs");
    }

    @Test
    void migrationRejectsNegativeCountersAndMetrics() throws IOException {
        String sql = migrationSql();

        assertThat(sql).contains("chk_facebook_import_job_counts");
        assertThat(sql).contains("chk_facebook_engagement_nonnegative");
        assertThat(sql).contains("chk_facebook_post_media_dimensions");
    }

    private String migrationSql() throws IOException {
        try (var stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(MIGRATION)) {
            assertThat(stream).as("migration resource").isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
