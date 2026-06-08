package com.dasigconnect.backend.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetIntegrityCheck;
import com.dasigconnect.backend.model.entity.MediaIntegrityReviewStatus;
import com.dasigconnect.backend.model.entity.MediaIntegrityStatus;
import com.dasigconnect.backend.model.entity.MediaIntegrityTrigger;
import com.dasigconnect.backend.repository.MediaAssetIntegrityCheckRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.MediaIntegrityTarget;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.service.AuditLogService;
import com.dasigconnect.backend.service.MediaIntegrityAlertService;
import com.dasigconnect.backend.service.MediaIntegrityRecordService;
import com.dasigconnect.backend.service.MediaIntegrityService;
import com.dasigconnect.backend.service.SupabaseStorageService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * D7 - Preservation and Governance Verification harness (UC-4.12 / §9, scope doc).
 *
 * <p>Drives the real {@link MediaIntegrityService} + {@link MediaIntegrityRecordService} against
 * the frozen scenario set in {@code docs/eval/D7_preservation_set.csv} with controllable storage,
 * then asserts every §9.3 (4.12) target and writes {@code docs/eval/D7_results.md}. Externals
 * (Supabase storage, alerts, audit) are stubbed so the run is deterministic and repeatable, in the
 * same spirit as the D5 ingestion-spike harness. Live dev-bucket verification is a complementary
 * spot-check, not a substitute for this regression gate.
 */
class MediaIntegrityVerificationD7Test {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    /** One in-memory storage object whose bytes/availability the harness mutates between phases. */
    private static final class StorageState {
        byte[] bytes;
        Mode mode = Mode.NORMAL;

        StorageState(byte[] bytes) {
            this.bytes = bytes;
        }

        enum Mode { NORMAL, MISSING, ERROR }
    }

    private record Fixture(
            String fixtureId,
            String assetCode,
            String institution,
            String mediaType,
            String scenario,
            String expectedFinalStatus) {
    }

