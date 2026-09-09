package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class V22__allow_same_email_across_providers extends BaseJavaMigration {
    private static final Set<String> EMAIL_ONLY = Set.of("email");
    private static final Set<String> EMAIL_AND_PROVIDER = Set.of("email", "provider");

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        Map<String, Set<String>> uniqueConstraints = uniqueConstraints(connection);

        for (Map.Entry<String, Set<String>> entry : uniqueConstraints.entrySet()) {
            if (entry.getValue().equals(EMAIL_ONLY)) {
                dropConstraint(connection, entry.getKey());
            }
        }

        uniqueConstraints = uniqueConstraints(connection);
        boolean compositeExists = uniqueConstraints.values().stream()
                .anyMatch(columns -> columns.equals(EMAIL_AND_PROVIDER));
        if (!compositeExists) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE users ADD CONSTRAINT uk_users_email_provider UNIQUE (email, provider)");
            }
        }
    }

    private Map<String, Set<String>> uniqueConstraints(Connection connection) throws SQLException {
        String sql = """
                SELECT tc.constraint_name, kcu.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_catalog = kcu.constraint_catalog
                 AND tc.constraint_schema = kcu.constraint_schema
                 AND tc.constraint_name = kcu.constraint_name
                 AND tc.table_name = kcu.table_name
                WHERE LOWER(tc.table_name) = 'users'
                  AND tc.constraint_type = 'UNIQUE'
                ORDER BY tc.constraint_name, kcu.ordinal_position
                """;

        Map<String, Set<String>> constraints = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String name = resultSet.getString("constraint_name");
                String column = resultSet.getString("column_name");
                constraints.computeIfAbsent(name, ignored -> new LinkedHashSet<>())
                        .add(column.toLowerCase(Locale.ROOT));
            }
        }
        return constraints;
    }

    private void dropConstraint(Connection connection, String constraintName) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE users DROP CONSTRAINT " + quoteIdentifier(constraintName));
        }
    }

    private String quoteIdentifier(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
