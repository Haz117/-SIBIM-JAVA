package com.sibim.controller;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.sibim.config.Areas;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.repository.UsuarioRepository;
import com.sibim.session.SessionManager;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.NotificacionUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import com.sibim.util.DialogUtil;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ConfiguracionController {

    @FXML private Label lblNombreUsuario;
    @FXML private Label lblUsernameUsuario;
    @FXML private Label lblRolUsuario;
    @FXML private Label lblAreaUsuario;
    @FXML private Label lblAvatarPerfil;
    @FXML private Label dotSistemaModo;
    @FXML private Label lblSistemaModo;
    @FXML private Label lblSistemaHora;

    @FXML private TableView<Usuario> usersTable;
    @FXML private TableColumn<Usuario, String> colNombre;
    @FXML private TableColumn<Usuario, String> colUsername;
    @FXML private TableColumn<Usuario, String> colCargo;
    @FXML private TableColumn<Usuario, String> colRol;
    @FXML private TableColumn<Usuario, String> colArea;
    @FXML private VBox adminSection;
    @FXML private VBox auditSection;
    @FXML private Button btnEditUser;
    @FXML private Button btnPasswordUser;
    @FXML private Button btnDeleteUser;

    private final UsuarioRepository usuarioRepo = new UsuarioRepository();
    private final com.sibim.repository.AuditLogRepository auditRepo = new com.sibim.repository.AuditLogRepository();
    private final com.sibim.repository.ConteoRepository conteoRepo = new com.sibim.repository.ConteoRepository();

    @FXML
    public void initialize() {
        Usuario me = SessionManager.getCurrentUser();
        String nombre = me.getNombre();
        lblNombreUsuario.setText(nombre);
        lblUsernameUsuario.setText("@" + me.getUsername());
        lblRolUsuario.setText(me.getRol().getEtiqueta());
        lblAreaUsuario.setText(me.getArea() != null ? me.getArea() : "—");
        if (lblAvatarPerfil != null && nombre != null && !nombre.isBlank()) {
            lblAvatarPerfil.setText(String.valueOf(nombre.charAt(0)).toUpperCase());
        }

        // System info
        if (lblSistemaModo != null) {
            boolean demo = com.sibim.db.DatabaseConfig.isDemoMode();
            lblSistemaModo.setText(demo ? "Modo demostración" : "Base de datos activa");
            lblSistemaModo.getStyleClass().add(demo ? "sys-info-demo" : "sys-info-ok");
            if (dotSistemaModo != null) dotSistemaModo.getStyleClass().add(demo ? "dot-amber" : "dot-green");
        }
        if (lblSistemaHora != null)
            lblSistemaHora.setText(java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

        boolean isAdmin = SessionManager.isAdmin();
        adminSection.setVisible(isAdmin);
        adminSection.setManaged(isAdmin);
        if (auditSection != null) {
            auditSection.setVisible(isAdmin);
            auditSection.setManaged(isAdmin);
        }

        if (isAdmin) {
            setupUsersTable();
            loadUsers();
            usersTable.getSelectionModel().selectedItemProperty().addListener((obs, o, sel) -> {
                boolean s = sel != null;
                if (btnEditUser     != null) btnEditUser.setDisable(!s);
                if (btnPasswordUser != null) btnPasswordUser.setDisable(!s);
                if (btnDeleteUser   != null) btnDeleteUser.setDisable(!s);
            });
            usersTable.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && usersTable.getSelectionModel().getSelectedItem() != null)
                    onEditUsuario();
            });

            // Context menu
            ContextMenu cm = new ContextMenu();
            MenuItem cmEditar    = new MenuItem("✏  Editar");
            MenuItem cmPassword  = new MenuItem("🔑  Cambiar contraseña");
            MenuItem cmEliminar  = new MenuItem("✕  Eliminar");
            cmEditar.setOnAction(e -> onEditUsuario());
            cmPassword.setOnAction(e -> onCambiarPassword());
            cmEliminar.setOnAction(e -> onDeleteUsuario());
            cm.getItems().addAll(cmEditar, cmPassword, new SeparatorMenuItem(), cmEliminar);
            usersTable.setContextMenu(cm);
        }
    }

    private void setupUsersTable() {
        usersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colUsername.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUsername()));
        colCargo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCargo()));
        colRol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRol().getEtiqueta()));
        colRol.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            { badge.getStyleClass().add("cell-badge"); }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(null);
                if (empty || item == null) return;
                badge.setText(item);
                badge.getStyleClass().removeAll("cell-badge-purple","cell-badge-blue","cell-badge-teal");
                badge.getStyleClass().add(switch (item) {
                    case "Administrador" -> "cell-badge-purple";
                    case "Secretario"    -> "cell-badge-blue";
                    default              -> "cell-badge-teal";
                });
                setGraphic(badge); setText(null);
            }
        });
        colArea.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getArea() != null ? c.getValue().getArea() : "—"));
        colArea.setCellFactory(col -> new TableCell<>() {
            private final Tooltip tip = new Tooltip();
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setTooltip(null); return; }
                setText(item);
                tip.setText(item);
                setTooltip(tip);
            }
        });
    }

    private void refreshProfileCard(Usuario u) {
        SessionManager.setCurrentUser(u);
        lblNombreUsuario.setText(u.getNombre());
        lblUsernameUsuario.setText("@" + u.getUsername());
        lblRolUsuario.setText(u.getRol().getEtiqueta());
        lblAreaUsuario.setText(u.getArea() != null ? u.getArea() : "—");
        if (lblAvatarPerfil != null && u.getNombre() != null && !u.getNombre().isBlank())
            lblAvatarPerfil.setText(String.valueOf(u.getNombre().charAt(0)).toUpperCase());
    }

    private void loadUsers() {
        DialogUtil.runAsync(
            () -> usuarioRepo.findAll(),
            users -> usersTable.setItems(FXCollections.observableArrayList(users)),
            e -> NotificacionUtil.error(usersTable.getScene(), "No se pudo cargar la lista de usuarios")
        );
    }

    @FXML
    private void onNuevoUsuario() { showUserDialog(null, false); }

    @FXML
    private void onEditUsuario() {
        Usuario sel = usersTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            NotificacionUtil.advertencia(usersTable.getScene(), "Selecciona un usuario para editar");
            return;
        }
        showUserDialog(sel, false);
    }

    @FXML
    private void onCambiarPassword() {
        Usuario sel = usersTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            NotificacionUtil.advertencia(usersTable.getScene(), "Selecciona un usuario primero");
            return;
        }
        showUserDialog(sel, true);
    }

    @FXML
    private void onDeleteUsuario() {
        Usuario sel = usersTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        if (sel.getId().equals(SessionManager.getCurrentUser().getId())) {
            NotificacionUtil.error(usersTable.getScene(), "No puedes eliminar tu propia cuenta");
            return;
        }
        if (!ConfirmacionUtil.confirmarEliminar(sel.getNombre())) return;
        DialogUtil.runAsync(
            () -> usuarioRepo.delete(sel.getId()),
            () -> {
                loadUsers();
                NotificacionUtil.exito(usersTable.getScene(), "Usuario \"" + sel.getNombre() + "\" eliminado");
            },
            e -> NotificacionUtil.error(usersTable.getScene(),
                e instanceof IllegalStateException ? e.getMessage() : "No se pudo eliminar el usuario")
        );
    }

    private void showUserDialog(Usuario existing, boolean passwordOnly) {
        boolean isNew = existing == null;
        Dialog<ButtonType> dialog = DialogUtil.createButtonDialog(480);

        String accent  = passwordOnly ? "#B45309" : (isNew ? "#4F46E5" : "#0369A1");
        String accent2 = passwordOnly ? "#D97706" : (isNew ? "#7C3AED" : "#0891B2");
        String iconChar = passwordOnly ? "🔑" : (isNew ? "👤" : "✏");
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

        // Disable OK until required fields are valid — prevents validation-after-close
        if (okBtn != null) {
            if (passwordOnly) {
                okBtn.setDisable(true);
                fPassword.textProperty().addListener((obs, o, n) -> okBtn.setDisable(n.length() < 8));
            } else if (isNew) {
                okBtn.setDisable(true);
                Runnable check = () -> okBtn.setDisable(
                    fNombre.getText().isBlank() || fUsername.getText().isBlank() || fPassword.getText().length() < 8);
                fNombre.textProperty().addListener((obs, o, n) -> check.run());
                fUsername.textProperty().addListener((obs, o, n) -> check.run());
                fPassword.textProperty().addListener((obs, o, n) -> check.run());
            } else {
                okBtn.setDisable(fNombre.getText().isBlank() || fUsername.getText().isBlank());
                Runnable check = () -> okBtn.setDisable(fNombre.getText().isBlank() || fUsername.getText().isBlank());
                fNombre.textProperty().addListener((obs, o, n) -> check.run());
                fUsername.textProperty().addListener((obs, o, n) -> check.run());
            }
        }

        dialog.getDialogPane().setContent(new VBox(0, header, grid));
        Platform.runLater(() -> (passwordOnly ? fPassword : fNombre).requestFocus());

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            if (passwordOnly) {
                DialogUtil.runAsync(
                    () -> {
                        String hash = BCrypt.withDefaults().hashToString(12, fPassword.getText().toCharArray());
                        usuarioRepo.updatePassword(existing.getId(), hash);
                    },
                    () -> {
                        loadUsers();
                        NotificacionUtil.exito(usersTable.getScene(), "Contraseña actualizada correctamente");
                    },
                    e -> NotificacionUtil.error(usersTable.getScene(), "No se pudo guardar el usuario")
                );
            } else {
                DialogUtil.runAsync(
                    () -> {
                        String usernameVal = fUsername.getText().trim().toLowerCase();
                        String excludeId = existing != null ? existing.getId() : null;
                        if (usuarioRepo.existsByUsername(usernameVal, excludeId)) {
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
                        usuarioRepo.save(u);
                        return u;
                    },
                    u -> {
                        boolean editedSelf = !isNew && u.getId().equals(SessionManager.getCurrentUser().getId());
                        loadUsers();
                        NotificacionUtil.exito(usersTable.getScene(),
                            isNew ? "Usuario registrado exitosamente" : "Usuario actualizado correctamente");
                        if (editedSelf) refreshProfileCard(u);
                    },
                    e -> {
                        if (e instanceof IllegalStateException) {
                            NotificacionUtil.advertencia(usersTable.getScene(), e.getMessage());
                        } else {
                            NotificacionUtil.error(usersTable.getScene(), "No se pudo guardar el usuario");
                        }
                    }
                );
            }
        }
    }

    @FXML
    private void onVerAuditoria() {
        DialogUtil.runAsync(
            () -> auditRepo.findAll(300),
            this::showAuditoriaDialog,
            e -> NotificacionUtil.error(usersTable.getScene(), "No se pudo cargar el historial de auditoría")
        );
    }

    private void showAuditoriaDialog(List<com.sibim.model.AuditLog> entries) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(600);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("📜", "Historial de Auditoría",
            "Cambios en bienes, categorías y usuarios — últimos " + entries.size() + " registros",
            "#475569", "#334155");

        VBox list = new VBox(6);
        list.setPadding(new javafx.geometry.Insets(4));
        if (entries.isEmpty()) {
            Label empty = new Label("Sin actividad registrada todavía");
            empty.getStyleClass().add("muted");
            list.getChildren().add(empty);
        }
        for (com.sibim.model.AuditLog a : entries) {
            HBox row = new HBox(12);
            row.getStyleClass().add("dlg-detail-header");
            row.setPadding(new javafx.geometry.Insets(9, 14, 9, 14));
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            VBox info = new VBox(2);
            Label titulo = new Label(accionEtiqueta(a.getAccion()) + " — " + entidadEtiqueta(a.getEntidad())
                + (a.getEntidadNombre() != null ? " \"" + a.getEntidadNombre() + "\"" : ""));
            titulo.getStyleClass().add("dlg-detail-value");
            Label detalle = new Label((a.getDetalle() != null ? a.getDetalle() + " · " : "")
                + a.getUsuarioNombre() + " · " + com.sibim.util.FormatUtils.formatDateTime(a.getCreadoEn()));
            detalle.getStyleClass().add("muted-sm");
            detalle.setWrapText(true);
            info.getChildren().addAll(titulo, detalle);
            HBox.setHgrow(info, javafx.scene.layout.Priority.ALWAYS);
            row.getChildren().add(info);
            list.getChildren().add(row);
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(400);
        scroll.getStyleClass().add("page-scroll");

        dialog.getDialogPane().setContent(new VBox(0, header, scroll));
        dialog.showAndWait();
    }

    private String accionEtiqueta(String accion) {
        if (accion == null) return "—";
        return switch (accion) {
            case "crear" -> "Creado";
            case "actualizar" -> "Actualizado";
            case "eliminar" -> "Eliminado";
            case "baja" -> "Dado de baja";
            case "reactivar" -> "Reactivado";
            default -> accion;
        };
    }

    private String entidadEtiqueta(String entidad) {
        if (entidad == null) return "—";
        return switch (entidad) {
            case "producto" -> "Bien";
            case "categoria" -> "Categoría";
            case "usuario" -> "Usuario";
            default -> entidad;
        };
    }

    @FXML
    private void onVerConteos() {
        DialogUtil.runAsync(
            () -> conteoRepo.findAll(100),
            this::showConteosDialog,
            e -> NotificacionUtil.error(usersTable.getScene(), "No se pudo cargar el historial de conteos")
        );
    }

    private void showConteosDialog(List<com.sibim.model.ConteoFisico> conteos) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(600);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("📋", "Historial de Conteos Físicos",
            "Tomas de inventario físico realizadas",
            "#0891B2", "#0E7490");

        VBox list = new VBox(6);
        list.setPadding(new javafx.geometry.Insets(4));
        if (conteos.isEmpty()) {
            Label empty = new Label("No se ha registrado ningún conteo físico todavía");
            empty.getStyleClass().add("muted");
            list.getChildren().add(empty);
        }
        for (com.sibim.model.ConteoFisico c : conteos) {
            HBox row = new HBox(12);
            row.getStyleClass().add("dlg-detail-header");
            row.setPadding(new javafx.geometry.Insets(9, 14, 9, 14));
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            VBox info = new VBox(2);
            Label titulo = new Label(com.sibim.util.FormatUtils.formatDateTime(c.getCreadoEn())
                + " · " + c.getUsuarioNombre());
            titulo.getStyleClass().add("dlg-detail-value");
            Label detalle = new Label(c.getTotalContados() + " bien(es) revisado(s) · "
                + c.getTotalDiscrepancias() + " diferencia(s)");
            detalle.getStyleClass().add("muted-sm");
            info.getChildren().addAll(titulo, detalle);
            HBox.setHgrow(info, javafx.scene.layout.Priority.ALWAYS);
            row.getChildren().add(info);
            list.getChildren().add(row);
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(400);
        scroll.getStyleClass().add("page-scroll");

        dialog.getDialogPane().setContent(new VBox(0, header, scroll));
        dialog.showAndWait();
    }
}