    @Test
    void d7_preservationAndGovernanceVerification() throws Exception {
        // ---- load the frozen dataset ------------------------------------------------------------
        Path datasetPath = resolveEvalFile("D7_preservation_set.csv");
        List<Fixture> fixtures = parseFixtures(datasetPath);
        assertThat(fixtures).as("D7 set must hold >= 30 assets").hasSizeGreaterThanOrEqualTo(30);

        // ---- wiring: real services, stubbed externals, stateful in-memory asset store -----------
        MediaAssetRepository mediaAssetRepository = mock(MediaAssetRepository.class);
        MediaAssetIntegrityCheckRepository checkRepository = mock(MediaAssetIntegrityCheckRepository.class);
        SupabaseStorageService storageService = mock(SupabaseStorageService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        UserRepository userRepository = mock(UserRepository.class);
        MediaIntegrityAlertService alertService = mock(MediaIntegrityAlertService.class);

        MediaIntegrityRecordService recordService =
                new MediaIntegrityRecordService(mediaAssetRepository, checkRepository);
        MediaIntegrityService service = new MediaIntegrityService(
                mediaAssetRepository, checkRepository, storageService, recordService,
                auditLogService, userRepository, alertService);

        Map<String, Institution> institutions = new LinkedHashMap<>();
        Map<UUID, MediaAsset> assetStore = new LinkedHashMap<>();
        Map<String, StorageState> storage = new LinkedHashMap<>(); // keyed by storageUrl
        Map<String, MediaAsset> byCode = new LinkedHashMap<>();

        for (Fixture f : fixtures) {
            Institution inst = institutions.computeIfAbsent(f.institution(), name -> {
                Institution i = new Institution();
                i.setId(UUID.randomUUID());
                return i;
            });
            MediaAsset asset = new MediaAsset();
            asset.setId(UUID.randomUUID());
            asset.setInstitution(inst);
            asset.setAssetCode(f.assetCode());
            String url = "https://storage.example/d7/" + f.assetCode();
            asset.setStorageUrl(url);
            asset.setIntegrityStatus(MediaIntegrityStatus.PENDING);
            assetStore.put(asset.getId(), asset);
            byCode.put(f.assetCode(), asset);
            storage.put(url, new StorageState(canonicalBytes(f.assetCode())));
        }

        // repository stubs operate on the live in-memory entities (JPA dirty-checking semantics:
        // the entity returned by the finder is the same instance record/verify mutate).
        when(mediaAssetRepository.findActiveByIdForIntegrityUpdate(any()))
                .thenAnswer(inv -> Optional.ofNullable(assetStore.get(inv.<UUID>getArgument(0))));
        when(mediaAssetRepository.findIntegrityTargetById(any()))
                .thenAnswer(inv -> Optional.ofNullable(assetStore.get(inv.<UUID>getArgument(0)))
                        .map(a -> new MediaIntegrityTarget(
                                a.getId(), a.getInstitution().getId(), a.getStorageUrl(), a.getAssetCode())));
        when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(inv -> inv.getArgument(0));

        List<MediaAssetIntegrityCheck> historyLog = new ArrayList<>();
        when(checkRepository.save(any(MediaAssetIntegrityCheck.class))).thenAnswer(inv -> {
            historyLog.add(inv.getArgument(0));
            return inv.getArgument(0);
        });

        when(storageService.openObject(anyString())).thenAnswer(inv -> openStored(storage, inv.getArgument(0)));

        Map<UUID, Integer> alertCounts = new LinkedHashMap<>();
        AtomicInteger totalAlerts = new AtomicInteger();
        doAnswer(inv -> {
            MediaIntegrityTarget t = inv.getArgument(0);
            alertCounts.merge(t.assetId(), 1, Integer::sum);
            totalAlerts.incrementAndGet();
            return null;
        }).when(alertService).notifyFailure(any(), any());

        // ---- Phase 1: ingest fixity for every asset (coverage) ----------------------------------
        Map<String, String> ingestBaseline = new LinkedHashMap<>();
        for (Fixture f : fixtures) {
            MediaAsset asset = byCode.get(f.assetCode());
            service.verifyIngest(asset.getId());
            ingestBaseline.put(f.assetCode(), asset.getContentSha256());
        }

        long withBaseline = fixtures.stream()
                .filter(f -> byCode.get(f.assetCode()).getContentSha256() != null)
                .count();
        double coverage = 100.0 * withBaseline / fixtures.size();

        // ---- Phase 2: scheduled recheck under each controlled scenario --------------------------
        int controlledFailures = 0;
        int detectedFailures = 0;
        int missingTotal = 0;
        int missingDetected = 0;
        int mismatchTotal = 0;
        int mismatchDetected = 0;
        int errorTotal = 0;
        int errorDetected = 0;
        int baselineOverwrites = 0;

        for (Fixture f : fixtures) {
            MediaAsset asset = byCode.get(f.assetCode());
            StorageState state = storage.get(asset.getStorageUrl());
            MediaIntegrityTarget target = new MediaIntegrityTarget(
                    asset.getId(), asset.getInstitution().getId(), asset.getStorageUrl(), asset.getAssetCode());

            switch (f.scenario()) {
                case "verified" -> service.verifyScheduled(target);
                case "mismatch" -> {
                    controlledFailures++;
                    mismatchTotal++;
                    state.bytes = tamperedBytes(f.assetCode());
                    service.verifyScheduled(target);
                    if (asset.getIntegrityStatus() == MediaIntegrityStatus.MISMATCH) {
                        detectedFailures++;
                        mismatchDetected++;
                    }
                }
                case "missing" -> {
                    controlledFailures++;
                    missingTotal++;
                    state.mode = StorageState.Mode.MISSING;
                    service.verifyScheduled(target);
                    if (asset.getIntegrityStatus() == MediaIntegrityStatus.MISSING) {
                        detectedFailures++;
                        missingDetected++;
                    }
                }
                case "error" -> {
                    controlledFailures++;
                    errorTotal++;
                    state.mode = StorageState.Mode.ERROR;
                    service.verifyScheduled(target);
                    if (asset.getIntegrityStatus() == MediaIntegrityStatus.ERROR) {
                        detectedFailures++;
                        errorDetected++;
                    }
                }
                case "recover" -> {
                    // fails once (alerts + OPEN), then recovers (RESOLVED, no new alert)
                    state.bytes = tamperedBytes(f.assetCode());
                    service.verifyScheduled(target);
                    assertThat(asset.getIntegrityStatus()).isEqualTo(MediaIntegrityStatus.MISMATCH);
                    state.bytes = canonicalBytes(f.assetCode());
                    service.verifyScheduled(target);
                }
                case "repeat_failure" -> {
                    // fails twice with the same status; the second alert is suppressed (24h dedup)
                    state.bytes = tamperedBytes(f.assetCode());
                    service.verifyScheduled(target);
                    service.verifyScheduled(target);
                }
                default -> throw new IllegalStateException("Unknown scenario: " + f.scenario());
            }

            // baseline is immutable: a mismatch must never overwrite the ingest checksum
            if (mustPreserveBaseline(f.scenario())
                    && !ingestBaseline.get(f.assetCode()).equals(asset.getContentSha256())) {
                baselineOverwrites++;
            }

            assertThat(asset.getIntegrityStatus().name())
                    .as("final status for %s", f.assetCode())
                    .isEqualTo(f.expectedFinalStatus());
        }

        // ---- Targeted behavior assertions -------------------------------------------------------
        MediaAsset recover = byCode.get("AST-A019");
        assertThat(recover.getIntegrityReviewStatus()).isEqualTo(MediaIntegrityReviewStatus.RESOLVED);
        assertThat(alertCounts.getOrDefault(recover.getId(), 0)).isEqualTo(1);

        MediaAsset repeat = byCode.get("AST-A020");
        assertThat(alertCounts.getOrDefault(repeat.getId(), 0))
                .as("repeated identical failure must alert only once (24h dedup)").isEqualTo(1);

        // tenant isolation: every recorded check belongs to the institution of the asset it references.
        long crossTenantRows = historyLog.stream()
                .filter(c -> !c.getInstitution().getId().equals(c.getAsset().getInstitution().getId()))
                .count();
        assertThat(crossTenantRows).as("no cross-tenant integrity-check rows").isZero();

        // application-layer cross-tenant guard: recording asset A under institution B is rejected.
        MediaAsset tenantA = byCode.get("AST-A001");
        UUID foreignInstitution = byCode.get("AST-B001").getInstitution().getId();
        assertThatThrownBy(() -> recordService.record(
                tenantA.getId(), foreignInstitution, "f".repeat(64),
                null, MediaIntegrityTrigger.MANUAL, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");

        // ---- §9.3 (4.12) targets ----------------------------------------------------------------
        assertThat(coverage).as("100% checksum coverage on active D7 assets").isEqualTo(100.0);
        assertThat(missingDetected).as("all controlled missing detected").isEqualTo(missingTotal);
        assertThat(mismatchDetected).as("all controlled mismatch detected").isEqualTo(mismatchTotal);
        assertThat(errorDetected).as("all controlled read errors detected").isEqualTo(errorTotal);
        assertThat(baselineOverwrites).as("0 expected checksums silently overwritten").isZero();
        assertThat(detectedFailures).as("every controlled integrity failure was detected").isEqualTo(controlledFailures);

        writeResults(coverage, fixtures.size(), withBaseline,
                missingDetected, missingTotal, mismatchDetected, mismatchTotal,
                errorDetected, errorTotal, baselineOverwrites, totalAlerts.get(),
                institutions.size(), historyLog.size(), datasetPath);
    }

    // ---- helpers --------------------------------------------------------------------------------

    private static byte[] canonicalBytes(String assetCode) {
        return ("d7-canonical-content-of-" + assetCode + "-"
                + "x".repeat(256)).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] tamperedBytes(String assetCode) {
        return ("d7-TAMPERED-content-of-" + assetCode).getBytes(StandardCharsets.UTF_8);
    }

    private static boolean mustPreserveBaseline(String scenario) {
        return scenario.equals("mismatch") || scenario.equals("recover")
                || scenario.equals("repeat_failure");
    }

    private static ByteArrayInputStream openStored(Map<String, StorageState> storage, String url) {
        StorageState state = storage.get(url);
        if (state == null || state.mode == StorageState.Mode.MISSING) {
            throw new SupabaseStorageService.StorageObjectNotFoundException(
                    "Stored media object was not found.");
        }
        if (state.mode == StorageState.Mode.ERROR) {
            throw new RuntimeException("Simulated storage read error.");
        }
        return new ByteArrayInputStream(state.bytes);
    }

    private static List<Fixture> parseFixtures(Path path) throws Exception {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<Fixture> out = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty()) {
                continue;
            }
            String[] c = line.split(",", -1);
            out.add(new Fixture(c[0], c[1], c[2], c[3], c[4], c[5]));
        }
        return out;
    }

    private static Path resolveEvalFile(String name) {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            Path candidate = dir.resolve("docs").resolve("eval").resolve(name);
            if (Files.exists(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not locate docs/eval/" + name
                + " from " + Paths.get("").toAbsolutePath());
    }

    private static void writeResults(double coverage, int total, long withBaseline,
                                     int missingDetected, int missingTotal,
                                     int mismatchDetected, int mismatchTotal,
                                     int errorDetected, int errorTotal,
                                     int baselineOverwrites, int totalAlerts,
                                     int institutions, int historyRows, Path datasetPath) {
        try {
            Path out = datasetPath.resolveSibling("D7_results.md");
            String ts = ZonedDateTime.now().format(TS);
            StringBuilder sb = new StringBuilder();
            sb.append("# D7 - Preservation and Governance Verification results\n\n");
            sb.append("Generated by `MediaIntegrityVerificationD7Test` against the frozen set in ")
                    .append("`D7_preservation_set.csv` (real `MediaIntegrityService` + ")
                    .append("`MediaIntegrityRecordService`; storage, alerts, and audit stubbed). ")
                    .append("PASS = every §9.3 (4.12) target met.\n\n");
            sb.append("| run_at | assets | tenants | checksum_coverage | missing_detected | ")
                    .append("mismatch_detected | error_detected | baseline_overwrites | ")
                    .append("alerts_fired | history_rows | result |\n");
            sb.append("|---|---|---|---|---|---|---|---|---|---|---|\n");
            sb.append(String.format(
                    "| %s | %d | %d | %.1f%% (%d/%d) | %d/%d | %d/%d | %d/%d | %d | %d | %d | %s |%n",
                    ts, total, institutions, coverage, withBaseline, total,
                    missingDetected, missingTotal, mismatchDetected, mismatchTotal,
                    errorDetected, errorTotal, baselineOverwrites, totalAlerts, historyRows,
                    (coverage == 100.0 && missingDetected == missingTotal
                            && mismatchDetected == mismatchTotal && errorDetected == errorTotal
                            && baselineOverwrites == 0) ? "PASS" : "FAIL"));
            sb.append("\n");
            sb.append("- **Checksum coverage:** every active D7 asset received a SHA-256 ingest baseline.\n");
            sb.append("- **Detection:** all controlled missing, byte-mismatch, and read-error fixtures were detected.\n");
            sb.append("- **Immutable baseline:** ").append(baselineOverwrites)
                    .append(" expected checksums were overwritten after a mismatch (target 0).\n");
            sb.append("- **Alert dedup:** a repeated identical failure alerted once; recovery resolved the review without a new alert.\n");
            sb.append("- **Tenant isolation:** no cross-tenant integrity-check rows; the record service rejects a foreign-institution write.\n");
            sb.append("- **Note:** DB-level RLS (V35 policy) is the storage-layer complement to the application guard exercised here.\n");
            Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // results file is a side-output; the assertions above are the gate.
        }
    }
}
