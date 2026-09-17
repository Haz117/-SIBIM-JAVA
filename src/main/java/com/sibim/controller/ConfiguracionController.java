package com.sibim.controller;

import com.sibim.controller.dialogs.UsuarioDialogFactory;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.repository.UsuarioRepository;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppExecutor;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Priority;
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
    @FXML private TextField tfLogoPath;
    @FXML private Button    btnLogoPath;
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
    @FXML private CheckBox chkAnimaciones;
    @FXML private Button btnGenerarRespaldo;
    @FXML private Button btnRestaurar;
    @FXML private Button btnEditUser;
    @FXML private Button btnPasswordUser;
    @FXML private Button btnDeleteUser;
    @FXML private TextField userSearchField;
    @FXML private Button btnClearUserSearch;
    @FXML private ProgressIndicator usersSpinner;

    private boolean configDirty = false;

    private final UsuarioRepository usuarioRepo = new UsuarioRepository();
    private List<Usuario> allUsers = new java.util.ArrayList<>();
    private final com.sibim.repository.AuditLogRepository auditRepo = new com.sibim.repository.AuditLogRepository();
    private final com.sibim.repository.ConteoRepository conteoRepo = new com.sibim.repository.ConteoRepository();
    private final com.sibim.service.BackupService backupService = new com.sibim.service.BackupService();
    private final com.sibim.repository.ConfiguracionRepository configRepo = new com.sibim.repository.ConfiguracionRepository();
    private ConfiguracionBackupManager backupManager;

    @FXML
    public void initialize() {
        for (Label badge : new Label[]{ helpUsuarios, helpAuditoria, helpRespaldo }) {
            if (badge != null) DialogUtil.enableClickToShowTooltip(badge);
        }

        if (chkAnimaciones != null) chkAnimaciones.setSelected(AnimationUtils.isEnabled());

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
        if (configCard != null) {
            AnimationUtils.fadeInUp(configCard, 320, 105);
            configCard.sceneProperty().addListener((obs, old, scene) -> {
                if (scene == null) return;
                scene.getAccelerators().put(
                    new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.S,
                        javafx.scene.input.KeyCombination.CONTROL_DOWN),
                    () -> { if (btnGuardarConfig != null && !btnGuardarConfig.isDisable()) onGuardarConfig(); }
                );
            });
        }

        // Entrance animations — cards cascade in from below
        if (profileCard  != null) AnimationUtils.fadeInUp(profileCard,  320,   0);
        if (sysInfoCard  != null) AnimationUtils.fadeInUp(sysInfoCard,  320,  70);
        if (isAdmin) {
            if (adminSection  != null) AnimationUtils.fadeInUp(adminSection,  320, 175);
            if (auditSection  != null) AnimationUtils.fadeInUp(auditSection,  320, 245);
            if (backupSection != null) AnimationUtils.fadeInUp(backupSection, 320, 315);
        }

        if (isAdmin) {
            AppExecutor.submit(() -> {
                java.util.Map<String, String> cfg;
                try { cfg = configRepo.findAll(); }
                catch (Exception e) { cfg = java.util.Map.of(); }
                final var cfgFinal = cfg;
                javafx.application.Platform.runLater(() -> {
                    new ConfigEmailSectionBuilder(backupSection, configRepo).build(cfgFinal);
                    new ConfigSchedulerSectionBuilder(backupSection, configRepo).build(cfgFinal);
                });
            });
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
        backupManager = new ConfiguracionBackupManager(backupService, backupSection);
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
                if (tfLogoPath          != null) tfLogoPath.setText(cfg.getOrDefault("logo_path", ""));
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
                if (tfLogoPath          != null) tfLogoPath.setEditable(isAdmin);
                if (btnLogoPath         != null) btnLogoPath.setDisable(!isAdmin);
                if (lblConfigHint != null)
                    lblConfigHint.setText(isAdmin ? "Los cambios afectan reportes y documentos" : "Solo el administrador puede guardar cambios");
                // Dirty tracking — deferred so setText() above doesn't trigger it
                if (isAdmin) javafx.application.Platform.runLater(() -> {
                    configDirty = false;
                    for (javafx.scene.control.TextField tf : new javafx.scene.control.TextField[]{
                            tfNombreAyuntamiento, tfMunicipio, tfResponsable, tfCorreoContacto, tfLogoPath}) {
                        if (tf != null) tf.textProperty().addListener((o, a, b) -> markConfigDirty());
                    }
                });
            },
            e -> {}
        );
    }

    private void markConfigDirty() {
        if (configDirty) return;
        configDirty = true;
        if (lblConfigHint != null) lblConfigHint.setText("• Cambios sin guardar  (Ctrl+S)");
    }

    private void clearConfigDirty() {
        configDirty = false;
        if (lblConfigHint != null)
            lblConfigHint.setText(SessionManager.isAdmin()
                ? "Los cambios afectan reportes y documentos"
                : "Solo el administrador puede guardar cambios");
    }

    @FXML
    private void onGuardarConfig() {
        String nombre    = tfNombreAyuntamiento != null ? tfNombreAyuntamiento.getText().strip() : "";
        String municipio = tfMunicipio != null ? tfMunicipio.getText().strip() : "";
        String resp      = tfResponsable != null ? tfResponsable.getText().strip() : "";
        String correo    = tfCorreoContacto != null ? tfCorreoContacto.getText().strip() : "";
        String logoPath  = tfLogoPath != null ? tfLogoPath.getText().strip() : "";
        DialogUtil.runAsync(
            () -> {
                configRepo.set("nombre_ayuntamiento", nombre);
                configRepo.set("municipio",           municipio);
                configRepo.set("responsable",         resp);
                configRepo.set("correo_contacto",     correo);
                configRepo.set("logo_path",           logoPath);
                new com.sibim.repository.AuditLogRepository().log("configuracion", "general", "Datos generales",
                    "actualizar", "Datos generales del ayuntamiento actualizados");
                return null;
            },
            v -> {
                clearConfigDirty();
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

    @FXML
    private void onSeleccionarLogo() {
        if (!SessionManager.isAdmin()) return;
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Seleccionar logo del ayuntamiento");
        chooser.getExtensionFilters().add(
            new javafx.stage.FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.gif"));
        java.io.File file = chooser.showOpenDialog(com.sibim.MainApp.getPrimaryStage());
        if (file != null && tfLogoPath != null) {
            tfLogoPath.setText(file.getAbsolutePath());
        }
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
                if (canEditRol()) badge.setTooltip(new Tooltip("Doble clic para cambiar el rol"));
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
        if (usersSpinner != null) { usersSpinner.setVisible(true); usersSpinner.setManaged(true); }
        DialogUtil.runAsync(
            () -> usuarioRepo.findAll(),
            users -> {
                if (usersSpinner != null) { usersSpinner.setVisible(false); usersSpinner.setManaged(false); }
                allUsers = new java.util.ArrayList<>(users);
                applyUserFilter();
            },
            e -> {
                if (usersSpinner != null) { usersSpinner.setVisible(false); usersSpinner.setManaged(false); }
                NotificacionUtil.error(usersTable.getScene(), "No se pudo cargar la lista de usuarios");
            }
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
            entries -> new AuditoriaDialog().show(entries),
            e -> NotificacionUtil.error(usersTable.getScene(), "No se pudo cargar el historial de auditoría")
        );
    }

    @FXML
    private void onVerConteos() {
        DialogUtil.runAsync(
            () -> conteoRepo.findAll(100),
            conteos -> new ConteosDialog(conteoRepo).show(conteos),
            e -> NotificacionUtil.error(usersTable.getScene(), "No se pudo cargar el historial de conteos")
        );
    }

    @FXML
    private void onToggleAnimaciones() {
        AnimationUtils.setEnabled(chkAnimaciones.isSelected());
    }

    // ── Respaldo y restauración ──────────────────────────────────────────

    @FXML
    private void onGenerarRespaldo() { backupManager.onGenerarRespaldo(); }

    @FXML
    private void onRestaurar() { backupManager.onRestaurar(); }
}
