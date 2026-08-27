package com.sibim.controller;

import com.sibim.controller.dialogs.UsuarioDialogFactory;
import com.sibim.model.Usuario;
import com.sibim.repository.UsuarioRepository;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.NotificacionUtil;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import com.sibim.util.DialogUtil;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

public class ConfiguracionController {

    @FXML private VBox  profileCard;
    @FXML private VBox  sysInfoCard;
    @FXML private Label lblNombreUsuario;
    @FXML private Label lblUsernameUsuario;
    @FXML private Label lblRolUsuario;
    @FXML private Label lblAreaUsuario;
    @FXML private Label lblAvatarPerfil;
    @FXML private Label dotSistemaModo;
    @FXML private Label lblSistemaModo;
    @FXML private Label helpUsuarios;
    @FXML private Label helpAuditoria;
    @FXML private Label helpRespaldo;
    @FXML private Label lblSistemaHora;

    @FXML private TableView<Usuario> usersTable;
    @FXML private TableColumn<Usuario, String> colNombre;
    @FXML private TableColumn<Usuario, String> colUsername;
    @FXML private TableColumn<Usuario, String> colCargo;
    @FXML private TableColumn<Usuario, String> colRol;
    @FXML private TableColumn<Usuario, String> colArea;
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
            lblSistemaHora.setText(java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

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

        // Entrance animations — cards cascade in from below
        if (profileCard  != null) AnimationUtils.fadeInUp(profileCard,  320,   0);
        if (sysInfoCard  != null) AnimationUtils.fadeInUp(sysInfoCard,  320,  70);
        if (isAdmin) {
            if (adminSection  != null) AnimationUtils.fadeInUp(adminSection,  320, 140);
            if (auditSection  != null) AnimationUtils.fadeInUp(auditSection,  320, 210);
            if (backupSection != null) AnimationUtils.fadeInUp(backupSection, 320, 280);
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
                if (usersTable.getSelectionModel().getSelectedItem() == null) return;
                if (ev.getCode() == javafx.scene.input.KeyCode.DELETE) {
                    onDeleteUsuario(); ev.consume();
                } else if (ev.getCode() == javafx.scene.input.KeyCode.E && ev.isControlDown()) {
                    onEditUsuario(); ev.consume();
                }
            });

            // Context menu
            ContextMenu cm = new ContextMenu();
            MenuItem cmEditar    = new MenuItem("Editar");
            cmEditar.setGraphic(new FontIcon("mdi2p-pencil"));
            MenuItem cmPassword  = new MenuItem("Cambiar contraseña");
            cmPassword.setGraphic(new FontIcon("mdi2k-key-outline"));
            MenuItem cmEliminar  = new MenuItem("Eliminar");
            cmEliminar.setGraphic(new FontIcon("mdi2d-delete-outline"));
            cmEditar.setOnAction(e -> onEditUsuario());
            cmPassword.setOnAction(e -> onCambiarPassword());
            cmEliminar.setOnAction(e -> onDeleteUsuario());
            cm.getItems().addAll(cmEditar, cmPassword, new SeparatorMenuItem(), cmEliminar);
            usersTable.setContextMenu(cm);
        }
    }

    private void setupUsersTable() {
        usersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colUsername.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUsername()));
        colCargo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCargo()));
        colRol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRol().getEtiqueta()));
        colRol.setCellFactory(com.sibim.util.DialogUtil.badgeCellFactory(item -> switch (item) {
            case "Administrador" -> "cell-badge-purple";
            case "Secretario"    -> "cell-badge-blue";
            default              -> "cell-badge-teal";
        }));
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
            v -> NotificacionUtil.restauracionCompletada(backupSection.getScene(), origen.getName()),
            e -> NotificacionUtil.error(backupSection.getScene(),
                e instanceof java.sql.SQLException ? e.getMessage() : "No se pudo restaurar el respaldo")
        );
    }
}
