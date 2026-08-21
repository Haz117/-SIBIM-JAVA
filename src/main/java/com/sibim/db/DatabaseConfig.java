package com.sibim.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

public final class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);
    private static HikariDataSource dataSource;
    private static boolean demoMode = false;
    private static boolean offlineMode = false;

    private DatabaseConfig() {}

    /** True demo mode: in-memory fictional data (DemoDataStore), reset on
     *  every restart. Only entered deliberately (DEMO_MODE=true in .env),
     *  for local development without a Postgres instance at all. */
    public static boolean isDemoMode() { return demoMode; }

    public static void setDemoMode(boolean dm) { demoMode = dm; }

    /** Offline mode: the real Postgres was reachable at some point (or is
     *  expected to be) but isn't reachable right now — writes go to a local
     *  SQLite store (see com.sibim.db.offline) and queue for automatic sync
     *  once the connection comes back. This is what Main.java falls back to
     *  when DatabaseConfig.init() fails at startup, unless DEMO_MODE is set. */
    public static boolean isOfflineMode() { return offlineMode; }

    public static void setOfflineMode(boolean om) { offlineMode = om; }

    public static void init() {
        if (dataSource != null) return;

        // Search for .env in the standard production location first
        // (%APPDATA%\SIBIM\ on Windows, ~/.sibim/ on other OS), then fall
        // back to the working directory for development.
        Dotenv dotenv = loadDotenv();

        String url      = getEnv(dotenv, "DB_URL", "jdbc:postgresql://localhost:5432/sibim");
        String user     = getEnv(dotenv, "DB_USER", "postgres");
        String password = getEnv(dotenv, "DB_PASSWORD", "");

        // A blank password only works if the server trusts the connection
        // unconditionally (pg_hba.conf "trust") — fine for local dev, a real
        // misconfiguration risk in production. Make it loud instead of
        // silently connecting with no client-side credential.
        if (password.isBlank()) {
            log.warn("DB_PASSWORD no está configurada (.env o variable de entorno) — "
                + "conectando sin contraseña. Solo seguro si pg_hba.conf usa 'trust'. "
                + "No dejes esto así en producción.");
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(3);
        config.setConnectionTimeout(8_000);
        // 8 s gives enough time for VPN / remote DB connections to establish.
        config.setInitializationFailTimeout(8_000);
        config.setIdleTimeout(600_000);
        config.setMaxLifetime(1_800_000);
        // PostgreSQL JDBC driver properties (pgjdbc)
        config.addDataSourceProperty("prepareThreshold", "3");
        config.addDataSourceProperty("preparedStatementCacheQueries", "25");
        config.addDataSourceProperty("socketTimeout", "30");
        // SSL: defaults to "prefer" (uses SSL when available, no error if not).
        // Set DB_SSL_MODE=require in .env for Supabase or any remote/production DB.
        String sslMode = getEnv(dotenv, "DB_SSL_MODE", "prefer");
        config.addDataSourceProperty("sslmode", sslMode);

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
        dataSource = null;
    }

    private static Dotenv loadDotenv() {
        // 1. Production: %APPDATA%\SIBIM\.env  (Windows) or ~/.sibim/.env
        String appData = System.getenv("APPDATA");
        String prodDir = (appData != null && !appData.isBlank())
            ? appData + File.separator + "SIBIM"
            : System.getProperty("user.home") + File.separator + ".sibim";
        Dotenv candidate = Dotenv.configure().directory(prodDir).ignoreIfMissing().load();
        if (candidate.get("DB_URL") != null || candidate.get("DB_PASSWORD") != null)
            return candidate;

        // 2. Dev fallback: working directory / project root
        return Dotenv.configure().ignoreIfMissing().load();
    }

    private static String getEnv(Dotenv dotenv, String key, String fallback) {
        String value = dotenv.get(key);
        if (value == null || value.isBlank()) value = System.getenv(key);
        return (value != null && !value.isBlank()) ? value : fallback;
    }
}
