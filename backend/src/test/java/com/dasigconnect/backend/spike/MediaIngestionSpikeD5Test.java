package com.dasigconnect.backend.spike;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.external.ClaudeVisionClient;
import com.dasigconnect.backend.external.VoyageAIClient;
import com.dasigconnect.backend.model.dto.ai.MediaClassificationDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetStatus;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.MediaImportBatch;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.InstitutionRepository;
import com.dasigconnect.backend.repository.MediaAssetEmbeddingRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.MediaImportBatchRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.service.MediaIngestionQueueService;
import com.dasigconnect.backend.service.MediaQualityAnalysisService;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * D5 load spike (scope §9.3, anchor result #1): ingest 200 assets through the real bounded
 * queue + real HikariCP pool and prove <b>zero dropped connections</b> with the pool capped
 * at 5, while every asset reaches {@code READY}.
 *
 * <p><b>Gated + self-cleaning.</b> Skipped by normal {@code mvn test}; only runs with
 * {@code -Dspike.d5=true}. It writes 200 rows to the configured (dev) database and deletes
 * them in {@code finally}. External AI calls (Claude, Voyage) and the image download are
 * stubbed so this measures the pipeline + pool, not external latency/cost (the chosen
 * "stubbed externals" mode).
 *
 * <pre>{@code  ./mvnw test -Dtest=MediaIngestionSpikeD5Test -Dspike.d5=true }</pre>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "spike.d5", matches = "true")
class MediaIngestionSpikeD5Test {

    private static final int ASSET_COUNT = 200;
    private static final Duration MAX_WAIT = Duration.ofMinutes(5);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(250);
    private static final Duration MONITOR_INTERVAL = Duration.ofMillis(50);

    @MockitoBean private ClaudeVisionClient claudeVisionClient;
    @MockitoBean private VoyageAIClient voyageAIClient;
    // Mocked so the per-asset image download / CPU pass does not run — this spike isolates
    // the queue + connection-pool behaviour (externals stubbed).
    @MockitoBean private MediaQualityAnalysisService mediaQualityAnalysisService;
    // Full-context boot pulls in EmailService → JavaMailSender; no mail server in tests.
    @MockitoBean private JavaMailSender javaMailSender;

    @Autowired private MediaIngestionQueueService ingestionQueueService;
    @Autowired private MediaAssetRepository mediaAssetRepository;
    @Autowired private MediaAssetEmbeddingRepository mediaAssetEmbeddingRepository;
    @Autowired private MediaImportBatchRepository mediaImportBatchRepository;
    @Autowired private InstitutionRepository institutionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DataSource dataSource;

