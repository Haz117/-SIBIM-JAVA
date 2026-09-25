package com.sibim.controller;

import com.sibim.db.DatabaseConfig;
import com.sibim.db.offline.SyncService;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Smoke test for SplashController.
 *
 * No session is needed (splash runs before login). Demo mode is NOT set here
 * because initDatabase() overrides demoMode from dotenv — instead the test
 * simply verifies structural node wiring before any async operations complete.
 * The background db-init thread may fail silently, which is expected.
 */
class SplashControllerSmokeTest extends ControllerSmokeTestBase {

    @Override
    public void start(Stage stage) throws Exception {
        Parent root = new FXMLLoader(getClass().getResource("/fxml/splash.fxml")).load();
        stage.setScene(new Scene(root, 800, 520));
        stage.show();
        stage.toFront();
    }

    @Override
    public void stop() throws Exception {
        // The splash's "db-init" virtual thread outlives this test. When the connection
        // fails it calls DatabaseConfig.close() (closing the GLOBAL pool) and flips offline
        // mode ~8 s after start — which would close/flip whatever pool the next integration
        // test class has just installed. Wait for it to settle before touching shared state.
        // It may also succeed (a reachable DB in .env), so wait for "finished", not "failed".
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
        while (!SplashController.isDbInitFinished() && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        // initDatabase()'s finally block then starts SyncService's global "sibim-sync"
        // watcher, which would keep flipping the shared DatabaseConfig in later tests.
        for (int i = 0; i < 10; i++) {
            SyncService.stopWatching();
            Thread.sleep(100);
        }
        DatabaseConfig.setOfflineMode(false);
        DatabaseConfig.setDemoMode(false);
    }

    @Test
    void fxmlCarga_sinExcepcion() { /* llegar aquí = pass */ }

    @Test
    void nodosClavePresentes() {
        assertNotNull(lookup("#logoBadge").query(),    "logoBadge debe existir");
        assertNotNull(lookup("#progressBar").query(),  "progressBar debe existir");
        assertNotNull(lookup("#lblStatus").query(),    "lblStatus debe existir");
        assertNotNull(lookup("#splashRoot").query(),   "splashRoot debe existir");
    }
}
