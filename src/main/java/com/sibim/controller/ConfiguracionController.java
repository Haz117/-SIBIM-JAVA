package com.sibim.controller;

import com.sibim.controller.dialogs.UsuarioDialogFactory;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.repository.UsuarioRepository;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;

public class ConfiguracionController {

    @FXML private VBox  profileCard;
    @FXML private VBox  sysInfoCard;
    @FXML private VBox  configCard;
    @FXML private Label lblNombreUsuario;
    @FXML private Label lblUsernameUsuario;
    @FXML private Label lblRolUsuario;
    @FXML private Label lblAreaUsuario;
    @FXML private Label lblAvatarPerfil;
    @FXML private Label lblDecoNombre;
    @FXML private Label lblDecoSub;
    @FXML private Label dotSistemaModo;
    @FXML private Label lblSistemaModo;
    @FXML private Label helpUsuarios;
    @FXML private Label helpAuditoria;
    @FXML private Label helpRespaldo;
    @FXML private Label lblSistemaHora;
    @FXML private Label lblConfigHint;

    @FXML private TextField tfNombreAyuntamiento;
    @FXML private TextField tfMunicipio;
    @FXML private TextField tfResponsable;
    @FXML private TextField tfCorreoContacto;
    @FXML private Button    btnGuardarConfig;

    @FXML private TableView<Usuario> usersTable;
    @FXML private TableColumn<Usuario, String>  colNombre;
    @FXML private TableColumn<Usuario, String>  colUsername;
    @FXML private TableColumn<Usuario, String>  colCargo;
    @FXML private TableColumn<Usuario, String>  colRol;
    @FXML private TableColumn<Usuario, String>  colArea;
    @FXML private TableColumn<Usuario, Boolean> colActivo;
    @FXML private VBox adminSection;
    @FXML private VBox auditSection;
    @FXML private VBox backupSection;
    @FXML private Button btnGenerarRespaldo;
    @FXML private Button btnRestaurar;
    @FXML private Button btnEditUser;
    @FXML private Button btnPasswordUser;
    @FXML private Button btnDeleteUser;
    @FXML private TextField userSearchField;
    @FXML private Button btnClearUserSearch;

    private final UsuarioRepository usuarioRepo = new UsuarioRepository();
    private List<Usuario> allUsers = new java.util.ArrayList<>();
    private final com.sibim.repository.AuditLogRepository auditRepo = new com.sibim.repository.AuditLogRepository();
    private final com.sibim.repository.ConteoRepository conteoRepo = new com.sibim.repository.ConteoRepository();
    private final com.sibim.service.BackupService backupService = new com.sibim.service.BackupService();
    private final com.sibim.repository.ConfiguracionRepository configRepo = new com.sibim.repository.ConfiguracionRepository();

