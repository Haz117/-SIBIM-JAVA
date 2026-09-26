package com.sibim.db;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies pending Flyway migrations at startup.
 *
 * Flyway's {@code repair()} realigns the description and checksum of every
 * already-applied migration with the files shipped in this build <em>without
 * running their SQL</em>. Calling it on every start therefore hides a version
 * collision (two different scripts sharing a version number): the history says
 * the migration ran, but its changes never reached the database. So it only
 * runs when explicitly requested with {@value #AUTO_REPAIR_KEY}=true; otherwise
 * {@code migrate()} validates the history and stops on a mismatch or a failed
 * migration instead of silently rewriting it.
 *
 * With {@value #MIGRATE_KEY}=false the app never changes the schema: that's
 * how the PCs run with the least-privilege role (scripts/sql/rol_app_minimo.sql),
 * which has no DDL rights. The schema is then migrated from one admin machine
 * with the owner account, and every other PC only checks it's up to date.
 */
public final class MigrationRunner {

    /** .env / environment key that re-enables repair-before-migrate. */
    public static final String AUTO_REPAIR_KEY = "FLYWAY_AUTO_REPAIR";

    /** .env / environment key; "false" = validate only, never migrate. */
    public static final String MIGRATE_KEY = "DB_MIGRATE";

    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

    private MigrationRunner() {}

    /**
     * Brings the connected database's schema in line with this build, or
     * fails: migrates it (or, with {@value #MIGRATE_KEY}=false, only checks
     * it), using the flags from the same .env as the connection. Runs at
     * startup AND every time the app comes back online — a PC that started
     * without a connection must not go online against a schema it never
     * migrated or validated.
     *
     * @throws MigracionesPendientesException / FlywayValidateException when
     *         the app must not work online against this database yet
     */
    public static void asegurarEsquema() {
        Flyway flyway = Flyway.configure()
            .dataSource(DatabaseConfig.getDataSource())
            .locations(ubicacionMigraciones())
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load();
        boolean autoRepair = "true".equalsIgnoreCase(DatabaseConfig.setting(AUTO_REPAIR_KEY, "false"));
        boolean migrar = !"false".equalsIgnoreCase(DatabaseConfig.setting(MIGRATE_KEY, "true"));
        run(flyway, autoRepair, migrar);
    }

    /** A user-facing reason why the app can't work online yet, or null if
     *  {@code e} is just "no connection". */
    public static String motivoEsquema(Throwable e) {
        if (e instanceof MigracionesPendientesException) return e.getMessage();
        if (e instanceof org.flywaydb.core.api.exception.FlywayValidateException)
            return "Las migraciones de la base de datos no coinciden con esta versión del programa; "
                + "no se modificó el historial. Si el cambio es intencional, agrega " + AUTO_REPAIR_KEY
                + "=true al .env una vez y reinicia. Detalle: " + e.getMessage();
        return null;
    }

    // Resolves the Flyway migrations location in a way that bypasses the Java
    // module system's cross-module resource encapsulation. getResource() from
    // within com.sibim itself always succeeds (a module can read its own
    // resources). When running exploded (mvn javafx:run / IDE) the URL is a
    // plain file:// path, so Flyway gets a "filesystem:" location and reads the
    // SQL files directly; inside a JAR it falls back to the classpath location
    // and relies on the module's opens.
    private static String ubicacionMigraciones() {
        try {
            java.net.URL url = MigrationRunner.class.getResource("/db/migration");
            if (url != null && "file".equals(url.getProtocol())) {
                return "filesystem:" + java.nio.file.Paths.get(url.toURI());
            }
        } catch (Exception ignored) {
            log.debug("Could not resolve migrations location via URI, falling back to classpath", ignored);
        }
        return "classpath:db/migration";
    }

    public static void run(Flyway flyway, boolean autoRepair) {
        run(flyway, autoRepair, true);
    }

    public static void run(Flyway flyway, boolean autoRepair, boolean migrar) {
        if (!migrar) {
            int pendientes = flyway.info().pending().length;
            if (pendientes > 0) throw new MigracionesPendientesException(pendientes);
            flyway.validate();
            return;
        }
        if (autoRepair) {
            log.warn("{}=true — reparando el historial de migraciones antes de migrar. "
                + "Esto NO ejecuta el SQL de las migraciones cuyo checksum cambió.", AUTO_REPAIR_KEY);
            flyway.repair();
        }
        flyway.migrate();
    }

    /** This build needs schema changes the database doesn't have yet, and
     *  this PC isn't allowed to apply them ({@value #MIGRATE_KEY}=false). */
    public static final class MigracionesPendientesException extends RuntimeException {
        MigracionesPendientesException(int pendientes) {
            super("La base de datos tiene " + pendientes + " migración(es) pendiente(s) para esta versión de SIBIM "
                + "y esta PC no puede aplicarlas (" + MIGRATE_KEY + "=false). Ejecuta esta versión una vez desde "
                + "la PC de administración, con el usuario dueño de la base y sin " + MIGRATE_KEY + "=false.");
        }
    }
}