    /**
     * Force the real dev Supabase Postgres for this spike — the default test harness boots
     * embedded H2, which has neither the Supabase Session Pooler (the 5-connection limit we
     * are proving) nor pgvector (the {@code CAST AS vector} write that flips assets to READY).
     * Reads creds straight from {@code backend/.env}; uses the existing V1–V30 schema
     * (Flyway + DDL disabled). Secrets are passed to the property registry, never logged.
     */
    @DynamicPropertySource
    static void useRealSupabase(DynamicPropertyRegistry registry) {
        Map<String, String> env = loadDotEnv();
        registry.add("spring.datasource.url", () -> env.get("DASIG_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> env.get("DASIG_DATABASE_USER"));
        registry.add("spring.datasource.password", () -> env.get("DASIG_DATABASE_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // Pin the pool to the production cap so the measurement faithfully proves the
        // Supabase Session-Pooler 5-connection limit is respected (not Hikari's default 10).
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "5");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.sql.init.mode", () -> "never");
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
            // Leave empty → context fails fast with a clear "url must not be null" message.
        }
        return env;
    }

    @Test
    void ingest200Assets_zeroDroppedConnections_poolStaysWithinLimit() throws Exception {
        stubExternalClients();

        Institution institution = institutionRepository.findAll().stream().findFirst().orElse(null);
        User uploader = userRepository.findAll().stream().findFirst().orElse(null);
        assumeTrue(institution != null && uploader != null,
                "D5 spike needs at least one institution and one user in the target DB.");

        HikariPoolMXBean pool = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
        int maxPoolSize = dataSource.unwrap(HikariDataSource.class).getMaximumPoolSize();

        MediaImportBatch batch = createBatch(institution, uploader);
        List<UUID> assetIds = insertProcessingAssets(institution, uploader, batch);

        AtomicInteger maxActive = new AtomicInteger(0);
        AtomicInteger maxAwaiting = new AtomicInteger(0);
        ScheduledExecutorService monitor = startPoolMonitor(pool, maxActive, maxAwaiting);

        Instant start = Instant.now();
        try {
            assetIds.forEach(id -> ingestionQueueService.enqueue(id, "https://stub.local/spike.jpg"));

            StatusCounts counts = awaitTerminal(batch.getId(), institution.getId());
            Duration wall = Duration.between(start, Instant.now());
            monitor.shutdownNow();

            double throughputPerMin = ASSET_COUNT / Math.max(0.001, wall.toMillis() / 60000.0);
            writeResult(wall, throughputPerMin, maxActive.get(), maxAwaiting.get(), maxPoolSize, counts);

            // The thesis: pool never exceeded its cap, nothing was dropped, all reached READY.
            assertThat(maxActive.get())
                    .as("max concurrent Hikari connections must stay within the pool cap")
                    .isLessThanOrEqualTo(maxPoolSize);
            assertThat(counts.failed())
                    .as("no asset should end FAILED (dropped/failed ingestion)")
                    .isZero();
            assertThat(counts.ready())
                    .as("every asset should reach READY")
                    .isEqualTo(ASSET_COUNT);
        } finally {
            monitor.shutdownNow();
            cleanup(assetIds, batch.getId());
        }
    }

    private void stubExternalClients() {
        when(claudeVisionClient.classifyMedia(anyList())).thenReturn(
                new MediaClassificationDto("Event", 0.9, "Stubbed spike asset", List.of("spike", "load")));
        when(claudeVisionClient.prepareImageForEmbedding(any()))
                .thenReturn(new ClaudeVisionClient.PreparedImage(new byte[] {1, 2, 3}, "image/jpeg"));
        when(claudeVisionClient.modelName()).thenReturn("stub-claude");

        String vector = vector1024();
        when(voyageAIClient.embedDocument(any())).thenReturn(vector);
        when(voyageAIClient.embedImageDocument(any(), any())).thenReturn(vector);
        when(voyageAIClient.modelName()).thenReturn("voyage-4-lite");
        when(voyageAIClient.multimodalModelName()).thenReturn("voyage-multimodal-3.5");
    }

    private MediaImportBatch createBatch(Institution institution, User uploader) {
        MediaImportBatch batch = new MediaImportBatch();
        batch.setInstitution(institution);
        batch.setUploadedBy(uploader);
        batch.setAssetCount(ASSET_COUNT);
        return mediaImportBatchRepository.save(batch);
    }

    private List<UUID> insertProcessingAssets(Institution institution, User uploader, MediaImportBatch batch) {
        List<MediaAsset> assets = new ArrayList<>(ASSET_COUNT);
        for (int i = 0; i < ASSET_COUNT; i++) {
            MediaAsset asset = new MediaAsset();
            asset.setInstitution(institution);
            asset.setUploader(uploader);
            asset.setAssetCode("SPIKE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            asset.setStorageUrl("https://stub.local/spike-" + i + ".jpg");
            asset.setFileName("spike-" + i + ".jpg");
            asset.setFileType(MediaFileType.jpeg);
            asset.setFileSizeBytes(1024L);
            asset.setStatus(MediaAssetStatus.PROCESSING);
            asset.setImportBatchId(batch.getId());
            assets.add(asset);
        }
        return mediaAssetRepository.saveAll(assets).stream().map(MediaAsset::getId).toList();
    }

    private ScheduledExecutorService startPoolMonitor(HikariPoolMXBean pool,
                                                      AtomicInteger maxActive,
                                                      AtomicInteger maxAwaiting) {
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
        monitor.scheduleAtFixedRate(() -> {
            maxActive.accumulateAndGet(pool.getActiveConnections(), Math::max);
            maxAwaiting.accumulateAndGet(pool.getThreadsAwaitingConnection(), Math::max);
        }, 0, MONITOR_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
        return monitor;
    }

    private StatusCounts awaitTerminal(UUID batchId, UUID institutionId) throws InterruptedException {
        Instant deadline = Instant.now().plus(MAX_WAIT);
        while (Instant.now().isBefore(deadline)) {
            StatusCounts counts = countStatuses(batchId, institutionId);
            if (counts.ready() + counts.failed() >= ASSET_COUNT) {
                return counts;
            }
            Thread.sleep(POLL_INTERVAL.toMillis());
        }
        return countStatuses(batchId, institutionId);
    }

    private StatusCounts countStatuses(UUID batchId, UUID institutionId) {
        int ready = 0;
        int failed = 0;
        int processing = 0;
        for (MediaAsset asset : mediaAssetRepository.findActiveByImportBatch(batchId, institutionId)) {
            switch (asset.getStatus()) {
                case READY -> ready++;
                case FAILED -> failed++;
                default -> processing++;
            }
        }
        return new StatusCounts(ready, failed, processing);
    }

    private void cleanup(List<UUID> assetIds, UUID batchId) {
        for (UUID id : assetIds) {
            try {
                mediaAssetEmbeddingRepository.deleteByAssetId(id);
            } catch (Exception ignored) {
                // best-effort: an asset may have no embedding row
            }
        }
        try {
            mediaAssetRepository.deleteAllById(assetIds);
        } catch (Exception ignored) {
            // leave nothing partially attached if a delete races; manual cleanup by batch id remains possible
        }
        try {
            mediaImportBatchRepository.deleteById(batchId);
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private void writeResult(Duration wall, double throughputPerMin, int maxActive, int maxAwaiting,
                             int maxPoolSize, StatusCounts counts) {
        String row = String.format(
                "| %s | %d | %d ms | %.1f/min | %d | %d | %d | %d | %d | %s |%n",
                ZonedDateTime.now(), ASSET_COUNT, wall.toMillis(), throughputPerMin,
                maxActive, maxPoolSize, maxAwaiting, counts.ready(), counts.failed(),
                (maxActive <= maxPoolSize && counts.failed() == 0 && counts.ready() == ASSET_COUNT) ? "PASS" : "FAIL");
        try {
            Path file = Path.of("..", "docs", "eval", "D5_results.md");
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                Files.writeString(file,
                        "# D5 — 200-asset ingestion load spike results\n\n"
                        + "Generated by `MediaIngestionSpikeD5Test` (stubbed externals). "
                        + "Pass = pool within cap, 0 FAILED, all READY.\n\n"
                        + "| run_at | assets | wall_clock | throughput | max_active_conns | pool_cap "
                        + "| max_awaiting | ready | failed | result |\n"
                        + "|---|---|---|---|---|---|---|---|---|---|\n");
            }
            Files.writeString(file, row, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception e) {
            System.out.println("D5 result row (could not write file): " + row);
        }
    }

    private static String vector1024() {
        StringBuilder sb = new StringBuilder(1024 * 6);
        sb.append('[');
        for (int i = 0; i < 1024; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("0.01");
        }
        return sb.append(']').toString();
    }

    private record StatusCounts(int ready, int failed, int processing) {}
}
