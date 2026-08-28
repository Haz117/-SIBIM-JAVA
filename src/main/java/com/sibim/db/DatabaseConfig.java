package com.sibim.db;

import com.sibim.db.offline.OfflineLocalDataStore;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

public final class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);
    private static HikariDataSource dataSource;
    private static boolean demoMode = false;
    private static boolean offlineMode = false;

    private static final Set<String> STRONG_SSL = Set.of("require", "verify-ca", "verify-full");

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

    /** Returns the active local data store, or null when running against live Postgres. */
    public static LocalDataStore getLocalDataStore() {
        if (isOfflineMode()) return new OfflineLocalDataStore();
        if (isDemoMode()) return new DemoLocalDataStore();
        return null;
    }

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

        boolean isRemote = isRemoteUrl(url);
        boolean bypass   = "true".equalsIgnoreCase(getEnv(dotenv, "DB_SSL_BYPASS", "false"));
        // Remote connections default to "require"; local connections to "prefer".
        String sslMode = resolveSslMode(getEnv(dotenv, "DB_SSL_MODE", null), isRemote);
        enforceSslPolicy(url, sslMode, isRemote, bypass);
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

    /**
     * Replaces the active DataSource with the given one — used exclusively by
     * integration tests (IntegrationTestBase) to point the application at an
     * EmbeddedPostgres instance instead of the real DB configured in .env.
     * Never call this from production code.
     */
    public static void setDataSourceForTest(HikariDataSource ds) {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        dataSource = ds;
        demoMode = false;
        offlineMode = false;
    }

    public static void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        dataSource = null;
    }

    static boolean isRemoteUrl(String url) {
        return !url.contains("localhost") && !url.contains("127.0.0.1") && !url.contains("::1");
    }

    /** Returns the explicit sslMode if set, or a safe default based on whether the host is remote. */
    static String resolveSslMode(String explicitMode, boolean isRemote) {
        if (explicitMode != null && !explicitMode.isBlank()) return explicitMode;
        return isRemote ? "require" : "prefer";
    }

    /**
     * Enforces SSL policy for remote connections.
     * Remote + weak SSL + no bypass → throws (blocks startup).
     * Remote + weak SSL + DB_SSL_BYPASS=true → warns and continues (dev escape hatch).
     * Local connections are not checked.
     */
    static void enforceSslPolicy(String url, String sslMode, boolean isRemote, boolean bypass) {
        if (!isRemote || STRONG_SSL.contains(sslMode.toLowerCase())) return;
        String msg = String.format(
            "BLOQUEADO: conexión remota (%s) con DB_SSL_MODE='%s' — las credenciales viajarían "
            + "sin cifrar. Agrega DB_SSL_MODE=require al .env. "
            + "Para desarrollo sin SSL usa DB_SSL_BYPASS=true.", url, sslMode);
        if (bypass) {
            log.warn(msg);
        } else {
            throw new IllegalStateException(msg);
        }
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
