package com.sibim.controller;

import com.sibim.MainApp;
import com.sibim.db.DatabaseConfig;
import com.sibim.model.Usuario;
import com.sibim.service.AuthService;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.CambiarPasswordDialog;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
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
    @FXML private VBox testAccountsBox;
    @FXML private Label brandLogoBadge;
    @FXML private VBox featureList;
    @FXML private VBox formHeader;
    @FXML private VBox usernameBox;
    @FXML private VBox passwordBox;
    @FXML private Label secureBadge;

    private final AuthService authService = new AuthService();

    @FXML
    public void initialize() {
        errorLabel.setVisible(false);
        spinner.setVisible(false);

        // Test-account quick-fill only makes sense against the seeded demo
        // data — a real deployment with a production Postgres has no such
        // accounts, and shipping visible real passwords is bad hygiene.
        if (testAccountsBox != null && !DatabaseConfig.isDemoMode()) {
            testAccountsBox.setVisible(false);
            testAccountsBox.setManaged(false);
        }

        usernameField.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) handleLogin(); });
        passwordField.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) handleLogin(); });
        // Clear error style on typing
        usernameField.textProperty().addListener((obs, o, n) -> {
            if (!n.isBlank()) usernameField.getStyleClass().remove("field-error");
        });
        passwordField.textProperty().addListener((obs, o, n) -> {
            if (!n.isBlank()) passwordField.getStyleClass().remove("field-error");
        });

        playEntrance();
    }

    /** The panels slide/fade in as a whole first (primary arrival motion);
     *  once each is fully visible, a second layer of staggered per-element
     *  flourishes plays on top — a logo "pop", the feature checklist
     *  cascading in, and the form fields arriving one after another. Timed
     *  so the two layers never overlap (no compounding opacity fades). */
    private void playEntrance() {
        if (brandPanel != null) AnimationUtils.fadeInLeft(brandPanel, 480, 0);
        if (formPanel  != null) AnimationUtils.fadeInRight(formPanel, 480, 140);

        if (brandLogoBadge != null) {
            brandLogoBadge.setOpacity(0);
            brandLogoBadge.setScaleX(0.6); brandLogoBadge.setScaleY(0.6);
            FadeTransition fade = new FadeTransition(Duration.millis(320), brandLogoBadge);
            fade.setFromValue(0); fade.setToValue(1);
            ScaleTransition pop = new ScaleTransition(Duration.millis(320), brandLogoBadge);
            pop.setFromX(0.6); pop.setFromY(0.6); pop.setToX(1); pop.setToY(1);
            pop.setInterpolator(Interpolator.EASE_OUT);
            ParallelTransition logoIn = new ParallelTransition(fade, pop);
            logoIn.setDelay(Duration.millis(520));
            logoIn.play();
        }
        if (featureList != null) staggerFadeInUp(featureList.getChildren(), 680, 70);

        if (formHeader   != null) AnimationUtils.fadeInUp(formHeader,   300, 640);
        if (usernameBox  != null) AnimationUtils.fadeInUp(usernameBox,  300, 700);
        if (passwordBox  != null) AnimationUtils.fadeInUp(passwordBox,  300, 760);
        if (loginButton  != null) AnimationUtils.fadeInUp(loginButton,  300, 820);
        if (secureBadge  != null) AnimationUtils.fadeInUp(secureBadge,  300, 880);
    }

    private void staggerFadeInUp(Iterable<Node> nodes, int baseDelayMs, int staggerMs) {
        int i = 0;
        for (Node n : nodes) {
            AnimationUtils.fadeInUp(n, 280, baseDelayMs + i * staggerMs);
            i++;
        }
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
                Usuario user = SessionManager.getCurrentUser();
                if (user != null && user.isDebeCambiarPassword()) {
                    CambiarPasswordDialog.mostrarObligatorio(user,
                        () -> {
                            try { MainApp.showMain(); }
                            catch (Exception e) { showError("No se pudo cargar la pantalla principal"); }
                        },
                        () -> {
                            SessionManager.logout();
                            passwordField.clear();
                        });
                } else {
                    try { MainApp.showMain(); }
                    catch (Exception e) { showError("No se pudo cargar la pantalla principal"); }
                }
            }
            @Override protected void failed() {
                setLoading(false);
                Throwable ex = getException();
                showError(ex.getMessage() != null ? ex.getMessage() : "Error desconocido");
                passwordField.clear();
                passwordField.requestFocus();
            }
        };
        com.sibim.util.AppExecutor.submit(task);
    }

    @FXML private void fillAdmin()     { fill("superusuario",    "admin123456"); }
    @FXML private void fillSecretario(){ fill("secretario.demo", "sec123456"); }
    @FXML private void fillDireccion() { fill("direccion.demo",  "dir123456"); }

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
