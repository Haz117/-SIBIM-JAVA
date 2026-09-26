package com.sibim.db;

import com.sibim.db.offline.OfflineLocalDataStore;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
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

    public static void setDemoMode(boolean dm) {
        demoMode = dm;
        offlineMode = false;
    }

    /** Offline mode: the real Postgres was reachable at some point (or is
     *  expected to be) but isn't reachable right now — writes go to a local
     *  SQLite store (see com.sibim.db.offline) and queue for automatic sync
     *  once the connection comes back. This is what Main.java falls back to
     *  when DatabaseConfig.init() fails at startup, unless DEMO_MODE is set. */
    public static boolean isOfflineMode() { return offlineMode; }

    public static void setOfflineMode(boolean om) {
        offlineMode = om;
        demoMode = false;
    }

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

        CredencialesUrl url0 = separarCredenciales(getEnv(dotenv, "DB_URL", "jdbc:postgresql://localhost:5432/sibim"));
        String url      = url0.url();
        String user     = getEnv(dotenv, "DB_USER", url0.user() != null ? url0.user() : "postgres");
        String password = getEnv(dotenv, "DB_PASSWORD", url0.password() != null ? url0.password() : "");
        if (url0.user() != null || url0.password() != null) {
            log.warn("DB_URL trae user= o password= como parámetros; se ignoran y se usan DB_USER/DB_PASSWORD. "
                + "Quítalos del DB_URL del .env (el driver los prefería a DB_USER, así que cambiar DB_USER no "
                + "cambiaba el usuario con el que se conecta la app).");
        }
        boolean isRemote = isRemoteUrl(url);
        // Last line of defence behind the surefire sandbox (pom.xml): a test run
        // must never reach a remote database — one already restored a test
        // backup over production. sibim.test is only ever set by surefire.
        if (isRemote && Boolean.getBoolean("sibim.test")) {
            throw new IllegalStateException(
                "Los tests no pueden conectarse a una base de datos remota: " + url.replaceAll("password=[^&]*", "password=***"));
        }
        boolean bypass   = "true".equalsIgnoreCase(getEnv(dotenv, "DB_SSL_BYPASS", "false"));

        enforcePasswordPolicy(url, isRemote, password.isBlank(), bypass);

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
        // connectTimeout: TCP connect itself (seconds). Without this, a dropped-packet
        // scenario (Supabase unreachable) blocks for the OS default (~2 min) regardless
        // of HikariCP's own initializationFailTimeout.
        config.addDataSourceProperty("connectTimeout", "8");
        config.addDataSourceProperty("socketTimeout", "30");

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
        dataSource = ds;
        demoMode = false;
        offlineMode = false;
    }

    /** Clears a test-owned datasource only if it is still the active one. */
    public static void clearDataSourceForTest(HikariDataSource ds) {
        if (dataSource != ds) return;
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
        dataSource = null;
    }

    public static void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        dataSource = null;
    }

    record CredencialesUrl(String url, String user, String password) {}

    /** pgjdbc gives user=/password= query parameters in the URL precedence
     *  over the user and password the pool passes, so a DB_URL carrying the
     *  owner's credentials (as configurar-sibim.ps1 used to write it) kept
     *  the app connecting as the owner no matter what DB_USER said — and the
     *  password ended up in any log line that printed the URL. They're taken
     *  out of the URL; the caller only uses them when DB_USER/DB_PASSWORD
     *  are missing. */
    static CredencialesUrl separarCredenciales(String url) {
        int q = url.indexOf('?');
        if (q < 0) return new CredencialesUrl(url, null, null);
        String user = null, password = null;
        List<String> resto = new ArrayList<>();
        for (String par : url.substring(q + 1).split("&")) {
            if (par.isEmpty()) continue;
            String clave = par.contains("=") ? par.substring(0, par.indexOf('=')) : par;
            String valor = par.contains("=") ? par.substring(par.indexOf('=') + 1) : "";
            if (clave.equalsIgnoreCase("user")) user = URLDecoder.decode(valor, StandardCharsets.UTF_8);
            else if (clave.equalsIgnoreCase("password")) password = URLDecoder.decode(valor, StandardCharsets.UTF_8);
            else resto.add(par);
        }
        String base = url.substring(0, q);
        return new CredencialesUrl(resto.isEmpty() ? base : base + "?" + String.join("&", resto), user, password);
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
     * Enforces a non-blank DB_PASSWORD for remote connections.
     * Remote + blank password + no bypass → throws (blocks startup): anyone who
     * reaches the host connects with no credential at all.
     * Remote + blank password + DB_SSL_BYPASS=true → warns and continues (dev escape hatch,
     * shared with enforceSslPolicy since both guard the same class of mistake).
     * Local connections only warn — a blank password there just means pg_hba.conf trusts
     * the client outright, which is a normal local-dev setup.
     */
    static void enforcePasswordPolicy(String url, boolean isRemote, boolean passwordBlank, boolean bypass) {
        if (!passwordBlank) return;
        if (!isRemote) {
            log.warn("DB_PASSWORD no está configurada (.env o variable de entorno) — "
                + "conectando sin contraseña. Solo seguro si pg_hba.conf usa 'trust'. "
                + "No dejes esto así en producción.");
            return;
        }
        String msg = "BLOQUEADO: conexión remota (" + url + ") sin DB_PASSWORD — "
            + "cualquiera que alcance el host se conecta sin credencial. Configura "
            + "DB_PASSWORD en el .env. Para desarrollo sin contraseña usa DB_SSL_BYPASS=true.";
        if (bypass) log.warn(msg);
        else throw new IllegalStateException(msg);
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

    /** A setting from the same .env the connection uses (%APPDATA%\SIBIM\
     *  first, then the working directory), else the environment. Startup
     *  flags (DEMO_MODE, DB_MIGRATE, FLYWAY_AUTO_REPAIR) must come from here:
     *  reading only the working directory ignored them on installed PCs,
     *  whose .env lives in %APPDATA%\SIBIM\. */
    public static String setting(String key, String fallback) {
        return getEnv(loadDotenv(), key, fallback);
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
