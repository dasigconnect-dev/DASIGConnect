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
 * D1 candidate-pair miner (UC-4.4 eval prep). Scans the <b>real</b> image assets already in the
 * dev library, computes the production dHash, and emits real asset-code pairs to
 * {@code docs/eval/D1_candidates.csv} with a computed Hamming distance and a <em>blank</em>
 * label for human confirmation. It surfaces near-duplicate candidates (small Hamming) and a
 * sample of clearly-distant pairs (negatives) so a labeller reviews real pairs instead of
 * hunting from scratch.
 *
 * <p>It does <b>not</b> assign labels (that is human ground truth, eval §9.1) and never touches
 * the frozen {@code D1_duplicate_pairs.csv}. Gated + self-skipping.
 *
 * <pre>{@code  ./mvnw test -Dtest=DuplicateCandidateMiningD1Test -Dmine.d1=true }</pre>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "mine.d1", matches = "true")
class DuplicateCandidateMiningD1Test {

    private static final int NEAR_CANDIDATE_MAX_HAMMING = 12; // surface likely exact/near dupes
    private static final int NEGATIVE_SAMPLE = 15;            // a few clearly-distant pairs as negatives
    private static final int MAX_IMAGES = 400;               // bound the download/compare work
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
    void mineCandidatePairsFromRealLibrary() throws Exception {
        HttpClient http = HttpClient.newBuilder().connectTimeout(DOWNLOAD_TIMEOUT).build();

        List<Hashed> hashed = new ArrayList<>();
        int scanned = 0;
        for (MediaAsset asset : mediaAssetRepository.findAllActive()) {
            if (asset.getFileType() == null || !asset.getFileType().isImage() || asset.getStorageUrl() == null) {
                continue;
            }
            if (scanned >= MAX_IMAGES) {
                break;
            }
            scanned++;
            BufferedImage image = download(http, asset.getStorageUrl());
            if (image != null) {
                hashed.add(new Hashed(asset.getAssetCode(), MediaQualityAnalysisService.computeDifferenceHash(image)));
            }
        }

        System.out.printf("D1 mining: %d real image assets scanned, %d decoded.%n", scanned, hashed.size());
        assumeTrue(hashed.size() >= 2,
                "Dev library has fewer than 2 decodable real images — upload a real event set first, "
                + "then re-run with -Dmine.d1=true.");

        List<String> near = new ArrayList<>();
        List<String> negatives = new ArrayList<>();
        int pairId = 1;
        for (int i = 0; i < hashed.size(); i++) {
            for (int j = i + 1; j < hashed.size(); j++) {
                int distance = MediaQualityAnalysisService.hammingDistance(hashed.get(i).hash(), hashed.get(j).hash());
                String row = String.format("%d,%s,%s,,%d,%s",
                        pairId, hashed.get(i).code(), hashed.get(j).code(), distance,
                        distance <= NEAR_CANDIDATE_MAX_HAMMING ? "near-candidate" : "distant");
                if (distance <= NEAR_CANDIDATE_MAX_HAMMING) {
                    near.add(row);
                    pairId++;
                } else if (negatives.size() < NEGATIVE_SAMPLE) {
                    negatives.add(row);
                    pairId++;
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("pair_id,asset_a_code,asset_b_code,label,hamming_distance,notes\n");
        sb.append("# Auto-mined ").append(ZonedDateTime.now()).append(" from ").append(hashed.size())
                .append(" real images. LABEL each row (exact|near|distinct_same_event|unrelated), then move\n");
        sb.append("# confirmed rows into D1_duplicate_pairs.csv. Labels are human ground truth — not auto-filled.\n");
        near.forEach(r -> sb.append(r).append('\n'));
        negatives.forEach(r -> sb.append(r).append('\n'));

        Path out = Path.of("..", "docs", "eval", "D1_candidates.csv");
        Files.createDirectories(out.getParent());
        Files.writeString(out, sb.toString());
        System.out.printf("D1 mining: wrote %d near-candidate + %d distant pairs to docs/eval/D1_candidates.csv.%n",
                near.size(), negatives.size());

        assertThat(hashed).isNotEmpty();
    }

    private BufferedImage download(HttpClient http, String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url)).timeout(DOWNLOAD_TIMEOUT).GET().build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            try (InputStream body = response.body()) {
                return ImageIO.read(body);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private record Hashed(String code, long hash) {}

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
