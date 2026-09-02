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
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField passwordRevealField;
    @FXML private Button btnTogglePassword;
    @FXML private org.kordamp.ikonli.javafx.FontIcon iconTogglePassword;
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
    @FXML private Label lblCapsLock;

    private final AuthService authService = new AuthService();

    @FXML
    public void initialize() {
        errorLabel.setVisible(false);
        spinner.setVisible(false);

        // Show/hide password — PasswordField itself can't reveal its text,
        // so passwordRevealField is a plain TextField kept in sync and
        // swapped in visually; passwordField.getText() (what handleLogin
        // reads) stays correct either way since they're bound together.
        if (passwordRevealField != null && btnTogglePassword != null) {
            passwordRevealField.textProperty().bindBidirectional(passwordField.textProperty());
            btnTogglePassword.setOnAction(e -> {
                boolean revealing = !passwordRevealField.isVisible();
                passwordField.setVisible(!revealing);
                passwordField.setManaged(!revealing);
                passwordRevealField.setVisible(revealing);
                passwordRevealField.setManaged(revealing);
                if (iconTogglePassword != null)
                    iconTogglePassword.setIconLiteral(revealing ? "mdi2e-eye-off-outline" : "mdi2e-eye-outline");
                Tooltip.install(btnTogglePassword,
                    new Tooltip(revealing ? "Ocultar contraseña" : "Mostrar contraseña"));
                (revealing ? passwordRevealField : passwordField).requestFocus();
                (revealing ? passwordRevealField : passwordField).end();
            });
        }

        // Test-account quick-fill only makes sense against the seeded demo
        // data — a real deployment with a production Postgres has no such
        // accounts, and shipping visible real passwords is bad hygiene.
        if (testAccountsBox != null && !DatabaseConfig.isDemoMode()) {
            testAccountsBox.setVisible(false);
            testAccountsBox.setManaged(false);
        }

        // Enter-to-submit already comes for free from onAction="#handleLogin"
        // on both fields in login.fxml (TextField/PasswordField fire an
        // ActionEvent on Enter natively) — a redundant KEY_PRESSED handler
        // here used to fire handleLogin() a second time for the same
        // keypress, submitting the login (and everything MainController does
        // once on startup — toasts, audit log, timers) twice.

        // Clear error style on typing
        usernameField.textProperty().addListener((obs, o, n) -> {
            if (!n.isBlank()) usernameField.getStyleClass().remove("field-error");
        });
        passwordField.textProperty().addListener((obs, o, n) -> {
            if (!n.isBlank()) passwordField.getStyleClass().remove("field-error");
        });

        // Caps Lock warning — checked on focus and on every key stroke in both password fields
        java.util.function.Supplier<Boolean> isCapsOn = () -> {
            try { return java.awt.Toolkit.getDefaultToolkit()
                    .getLockingKeyState(java.awt.event.KeyEvent.VK_CAPS_LOCK); }
            catch (Exception ignored) { return false; }
        };
        javafx.event.EventHandler<javafx.scene.input.KeyEvent> capsCheck = e -> {
            boolean on = isCapsOn.get();
            if (lblCapsLock != null) {
                lblCapsLock.setVisible(on);
                lblCapsLock.setManaged(on);
            }
        };
        passwordField.setOnKeyPressed(capsCheck);
        passwordRevealField.setOnKeyPressed(capsCheck);
        passwordField.focusedProperty().addListener((obs, o, focused) -> {
            if (lblCapsLock == null) return;
            if (focused) { boolean on = isCapsOn.get(); lblCapsLock.setVisible(on); lblCapsLock.setManaged(on); }
            else          { lblCapsLock.setVisible(false); lblCapsLock.setManaged(false); }
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
        if (loginButton.isDisabled()) return; // already submitting — ignore a second trigger
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
                // Can't push a password change anywhere while offline (user
                // management isn't in the sync scope — see the offline-mode
                // plan), so the mandatory dialog is deferred rather than
                // shown against a change that would just be lost. The
                // account keeps its known temporary password until they log
                // in again with a real connection.
                boolean canChangePassword = !com.sibim.db.DatabaseConfig.isOfflineMode();
                if (user != null && user.isDebeCambiarPassword() && canChangePassword) {
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

    // Both call sites (handleLogin direct path + Task.succeeded/failed) run on
    // the FX application thread, so Platform.runLater() is never needed here.
    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
        AnimationUtils.fadeIn(errorLabel, 200, 0);
        AnimationUtils.shake(passwordField);
        PauseTransition dismiss = new PauseTransition(Duration.seconds(5));
        dismiss.setOnFinished(e -> {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        });
        dismiss.play();
    }

    private void setLoading(boolean loading) {
        loginButton.setDisable(loading);
        spinner.setVisible(loading);
        spinner.setManaged(loading);
        if (loading) {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        }
    }
}
