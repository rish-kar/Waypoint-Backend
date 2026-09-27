package com.waypoint.backend.config.application;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OnboardingMigrationRecoveryTests {

    @Test
    void v32RestoresMissingColumnBackfillsExistingUsersAndKeepsNewUsersUnonboarded() throws Exception {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:onboarding-recovery;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );

        migrateTo(dataSource, "31");

        UUID existingUserId = UUID.randomUUID();
        insertUser(dataSource, existingUserId, "existing@example.com", true);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE users DROP COLUMN onboarding_completed");
        }

        migrateTo(dataSource, "32");

        assertThat(onboardingCompleted(dataSource, existingUserId)).isTrue();

        UUID newUserId = UUID.randomUUID();
        insertUser(dataSource, newUserId, "new@example.com", null);

        assertThat(onboardingCompleted(dataSource, newUserId)).isFalse();
    }

    private static void migrateTo(DataSource dataSource, String version) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(version))
                .load()
                .migrate();
    }

    private static void insertUser(DataSource dataSource, UUID id, String email, Boolean onboardingCompleted)
            throws Exception {
        String columns = "id, email, provider, provider_user_id, created_at, updated_at, last_login_at";
        String values = "?, ?, ?, ?, ?, ?, ?";
        if (onboardingCompleted != null) {
            columns += ", onboarding_completed";
            values += ", ?";
        }

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO users (" + columns + ") VALUES (" + values + ")"
             )) {
            Instant now = Instant.parse("2026-09-27T15:00:00Z");
            statement.setObject(1, id);
            statement.setString(2, email);
            statement.setString(3, "google");
            statement.setString(4, id.toString());
            statement.setTimestamp(5, Timestamp.from(now));
            statement.setTimestamp(6, Timestamp.from(now));
            statement.setTimestamp(7, Timestamp.from(now));
            if (onboardingCompleted != null) {
                statement.setBoolean(8, onboardingCompleted);
            }
            statement.executeUpdate();
        }
    }

    private static boolean onboardingCompleted(DataSource dataSource, UUID id) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT onboarding_completed FROM users WHERE id = ?"
             )) {
            statement.setObject(1, id);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getBoolean(1);
            }
        }
    }
}
