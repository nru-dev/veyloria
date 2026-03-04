package dev.laakirun.veyloria.server.db;

import dev.laakirun.veyloria.common.config.ServerConfig;
import dev.laakirun.veyloria.common.config.VeyloriaPaths;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DatabaseManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("veyloria.db");

    private final Path databasePath;

    public DatabaseManager(ServerConfig config) {
        this.databasePath = VeyloriaPaths.resolveGameRelative(config.databasePath());
    }

    public void initialize() {
        try {
            Class.forName("org.sqlite.JDBC");
            LOGGER.info("SQLite initialized at {}", databasePath);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Failed to load SQLite driver", exception);
        }
    }

    public Connection connection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databasePath);
    }
}
