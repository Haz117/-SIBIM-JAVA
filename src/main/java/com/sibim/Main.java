package com.sibim;

import com.sibim.db.DatabaseConfig;
import com.sibim.db.offline.SyncService;
import io.github.cdimascio.dotenv.Dotenv;
import javafx.application.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class Main {

    // File+console logging with daily rotation is configured declaratively
    // in src/main/resources/logback.xml (logback resolves its ${user.home}
    // placeholder against the System property automatically) — no manual
    // setup needed here.
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        // Catches anything that escapes every other try/catch in the app —
        // without this, an exception on a background thread (e.g. a JavaFX
        // Task with no setOnFailed handler) fails silently: nothing is
        // logged and the user gets no indication anything went wrong.
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) ->
            log.error("Excepcion no capturada en el hilo '{}'", thread.getName(), ex));

        // Initialize DB pool before JavaFX starts; fall back to demo/offline
        // mode if the connection fails OR the SIBIM schema isn't present
        // (e.g. a fresh Postgres instance with no migrations applied yet).
        try {
            DatabaseConfig.init();
            try (Connection c = DatabaseConfig.getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT 1 FROM products LIMIT 1")) {
                ps.execute(); // throws if table doesn't exist
            }
        } catch (Exception e) {
            log.warn("No se pudo conectar a la base de datos o el esquema no existe: {}", e.getMessage());
            DatabaseConfig.close();
            // DEMO_MODE is an explicit opt-in for local development without a
            // real Postgres at all (in-memory fictional data, reset on every
            // restart, quick-fill demo accounts on Login). Any other
            // unreachable-DB situation — the real, unplanned case a
            // deployed instance can hit — now falls back to modo offline
            // instead: local persistence + automatic sync when the
            // connection comes back. See SyncService.
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            boolean demoRequested = "true".equalsIgnoreCase(dotenv.get("DEMO_MODE", System.getenv("DEMO_MODE")));
            if (demoRequested) {
                log.info("DEMO_MODE=true — iniciando en modo demo (datos ficticios en memoria).");
                DatabaseConfig.setDemoMode(true);
            } else {
                log.info("Iniciando en modo offline — los cambios se guardan localmente y se sincronizan al reconectar.");
                DatabaseConfig.setOfflineMode(true);
                SyncService.startWatching();
            }
        }

        // Launch JavaFX
        Application.launch(MainApp.class, args);

        // Shutdown pool on exit
        SyncService.stopWatching();
        DatabaseConfig.close();
    }
}
