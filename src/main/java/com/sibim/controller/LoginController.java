package com.sibim.controller;

import com.sibim.MainApp;
import com.sibim.service.AuthService;
import com.sibim.util.AnimationUtils;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Label errorLabel;
    @FXML private ProgressIndicator spinner;
    @FXML private StackPane brandPanel;
    @FXML private VBox formPanel;

    private final AuthService authService = new AuthService();

    @FXML
    public void initialize() {
        errorLabel.setVisible(false);
        spinner.setVisible(false);

        usernameField.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) handleLogin(); });
        passwordField.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) handleLogin(); });
        // Clear error style on typing
        usernameField.textProperty().addListener((obs, o, n) -> {
            if (!n.isBlank()) usernameField.getStyleClass().remove("field-error");
        });
        passwordField.textProperty().addListener((obs, o, n) -> {
            if (!n.isBlank()) passwordField.getStyleClass().remove("field-error");
        });

        // Entrance animations
        if (brandPanel != null) AnimationUtils.fadeInLeft(brandPanel, 520, 0);
        if (formPanel  != null) AnimationUtils.fadeInRight(formPanel, 520, 160);
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        // Visual validation: highlight empty fields
        boolean hasError = false;
        if (username.isBlank()) {
            usernameField.getStyleClass().add("field-error");
            hasError = true;
        } else {
            usernameField.getStyleClass().remove("field-error");
        }
        if (password.isBlank()) {
            passwordField.getStyleClass().add("field-error");
            hasError = true;
        } else {
            passwordField.getStyleClass().remove("field-error");
        }
        if (hasError) {
            showError("Por favor ingresa usuario y contraseña");
            return;
        }

        setLoading(true);

        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                authService.login(username, password);
                return null;
            }
            @Override protected void succeeded() {
                setLoading(false);
                try { MainApp.showMain(); }
                catch (Exception e) { showError("Error al cargar la pantalla principal: " + e.getMessage()); }
            }
            @Override protected void failed() {
                setLoading(false);
                Throwable ex = getException();
                showError(ex.getMessage() != null ? ex.getMessage() : "Error desconocido");
                passwordField.clear();
                passwordField.requestFocus();
            }
        };
        new Thread(task).start();
    }

    @FXML private void fillAdmin()     { fill("superusuario", "admin123456"); }
    @FXML private void fillSecretario(){ fill("jm.zuniga",    "sec123456"); }
    @FXML private void fillDireccion() { fill("rh.garcia",    "dir123456"); }

    private void fill(String user, String pass) {
        usernameField.setText(user);
        passwordField.setText(pass);
        passwordField.requestFocus();
    }

    private void showError(String msg) {
        Platform.runLater(() -> {
            errorLabel.setText(msg);
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
            AnimationUtils.fadeIn(errorLabel, 200, 0);
            AnimationUtils.shake(passwordField);
            // Auto-dismiss after 5 seconds
            PauseTransition dismiss = new PauseTransition(Duration.seconds(5));
            dismiss.setOnFinished(e -> {
                errorLabel.setVisible(false);
                errorLabel.setManaged(false);
            });
            dismiss.play();
        });
    }

    private void setLoading(boolean loading) {
        Platform.runLater(() -> {
            loginButton.setDisable(loading);
            spinner.setVisible(loading);
            spinner.setManaged(loading);
            if (loading) {
                errorLabel.setVisible(false);
                errorLabel.setManaged(false);
            }
        });
    }
}
