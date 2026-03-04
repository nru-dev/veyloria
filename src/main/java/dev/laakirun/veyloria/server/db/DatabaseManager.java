package dev.laakirun.veyloria.server.db;

import dev.laakirun.veyloria.common.config.ServerConfig;
import dev.laakirun.veyloria.common.config.VeyloriaPaths;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DatabaseManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("veyloria.db");
    private static final String SQLITE_DRIVER_CLASS = "org.sqlite.JDBC";

    private final Path databasePath;
    private volatile Driver sqliteDriver;

    public DatabaseManager(ServerConfig config) {
        this.databasePath = VeyloriaPaths.resolveGameRelative(config.databasePath());
    }

    public void initialize() {
        sqliteDriver = loadSqliteDriver();
        if (sqliteDriver == null) {
            throw new IllegalStateException("Failed to load SQLite driver");
        }
        LOGGER.info("SQLite initialized at {}", databasePath);
    }

    public Connection connection() throws SQLException {
        String url = "jdbc:sqlite:" + databasePath;
        Driver loadedDriver = sqliteDriver;
        if (loadedDriver != null) {
            Connection direct = loadedDriver.connect(url, new Properties());
            if (direct != null) {
                return direct;
            }
        }
        return DriverManager.getConnection(url);
    }

    private Driver loadSqliteDriver() {
        LinkedHashSet<ClassLoader> candidateLoaders = new LinkedHashSet<>();
        candidateLoaders.add(DatabaseManager.class.getClassLoader());
        candidateLoaders.add(Thread.currentThread().getContextClassLoader());
        candidateLoaders.add(ClassLoader.getSystemClassLoader());
        candidateLoaders.add(DriverManager.class.getClassLoader());

        for (ClassLoader loader : candidateLoaders) {
            if (loader == null) {
                continue;
            }
            try {
                Class<?> clazz = Class.forName(SQLITE_DRIVER_CLASS, true, loader);
                Object instance = clazz.getDeclaredConstructor().newInstance();
                if (instance instanceof Driver driver) {
                    return driver;
                }
                LOGGER.warn("Class {} loaded by {} but is not a java.sql.Driver", SQLITE_DRIVER_CLASS, loader);
            } catch (ClassNotFoundException ignored) {
                // Try next classloader.
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to initialize SQLite driver", exception);
            }
        }
        return null;
    }
}
