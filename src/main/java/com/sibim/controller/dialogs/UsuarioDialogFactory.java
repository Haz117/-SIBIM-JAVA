package com.sibim.controller.dialogs;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.sibim.config.Areas;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.repository.UsuarioRepository;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class UsuarioDialogFactory {

    private UsuarioDialogFactory() {}

    /**
     * Builds and shows the user create/edit/password-change dialog.
     *
     * @param existing     the user being edited, or null when creating a new one
     * @param passwordOnly true to show only the password field (change-password flow)
     * @param ownerScene   scene used for owner-window lookup and notifications
     * @param repo         UsuarioRepository for persistence calls
     * @param onSaved      called on the FX thread after a successful save —
     *                     receives the saved Usuario (or null for password-only changes)
     */
    public static void show(Usuario existing, boolean passwordOnly,
                            Scene ownerScene,
                            UsuarioRepository repo,
                            Consumer<Usuario> onSaved) {
        boolean isNew = existing == null;
        Dialog<ButtonType> dialog = DialogUtil.createButtonDialog(480);

        String accent  = passwordOnly ? "#B45309" : (isNew ? "#4F46E5" : "#0369A1");
        String accent2 = passwordOnly ? "#D97706" : (isNew ? "#7C3AED" : "#0891B2");
        String iconChar = passwordOnly ? "mdi2k-key-outline" : (isNew ? "mdi2a-account-plus-outline" : "mdi2p-pencil");
        String titleStr = passwordOnly ? "Cambiar Contraseña"
            : (isNew ? "Nuevo Usuario" : "Editar Usuario");
        String subStr = passwordOnly
            ? "Actualiza la contraseña de " + (existing != null ? existing.getNombre() : "usuario")
            : (isNew ? "Registra una nueva cuenta de acceso al sistema"
                     : "Actualiza los datos de " + existing.getNombre());

        DialogUtil.styleOkButton(dialog.getDialogPane(), accent2);
        HBox header = DialogUtil.gradientHeader(iconChar, titleStr, subStr, accent, accent2);
        GridPane grid = DialogUtil.formGrid(115);
        Node okBtn = DialogUtil.getOkButton(dialog.getDialogPane());

        int row = 0;

        TextField fNombre = new TextField(existing != null ? existing.getNombre() : "");
        fNombre.setPromptText("Nombre completo del usuario");
        fNombre.setMaxWidth(Double.MAX_VALUE);

        TextField fUsername = new TextField(existing != null ? existing.getUsername() : "");
        fUsername.setPromptText("nombre.apellido (sin espacios)");
        fUsername.setMaxWidth(Double.MAX_VALUE);

        PasswordField fPassword = new PasswordField();
        fPassword.setPromptText("Mínimo 8 caracteres");
        fPassword.setMaxWidth(Double.MAX_VALUE);

        TextField fCargo = new TextField(existing != null ? existing.getCargo() : "");
        fCargo.setPromptText("ej. Director, Secretario, Encargado...");
        fCargo.setMaxWidth(Double.MAX_VALUE);

        ComboBox<Rol> fRol = new ComboBox<>(FXCollections.observableArrayList(Rol.values()));
        fRol.setValue(existing != null ? existing.getRol() : Rol.DIRECCION);
        fRol.setMaxWidth(Double.MAX_VALUE);
        fRol.setConverter(new javafx.util.StringConverter<>() {
            public String toString(Rol r) {
                return r == null ? "" : switch (r) {
                    case ADMIN      -> "Administrador — acceso total";
                    case SECRETARIO -> "Secretario — ve su secretaría y sus direcciones";
                    case DIRECCION  -> "Dirección — acceso solo a su área";
                };
            }
            public Rol fromString(String s) { return null; }
        });

        List<String> areaNames = new java.util.ArrayList<>(Areas.getAllAreaNames());
        ComboBox<String> fArea = new ComboBox<>(FXCollections.observableArrayList(areaNames));
        fArea.setValue(existing != null ? existing.getArea() : null);
        fArea.setPromptText("Selecciona el área asignada");
        fArea.setMaxWidth(Double.MAX_VALUE);
        fArea.setDisable(fRol.getValue() == Rol.ADMIN);

        if (!passwordOnly) {
            grid.add(DialogUtil.fieldLabel("Nombre *"),   0, row); grid.add(fNombre,   1, row++);
            grid.add(DialogUtil.fieldLabel("Usuario *"),  0, row); grid.add(fUsername, 1, row++);
            grid.add(DialogUtil.fieldLabel("Cargo"),      0, row); grid.add(fCargo,    1, row++);
            grid.add(DialogUtil.fieldLabel("Rol *"),      0, row); grid.add(fRol,      1, row++);
            grid.add(DialogUtil.fieldLabel("Área"),       0, row); grid.add(fArea,     1, row++);
        }
        if (existing == null || passwordOnly) {
            grid.add(DialogUtil.fieldLabel("Contraseña *"), 0, row); grid.add(fPassword, 1, row);
        }

        fRol.valueProperty().addListener((obs, o, n) -> fArea.setDisable(n == Rol.ADMIN));

        Label lblFormError = new Label();
        lblFormError.getStyleClass().add("field-error-label");
        lblFormError.setVisible(false);
        lblFormError.setManaged(false);
        lblFormError.setWrapText(true);

        // Area is required for every role except ADMIN — SessionManager
        // .getAccessibleAreas() builds its area set from user.getArea(), so
        // a SECRETARIO/DIRECCION saved with no area ends up with a Set
        // containing null as its only element: they log in successfully but
        // every area-filtered query (products, movements, alerts...) comes
        // back empty, with nothing in the UI explaining why. This used to
        // be silently saveable — now it blocks OK like every other required
        // field in this form.
        java.util.function.Supplier<Boolean> areaMissing = () ->
            !passwordOnly && fRol.getValue() != Rol.ADMIN && (fArea.getValue() == null || fArea.getValue().isBlank());

        // Disable OK until required fields are valid — prevents validation-after-close
        if (okBtn != null) {
            if (passwordOnly) {
                okBtn.setDisable(true);
                fPassword.textProperty().addListener((obs, o, n) -> okBtn.setDisable(n.length() < 8));
            } else if (isNew) {
                okBtn.setDisable(true);
                Runnable check = () -> {
                    boolean missing = fNombre.getText().isBlank() || fUsername.getText().isBlank()
                        || fPassword.getText().length() < 8 || areaMissing.get();
                    okBtn.setDisable(missing);
                    lblFormError.setVisible(areaMissing.get());
                    lblFormError.setManaged(areaMissing.get());
                    if (areaMissing.get()) lblFormError.setText("Selecciona el área asignada para este rol.");
                };
                fNombre.textProperty().addListener((obs, o, n) -> check.run());
                fUsername.textProperty().addListener((obs, o, n) -> check.run());
                fPassword.textProperty().addListener((obs, o, n) -> check.run());
                fRol.valueProperty().addListener((obs, o, n) -> check.run());
                fArea.valueProperty().addListener((obs, o, n) -> check.run());
            } else {
                Runnable check = () -> {
                    boolean missing = fNombre.getText().isBlank() || fUsername.getText().isBlank() || areaMissing.get();
                    okBtn.setDisable(missing);
                    lblFormError.setVisible(areaMissing.get());
                    lblFormError.setManaged(areaMissing.get());
                    if (areaMissing.get()) lblFormError.setText("Selecciona el área asignada para este rol.");
                };
                check.run();
                fNombre.textProperty().addListener((obs, o, n) -> check.run());
                fUsername.textProperty().addListener((obs, o, n) -> check.run());
                fRol.valueProperty().addListener((obs, o, n) -> check.run());
                fArea.valueProperty().addListener((obs, o, n) -> check.run());
            }
        }

        AnimationUtils.staggeredFadeInUp(java.util.List.of(header, grid), 270, 70);
        dialog.getDialogPane().setContent(new VBox(0, header, grid, lblFormError));
        Platform.runLater(() -> (passwordOnly ? fPassword : fNombre).requestFocus());

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            if (passwordOnly) {
                DialogUtil.runAsync(
                    () -> {
                        String hash = BCrypt.withDefaults().hashToString(12, fPassword.getText().toCharArray());
                        repo.updatePassword(existing.getId(), hash);
                    },
                    () -> {
                        NotificacionUtil.exito(ownerScene, "Contraseña actualizada correctamente");
                        onSaved.accept(null);
                    },
                    e -> NotificacionUtil.error(ownerScene, "No se pudo guardar el usuario")
                );
            } else {
                DialogUtil.runAsync(
                    () -> {
                        String usernameVal = fUsername.getText().trim().toLowerCase();
                        String excludeId = existing != null ? existing.getId() : null;
                        if (repo.existsByUsername(usernameVal, excludeId)) {
                            throw new IllegalStateException("Ya existe un usuario con ese nombre de usuario");
                        }
                        Usuario u = existing != null ? existing : new Usuario();
                        if (u.getId() == null) {
                            u.setId(UUID.randomUUID().toString());
                            u.setCreadoEn(LocalDateTime.now());
                        }
                        u.setNombre(fNombre.getText().trim());
                        u.setUsername(usernameVal);
                        u.setCargo(fCargo.getText().trim());
                        u.setRol(fRol.getValue());
                        u.setArea(fRol.getValue() == Rol.ADMIN ? null : fArea.getValue());
                        if (!fPassword.getText().isBlank()) {
                            u.setPasswordHash(BCrypt.withDefaults().hashToString(12, fPassword.getText().toCharArray()));
                            // The admin just typed this password themself — force the
                            // new user to pick their own on first login.
                            u.setDebeCambiarPassword(true);
                        }
                        repo.save(u);
                        return u;
                    },
                    u -> {
                        NotificacionUtil.exito(ownerScene,
                            isNew ? "Usuario registrado exitosamente" : "Usuario actualizado correctamente");
                        onSaved.accept(u);
                    },
                    e -> {
                        if (e instanceof IllegalStateException) {
                            NotificacionUtil.advertencia(ownerScene, e.getMessage());
                        } else {
                            NotificacionUtil.error(ownerScene, "No se pudo guardar el usuario");
                        }
                    }
                );
            }
        }
    }
}
