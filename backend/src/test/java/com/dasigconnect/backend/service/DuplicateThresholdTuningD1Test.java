package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * D1 duplicate-threshold tuning harness (scope §9.3, UC-4.4). Given the <b>labelled</b>
 * {@code docs/eval/D1_duplicate_pairs.csv}, it fetches each asset's image, computes the same
 * production dHash ({@link MediaQualityAnalysisService#computeDifferenceHash}) + Hamming
 * distance, sweeps the threshold, and writes a precision/recall curve to
 * {@code docs/eval/D1_tuning_results.md}. It does <b>not</b> pick the threshold — the team
 * reads the curve and sets {@code MediaQualityAnalysisService.DUPLICATE_HAMMING_THRESHOLD}.
 *
 * <p><b>Gated</b> ({@code -Dtune.d1=true}) and <b>skips cleanly</b> until D1 is labelled, so it
 * never runs in the normal suite and never fabricates data (eval discipline §9.1).
 *
 * <pre>{@code  ./mvnw test -Dtest=DuplicateThresholdTuningD1Test -Dtune.d1=true }</pre>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "tune.d1", matches = "true")
class DuplicateThresholdTuningD1Test {

    private static final int MAX_THRESHOLD = 16;
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(15);

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
    void sweepHammingThreshold_overLabelledPairs() throws Exception {
        List<LabelledPair> pairs = readLabelledPairs();
        assumeTrue(!pairs.isEmpty(),
                "D1 is not labelled yet (docs/eval/D1_duplicate_pairs.csv has no usable rows). "
                + "Populate asset codes + labels, then re-run with -Dtune.d1=true.");

        Map<String, Long> hashByCode = new HashMap<>();
        HttpClient http = HttpClient.newBuilder().connectTimeout(DOWNLOAD_TIMEOUT).build();
        List<ScoredPair> scored = new ArrayList<>();
        int skipped = 0;
        for (LabelledPair pair : pairs) {
            Long hashA = hashFor(pair.codeA(), hashByCode, http);
            Long hashB = hashFor(pair.codeB(), hashByCode, http);
            if (hashA == null || hashB == null) {
                skipped++;
                continue;
            }
            scored.add(new ScoredPair(MediaQualityAnalysisService.hammingDistance(hashA, hashB), pair.positive()));
        }
        assumeTrue(!scored.isEmpty(),
                "No D1 pairs could be scored (asset codes not found or images undecodable). Skipped " + skipped + ".");

        List<String> rows = new ArrayList<>();
        int bestThreshold = 0;
        double bestF1 = -1.0;
        for (int t = 0; t <= MAX_THRESHOLD; t++) {
            int tp = 0;
            int fp = 0;
            int fn = 0;
            int tn = 0;
            for (ScoredPair sp : scored) {
                boolean predictedDuplicate = sp.hamming() <= t;
                if (sp.positive() && predictedDuplicate) {
                    tp++;
                } else if (!sp.positive() && predictedDuplicate) {
                    fp++;
                } else if (sp.positive()) {
                    fn++;
                } else {
                    tn++;
                }
            }
            double precision = tp + fp == 0 ? 0.0 : (double) tp / (tp + fp);
            double recall = tp + fn == 0 ? 0.0 : (double) tp / (tp + fn);
            double f1 = precision + recall == 0 ? 0.0 : 2 * precision * recall / (precision + recall);
            if (f1 > bestF1) {
                bestF1 = f1;
                bestThreshold = t;
            }
            rows.add(String.format("| %d | %.3f | %.3f | %.3f | %d | %d | %d | %d |",
                    t, precision, recall, f1, tp, fp, fn, tn));
        }

        writeResults(scored.size(), skipped, bestThreshold, bestF1, rows);
        // Harness, not a gate: assert only that a curve was produced over real labelled data.
        assertThat(scored).isNotEmpty();
    }

    private Long hashFor(String code, Map<String, Long> cache, HttpClient http) {
        if (code == null || code.isBlank()) {
            return null;
        }
        if (cache.containsKey(code)) {
            return cache.get(code);
        }
        Long hash = null;
        try {
            Optional<MediaAsset> asset = mediaAssetRepository.findByAssetCode(code.trim());
            if (asset.isPresent() && asset.get().getStorageUrl() != null) {
                BufferedImage image = download(http, asset.get().getStorageUrl());
                if (image != null) {
                    hash = MediaQualityAnalysisService.computeDifferenceHash(image);
                }
            }
        } catch (Exception e) {
            hash = null;
        }
        cache.put(code, hash);
        return hash;
    }

    private BufferedImage download(HttpClient http, String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url)).timeout(DOWNLOAD_TIMEOUT).GET().build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return null;
        }
        try (InputStream body = response.body()) {
            return ImageIO.read(body);
        }
    }

    /** Parse D1: pair_id,asset_a_code,asset_b_code,label,hamming_distance,notes. */
    private List<LabelledPair> readLabelledPairs() throws Exception {
        List<LabelledPair> pairs = new ArrayList<>();
        Path file = Path.of("..", "docs", "eval", "D1_duplicate_pairs.csv");
        if (!Files.exists(file)) {
            return pairs;
        }
        for (String line : Files.readAllLines(file)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("pair_id")) {
                continue;
            }
            String[] cols = trimmed.split(",", -1);
            if (cols.length < 4) {
                continue;
            }
            String codeA = cols[1].trim();
            String codeB = cols[2].trim();
            String label = cols[3].trim().toLowerCase();
            if (codeA.isEmpty() || codeB.isEmpty() || label.isEmpty()) {
                continue;
            }
            boolean positive = label.equals("exact") || label.equals("near");
            boolean known = positive || label.equals("distinct_same_event") || label.equals("unrelated");
            if (known) {
                pairs.add(new LabelledPair(codeA, codeB, positive));
            }
        }
        return pairs;
    }

    private void writeResults(int scoredPairs, int skipped, int bestThreshold, double bestF1, List<String> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("# D1 — duplicate Hamming-threshold tuning results\n\n");
        sb.append(String.format("Run %s · scored pairs: %d · skipped (asset/image missing): %d · "
                + "current code threshold: Hamming ≤ 6.%n%n", ZonedDateTime.now(), scoredPairs, skipped));
        sb.append(String.format("**Best F1 = %.3f at Hamming ≤ %d.**%n%n", bestF1, bestThreshold));
        sb.append("| threshold | precision | recall | f1 | tp | fp | fn | tn |\n");
        sb.append("|---|---|---|---|---|---|---|---|\n");
        rows.forEach(r -> sb.append(r).append('\n'));
        try {
            Path file = Path.of("..", "docs", "eval", "D1_tuning_results.md");
            Files.createDirectories(file.getParent());
            Files.writeString(file, sb.toString());
        } catch (Exception e) {
            System.out.println(sb);
        }
    }

    private record LabelledPair(String codeA, String codeB, boolean positive) {}

    private record ScoredPair(int hamming, boolean positive) {}

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
