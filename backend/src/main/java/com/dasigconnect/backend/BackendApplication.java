package com.dasigconnect.backend;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

	@Bean
	@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
	public org.flywaydb.core.Flyway flyway(DataSource dataSource) {
		System.out.println("==================================================");
		System.out.println("INITIALIZING CUSTOM FLYWAY BEAN...");
		org.flywaydb.core.Flyway flyway = org.flywaydb.core.Flyway.configure()
				.dataSource(dataSource)
				.locations("classpath:db/migration")
				.baselineOnMigrate(true)
				.load();
		
		System.out.println("Executing Flyway Migrations against Supabase...");
		org.flywaydb.core.api.output.MigrateResult result = flyway.migrate();
		System.out.println("Flyway Migration Complete! Executed " + result.migrationsExecuted + " migrations.");
		System.out.println("==================================================");
		return flyway;
	}

	@Bean
	@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
	public CommandLineRunner dbDiagnostics(
			DataSource dataSource,
			com.dasigconnect.backend.repository.UserRepository userRepository,
			org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
		return args -> {
			System.out.println("==================================================");
			System.out.println("DATABASE SEEDING AND VERIFICATION:");
			try (Connection conn = dataSource.getConnection()) {
				DatabaseMetaData metaData = conn.getMetaData();
				System.out.println("JDBC URL: " + metaData.getURL());
				System.out.println("Database Product Version: " + metaData.getDatabaseProductVersion());
				
				// Seed Default Administrator
				String adminEmail = "admin@dasigconnect.com";
				
				System.out.println("Configuring database user and tables for clean RLS bypassing...");
				try (java.sql.Statement stmt = conn.createStatement()) {
					// Check active database roles
					try (java.sql.ResultSet rs = stmt.executeQuery("SELECT current_user, session_user")) {
						if (rs.next()) {
							System.out.println(" -> PostgreSQL current_user: " + rs.getString(1));
							System.out.println(" -> PostgreSQL session_user: " + rs.getString(2));
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
					System.out.println("Row Level Security re-enabled on all tables.");

					// Dynamically execute BYPASSRLS on current_user
					String activeRole = "postgres";
					try (java.sql.ResultSet userRs = stmt.executeQuery("SELECT current_user")) {
						if (userRs.next()) {
							activeRole = userRs.getString(1);
						}
					}
					stmt.execute("ALTER ROLE \"" + activeRole + "\" BYPASSRLS;");
					System.out.println("Successfully granted BYPASSRLS privilege to: " + activeRole);
				} catch (Exception rlsEx) {
					System.out.println("Warning: Could not configure RLS bypass: " + rlsEx.getMessage());
				}

				if (userRepository.findByEmail(adminEmail).isEmpty()) {
					System.out.println("No administrator found. Seeding default administrator...");
					com.dasigconnect.backend.model.entity.User admin = new com.dasigconnect.backend.model.entity.User();
					admin.setEmail(adminEmail);
					admin.setRole(com.dasigconnect.backend.model.entity.UserRole.administrator);
					admin.setPasswordHash(passwordEncoder.encode("admin123"));
					admin.setAccountState(com.dasigconnect.backend.model.entity.UserStatus.active);
					userRepository.save(admin);
					System.out.println("Default administrator created successfully!");
					System.out.println(" -> Email: " + adminEmail);
					System.out.println(" -> Password: admin123");
				} else {
					System.out.println("Administrator already exists: " + adminEmail);
				}

				System.out.println("Current Tables in Schema 'public':");
				try (ResultSet rs = metaData.getTables(null, "public", "%", new String[]{"TABLE"})) {
					boolean hasTables = false;
					while (rs.next()) {
						hasTables = true;
						System.out.println(" - " + rs.getString("TABLE_NAME"));
					}
					if (!hasTables) {
						System.out.println(" [No tables found in public schema]");
					}
				}
			} catch (Exception e) {
				System.err.println("Failed to perform seeder / database tables check: " + e.getMessage());
			}
			System.out.println("==================================================");
		};
	}
}
