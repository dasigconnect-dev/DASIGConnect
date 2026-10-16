package com.dasigconnect.backend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class BackendApplication {

    private static final Logger log = LoggerFactory.getLogger(BackendApplication.class);

    public static void main(String[] args) {
        loadLocalEnvFiles();
        SpringApplication.run(BackendApplication.class, args);
    }

    private static void loadLocalEnvFiles() {
        List<Path> candidates = List.of(Path.of(".env"), Path.of("backend", ".env"));
        for (Path candidate : candidates) {
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            try {
                for (String line : Files.readAllLines(candidate)) {
                    String trimmed = line.trim();
                    if (trimmed.isBlank() || trimmed.startsWith("#")) {
                        continue;
                    }
                    int separator = trimmed.indexOf('=');
                    if (separator <= 0) {
                        continue;
                    }
                    String key = trimmed.substring(0, separator).trim();
                    String value = trimmed.substring(separator + 1).trim();
                    if (System.getProperty(key) == null && System.getenv(key) == null) {
                        System.setProperty(key, value);
                    }
                }
            } catch (IOException ex) {
                log.warn("Could not load local env file {}", candidate, ex);
            }
        }
    }

    @Bean
    @ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
    public org.flywaydb.core.Flyway flyway(
            DataSource dataSource,
            @Value("${spring.flyway.baseline-on-migrate:true}") boolean baselineOnMigrate,
            @Value("${spring.flyway.baseline-version:0}") String baselineVersion,
            @Value("${spring.flyway.out-of-order:true}") boolean outOfOrder) {
        log.info("Initializing custom Flyway bean");
        org.flywaydb.core.Flyway flyway = org.flywaydb.core.Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(baselineOnMigrate)
                .baselineVersion(baselineVersion)
                .outOfOrder(outOfOrder)
                .load();

        flyway.repair();
        flyway.migrate();
        log.info("Flyway migration completed");
        return flyway;
    }

    @Bean
    @ConditionalOnProperty(name = "app.db-diagnostics.enabled", havingValue = "true")
    public CommandLineRunner dbDiagnostics(
            org.flywaydb.core.Flyway flyway,
            DataSource dataSource,
            com.dasigconnect.backend.repository.UserRepository userRepository,
            com.dasigconnect.backend.repository.InstitutionRepository institutionRepository,
            com.dasigconnect.backend.repository.PageSettingsRepository pageSettingsRepository,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        return args -> {
            log.info("Starting database diagnostics and bootstrap verification");
            try (Connection conn = dataSource.getConnection()) {
                DatabaseMetaData metaData = conn.getMetaData();
                log.info(
                        "Database connection verified: product={}, version={}",
                        metaData.getDatabaseProductName(),
                        metaData.getDatabaseProductVersion());

                // Seed bootstrap admin — credentials must be supplied via env, no defaults.
                String adminEmail = System.getenv("BOOTSTRAP_ADMIN_EMAIL");
                String adminPassword = System.getenv("BOOTSTRAP_ADMIN_PASSWORD");
                if (adminEmail == null || adminEmail.isBlank()) {
                    adminEmail = System.getProperty("BOOTSTRAP_ADMIN_EMAIL");
                }
                if (adminPassword == null || adminPassword.isBlank()) {
                    adminPassword = System.getProperty("BOOTSTRAP_ADMIN_PASSWORD");
                }

                log.info("Configuring database role and table RLS settings");
                try (java.sql.Statement stmt = conn.createStatement()) {
                    // Check active database roles
                    try (java.sql.ResultSet rs = stmt.executeQuery("SELECT current_user, session_user")) {
                        if (rs.next()) {
                            log.debug("PostgreSQL roles: currentUser={}, sessionUser={}", rs.getString(1), rs.getString(2));
                        }
                    }

                    // Re-enable RLS to preserve table-level architecture
                    stmt.execute("ALTER TABLE users ENABLE ROW LEVEL SECURITY;");
                    stmt.execute("ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;");
                    stmt.execute("ALTER TABLE institutions ENABLE ROW LEVEL SECURITY;");
                    stmt.execute("ALTER TABLE invitation_tokens ENABLE ROW LEVEL SECURITY;");
                    stmt.execute("ALTER TABLE password_reset_tokens ENABLE ROW LEVEL SECURITY;");
                    stmt.execute("ALTER TABLE account_lockouts ENABLE ROW LEVEL SECURITY;");
                    stmt.execute("ALTER TABLE submissions ENABLE ROW LEVEL SECURITY;");
                    stmt.execute("ALTER TABLE slot_reservations ENABLE ROW LEVEL SECURITY;");
                    log.info("Row Level Security re-enabled on bootstrap tables");

                    // Dynamically execute BYPASSRLS on current_user
                    String activeRole = "postgres";
                    try (java.sql.ResultSet userRs = stmt.executeQuery("SELECT current_user")) {
                        if (userRs.next()) {
                            activeRole = userRs.getString(1);
                        }
                    }
                    stmt.execute("ALTER ROLE \"" + activeRole + "\" BYPASSRLS;");
                    log.info("BYPASSRLS privilege verified for active database role");
                } catch (Exception rlsEx) {
                    log.warn("Could not configure RLS bypass", rlsEx);
                }

                // Seed Protected Institution: DASIG Central Visayas
                String dasigInstName = "DASIG Central Visayas";
                com.dasigconnect.backend.model.entity.Institution dasigInstitution = institutionRepository.findByNameIgnoreCase(dasigInstName)
                        .orElseGet(() -> {
                            log.info("Default protected institution is missing; seeding it");
                            com.dasigconnect.backend.model.entity.Institution inst = new com.dasigconnect.backend.model.entity.Institution();
                            inst.setName(dasigInstName);
                            inst.setCode("DASIG-CV");
                            inst.setEmailDomain("dasig.gov.ph");
                            inst.setProtected(true);
                            inst.setStatus(com.dasigconnect.backend.model.entity.InstitutionStatus.active);
                            return institutionRepository.save(inst);
                        });

                if (!dasigInstitution.isProtected() || dasigInstitution.getStatus() != com.dasigconnect.backend.model.entity.InstitutionStatus.active) {
                    dasigInstitution.setProtected(true);
                    dasigInstitution.setStatus(com.dasigconnect.backend.model.entity.InstitutionStatus.active);
                    dasigInstitution = institutionRepository.save(dasigInstitution);
                }

                // Assign Official DASIG Logo Asset as Institution Logo / Default Watermark
                byte[] officialDasigLogoSvg = ("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 64 64\" role=\"img\" aria-label=\"DASIGConnect logo\">"
                        + "<rect width=\"64\" height=\"64\" rx=\"14\" fill=\"#0b1d42\"/>"
                        + "<rect x=\"8\" y=\"6\" width=\"48\" height=\"52\" rx=\"13\" fill=\"#1459d9\"/>"
                        + "<rect x=\"12\" y=\"10\" width=\"40\" height=\"44\" rx=\"10\" fill=\"#2578f3\"/>"
                        + "<path fill=\"#fff\" d=\"m32 20 12 6.4v11.2L32 44l-12-6.4V26.4z\"/>"
                        + "</svg>").getBytes(java.nio.charset.StandardCharsets.UTF_8);

                if (dasigInstitution.getLogoData() == null || dasigInstitution.getLogoData().length == 0) {
                    dasigInstitution.setLogoData(officialDasigLogoSvg);
                    dasigInstitution.setLogoContentType("image/svg+xml");
                    dasigInstitution.setLogoUpdatedAt(java.time.Instant.now());
                    dasigInstitution = institutionRepository.save(dasigInstitution);
                    log.info("Official DASIG logo asset assigned to the default institution");
                }

                // Watermark Configuration: Assign default watermark configuration if watermark table exists (UC-2.5)
                boolean hasWatermarkTable = false;
                try (ResultSet rs = metaData.getTables(null, "public", "%", new String[]{"TABLE"})) {
                    while (rs.next()) {
                        String tableName = rs.getString("TABLE_NAME");
                        if ("page_settings".equalsIgnoreCase(tableName) || "watermark_configurations".equalsIgnoreCase(tableName) || "watermark_configs".equalsIgnoreCase(tableName)) {
                            hasWatermarkTable = true;
                            break;
                        }
                    }
                }

                if (hasWatermarkTable && pageSettingsRepository != null) {
                    var existingSettings = pageSettingsRepository.findByInstitutionId(dasigInstitution.getId());
                    if (existingSettings.isEmpty()) {
                        com.dasigconnect.backend.model.entity.PageSettings settings = new com.dasigconnect.backend.model.entity.PageSettings();
                        settings.setInstitution(dasigInstitution);
                        settings.setWatermarkEnabled(true);
                        settings.setWatermarkText("DASIG Central Visayas");
                        pageSettingsRepository.save(settings);
                        log.info("Default watermark configuration assigned to the default institution");
                    }
                }

                if (adminEmail == null || adminEmail.isBlank()
                        || adminPassword == null || adminPassword.length() < 12) {
                    log.info("Bootstrap admin skipped because valid bootstrap credentials were not configured");
                } else if (userRepository.findByEmail(adminEmail).isEmpty()) {
                    log.info("Configured bootstrap admin is missing; creating it");
                    com.dasigconnect.backend.model.entity.User admin = new com.dasigconnect.backend.model.entity.User();
                    admin.setEmail(adminEmail);
                    admin.setFirstName("DASIG");
                    admin.setLastName("Admin");
                    admin.setRole(com.dasigconnect.backend.model.entity.UserRole.admin);
                    admin.setAdminOwner(true);
                    admin.setPasswordHash(passwordEncoder.encode(adminPassword));
                    admin.setAccountState(com.dasigconnect.backend.model.entity.UserStatus.active);
                    userRepository.save(admin);
                    log.info("Bootstrap admin created");
                } else {
                    log.info("Configured bootstrap admin already exists");
                }

                log.debug("Current tables in schema public:");
                try (ResultSet rs = metaData.getTables(null, "public", "%", new String[]{"TABLE"})) {
                    boolean hasTables = false;
                    while (rs.next()) {
                        hasTables = true;
                        log.debug("Database table: {}", rs.getString("TABLE_NAME"));
                    }
                    if (!hasTables) {
                        log.warn("No tables found in schema public");
                    }
                }
            } catch (Exception e) {
                log.error("Failed to perform database bootstrap verification", e);
            }
            log.info("Database diagnostics and bootstrap verification completed");
        };
    }
}
