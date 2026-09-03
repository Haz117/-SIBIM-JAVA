package com.sibim.controller;

import com.sibim.model.AuditLog;
import com.sibim.repository.AuditLogRepository;
import com.sibim.service.ReporteService;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppExecutor;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class AuditoriaController {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaController.class);

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final DateTimeFormatter FECHA_FMT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @FXML private HBox filterBar;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> entidadFilter;
    @FXML private TextField usuarioFilter;
    @FXML private DatePicker desdeField;
    @FXML private DatePicker hastaField;
    @FXML private Label lblTotal;
    @FXML private TableView<AuditLog> table;
    @FXML private TableColumn<AuditLog, String> colFecha;
    @FXML private TableColumn<AuditLog, String> colEntidad;
    @FXML private TableColumn<AuditLog, String> colNombre;
    @FXML private TableColumn<AuditLog, String> colAccion;
    @FXML private TableColumn<AuditLog, String> colUsuario;
    @FXML private TableColumn<AuditLog, String> colDetalle;
    @FXML private Label lblPagina;
    @FXML private Button btnPrimera;
    @FXML private Button btnAnterior;
    @FXML private Button btnSiguiente;
    @FXML private Button btnUltima;
    @FXML private Button btnRefresh;
    @FXML private ComboBox<Integer> pageSizeBox;
    @FXML private HBox paginationBar;

    private static final java.util.prefs.Preferences STICKY =
        java.util.prefs.Preferences.userRoot().node("sibim/filters/auditoria");

    private final AuditLogRepository auditRepo    = new AuditLogRepository();
    private final ReporteService      reporteService = new ReporteService();

    private int currentPage = 0;
    private int pageSize    = DEFAULT_PAGE_SIZE;
    private int totalCount  = 0;

    private PauseTransition searchDebounce;

    @FXML
    public void initialize() {
        setupColumns();
        setupEntidadFilter();
        setupPageSizeBox();

        searchDebounce = new PauseTransition(Duration.millis(280));
        searchDebounce.setOnFinished(e -> {
            currentPage = 0;
            loadData();
        });

        // Restore sticky filters from previous navigation
        if (searchField   != null) searchField.setText(STICKY.get("search", ""));
        if (usuarioFilter != null) usuarioFilter.setText(STICKY.get("usuario", ""));
        String stickyEntidad = STICKY.get("entidad", "Todas");
        if (entidadFilter != null && entidadFilter.getItems().contains(stickyEntidad))
            entidadFilter.getSelectionModel().select(stickyEntidad);
        String desdeStr = STICKY.get("desde", "");
        String hastaStr = STICKY.get("hasta", "");
        if (!desdeStr.isBlank() && desdeField != null)
            try { desdeField.setValue(java.time.LocalDate.parse(desdeStr)); } catch (Exception ignored) {}
        if (!hastaStr.isBlank() && hastaField != null)
            try { hastaField.setValue(java.time.LocalDate.parse(hastaStr)); } catch (Exception ignored) {}

        AnimationUtils.fadeInDown(filterBar, 220, 0);
        loadData();

        if (searchField != null) {
            com.sibim.util.SearchUtils.setupSearchHistory("sibim/search-history/auditoria", searchField, () -> {
                currentPage = 0;
                loadData();
            });
            javafx.application.Platform.runLater(() -> searchField.requestFocus());
        }

        if (table != null) {
            table.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) {
                    com.sibim.model.AuditLog sel = table.getSelectionModel().getSelectedItem();
                    if (sel != null) showDetalle(sel);
                }
            });
            table.setOnKeyPressed(e -> {
                if (e.getCode() == javafx.scene.input.KeyCode.F5) { loadData(); e.consume(); }
                else if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                    com.sibim.model.AuditLog sel = table.getSelectionModel().getSelectedItem();
                    if (sel != null) { showDetalle(sel); e.consume(); }
                } else if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                    table.getSelectionModel().clearSelection(); e.consume();
                }
            });

            javafx.scene.control.ContextMenu cm = new javafx.scene.control.ContextMenu();
            javafx.scene.control.MenuItem cmDetalle = new javafx.scene.control.MenuItem("Ver detalle completo");
            cmDetalle.setGraphic(new org.kordamp.ikonli.javafx.FontIcon("mdi2e-eye-outline"));
            cmDetalle.setOnAction(e -> {
                com.sibim.model.AuditLog sel = table.getSelectionModel().getSelectedItem();
                if (sel != null) showDetalle(sel);
            });
            javafx.scene.control.MenuItem cmCopiar = new javafx.scene.control.MenuItem("Copiar detalle");
            cmCopiar.setGraphic(new org.kordamp.ikonli.javafx.FontIcon("mdi2c-content-copy"));
            cmCopiar.setOnAction(e -> {
                com.sibim.model.AuditLog sel = table.getSelectionModel().getSelectedItem();
                if (sel == null || sel.getDetalle() == null) return;
                javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
                cc.putString(sel.getDetalle());
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
                NotificacionUtil.exito(table.getScene(), "Detalle copiado al portapapeles");
            });
            cm.getItems().addAll(cmDetalle, new javafx.scene.control.SeparatorMenuItem(), cmCopiar);
            table.setContextMenu(cm);
        }
    }

    private void showDetalle(com.sibim.model.AuditLog entry) {
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dlg = new javafx.scene.control.Dialog<>();
        DialogUtil.applyOwner(dlg);
        dlg.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
        dlg.getDialogPane().setPrefWidth(480);
        DialogUtil.applyStylesheet(dlg.getDialogPane());

        javafx.scene.layout.HBox header = DialogUtil.gradientHeader(
            "mdi2m-magnify-scan", "Detalle del registro",
            entry.getEntidad() != null ? entry.getEntidad().toUpperCase() : "AUDITORÍA",
            "#4338CA", "#3730A3");

        javafx.scene.layout.GridPane g = new javafx.scene.layout.GridPane();
        g.setHgap(16); g.setVgap(8);
        g.setPadding(new javafx.geometry.Insets(16, 22, 16, 22));

        String[][] rows = {
            { "Fecha",    entry.getCreadoEn() != null ? entry.getCreadoEn().format(FECHA_FMT) : "—" },
            { "Entidad",  entry.getEntidad()       != null ? entry.getEntidad()       : "—" },
            { "Elemento", entry.getEntidadNombre() != null ? entry.getEntidadNombre() : "—" },
            { "Acción",   entry.getAccion()        != null ? entry.getAccion()        : "—" },
            { "Usuario",  entry.getUsuarioNombre() != null ? entry.getUsuarioNombre() : "—" },
        };
        for (int i = 0; i < rows.length; i++) {
            javafx.scene.control.Label k = new javafx.scene.control.Label(rows[i][0]);
            k.getStyleClass().add("dlg-detail-label"); k.setMinWidth(90);
            javafx.scene.control.Label v = new javafx.scene.control.Label(rows[i][1]);
            v.getStyleClass().add("dlg-detail-value");
            g.add(k, 0, i); g.add(v, 1, i);
        }
        // Detalle field — may be long, use a TextArea
        if (entry.getDetalle() != null && !entry.getDetalle().isBlank()) {
            javafx.scene.control.Label kDet = new javafx.scene.control.Label("Detalle");
            kDet.getStyleClass().add("dlg-detail-label"); kDet.setMinWidth(90);
            javafx.scene.control.TextArea ta = new javafx.scene.control.TextArea(entry.getDetalle());
            ta.setEditable(false); ta.setWrapText(true); ta.setPrefRowCount(4);
            ta.getStyleClass().add("audit-detail-area");
            g.add(kDet, 0, rows.length); g.add(ta, 1, rows.length);
            javafx.scene.layout.GridPane.setHgrow(ta, javafx.scene.layout.Priority.ALWAYS);
        }

        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(0, header, g);
        dlg.getDialogPane().setContent(content);
        AnimationUtils.staggeredFadeInUp(java.util.List.of(header, g), 260, 70);
        dlg.showAndWait();
    }

    private void setupColumns() {
        colFecha.setCellValueFactory(c -> {
            if (c.getValue().getCreadoEn() == null) return new SimpleStringProperty("—");
            return new SimpleStringProperty(c.getValue().getCreadoEn().format(FECHA_FMT));
        });

        colEntidad.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getEntidad() != null ? c.getValue().getEntidad() : "—"));

        colNombre.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getEntidadNombre() != null ? c.getValue().getEntidadNombre() : "—"));
        colNombre.setCellFactory(col -> new TableCell<>() {
            private final Tooltip tip = new Tooltip();
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setTooltip(null); return; }
                setText(item);
                tip.setText(item);
                setTooltip(tip);
            }
        });

        colAccion.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getAccion() != null ? c.getValue().getAccion() : "—"));
        colAccion.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("audit-pill-green","audit-pill-red","audit-pill-blue",
                                          "audit-pill-orange","audit-pill-slate","audit-pill-purple");
                if (empty || item == null) { setText(null); return; }
                setText(item);
                String cls = switch (item.toLowerCase()) {
                    case "login"              -> "audit-pill-green";
                    case "logout"             -> "audit-pill-slate";
                    case "save", "edicion",
                         "edición", "alta"   -> "audit-pill-blue";
                    case "delete", "baja"     -> "audit-pill-red";
                    case "conteo"             -> "audit-pill-purple";
                    default                   -> "audit-pill-orange";
                };
                getStyleClass().add(cls);
            }
        });

        colUsuario.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getUsuarioNombre() != null ? c.getValue().getUsuarioNombre() : "—"));

        colDetalle.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getDetalle() != null ? c.getValue().getDetalle() : "—"));
        colDetalle.setCellFactory(col -> new TableCell<>() {
            private final Tooltip tip = new Tooltip();
            {
                tip.setWrapText(true);
                tip.setMaxWidth(400);
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setTooltip(null); return; }
                setText(item);
                tip.setText(item);
                setTooltip(tip);
            }
        });
    }

    private void setupEntidadFilter() {
        if (entidadFilter == null) return;
        entidadFilter.getItems().addAll(
            "Todas", "sesion", "movimiento", "producto", "categoria",
            "usuario", "backup", "conteo"
        );
        entidadFilter.getSelectionModel().selectFirst();
    }

    private void setupPageSizeBox() {
        if (pageSizeBox == null) return;
        pageSizeBox.getItems().addAll(25, 50, 100);
        pageSizeBox.getSelectionModel().select(Integer.valueOf(DEFAULT_PAGE_SIZE));
        pageSizeBox.setOnAction(e -> {
            Integer sel = pageSizeBox.getSelectionModel().getSelectedItem();
            if (sel != null) { pageSize = sel; currentPage = 0; loadData(); }
        });
    }

    private void loadData() {
        String busqueda = searchField != null ? searchField.getText() : null;
        String entidad  = getEntidadValue();
        String usuario  = usuarioFilter != null ? usuarioFilter.getText() : null;
        LocalDate desde = desdeField != null ? desdeField.getValue() : null;
        LocalDate hasta = hastaField != null ? hastaField.getValue() : null;
        int offset = currentPage * pageSize;

        // Persist current filters
        STICKY.put("search",  busqueda != null ? busqueda : "");
        STICKY.put("usuario", usuario  != null ? usuario  : "");
        STICKY.put("entidad", entidad  != null ? entidad  : "Todas");
        STICKY.put("desde",   desde    != null ? desde.toString() : "");
        STICKY.put("hasta",   hasta    != null ? hasta.toString() : "");

        AppExecutor.submit(() -> {
            try {
                int total = auditRepo.countFiltrado(busqueda, entidad, usuario, desde, hasta);
                List<AuditLog> rows = auditRepo.findPaginated(pageSize, offset,
                    busqueda, entidad, usuario, desde, hasta);
                Platform.runLater(() -> {
                    totalCount = total;
                    table.getItems().setAll(rows);
                    AnimationUtils.staggerTableRows(table);
                    updatePaginationUI();
                });
            } catch (SecurityException se) {
                Platform.runLater(() -> {
                    table.getItems().clear();
                    if (lblTotal != null) lblTotal.setText("Acceso denegado — se requiere rol Administrador");
                    updatePaginationDisabled();
                });
            } catch (Exception ex) {
                log.error("Error al cargar registros de auditoría", ex);
                Platform.runLater(() -> {
                    javafx.scene.Scene scene = table.getScene();
                    if (scene != null)
                        NotificacionUtil.error(scene, "No se pudo cargar el registro de auditoría");
                });
            }
        });
    }

    private String getEntidadValue() {
        if (entidadFilter == null) return null;
        String sel = entidadFilter.getSelectionModel().getSelectedItem();
        return (sel == null || sel.equals("Todas")) ? null : sel;
    }

    private void updatePaginationUI() {
        int totalPages = totalCount == 0 ? 1 : (int) Math.ceil((double) totalCount / pageSize);

        if (lblTotal != null) {
            lblTotal.setText(totalCount == 1
                ? "1 registro encontrado"
                : totalCount + " registros encontrados");
        }
        if (lblPagina != null) {
            lblPagina.setText("Página " + (currentPage + 1) + " de " + totalPages);
        }

        boolean isFirst = currentPage == 0;
        boolean isLast  = currentPage >= totalPages - 1;

        if (btnPrimera   != null) btnPrimera.setDisable(isFirst);
        if (btnAnterior  != null) btnAnterior.setDisable(isFirst);
        if (btnSiguiente != null) btnSiguiente.setDisable(isLast);
        if (btnUltima    != null) btnUltima.setDisable(isLast);
    }

    private void updatePaginationDisabled() {
        if (lblTotal  != null) lblTotal.setText("");
        if (lblPagina != null) lblPagina.setText("—");
        if (btnPrimera   != null) btnPrimera.setDisable(true);
        if (btnAnterior  != null) btnAnterior.setDisable(true);
        if (btnSiguiente != null) btnSiguiente.setDisable(true);
        if (btnUltima    != null) btnUltima.setDisable(true);
    }

    @FXML private void onSearch() {
        if (searchDebounce != null) { searchDebounce.stop(); searchDebounce.playFromStart(); }
    }

    @FXML private void onFilter() {
        currentPage = 0;
        loadData();
    }

    @FXML private void onLimpiar() {
        if (searchField   != null) searchField.clear();
        if (usuarioFilter != null) usuarioFilter.clear();
        if (desdeField    != null) desdeField.setValue(null);
        if (hastaField    != null) hastaField.setValue(null);
        if (entidadFilter != null) entidadFilter.getSelectionModel().selectFirst();
        STICKY.put("search", ""); STICKY.put("usuario", "");
        STICKY.put("entidad", "Todas"); STICKY.put("desde", ""); STICKY.put("hasta", "");
        currentPage = 0;
        loadData();
    }

    @FXML private void onRefresh() { loadData(); }

    @FXML private void onExportarPdf() { exportar(() -> reporteService.exportAuditoriaPdf(
        getAllFilteredLogs(), getEntidadValue(), searchField != null ? searchField.getText() : null,
        desdeField != null ? desdeField.getValue() : null,
        hastaField != null ? hastaField.getValue() : null)); }

    @FXML private void onExportarCsv() { exportar(() -> reporteService.exportAuditoriaCsv(getAllFilteredLogs())); }

    private List<AuditLog> getAllFilteredLogs() throws Exception {
        String busqueda = searchField  != null ? searchField.getText()  : null;
        String entidad  = getEntidadValue();
        String usuario  = usuarioFilter != null ? usuarioFilter.getText() : null;
        LocalDate desde = desdeField   != null ? desdeField.getValue()  : null;
        LocalDate hasta = hastaField   != null ? hastaField.getValue()  : null;
        return auditRepo.findPaginated(50_000, 0, busqueda, entidad, usuario, desde, hasta);
    }

    private void exportar(java.util.concurrent.Callable<java.io.File> task) {
        javafx.scene.Scene scene = table.getScene();
        if (scene == null) return;
        DialogUtil.runAsyncWithProgress(scene, "Generando reporte…",
            task::call,
            file -> {
                if (file == null) { NotificacionUtil.advertencia(scene, "No hay registros para exportar con los filtros actuales."); return; }
                DialogUtil.showExportResultDialog(scene, file);
            },
            e -> NotificacionUtil.error(scene, "Error al generar el reporte de auditoría"));
    }

    @FXML private void onPrimera() {
        if (currentPage == 0) return;
        currentPage = 0;
        loadData();
    }

    @FXML private void onAnterior() {
        if (currentPage <= 0) return;
        currentPage--;
        loadData();
    }

    @FXML private void onSiguiente() {
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        if (currentPage >= totalPages - 1) return;
        currentPage++;
        loadData();
    }

    @FXML private void onUltima() {
        int totalPages = totalCount == 0 ? 1 : (int) Math.ceil((double) totalCount / pageSize);
        if (currentPage >= totalPages - 1) return;
        currentPage = totalPages - 1;
        loadData();
    }
}