    @FXML
    public void initialize() {
        for (Label badge : new Label[]{ helpUsuarios, helpAuditoria, helpRespaldo }) {
            if (badge != null) DialogUtil.enableClickToShowTooltip(badge);
        }

        Usuario me = SessionManager.getCurrentUser();
        if (me == null) return;
        String nombre = me.getNombre();
        lblNombreUsuario.setText(nombre);
        lblUsernameUsuario.setText("@" + me.getUsername());
        lblRolUsuario.setText(me.getRol().getEtiqueta());
        lblAreaUsuario.setText(me.getArea() != null ? me.getArea() : "—");
        if (lblAvatarPerfil != null && nombre != null && !nombre.isBlank()) {
            lblAvatarPerfil.setText(String.valueOf(nombre.charAt(0)).toUpperCase());
        }

        // System info — mirrors the status bar's own tri-state check
        // (MainController#updateStatusBar): this used to only ever check
        // isDemoMode(), so a PC working offline (real, unplanned case) saw
        // "Base de datos activa" with a green dot here — actively wrong at
        // exactly the moment a user most needs to know they're offline.
        if (lblSistemaModo != null) {
            boolean demo    = com.sibim.db.DatabaseConfig.isDemoMode();
            boolean offline = com.sibim.db.DatabaseConfig.isOfflineMode();
            String texto, dotClass;
            if (offline) {
                texto = "Modo offline · " + com.sibim.db.offline.SyncService.pendingCount() + " pendiente(s)";
                dotClass = "dot-amber";
            } else if (demo) {
                texto = "Modo demostración";
                dotClass = "dot-amber";
            } else {
                texto = "Base de datos activa";
                dotClass = "dot-green";
            }
            lblSistemaModo.setText(texto);
            lblSistemaModo.getStyleClass().removeAll("sys-info-demo", "sys-info-ok");
            lblSistemaModo.getStyleClass().add((demo || offline) ? "sys-info-demo" : "sys-info-ok");
            if (dotSistemaModo != null) {
                dotSistemaModo.getStyleClass().removeAll("dot-amber", "dot-green");
                dotSistemaModo.getStyleClass().add(dotClass);
            }
        }
        if (lblSistemaHora != null)
            lblSistemaHora.setText(com.sibim.util.FormatUtils.formatDateTime(java.time.LocalDateTime.now()));

        boolean isAdmin = SessionManager.isAdmin();
        adminSection.setVisible(isAdmin);
        adminSection.setManaged(isAdmin);
        if (auditSection != null) {
            auditSection.setVisible(isAdmin);
            auditSection.setManaged(isAdmin);
        }
        if (backupSection != null) {
            backupSection.setVisible(isAdmin);
            backupSection.setManaged(isAdmin);
        }

        // Config card — visible to all, editable only by admin
        loadConfigCard(isAdmin);
        if (configCard != null) AnimationUtils.fadeInUp(configCard, 320, 105);

        // Entrance animations — cards cascade in from below
        if (profileCard  != null) AnimationUtils.fadeInUp(profileCard,  320,   0);
        if (sysInfoCard  != null) AnimationUtils.fadeInUp(sysInfoCard,  320,  70);
        if (isAdmin) {
            if (adminSection  != null) AnimationUtils.fadeInUp(adminSection,  320, 175);
            if (auditSection  != null) AnimationUtils.fadeInUp(auditSection,  320, 245);
            if (backupSection != null) AnimationUtils.fadeInUp(backupSection, 320, 315);
        }

        if (isAdmin) {
            setupUsersTable();
            loadUsers();
            if (userSearchField != null) {
                userSearchField.textProperty().addListener((obs, o, n) -> {
                    if (btnClearUserSearch != null) btnClearUserSearch.setVisible(!n.isBlank());
                    applyUserFilter();
                });
                if (btnClearUserSearch != null)
                    btnClearUserSearch.setOnAction(e -> { userSearchField.clear(); userSearchField.requestFocus(); });
            }
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
            usersTable.setOnKeyPressed(ev -> {
                if (ev.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                    usersTable.getSelectionModel().clearSelection(); ev.consume(); return;
                }
                if (ev.getCode() == javafx.scene.input.KeyCode.F && ev.isControlDown()) {
                    if (userSearchField != null) { userSearchField.requestFocus(); userSearchField.selectAll(); }
                    ev.consume(); return;
                }
                if (usersTable.getSelectionModel().getSelectedItem() == null) return;
                if (ev.getCode() == javafx.scene.input.KeyCode.DELETE) {
                    onDeleteUsuario(); ev.consume();
                } else if (ev.getCode() == javafx.scene.input.KeyCode.E && ev.isControlDown()) {
                    onEditUsuario(); ev.consume();
                }
            });

            // Context menu
            ContextMenu cm = new ContextMenu();
            MenuItem cmEditar     = new MenuItem("Editar");
            cmEditar.setGraphic(new FontIcon("mdi2p-pencil"));
            MenuItem cmPassword   = new MenuItem("Cambiar contraseña");
            cmPassword.setGraphic(new FontIcon("mdi2k-key-outline"));
            MenuItem cmReactivar  = new MenuItem("Reactivar cuenta");
            cmReactivar.setGraphic(new FontIcon("mdi2a-account-check-outline"));
            MenuItem cmEliminar   = new MenuItem("Eliminar / Desactivar");
            cmEliminar.setGraphic(new FontIcon("mdi2d-delete-outline"));
            cmEditar.setOnAction(e -> onEditUsuario());
            cmPassword.setOnAction(e -> onCambiarPassword());
            cmReactivar.setOnAction(e -> onReactivarUsuario());
            cmEliminar.setOnAction(e -> onDeleteUsuario());
            cm.setOnShowing(e -> {
                Usuario sel = usersTable.getSelectionModel().getSelectedItem();
                cmReactivar.setVisible(sel != null && !sel.isActivo());
            });
            cm.getItems().addAll(cmEditar, cmPassword, cmReactivar, new SeparatorMenuItem(), cmEliminar);
            usersTable.setContextMenu(cm);
        }
    }

