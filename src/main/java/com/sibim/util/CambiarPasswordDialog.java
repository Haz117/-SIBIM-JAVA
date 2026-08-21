package com.sibim.util;

import com.sibim.MainApp;
import com.sibim.model.Usuario;
import com.sibim.repository.UsuarioRepository;
import com.sibim.service.AuthService;
import com.sibim.util.AnimationUtils;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Blocking dialog shown right after a successful login when the account's
 * debe_cambiar_password flag is set (seed/demo accounts, or an admin
 * password reset). There is no true "cancel": the account keeps a known
 * password until this completes, so the only way out is to actually set a
 * new one, or abandon the session entirely via "Cerrar sesión".
 *
 * Deliberately does NOT do the save asynchronously with a manual
 * dialog.close()/setResult() afterward — that combination was tried and
 * reproducibly resolved dialog.showAndWait()'s result to the wrong button
 * (logging the session out from under a successful save) regardless of
 * ButtonData used or whether close()/hide() was called on the Dialog or the
 * raw Stage. Root cause not fully pinned down (undocumented DialogPane
 * internals around how a window-hide not driven by its own button-click
 * handling resolves resultProperty), but the safe fix is to not fight it:
 * let JavaFX's own normal button-click handling close the dialog, which is
 * the one path proven to resolve results correctly. That means the save
 * itself has to happen synchronously, before the ActionEvent finishes
 * (or be left uncommitted via event.consume() to keep the dialog open on
 * failure). BCrypt hashing + a single UPDATE is ~100-300ms — an acceptable,
 * one-time pause for an explicit "Guardar" click.
 */
public final class CambiarPasswordDialog {

    private static final Logger log = LoggerFactory.getLogger(CambiarPasswordDialog.class);

    private CambiarPasswordDialog() {}

    public static void mostrarObligatorio(Usuario user, Runnable onCompletado, Runnable onCerrarSesion) {
        ButtonType btnGuardar = new ButtonType("Guardar y continuar", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnLogout  = new ButtonType("Cerrar sesión",       ButtonBar.ButtonData.CANCEL_CLOSE);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Cambio de contraseña obligatorio");
        dialog.getDialogPane().getButtonTypes().addAll(btnGuardar, btnLogout);
        dialog.getDialogPane().setPrefWidth(420);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        if (MainApp.getPrimaryStage() != null) dialog.initOwner(MainApp.getPrimaryStage());
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        // No true cancel via the window's own [X] — CANCEL_CLOSE on
        // btnLogout makes JavaFX resolve any close-via-X to that button on
        // its own, which is exactly the "Cerrar sesión" behavior we want,
        // through the normal/well-tested path instead of a custom handler.

        HBox header = DialogUtil.gradientHeader("mdi2l-lock-reset", "Actualiza tu contraseña",
            "Esta cuenta tiene una contraseña temporal conocida. Define una nueva antes de continuar.",
            "#6366F1", "#4F46E5");

        PasswordField fNueva = new PasswordField();
        fNueva.setPromptText("Nueva contraseña (mínimo 8 caracteres)");
        fNueva.getStyleClass().add("form-input");

        Label lblStrength = new Label();
        lblStrength.getStyleClass().add("pwd-strength-lbl");
        lblStrength.setVisible(false);
        lblStrength.setManaged(false);
        fNueva.textProperty().addListener((o, a, b) -> {
            boolean show = !b.isBlank();
            lblStrength.setVisible(show);
            lblStrength.setManaged(show);
            if (!show) return;
            int score = 0;
            if (b.length() >= 8) score++;
            if (b.matches(".*[0-9].*")) score++;
            if (b.matches(".*[!@#$%^&*_\\-+=].*")) score++;
            lblStrength.getStyleClass().removeAll("pwd-strength-weak", "pwd-strength-fair", "pwd-strength-strong");
            lblStrength.getStyleClass().add(score == 0 ? "pwd-strength-weak" : score == 1 ? "pwd-strength-fair" : "pwd-strength-strong");
            lblStrength.setText(score == 0 ? "Débil" : score == 1 ? "Regular" : "Fuerte");
        });

        PasswordField fConfirmar = new PasswordField();
        fConfirmar.setPromptText("Confirmar contraseña");
        fConfirmar.getStyleClass().add("form-input");

        Label errorLbl = new Label();
        errorLbl.getStyleClass().add("field-error-label");
        errorLbl.setVisible(false);
        errorLbl.setManaged(false);
        errorLbl.setWrapText(true);

        VBox form = new VBox(10,
            DialogUtil.fieldLabel("Nueva contraseña *"), fNueva, lblStrength,
            DialogUtil.fieldLabel("Confirmar contraseña *"), fConfirmar,
            errorLbl);
        form.setPadding(new Insets(18, 22, 20, 22));

        AnimationUtils.staggeredFadeInUp(java.util.List.of(header, form), 260, 70);
        dialog.getDialogPane().setContent(new VBox(header, form));

        Node guardarBtn = dialog.getDialogPane().lookupButton(btnGuardar);
        guardarBtn.getStyleClass().add("dialog-ok-btn");

        Runnable hideError = () -> { errorLbl.setVisible(false); errorLbl.setManaged(false); };
        Runnable showError = () -> {
            errorLbl.setVisible(true);
            errorLbl.setManaged(true);
        };

        fNueva.textProperty().addListener((o, a, b) -> hideError.run());
        fConfirmar.textProperty().addListener((o, a, b) -> hideError.run());

        guardarBtn.addEventFilter(ActionEvent.ACTION, event -> {
            String nueva = fNueva.getText();
            String confirmar = fConfirmar.getText();
            if (nueva.length() < 8) {
                event.consume();
                errorLbl.setText("La contraseña debe tener al menos 8 caracteres");
                showError.run();
                AnimationUtils.shake(fNueva);
                return;
            }
            if (!nueva.equals(confirmar)) {
                event.consume();
                errorLbl.setText("Las contraseñas no coinciden");
                showError.run();
                AnimationUtils.shake(fConfirmar);
                return;
            }
            try {
                String hash = new AuthService().hashPassword(nueva);
                new UsuarioRepository().completarCambioPassword(user.getId(), hash);
                user.setPasswordHash(hash);
                user.setDebeCambiarPassword(false);
                // Not consumed: the click proceeds through DialogPane's own
                // normal handling, which closes the window and resolves
                // showAndWait()'s result to btnGuardar correctly.
            } catch (Exception ex) {
                event.consume(); // keep the dialog open — save failed
                log.error("No se pudo completar el cambio obligatorio de contraseña para el usuario '{}'",
                    user.getUsername(), ex);
                errorLbl.setText("No se pudo guardar la contraseña. Intenta de nuevo.");
                showError.run();
            }
        });

        javafx.application.Platform.runLater(fNueva::requestFocus);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == btnLogout) {
            if (onCerrarSesion != null) onCerrarSesion.run();
        } else if (onCompletado != null) {
            onCompletado.run();
        }
    }
}
