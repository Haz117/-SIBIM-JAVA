package com.sibim.controller;

import com.sibim.db.DatabaseConfig;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for LoginController that verify pure UI behaviour (field wiring,
 * visibility toggling, empty-field validation) without touching the database.
 * Demo mode is activated so the AuthService never attempts a real connection.
 *
 * These tests open a real JavaFX window. On headless CI add:
 *   -Dtestfx.headless=true -Dtestfx.robot=glass -Dprism.order=sw
 * to the surefire argLine.
 */
class LoginControllerSmokeTest extends ApplicationTest {

    @Override
    public void start(Stage stage) throws Exception {
        DatabaseConfig.setDemoMode(true);
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/fxml/login.fxml"));
        Parent root = loader.load();
        stage.setScene(new Scene(root, 960, 620));
        stage.show();
        stage.toFront();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        DatabaseConfig.setDemoMode(false);
    }

    @Test
    void camposVacios_clickLogin_muestraErrorLabel() {
        // Both fields are empty — fire the button action on the FX thread.
        // interact() guarantees the action handler (handleLogin) completes and
        // all synchronous showError() updates are applied before we assert.
        Button loginBtn = lookup("#loginButton").queryAs(Button.class);
        interact(loginBtn::fire);
        Label error = lookup("#errorLabel").queryAs(Label.class);
        assertTrue(error.isVisible(),
            "errorLabel debe ser visible cuando se intenta login con campos vacíos");
        assertFalse(error.getText().isBlank(),
            "errorLabel debe contener un mensaje de error");
    }

    @Test
    void togglePassword_intercambiaVisibilidadDeCampos() {
        PasswordField pwdField = lookup("#passwordField").queryAs(PasswordField.class);
        TextField revealField  = lookup("#passwordRevealField").queryAs(TextField.class);
        Button    toggle       = lookup("#btnTogglePassword").queryAs(Button.class);

        // Initial state: password field visible, reveal field hidden
        assertTrue(pwdField.isVisible(),    "passwordField debe ser visible al inicio");
        assertFalse(revealField.isVisible(), "passwordRevealField debe estar oculto al inicio");

        // Fire the toggle action on the FX thread and wait for it to complete.
        // interact() is more reliable than clickOn() here: the button sits inside
        // a StackPane overlaid by the PasswordField, so a simulated robot click
        // can land on the wrong node depending on rendering order.
        interact(toggle::fire);
        assertFalse(pwdField.isVisible(),  "passwordField debe ocultarse tras el toggle");
        assertTrue(revealField.isVisible(), "passwordRevealField debe mostrarse tras el toggle");

        // Fire again — should revert to original state
        interact(toggle::fire);
        assertTrue(pwdField.isVisible(),    "passwordField debe volver a ser visible");
        assertFalse(revealField.isVisible(), "passwordRevealField debe ocultarse de nuevo");
    }
}