    private void loadConfigCard(boolean isAdmin) {
        DialogUtil.runAsync(
            () -> configRepo.findAll(),
            cfg -> {
                String nombre = cfg.getOrDefault("nombre_ayuntamiento", "H. Ayuntamiento");
                String municipio = cfg.getOrDefault("municipio", "");
                if (tfNombreAyuntamiento != null) tfNombreAyuntamiento.setText(nombre);
                if (tfMunicipio         != null) tfMunicipio.setText(municipio);
                if (tfResponsable       != null) tfResponsable.setText(cfg.getOrDefault("responsable", ""));
                if (tfCorreoContacto    != null) tfCorreoContacto.setText(cfg.getOrDefault("correo_contacto", ""));
                // update decorative badge in profile card
                if (lblDecoNombre != null) {
                    String[] parts = nombre.split("\\s+de\\s+", 2);
                    lblDecoNombre.setText(parts.length > 1 ? parts[0] + " de" : nombre);
                    if (lblDecoSub != null) lblDecoSub.setText(parts.length > 1 ? parts[1] : municipio);
                }
                if (btnGuardarConfig != null) btnGuardarConfig.setDisable(!isAdmin);
                if (tfNombreAyuntamiento != null) tfNombreAyuntamiento.setEditable(isAdmin);
                if (tfMunicipio         != null) tfMunicipio.setEditable(isAdmin);
                if (tfResponsable       != null) tfResponsable.setEditable(isAdmin);
                if (tfCorreoContacto    != null) tfCorreoContacto.setEditable(isAdmin);
                if (lblConfigHint != null)
                    lblConfigHint.setText(isAdmin ? "Los cambios afectan reportes y documentos" : "Solo el administrador puede guardar cambios");
            },
            e -> {}
        );
    }

    @FXML
    private void onGuardarConfig() {
        String nombre    = tfNombreAyuntamiento != null ? tfNombreAyuntamiento.getText().strip() : "";
        String municipio = tfMunicipio != null ? tfMunicipio.getText().strip() : "";
        String resp      = tfResponsable != null ? tfResponsable.getText().strip() : "";
        String correo    = tfCorreoContacto != null ? tfCorreoContacto.getText().strip() : "";
        DialogUtil.runAsync(
            () -> {
                configRepo.set("nombre_ayuntamiento", nombre);
                configRepo.set("municipio",           municipio);
                configRepo.set("responsable",         resp);
                configRepo.set("correo_contacto",     correo);
                return null;
            },
            v -> {
                NotificacionUtil.exito(configCard != null ? configCard.getScene() : null, "Configuración guardada");
                // refresh decorative badge
                if (lblDecoNombre != null) {
                    String[] parts = nombre.split("\\s+de\\s+", 2);
                    lblDecoNombre.setText(parts.length > 1 ? parts[0] + " de" : nombre);
                    if (lblDecoSub != null) lblDecoSub.setText(parts.length > 1 ? parts[1] : municipio);
                }
                if (configCard != null) AnimationUtils.statCardPop(configCard);
            },
            e -> NotificacionUtil.error(configCard != null ? configCard.getScene() : null, "No se pudo guardar la configuración")
        );
    }

