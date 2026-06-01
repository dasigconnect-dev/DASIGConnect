package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Throwaway investigative dump: writes {@code code,storageUrl} for active image assets to
 * {@code target/d1_asset_urls.csv} (git-ignored) so candidate pairs can be visually inspected
 * for D1 labelling. Gated, no downloads. Not part of the suite.
 *
 * <pre>{@code  ./mvnw test -Dtest=AssetUrlDumpTest -Ddump.urls=true }</pre>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "dump.urls", matches = "true")
class AssetUrlDumpTest {

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
    void dumpImageAssetUrls() throws Exception {
        String csv = mediaAssetRepository.findAllActive().stream()
                .filter(a -> a.getFileType() != null && a.getFileType().isImage() && a.getStorageUrl() != null)
                .map(a -> a.getAssetCode() + "," + a.getStorageUrl())
                .collect(Collectors.joining("\n"));
        Path out = Path.of("target", "d1_asset_urls.csv");
        Files.createDirectories(out.getParent());
        Files.writeString(out, "code,storage_url\n" + csv + "\n");
        System.out.println("Wrote asset URL dump to backend/target/d1_asset_urls.csv");
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
                env.put(trimmed.substring(0, idx).trim(), trimmed.substring(idx + 1).trim());
            }
        } catch (Exception e) {
            // leave empty
        }
        return env;
    }
}
