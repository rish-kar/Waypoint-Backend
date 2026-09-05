package com.waypoint.backend.database;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresMigrationIT {
    @Test
    void allMigrationsApplyAndHotPathIndexesExistOnPostgres() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            Flyway flyway = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .load();

            assertThat(flyway.migrate().success).isTrue();
            assertThat(flyway.validateWithResult().validationSuccessful).isTrue();

            try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(),
                    postgres.getUsername(),
                    postgres.getPassword()
            ); Statement statement = connection.createStatement()) {
                Set<String> tables = values(statement.executeQuery("""
                        SELECT table_name
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                        """), "table_name");
                assertThat(tables).contains("users", "subscriptions", "webhook_events");
                assertThat(tables).doesNotContain("admin_accounts");

                Set<String> indexes = values(statement.executeQuery("""
                        SELECT indexname
                        FROM pg_indexes
                        WHERE schemaname = 'public'
                        """), "indexname");
                assertThat(indexes).contains(
                        "idx_subscriptions_user_updated_at",
                        "idx_subscriptions_user_status_renews_at",
                        "idx_webhook_events_status_last_attempt_at",
                        "idx_admin_audit_events_admin_created_at"
                );

                Set<String> userUniqueConstraints = values(statement.executeQuery("""
                        SELECT constraint_name
                        FROM information_schema.table_constraints
                        WHERE table_schema = 'public'
                          AND table_name = 'users'
                          AND constraint_type = 'UNIQUE'
                        """), "constraint_name");
                assertThat(userUniqueConstraints)
                        .contains("uk_users_provider_user", "uk_users_email_provider")
                        .doesNotContain("users_email_key");

                statement.executeUpdate("""
                        INSERT INTO users (
                            id, email, display_name, provider, provider_user_id,
                            created_at, updated_at, last_login_at
                        ) VALUES
                            ('00000000-0000-0000-0000-000000000101', 'shared@example.com', 'Google User',
                             'GOOGLE', 'google-shared', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                            ('00000000-0000-0000-0000-000000000102', 'shared@example.com', 'Microsoft User',
                             'MICROSOFT', 'microsoft-shared', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """);

                try (ResultSet resultSet = statement.executeQuery("""
                        SELECT COUNT(*) AS account_count
                        FROM users
                        WHERE email = 'shared@example.com'
                        """)) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getInt("account_count")).isEqualTo(2);
                }
            }
        }
    }

    private Set<String> values(ResultSet resultSet, String column) throws Exception {
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        while (resultSet.next()) {
            values.add(resultSet.getString(column));
        }
        return values.stream().collect(Collectors.toSet());
    }
}
