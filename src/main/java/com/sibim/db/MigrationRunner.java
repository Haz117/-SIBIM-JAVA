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
 */
public final class MigrationRunner {

    /** .env / environment key that re-enables repair-before-migrate. */
    public static final String AUTO_REPAIR_KEY = "FLYWAY_AUTO_REPAIR";

    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

    private MigrationRunner() {}

    public static void run(Flyway flyway, boolean autoRepair) {
        if (autoRepair) {
            log.warn("{}=true — reparando el historial de migraciones antes de migrar. "
                + "Esto NO ejecuta el SQL de las migraciones cuyo checksum cambió.", AUTO_REPAIR_KEY);
            flyway.repair();
        }
        flyway.migrate();
    }
}
