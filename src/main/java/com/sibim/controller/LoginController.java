package com.sibim.controller;

import com.sibim.MainApp;
import com.sibim.db.DatabaseConfig;
import com.sibim.model.Usuario;
import com.sibim.repository.ConfiguracionRepository;
import com.sibim.service.AuthService;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.DialogUtil;
import com.sibim.controller.dialogs.CambiarPasswordDialog;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField passwordRevealField;
    @FXML private Button btnTogglePassword;
    @FXML private FontIcon iconTogglePassword;
    @FXML private Button loginButton;
    @FXML private Label errorLabel;
    @FXML private ProgressIndicator spinner;
    @FXML private VBox testAccountsBox;
    @FXML private Label lblCapsLock;
    @FXML private Label lblInstitucion;

    private final AuthService authService = new AuthService();
    private int failedAttempts = 0;
    private Timeline lockoutTimer = null;
    private PauseTransition errorDismiss = null;

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

        loadInstitucion();
    }

    /** The institution's configured name: it outlives any administration. */
    private void loadInstitucion() {
        if (lblInstitucion == null) return;
        String porDefecto = lblInstitucion.getText();
        DialogUtil.runAsync(
            () -> new ConfiguracionRepository().get("nombre_ayuntamiento", porDefecto),
            nombre -> { if (nombre != null && !nombre.isBlank()) lblInstitucion.setText(nombre); },
            e -> {}
        );
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

        Task<com.sibim.service.AuthService.LoginResult> task = new Task<>() {
            @Override protected com.sibim.service.AuthService.LoginResult call() throws Exception {
                return authService.login(username, password);
            }
            @Override protected void succeeded() {
                setLoading(false);
                com.sibim.service.AuthService.LoginResult result = getValue();
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
                    if (!pedirOperador(user)) {
                        SessionManager.logout();
                        passwordField.clear();
                        return;
                    }
                    try {
                        MainApp.showMain();
                        if (result != null && result.hasWarning()) {
                            javafx.application.Platform.runLater(() ->
                                com.sibim.util.NotificacionUtil.advertencia(
                                    com.sibim.MainApp.getPrimaryStage().getScene(),
                                    result.offlineWarning()));
                        }
                    } catch (Exception e) { showError("No se pudo cargar la pantalla principal"); }
                }
            }
            @Override protected void failed() {
                setLoading(false);
                Throwable ex = getException();
                showError(ex.getMessage() != null ? ex.getMessage() : "Error desconocido");
                passwordField.clear();
                passwordField.requestFocus();
                failedAttempts++;
                if (failedAttempts >= 5) {
                    startLockout();
                }
            }
        };
        com.sibim.util.AppExecutor.submit(task);
    }

    /** A shared área account is used by several people: who it is goes into
     *  the records. Asked at every sign-in, with the last name used on this PC
     *  already typed in — remembered without asking, the changes of whoever
     *  sat down next went under the previous person's name. False if they
     *  cancel the question (back to the login). */
    private boolean pedirOperador(Usuario user) {
        if (user == null || !com.sibim.service.CuentasAreaService.CARGO_COMPARTIDA.equals(user.getCargo())) return true;
        String recordado = com.sibim.session.OperadorRecordado.de(user.getId());
        var nombre = OperadorDialog.preguntar(user.getNombre(), recordado);
        nombre.ifPresent(n -> {
            SessionManager.setOperador(n);
            com.sibim.session.OperadorRecordado.recordar(user.getId(), n);
            OperadorDialog.registrar(user.getId(), n);
        });
        return nombre.isPresent();
    }

    @FXML
    private void onForgotPassword() {
        var dialog = com.sibim.util.DialogUtil.styledMessage(
            "mdi2l-lock-reset", "Restablecer contraseña", "Contacta al administrador del sistema",
            com.sibim.util.AppColors.PRIMARY_D, com.sibim.util.AppColors.INDIGO,
            "Para restablecer tu contraseña, contacta al administrador del sistema.\n\n"
            + "El administrador puede cambiarte la contraseña desde\n"
            + "Configuración → Usuarios → Cambiar contraseña.",
            MainApp.getPrimaryStage());
        dialog.getDialogPane().getButtonTypes().setAll(javafx.scene.control.ButtonType.OK);
        dialog.showAndWait();
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
        if (errorDismiss != null) errorDismiss.stop();
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
        AnimationUtils.fadeIn(errorLabel, 200, 0);
        AnimationUtils.shake(passwordField);
        errorDismiss = new PauseTransition(Duration.seconds(5));
        errorDismiss.setOnFinished(e -> {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        });
        errorDismiss.play();
    }

    private void startLockout() {
        loginButton.setDisable(true);
        usernameField.setDisable(true);
        passwordField.setDisable(true);
        if (passwordRevealField != null) passwordRevealField.setDisable(true);
        final int[] remaining = {30};
        showError("Demasiados intentos fallidos. Espera 30 segundos…");
        lockoutTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (--remaining[0] <= 0) {
                lockoutTimer.stop();
                endLockout();
            } else {
                showError("Demasiados intentos fallidos. Espera " + remaining[0] + " segundos…");
            }
        }));
        lockoutTimer.setCycleCount(30);
        lockoutTimer.play();
    }

    private void endLockout() {
        loginButton.setDisable(false);
        usernameField.setDisable(false);
        passwordField.setDisable(false);
        if (passwordRevealField != null) passwordRevealField.setDisable(false);
        failedAttempts = 0;
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        usernameField.requestFocus();
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
