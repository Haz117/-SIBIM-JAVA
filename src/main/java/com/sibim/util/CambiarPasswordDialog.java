package com.sibim.util;

import com.sibim.model.Usuario;
import com.sibim.repository.UsuarioRepository;
import com.sibim.service.AuthService;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Optional;

/**
 * Blocking dialog shown right after a successful login when the account's
 * debe_cambiar_password flag is set (seed/demo accounts, or an admin
 * password reset). There is no true "cancel": the account keeps a known
 * password until this completes, so the only way out is to actually set a
 * new one, or abandon the session entirely via "Cerrar sesión".
 */
public final class CambiarPasswordDialog {

    private CambiarPasswordDialog() {}

    public static void mostrarObligatorio(Usuario user, Runnable onCompletado, Runnable onCerrarSesion) {
        ButtonType btnGuardar = new ButtonType("Guardar y continuar", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnLogout  = new ButtonType("Cerrar sesión",       ButtonBar.ButtonData.CANCEL_CLOSE);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Cambio de contraseña obligatorio");
        dialog.getDialogPane().getButtonTypes().addAll(btnGuardar, btnLogout);
        dialog.getDialogPane().setPrefWidth(420);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        // No true cancel — force a choice between the two buttons below.
        dialog.setOnCloseRequest(Event::consume);

        HBox header = DialogUtil.gradientHeader("🔒", "Actualiza tu contraseña",
            "Esta cuenta tiene una contraseña temporal conocida. Define una nueva antes de continuar.",
            "#6366F1", "#4F46E5");

        PasswordField fNueva = new PasswordField();
        fNueva.setPromptText("Nueva contraseña (mínimo 8 caracteres)");
        fNueva.getStyleClass().add("form-input");

        PasswordField fConfirmar = new PasswordField();
        fConfirmar.setPromptText("Confirmar contraseña");
        fConfirmar.getStyleClass().add("form-input");

        Label errorLbl = new Label();
        errorLbl.getStyleClass().add("field-error-label");
        errorLbl.setVisible(false);
        errorLbl.setManaged(false);
        errorLbl.setWrapText(true);

        VBox form = new VBox(10,
            DialogUtil.fieldLabel("Nueva contraseña *"), fNueva,
            DialogUtil.fieldLabel("Confirmar contraseña *"), fConfirmar,
            errorLbl);
        form.setPadding(new Insets(18, 22, 20, 22));

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
            event.consume(); // validate/save first — only close once persisted
            String nueva = fNueva.getText();
            String confirmar = fConfirmar.getText();
            if (nueva.length() < 8) {
                errorLbl.setText("La contraseña debe tener al menos 8 caracteres");
                showError.run();
                return;
            }
            if (!nueva.equals(confirmar)) {
                errorLbl.setText("Las contraseñas no coinciden");
                showError.run();
                return;
            }
            guardarBtn.setDisable(true);
            fNueva.setDisable(true);
            fConfirmar.setDisable(true);
            DialogUtil.runAsync(
                () -> {
                    String hash = new AuthService().hashPassword(nueva);
                    new UsuarioRepository().completarCambioPassword(user.getId(), hash);
                    user.setPasswordHash(hash);
                    user.setDebeCambiarPassword(false);
                },
                () -> {
                    dialog.close();
                    if (onCompletado != null) onCompletado.run();
                },
                ex -> {
                    guardarBtn.setDisable(false);
                    fNueva.setDisable(false);
                    fConfirmar.setDisable(false);
                    errorLbl.setText("No se pudo guardar la contraseña. Intenta de nuevo.");
                    showError.run();
                }
            );
        });

        javafx.application.Platform.runLater(fNueva::requestFocus);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == btnLogout && onCerrarSesion != null) {
            onCerrarSesion.run();
        }
    }
}
