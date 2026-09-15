package com.sibim.controller;

import com.sibim.db.DatabaseConfig;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

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
        // Don't flip demoMode — initDatabase() may have already set it.
        // Just clean up the session (none was set, but be safe).
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
