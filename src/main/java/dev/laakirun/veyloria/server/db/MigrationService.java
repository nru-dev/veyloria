package dev.laakirun.veyloria.server.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MigrationService {
    private static final Logger LOGGER = LoggerFactory.getLogger("veyloria.db");
    private static final List<String> MIGRATIONS = List.of("data/veyloria/migrations/V001__init.sql");

    private final DatabaseManager databaseManager;

    public MigrationService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void migrate() {
        try (Connection connection = databaseManager.connection()) {
            for (String migration : MIGRATIONS) {
                String version = versionOf(migration);
                if (isApplied(connection, version)) {
                    continue;
                }
                LOGGER.info("Applying migration {}", version);
                executeStatements(connection, readResource(migration));
                markApplied(connection, version);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to apply migrations", exception);
        }
    }

    private boolean isApplied(Connection connection, String version) throws SQLException {
        try {
            try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM schema_migrations WHERE version = ?")) {
                statement.setString(1, version);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next();
                }
            }
        } catch (SQLException ignored) {
            return false;
        }
    }

    private void markApplied(Connection connection, String version) throws SQLException {
        try (PreparedStatement statement =
                 connection.prepareStatement("INSERT INTO schema_migrations(version, applied_at) VALUES(?, ?)")) {
            statement.setString(1, version);
            statement.setString(2, Instant.now().toString());
            statement.executeUpdate();
        }
    }

    private void executeStatements(Connection connection, String sql) throws SQLException {
        for (String statement : sql.split(";")) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty()) {
                connection.createStatement().execute(trimmed);
            }
        }
    }

    private static String readResource(String path) {
        try (InputStream stream = MigrationService.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing migration resource " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read migration " + path, exception);
        }
    }

    private static String versionOf(String path) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        int separator = fileName.indexOf("__");
        return separator >= 0 ? fileName.substring(0, separator) : fileName;
    }
}
