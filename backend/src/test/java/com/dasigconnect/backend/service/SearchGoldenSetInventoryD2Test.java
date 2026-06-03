package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.HashMap;
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
 * D2 library inventory dumper (UC-4.5 eval prep). Scans the <b>real</b> dev library and writes
 * each asset's searchable content profile to {@code docs/eval/D2_library_inventory.csv} so the
 * 20-query golden set ({@code D2_search_golden_set.csv}) can be authored against actual content
 * and relevant asset codes hand-verified.
 *
 * <p>Read-only; assigns no labels (that is human ground truth, eval §9.1). Gated + self-skipping.
 * Run with the backend <em>stopped</em> so the two HikariCP-5 pools don't exceed the Supabase
 * Session Pooler limit.
 *
 * <pre>{@code  ./mvnw test -Dtest=SearchGoldenSetInventoryD2Test -Dinventory.d2=true }</pre>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "inventory.d2", matches = "true")
class SearchGoldenSetInventoryD2Test {

    @MockitoBean private JavaMailSender javaMailSender;
    @Autowired private MediaAssetRepository mediaAssetRepository;

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
    void dumpLibraryInventory() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("# D2 library inventory — auto-generated ").append(ZonedDateTime.now()).append('\n');
        sb.append("# Use this to author docs/eval/D2_search_golden_set.csv and hand-verify relevant_asset_codes.\n");
        sb.append("asset_code,ai_category,asset_type,media_type,file_name,created_at_utc,"
                + "ai_description,ai_tags,specific_subjects,visible_objects,possible_use_cases\n");

        int count = 0;
        for (MediaAsset a : mediaAssetRepository.findAllActive()) {
            sb.append(csv(a.getAssetCode())).append(',')
              .append(csv(a.getAiCategory())).append(',')
              .append(csv(a.getAssetType())).append(',')
              .append(csv(a.getFileType() == null ? "" : a.getFileType().name())).append(',')
              .append(csv(a.getFileName())).append(',')
              .append(csv(a.getCreatedAt() == null ? "" : a.getCreatedAt().toString())).append(',')
              .append(csv(a.getAiDescription())).append(',')
              .append(csv(join(a.getAiTags()))).append(',')
              .append(csv(join(a.getSpecificSubjects()))).append(',')
              .append(csv(join(a.getVisibleObjects()))).append(',')
              .append(csv(join(a.getPossibleUseCases())))
              .append('\n');
            count++;
        }

        Path out = Path.of("..", "docs", "eval", "D2_library_inventory.csv");
        Files.createDirectories(out.getParent());
        Files.writeString(out, sb.toString());
        System.out.printf("D2 inventory: wrote %d assets to docs/eval/D2_library_inventory.csv.%n", count);

        assertThat(count).isGreaterThan(0);
    }

    private static String join(String[] values) {
        if (values == null || values.length == 0) {
            return "";
        }
        return String.join("; ", values);
    }

    /** CSV-quote a field: wrap in quotes and double any internal quotes; flatten newlines. */
    private static String csv(String value) {
        String v = value == null ? "" : value.replace("\r", " ").replace("\n", " ").trim();
        return "\"" + v.replace("\"", "\"\"") + "\"";
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
