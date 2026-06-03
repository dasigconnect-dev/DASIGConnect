package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dasigconnect.backend.model.dto.media.MediaAssetSearchRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetSearchResponseDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetSearchResultDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * D2 search golden-set scorer (UC-4.5 eval). Runs the frozen 20-query set in
 * {@code docs/eval/D2_search_golden_set.csv} through {@link MediaAssetSearchService} against the
 * <b>real</b> dev library + Voyage, and writes Recall@3 (primary, eval §9.3), MRR, and Recall@8 —
 * overall and by query type — to {@code docs/eval/D2_results.md}.
 *
 * <p>Read-only; needs {@code VOYAGE_API_KEY} in {@code backend/.env}. Run with the backend
 * <em>stopped</em> so the two HikariCP-5 pools don't exceed the Supabase Session Pooler limit.
 *
 * <pre>{@code  ./mvnw test -Dtest=SearchGoldenSetEvalD2Test -Deval.d2=true }</pre>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "eval.d2", matches = "true")
class SearchGoldenSetEvalD2Test {

    private static final int PAGE_SIZE = 8;

    @MockitoBean private JavaMailSender javaMailSender;
    @Autowired private MediaAssetSearchService searchService;

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
        registry.add("voyage.api.key", () -> env.getOrDefault("VOYAGE_API_KEY", ""));
    }

    @Test
    void scoreGoldenSet() throws Exception {
        JwtUserDetails admin = new JwtUserDetails(UUID.randomUUID(), "admin@dasig.test", "ADMINISTRATOR", null);

        List<Query> queries = readGoldenSet();
        assertThat(queries).as("golden set must have labeled queries").isNotEmpty();

        List<Row> rows = new ArrayList<>();
        for (Query q : queries) {
            MediaAssetSearchRequestDto dto = new MediaAssetSearchRequestDto();
            dto.setQuery(q.text);
            dto.setScope("network");
            dto.setPageSize(PAGE_SIZE);

            List<String> ranked = new ArrayList<>();
            try {
                MediaAssetSearchResponseDto response = searchService.search(dto, admin);
                for (MediaAssetSearchResultDto item : response.getItems()) {
                    ranked.add(item.getAsset().getAssetCode());
                }
            } catch (Exception ex) {
                System.out.printf("D2 query %s failed: %s%n", q.id, ex.getMessage());
            }

            int firstRelevantRank = 0;
            for (int i = 0; i < ranked.size(); i++) {
                if (q.relevant.contains(ranked.get(i))) {
                    firstRelevantRank = i + 1;
                    break;
                }
            }
            boolean hit3 = firstRelevantRank > 0 && firstRelevantRank <= 3;
            boolean hit8 = firstRelevantRank > 0 && firstRelevantRank <= 8;
            double rr = firstRelevantRank > 0 ? 1.0 / firstRelevantRank : 0.0;
            rows.add(new Row(q, hit3, hit8, rr, firstRelevantRank, ranked));
        }

        writeReport(rows);

        double recall3 = rows.stream().mapToDouble(r -> r.hit3 ? 1 : 0).average().orElse(0);
        System.out.printf("D2 RESULT: Recall@3 = %.3f over %d queries (target >= 0.70).%n", recall3, rows.size());
        assertThat(rows).hasSize(queries.size());
    }

    private void writeReport(List<Row> rows) throws Exception {
        int n = rows.size();
        double recall3 = rows.stream().mapToDouble(r -> r.hit3 ? 1 : 0).average().orElse(0);
        double recall8 = rows.stream().mapToDouble(r -> r.hit8 ? 1 : 0).average().orElse(0);
        double mrr = rows.stream().mapToDouble(r -> r.rr).average().orElse(0);

        StringBuilder sb = new StringBuilder();
        sb.append("# D2 — search golden-set results (UC-4.5)\n\n");
        sb.append("Run ").append(ZonedDateTime.now()).append(" · ").append(n)
          .append(" queries · pageSize ").append(PAGE_SIZE)
          .append(" · dataset `docs/eval/D2_search_golden_set.csv` (AI-inspected labels — verify before defense).\n\n");
        sb.append(String.format("**Recall@3 = %.3f** (primary, target >= 0.70) · MRR = %.3f · Recall@8 = %.3f%n%n",
                recall3, mrr, recall8));

        sb.append("## By query type\n\n");
        sb.append("| type | n | Recall@3 | MRR | Recall@8 |\n|---|---|---|---|---|\n");
        Map<String, List<Row>> byType = new LinkedHashMap<>();
        for (Row r : rows) {
            byType.computeIfAbsent(r.query.type, k -> new ArrayList<>()).add(r);
        }
        for (Map.Entry<String, List<Row>> e : byType.entrySet()) {
            List<Row> g = e.getValue();
            sb.append(String.format("| %s | %d | %.3f | %.3f | %.3f |%n",
                    e.getKey(), g.size(),
                    g.stream().mapToDouble(r -> r.hit3 ? 1 : 0).average().orElse(0),
                    g.stream().mapToDouble(r -> r.rr).average().orElse(0),
                    g.stream().mapToDouble(r -> r.hit8 ? 1 : 0).average().orElse(0)));
        }

        sb.append("\n## Per query\n\n");
        sb.append("| id | type | query | R@3 | first-rel rank | top-3 |\n|---|---|---|---|---|---|\n");
        for (Row r : rows) {
            String top3 = r.ranked.stream().limit(3).reduce((a, b) -> a + " " + b).orElse("(none)");
            sb.append(String.format("| %s | %s | %s | %s | %s | %s |%n",
                    r.query.id, r.query.type, escapePipe(r.query.text),
                    r.hit3 ? "✅" : "❌",
                    r.firstRelevantRank > 0 ? String.valueOf(r.firstRelevantRank) : "—",
                    escapePipe(top3)));
        }

        Path out = Path.of("..", "docs", "eval", "D2_results.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, sb.toString());
        System.out.printf("D2 eval: wrote results to docs/eval/D2_results.md.%n");
    }

    private String escapePipe(String s) {
        return s.replace("|", "\\|");
    }

    private List<Query> readGoldenSet() throws Exception {
        List<Query> queries = new ArrayList<>();
        Path csv = Path.of("..", "docs", "eval", "D2_search_golden_set.csv");
        List<String> lines = Files.readAllLines(csv);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("query_id")) {
                continue;
            }
            List<String> cols = parseCsv(line);
            if (cols.size() < 4) {
                continue;
            }
            String id = cols.get(0).trim();
            String text = cols.get(1).trim();
            String type = cols.get(2).trim();
            String relevantRaw = cols.get(3).trim();
            if (text.isEmpty() || relevantRaw.isEmpty()) {
                continue;
            }
            Set<String> relevant = new java.util.HashSet<>(
                    Arrays.stream(relevantRaw.split("\\|")).map(String::trim).filter(s -> !s.isEmpty()).toList());
            queries.add(new Query(id, text, type, relevant));
        }
        return queries;
    }

    /** Minimal CSV: double-quoted fields may contain commas; "" escapes a quote. */
    private List<String> parseCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                out.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        out.add(field.toString());
        return out;
    }

    private record Query(String id, String text, String type, Set<String> relevant) {}

    private record Row(Query query, boolean hit3, boolean hit8, double rr, int firstRelevantRank,
                       List<String> ranked) {}

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