    private void setupUsersTable() {
        usersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        FontIcon emptyIco = new FontIcon("mdi2a-account-multiple-outline");
        emptyIco.setIconSize(44);
        emptyIco.getStyleClass().add("empty-icon-lg");
        javafx.scene.control.Label emptyMsg = new javafx.scene.control.Label("Sin usuarios registrados");
        emptyMsg.getStyleClass().add("empty-state-msg");
        javafx.scene.control.Label emptyHint = new javafx.scene.control.Label("Usa el botón \"Nuevo usuario\" para crear el primero");
        emptyHint.getStyleClass().add("empty-state-hint");
        javafx.scene.layout.VBox emptyBox = new javafx.scene.layout.VBox(10, emptyIco, emptyMsg, emptyHint);
        emptyBox.setAlignment(javafx.geometry.Pos.CENTER);
        emptyBox.getStyleClass().add("empty-state-pane");
        usersTable.setPlaceholder(emptyBox);
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colUsername.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUsername()));
        colCargo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCargo()));
        colRol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRol().getEtiqueta()));
        colRol.setCellFactory(col -> new TableCell<>() {
            private final Label   badge  = new Label();
            private final ComboBox<Rol> combo  = new ComboBox<>();
            private final StackPane pane  = new StackPane(badge);
            { combo.getItems().addAll(Rol.values());
              combo.setConverter(new javafx.util.StringConverter<>() {
                  public String toString(Rol r) { return r == null ? "" : r.getEtiqueta(); }
                  public Rol fromString(String s) { return null; }
              });
              combo.setOnAction(e -> commitRolEdit(combo.getValue()));
              combo.focusedProperty().addListener((ob, o, n) -> { if (!n) cancelRolEdit(); });
              setOnMouseClicked(ev -> { if (ev.getButton() == MouseButton.PRIMARY && ev.getClickCount() == 2
                  && !isEmpty() && canEditRol()) startRolEdit(); }); }

            private boolean canEditRol() {
                Usuario u = getTableView().getItems().get(getIndex());
                Usuario me = SessionManager.getCurrentUser();
                return SessionManager.isAdmin() && (me == null || !me.getId().equals(u.getId()));
            }
            private void startRolEdit() {
                Usuario u = getTableView().getItems().get(getIndex());
                combo.setValue(u.getRol());
                pane.getChildren().setAll(combo);
                combo.requestFocus();
                combo.show();
            }
            private void cancelRolEdit() {
                pane.getChildren().setAll(badge);
                updateBadge(getItem());
            }
            private void commitRolEdit(Rol newRol) {
                if (newRol == null) { cancelRolEdit(); return; }
                Usuario u = getTableView().getItems().get(getIndex());
                if (u.getRol() == newRol) { cancelRolEdit(); return; }
                u.setRol(newRol);
                DialogUtil.runAsync(() -> usuarioRepo.save(u),
                    () -> {
                        loadUsers();
                        NotificacionUtil.exito(getTableView().getScene(),
                            "Rol de \"" + u.getNombre() + "\" actualizado a " + newRol.getEtiqueta());
                    },
                    ex -> {
                        u.setRol(u.getRol()); // revert (already set above — moot, reload fixes it)
                        NotificacionUtil.error(getTableView().getScene(), "No se pudo actualizar el rol");
                        loadUsers();
                    });
                pane.getChildren().setAll(badge);
            }
            private void updateBadge(String item) {
                if (item == null) { setText(null); setGraphic(null); return; }
                badge.setText(item);
                badge.getStyleClass().removeAll("cell-badge-purple", "cell-badge-blue", "cell-badge-teal");
                badge.getStyleClass().addAll("cell-badge", switch (item) {
                    case "Administrador" -> "cell-badge-purple";
                    case "Secretario"    -> "cell-badge-blue";
                    default              -> "cell-badge-teal";
                });
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                pane.getChildren().setAll(badge);
                if (empty || item == null) { setText(null); setGraphic(null); return; }
                updateBadge(item);
                setText(null); setGraphic(pane);
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

        if (colActivo != null) {
            colActivo.setCellValueFactory(c ->
                new javafx.beans.property.SimpleBooleanProperty(c.getValue().isActivo()).asObject());
            colActivo.setCellFactory(col -> new TableCell<>() {
                private final Label badge = new Label();
                @Override protected void updateItem(Boolean value, boolean empty) {
                    super.updateItem(value, empty);
                    setText(null);
                    if (empty || value == null) { setGraphic(null); return; }
                    badge.setText(value ? "Activo" : "Inactivo");
                    badge.getStyleClass().removeAll("cell-badge-success", "cell-badge-danger");
                    badge.getStyleClass().addAll("cell-badge", value ? "cell-badge-success" : "cell-badge-danger");
                    setGraphic(badge);
                }
            });
        }

        usersTable.setRowFactory(tv -> new javafx.scene.control.TableRow<>() {
            @Override protected void updateItem(Usuario u, boolean empty) {
                super.updateItem(u, empty);
                getStyleClass().remove("row-inactive");
                if (!empty && u != null && !u.isActivo()) getStyleClass().add("row-inactive");
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
        if (profileCard != null) AnimationUtils.statCardPop(profileCard);
    }

    private void loadUsers() {
        DialogUtil.runAsync(
            () -> usuarioRepo.findAll(),
            users -> { allUsers = new java.util.ArrayList<>(users); applyUserFilter(); },
            e -> NotificacionUtil.error(usersTable.getScene(), "No se pudo cargar la lista de usuarios")
        );
    }

    private void applyUserFilter() {
        String q = userSearchField != null ? userSearchField.getText().toLowerCase().trim() : "";
        if (q.isBlank()) {
            usersTable.getItems().setAll(allUsers);
        } else {
            usersTable.getItems().setAll(allUsers.stream()
                .filter(u -> (u.getNombre()   != null && u.getNombre().toLowerCase().contains(q))
                          || (u.getUsername() != null && u.getUsername().toLowerCase().contains(q))
                          || (u.getCargo()    != null && u.getCargo().toLowerCase().contains(q))
                          || (u.getArea()     != null && u.getArea().toLowerCase().contains(q)))
                .toList());
        }
        AnimationUtils.staggerTableRows(usersTable);
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

    public static boolean canDeleteUser(Usuario selectedUser, Usuario currentUser) {
        if (selectedUser == null || currentUser == null) return false;
        String selectedId = selectedUser.getId();
        String currentId = currentUser.getId();
        if (selectedId == null || currentId == null) return false;
        return !selectedId.equals(currentId);
    }

    public static boolean canUseDatabaseBackup(boolean offline, boolean demo) {
        return !offline && !demo;
    }

    @FXML
    private void onDeleteUsuario() {
        Usuario sel = usersTable.getSelectionModel().getSelectedItem();
        Usuario me = SessionManager.getCurrentUser();
        if (sel == null) return;
        if (!canDeleteUser(sel, me)) {
            NotificacionUtil.error(usersTable.getScene(),
                me == null ? "No hay una sesión activa para continuar" : "No puedes eliminar tu propia cuenta");
            return;
        }
        if (sel.getRol() == com.sibim.model.enums.Rol.ADMIN) {
            long admins = allUsers.stream()
                .filter(u -> u.getRol() == com.sibim.model.enums.Rol.ADMIN && u.isActivo()).count();
            if (admins <= 1) {
                NotificacionUtil.error(usersTable.getScene(),
                    "No se puede eliminar el único administrador del sistema");
                return;
            }
        }
        if (!ConfirmacionUtil.confirmarEliminar(sel.getNombre())) return;
        DialogUtil.runAsync(
            () -> { usuarioRepo.delete(sel.getId()); return null; },
            v -> { loadUsers(); NotificacionUtil.exito(usersTable.getScene(), "Usuario \"" + sel.getNombre() + "\" eliminado"); },
            e -> {
                if (e instanceof IllegalStateException) {
                    // FK violation — user has associated records; offer soft-delete instead
                    if (ConfirmacionUtil.confirmar("No se puede eliminar",
                            sel.getNombre() + " tiene movimientos u operaciones registradas a su nombre.\n"
                            + "¿Desactivar la cuenta para que no pueda iniciar sesión?")) {
                        DialogUtil.runAsync(
                            () -> { usuarioRepo.setActivo(sel.getId(), false); return null; },
                            v2 -> { loadUsers(); NotificacionUtil.info(usersTable.getScene(), "Cuenta de \"" + sel.getNombre() + "\" desactivada"); },
                            ex -> NotificacionUtil.error(usersTable.getScene(), "No se pudo desactivar el usuario")
                        );
                    }
                } else {
                    NotificacionUtil.error(usersTable.getScene(), "No se pudo eliminar el usuario");
                }
            }
        );
    }

    @FXML
    private void onReactivarUsuario() {
        Usuario sel = usersTable.getSelectionModel().getSelectedItem();
        if (sel == null || sel.isActivo()) return;
        DialogUtil.runAsync(
            () -> { usuarioRepo.setActivo(sel.getId(), true); return null; },
            v -> { loadUsers(); NotificacionUtil.exito(usersTable.getScene(), "Cuenta de \"" + sel.getNombre() + "\" reactivada"); },
            e -> NotificacionUtil.error(usersTable.getScene(), "No se pudo reactivar el usuario")
        );
    }

    private void showUserDialog(Usuario existing, boolean passwordOnly) {
        UsuarioDialogFactory.show(existing, passwordOnly, usersTable.getScene(), usuarioRepo, u -> {
            loadUsers();
            if (u != null && !passwordOnly
                    && u.getId().equals(SessionManager.getCurrentUser().getId())) {
                refreshProfileCard(u);
            }
        });
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
        DialogUtil.applyOwner(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(600);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2h-history", "Historial de Auditoría",
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
        scroll.getStyleClass().add("dlg-tabs-scroll");

        AnimationUtils.staggeredFadeInUp(java.util.List.of(header, scroll), 260, 70);
        dialog.getDialogPane().setContent(new VBox(0, header, scroll));
        dialog.showAndWait();
    }

    private void showConteoDetalleDialog(com.sibim.model.ConteoFisico conteo, List<com.sibim.model.ConteoItem> items) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(680);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2m-magnify", "Detalle del Conteo",
            com.sibim.util.FormatUtils.formatDateTime(conteo.getCreadoEn()) + " · " + conteo.getUsuarioNombre(),
            "#0891B2", "#0E7490");

        // Column headers — widths must match the data row cells built below
        // (Nombre grows, the rest are fixed) exactly, or the header labels
        // drift out of alignment with their own column. colWidths used to be
        // computed as percentages and then never actually applied (every
        // header got prefWidth(0) + Hgrow.ALWAYS instead, so all six ended
        // up equal-width regardless of these numbers).
        HBox colHeaders = new HBox();
        colHeaders.setPadding(new javafx.geometry.Insets(6, 14, 4, 14));
        colHeaders.setSpacing(0);
        String[] colTitles = { "Bien", "Área", "Sistema", "Contado", "Delta", "Ajustado" };
        double[] colWidths  = { -1, 120, 60, 60, 60, 80 };
        for (int i = 0; i < colTitles.length; i++) {
            Label lbl = new Label(colTitles[i]);
            lbl.getStyleClass().add("col-header");
            if (colWidths[i] < 0) {
                HBox.setHgrow(lbl, javafx.scene.layout.Priority.ALWAYS);
                lbl.setMaxWidth(Double.MAX_VALUE);
            } else {
                lbl.setPrefWidth(colWidths[i]);
            }
            colHeaders.getChildren().add(lbl);
        }

        VBox rows = new VBox(4);
        rows.setPadding(new javafx.geometry.Insets(4));
        if (items.isEmpty()) {
            Label empty = new Label("Sin ítems registrados en este conteo");
            empty.getStyleClass().add("muted");
            rows.getChildren().add(empty);
        }
        for (com.sibim.model.ConteoItem item : items) {
            int delta = item.getStockContado() - item.getStockSistema();
            HBox row = new HBox();
            row.getStyleClass().add("dlg-detail-header");
            row.setPadding(new javafx.geometry.Insets(8, 14, 8, 14));
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            Label lNombre = new Label(item.getProductoNombre());
            lNombre.setWrapText(false);
            lNombre.getStyleClass().add("dlg-detail-value");
            HBox.setHgrow(lNombre, javafx.scene.layout.Priority.ALWAYS);
            lNombre.setMaxWidth(Double.MAX_VALUE);

            Label lArea = new Label(item.getArea() != null ? item.getArea() : "—");
            lArea.getStyleClass().add("muted-sm");
            lArea.setPrefWidth(120);

            Label lSistema = new Label(String.valueOf(item.getStockSistema()));
            lSistema.getStyleClass().add("muted");
            lSistema.setPrefWidth(60);

            Label lContado = new Label(String.valueOf(item.getStockContado()));
            lContado.getStyleClass().add("muted");
            lContado.setPrefWidth(60);

            Label lDelta = new Label((delta > 0 ? "+" : "") + delta);
            lDelta.getStyleClass().add(delta == 0 ? "muted-sm" : (delta > 0 ? "field-hint-ok" : "field-hint-error"));
            lDelta.setPrefWidth(60);

            Label lAjustado = new Label(item.isAjustado() ? "✓ Sí" : "No");
            lAjustado.getStyleClass().add(item.isAjustado() ? "field-hint-ok" : "muted-sm");
            lAjustado.setPrefWidth(80);

            row.getChildren().addAll(lNombre, lArea, lSistema, lContado, lDelta, lAjustado);
            if (delta != 0) row.getStyleClass().add("row-highlight-amber");
            rows.getChildren().add(row);
        }

        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(380);
        scroll.getStyleClass().add("dlg-tabs-scroll");

        AnimationUtils.staggeredFadeInUp(java.util.List.of(header, colHeaders, scroll), 260, 60);
        dialog.getDialogPane().setContent(new VBox(0, header, colHeaders, scroll));
        dialog.showAndWait();
    }

    private String accionEtiqueta(String accion) {
        if (accion == null) return "—";
        return switch (accion) {
            case "crear"     -> "Creado";
            case "actualizar"-> "Actualizado";
            case "eliminar"  -> "Eliminado";
            case "baja"      -> "Dado de baja";
            case "reactivar" -> "Reactivado";
            case "login"     -> "Inicio de sesión";
            case "logout"    -> "Cierre de sesión";
            default          -> accion;
        };
    }

    private String entidadEtiqueta(String entidad) {
        if (entidad == null) return "—";
        return switch (entidad) {
            case "producto"  -> "Bien";
            case "categoria" -> "Categoría";
            case "usuario"   -> "Usuario";
            case "sesion"    -> "Sesión";
            default          -> entidad;
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
        DialogUtil.applyOwner(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(600);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2c-clipboard-list-outline", "Historial de Conteos Físicos",
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

            Button btnDetalle = new Button("Ver detalle");
            btnDetalle.getStyleClass().add("btn-secondary");
            btnDetalle.setOnAction(e -> DialogUtil.runAsync(
                () -> conteoRepo.findItems(c.getId()),
                items -> showConteoDetalleDialog(c, items),
                ex -> NotificacionUtil.error(usersTable.getScene(), "No se pudo cargar el detalle")
            ));

            row.getChildren().addAll(info, btnDetalle);
            list.getChildren().add(row);
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(400);
        scroll.getStyleClass().add("dlg-tabs-scroll");

        AnimationUtils.staggeredFadeInUp(java.util.List.of(header, scroll), 260, 70);
        dialog.getDialogPane().setContent(new VBox(0, header, scroll));
        dialog.showAndWait();
    }

    // ── Respaldo y restauración ──────────────────────────────────────────

    @FXML
    private void onGenerarRespaldo() {
        if (!canUseDatabaseBackup(com.sibim.db.DatabaseConfig.isOfflineMode(), com.sibim.db.DatabaseConfig.isDemoMode())) {
            NotificacionUtil.advertencia(backupSection != null ? backupSection.getScene() : null,
                "La copia de seguridad solo está disponible con la base de datos principal conectada.");
            return;
        }

        if (!ConfirmacionUtil.confirmar("Información sensible en el respaldo",
                "El archivo generado contendrá todos los datos del sistema: bienes, movimientos, "
                + "usuarios y auditoría.\n\nGuárdalo en un lugar seguro y no lo compartas. ¿Continuar?"))
            return;

        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Guardar respaldo de la base de datos");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("JSON", "*.json"));
        chooser.setInitialFileName("sibim_backup_"
            + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE) + ".json");
        java.io.File destino = chooser.showSaveDialog(com.sibim.MainApp.getPrimaryStage());
        if (destino == null) return;

        DialogUtil.runAsyncWithProgress(backupSection.getScene(), "Generando respaldo…",
            () -> { backupService.backup(destino); return destino; },
            file -> NotificacionUtil.exito(backupSection.getScene(), "Respaldo generado: " + file.getName()),
            e -> NotificacionUtil.error(backupSection.getScene(),
                e instanceof java.sql.SQLException ? e.getMessage() : "No se pudo generar el respaldo")
        );
    }

    @FXML
    private void onRestaurar() {
        if (!canUseDatabaseBackup(com.sibim.db.DatabaseConfig.isOfflineMode(), com.sibim.db.DatabaseConfig.isDemoMode())) {
            NotificacionUtil.advertencia(backupSection != null ? backupSection.getScene() : null,
                "La restauración solo está disponible con la base de datos principal conectada.");
            return;
        }

        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Restaurar desde un archivo de respaldo");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("JSON", "*.json"));
        java.io.File origen = chooser.showOpenDialog(com.sibim.MainApp.getPrimaryStage());
        if (origen == null) return;

        if (!ConfirmacionUtil.confirmar("Restaurar base de datos",
                "Esto reemplaza TODOS los datos actuales (bienes, movimientos, usuarios, categorías, "
                + "auditoría y conteos) con el contenido de \"" + origen.getName() + "\". "
                + "Esta acción no se puede deshacer. ¿Continuar?"))
            return;

        DialogUtil.runAsyncWithProgress(backupSection.getScene(), "Restaurando base de datos…",
            () -> { backupService.restore(origen); return null; },
            v -> {
                NotificacionUtil.restauracionCompletada(backupSection.getScene(), origen.getName());
                // Redirect to login so all in-memory caches reload with restored data
                javafx.animation.PauseTransition delay =
                    new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
                delay.setOnFinished(e -> {
                    com.sibim.session.SessionManager.logout();
                    try { com.sibim.MainApp.showLogin(); }
                    catch (Exception ex) { /* already showing the toast — user will restart manually */ }
                });
                delay.play();
            },
            e -> NotificacionUtil.error(backupSection.getScene(),
                e instanceof java.sql.SQLException ? e.getMessage() : "No se pudo restaurar el respaldo")
        );
    }
}
