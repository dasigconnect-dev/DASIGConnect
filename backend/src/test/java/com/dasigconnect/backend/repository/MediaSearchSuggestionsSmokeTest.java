package com.dasigconnect.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Smoke test for the UC-4.5 autocomplete native query against the <b>real</b> dev Supabase.
 * The service-level tests mock the repository, so they cannot catch SQL errors in the
 * {@code UNION}/{@code unnest} suggestion query — this exercises it for real.
 *
 * <p>Read-only. Run with the backend <em>stopped</em> so the two HikariCP-5 pools don't exceed
 * the Supabase Session Pooler limit:
 *
 * <pre>{@code  ./mvnw test -Dtest=MediaSearchSuggestionsSmokeTest -Dsmoke.suggest=true }</pre>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "smoke.suggest", matches = "true")
class MediaSearchSuggestionsSmokeTest {

    @MockitoBean private JavaMailSender javaMailSender;
    @Autowired private MediaAssetSearchRepository searchRepository;

    @DynamicPropertySource
    static void useRealSupabase(DynamicPropertyRegistry registry) {
        Map<String, String> env = loadDotEnv();
        registry.add("spring.datasource.url", () -> env.get("DASIG_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> env.get("DASIG_DATABASE_USER"));
        registry.add("spring.datasource.password", () -> env.get("DASIG_DATABASE_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.sql.init.mode", () -> "never");
    }

    @Test
    void suggest_networkScope_executesWithoutError() {
        // networkScope = true exercises every UNION branch (tags, ai_category, Claude keyword
        // arrays via unnest, filenames, uploaders, collections, folders) across the whole library.
        assertThatCode(() -> {
            List<Object[]> rows = searchRepository.suggest("e", null, true, 16);
            assertThat(rows).isNotNull();
            for (Object[] row : rows) {
                assertThat(row.length).isGreaterThanOrEqualTo(2);
            }
        }).doesNotThrowAnyException();
    }

    private static Map<String, String> loadDotEnv() {
        Map<String, String> env = new HashMap<>();
        try {
            for (String line : Files.readAllLines(Path.of(".env"))) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int idx = trimmed.indexOf('=');
                if (idx <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, idx).trim();
                String value = trimmed.substring(idx + 1).trim();
                if (value.length() >= 2
                        && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                env.put(key, value);
            }
        } catch (Exception e) {
            // leave empty → context fails fast with a clear message
        }
        return env;
    }
}
