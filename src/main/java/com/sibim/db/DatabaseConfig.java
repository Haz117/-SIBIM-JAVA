package com.sibim.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.cdimascio.dotenv.Dotenv;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public final class DatabaseConfig {

    private static HikariDataSource dataSource;
    private static boolean demoMode = false;

    private DatabaseConfig() {}

    public static boolean isDemoMode() { return demoMode; }

    public static void setDemoMode(boolean dm) { demoMode = dm; }

    public static void init() {
        if (dataSource != null) return;

        // Load from .env file in project root, or from environment variables
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        String url      = getEnv(dotenv, "DB_URL", "jdbc:postgresql://localhost:5432/sibim");
        String user     = getEnv(dotenv, "DB_USER", "postgres");
        String password = getEnv(dotenv, "DB_PASSWORD", "");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(5_000);
        config.setInitializationFailTimeout(3_000);
        config.setIdleTimeout(600_000);
        config.setMaxLifetime(1_800_000);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        dataSource = new HikariDataSource(config);
    }

    public static Connection getConnection() throws SQLException {
        if (dataSource == null) init();
        return dataSource.getConnection();
    }

    public static DataSource getDataSource() {
        if (dataSource == null) init();
        return dataSource;
    }

    public static void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private static String getEnv(Dotenv dotenv, String key, String fallback) {
        String value = dotenv.get(key);
        if (value == null || value.isBlank()) value = System.getenv(key);
        return (value != null && !value.isBlank()) ? value : fallback;
    }
}
