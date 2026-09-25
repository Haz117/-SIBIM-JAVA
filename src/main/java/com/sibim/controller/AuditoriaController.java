package com.sibim.controller;

import com.sibim.model.AuditLog;
import com.sibim.repository.AuditLogRepository;
import com.sibim.service.ReporteService;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppExecutor;
import com.sibim.util.DialogUtil;
import com.sibim.util.EmptyStateUtil;
import com.sibim.util.NotificacionUtil;
import com.sibim.util.SearchUtils;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.prefs.Preferences;

public class AuditoriaController {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaController.class);

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final DateTimeFormatter FECHA_FMT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @FXML private FlowPane filterBar;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> entidadFilter;
    @FXML private ComboBox<String> accionFilter;
    @FXML private ComboBox<String> usuarioFilter;
    @FXML private DatePicker desdeField;
    @FXML private DatePicker hastaField;
    @FXML private Button btnPresetHoy;
    @FXML private Button btnPresetSemana;
    @FXML private Button btnPresetMes;
    @FXML private Label lblTotal;
    @FXML private VBox resumenBox;
    @FXML private Button btnToggleResumen;
    @FXML private Label lblStatTotal;
    @FXML private Label lblStatLogins;
    @FXML private Label lblStatFallidos;
    @FXML private Label lblStatEliminaciones;
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
    @FXML private Button btnExportPdf;
    @FXML private Button btnExportExcel;
    @FXML private Button btnExportCsv;
    @FXML private ProgressIndicator loadSpinner;
    @FXML private Button btnRefresh;
    @FXML private Button btnResetColumns;
    @FXML private ComboBox<Integer> pageSizeBox;
    @FXML private HBox paginationBar;

    private static final Preferences STICKY =
        Preferences.userRoot().node("sibim/filters/auditoria");

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
        setupAccionFilter();
        setupUsuarioFilter();
        setupPageSizeBox();
        setupResumenToggle();

        searchDebounce = new PauseTransition(Duration.millis(280));
        searchDebounce.setOnFinished(e -> {
            currentPage = 0;
            loadData();
        });

        // Restore sticky filters from previous navigation
        if (searchField   != null) searchField.setText(STICKY.get("search", ""));
        String stickyUsuario = STICKY.get("usuario", "Todos");
        if (usuarioFilter != null && usuarioFilter.getItems().contains(stickyUsuario))
            usuarioFilter.getSelectionModel().select(stickyUsuario);
        String stickyEntidad = STICKY.get("entidad", "Todas");
        if (entidadFilter != null && entidadFilter.getItems().contains(stickyEntidad))
            entidadFilter.getSelectionModel().select(stickyEntidad);
        String stickyAccion = STICKY.get("accion", "Todas");
        if (accionFilter != null && accionFilter.getItems().contains(stickyAccion))
            accionFilter.getSelectionModel().select(stickyAccion);
        String desdeStr = STICKY.get("desde", "");
        String hastaStr = STICKY.get("hasta", "");
        if (!desdeStr.isBlank() && desdeField != null)
            try { desdeField.setValue(LocalDate.parse(desdeStr)); } catch (Exception ignored) {
                log.debug("Could not restore sticky 'desde' date filter value '{}'", desdeStr, ignored);
            }
        if (!hastaStr.isBlank() && hastaField != null)
            try { hastaField.setValue(LocalDate.parse(hastaStr)); } catch (Exception ignored) {
                log.debug("Could not restore sticky 'hasta' date filter value '{}'", hastaStr, ignored);
            }

        if (loadSpinner != null) { loadSpinner.setVisible(false); loadSpinner.setManaged(false); }
        updateExportButtons();

        AnimationUtils.fadeInDown(filterBar, 220, 0);
        loadData();

        if (searchField != null) {
            SearchUtils.setupSearchHistory("sibim/search-history/auditoria", searchField, () -> {
                currentPage = 0;
                loadData();
            });
            Platform.runLater(() -> searchField.requestFocus());
            searchField.sceneProperty().addListener((obs, old, scene) -> {
                if (scene == null) return;
                scene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN),
                    () -> { searchField.requestFocus(); searchField.selectAll(); });
                scene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.LEFT, KeyCombination.CONTROL_DOWN),
                    this::onAnterior);
                scene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.CONTROL_DOWN),
                    this::onSiguiente);
                scene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.HOME, KeyCombination.CONTROL_DOWN),
                    this::onPrimera);
                scene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.END, KeyCombination.CONTROL_DOWN),
                    this::onUltima);
            });
        }

        if (table != null) {
            table.setPlaceholder(EmptyStateUtil.build(
                "mdi2s-shield-lock-outline",
                "Sin actividad registrada",
                "Los eventos del sistema aparecerán aquí"));
            table.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) {
                    AuditLog sel = table.getSelectionModel().getSelectedItem();
                    if (sel != null) showDetalle(sel);
                }
            });
            table.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.F5) { loadData(); e.consume(); }
                else if (e.getCode() == KeyCode.ENTER) {
                    AuditLog sel = table.getSelectionModel().getSelectedItem();
                    if (sel != null) { showDetalle(sel); e.consume(); }
                } else if (e.getCode() == KeyCode.ESCAPE) {
                    table.getSelectionModel().clearSelection(); e.consume();
                }
            });

            table.setContextMenu(AuditoriaContextMenu.build(table, this::showDetalle));
        }
    }

    private void showDetalle(AuditLog entry) {
        AuditoriaDetailDialog.show(entry, FECHA_FMT);
    }

    private void setupColumns() {
        DialogUtil.setupColumnReset(table, btnResetColumns, STICKY);
        AuditoriaColumnSetup.configure(table, colFecha, colEntidad, colNombre, colAccion,
            colUsuario, colDetalle, FECHA_FMT, STICKY);
    }

    private void setupEntidadFilter() {
        if (entidadFilter == null) return;
        entidadFilter.getItems().addAll(
            "Todas", "sesion", "movimiento", "producto", "categoria",
            "usuario", "backup", "conteo"
        );
        entidadFilter.getSelectionModel().selectFirst();
    }

    /** Populated asynchronously from whatever "accion" values actually exist
     *  in the log, instead of a hardcoded list that would drift out of sync
     *  with whatever callers pass to {@code AuditLogRepository.log(...)}. */
    private void setupAccionFilter() {
        if (accionFilter == null) return;
        accionFilter.getItems().add("Todas");
        accionFilter.getSelectionModel().selectFirst();
        AppExecutor.submit(() -> {
            try {
                List<String> acciones = auditRepo.findDistinctAcciones();
                Platform.runLater(() -> {
                    List<String> items = new ArrayList<>();
                    items.add("Todas");
                    items.addAll(acciones);
                    accionFilter.getItems().setAll(items);
                    String sticky = STICKY.get("accion", "Todas");
                    if (accionFilter.getItems().contains(sticky))
                        accionFilter.getSelectionModel().select(sticky);
                    else
                        accionFilter.getSelectionModel().selectFirst();
                });
            } catch (Exception ignored) {
                // admin-only guard or a transient DB error — filter just stays on "Todas"
            }
        });
    }

    /** Same idea as {@link #setupAccionFilter()} but for usuarios — a free-text
     *  field could never match on a typo and a stale/renamed user would be
     *  impossible to filter by name if this instead read the live users table. */
    private void setupUsuarioFilter() {
        if (usuarioFilter == null) return;
        usuarioFilter.getItems().add("Todos");
        usuarioFilter.getSelectionModel().selectFirst();
        AppExecutor.submit(() -> {
            try {
                List<String> usuarios = auditRepo.findDistinctUsuarios();
                Platform.runLater(() -> {
                    List<String> items = new ArrayList<>();
                    items.add("Todos");
                    items.addAll(usuarios);
                    usuarioFilter.getItems().setAll(items);
                    String sticky = STICKY.get("usuario", "Todos");
                    if (usuarioFilter.getItems().contains(sticky))
                        usuarioFilter.getSelectionModel().select(sticky);
                    else
                        usuarioFilter.getSelectionModel().selectFirst();
                });
            } catch (Exception ignored) {
                log.debug("Could not load user filter options in AuditoriaController", ignored);
            }
        });
    }

    private void setupResumenToggle() {
        if (btnToggleResumen == null || resumenBox == null) return;
        DialogUtil.makeCollapsible("sibim/filters/auditoria-resumen", btnToggleResumen, resumenBox,
            "Mostrar resumen", "Ocultar resumen");
    }

    private void setupPageSizeBox() {
        if (pageSizeBox == null) return;
        pageSizeBox.getItems().addAll(25, 50, 100, 250);
        pageSizeBox.getSelectionModel().select(Integer.valueOf(DEFAULT_PAGE_SIZE));
        pageSizeBox.setOnAction(e -> {
            Integer sel = pageSizeBox.getSelectionModel().getSelectedItem();
            if (sel != null) { pageSize = sel; currentPage = 0; loadData(); }
        });
    }

    private void loadData() {
        if (loadSpinner != null) { loadSpinner.setVisible(true); loadSpinner.setManaged(true); }
        String busqueda = searchField != null ? searchField.getText() : null;
        String entidad  = getEntidadValue();
        String accion   = getAccionValue();
        String usuario  = getUsuarioValue();
        LocalDate desde = desdeField != null ? desdeField.getValue() : null;
        LocalDate hasta = hastaField != null ? hastaField.getValue() : null;
        int offset = currentPage * pageSize;

        // Persist current filters
        STICKY.put("search",  busqueda != null ? busqueda : "");
        STICKY.put("usuario", usuario  != null ? usuario  : "Todos");
        STICKY.put("entidad", entidad  != null ? entidad  : "Todas");
        STICKY.put("accion",  accion   != null ? accion   : "Todas");
        STICKY.put("desde",   desde    != null ? desde.toString() : "");
        STICKY.put("hasta",   hasta    != null ? hasta.toString() : "");

        AppExecutor.submit(() -> {
            try {
                int total = auditRepo.countFiltrado(busqueda, entidad, accion, usuario, desde, hasta);
                List<AuditLog> rows = auditRepo.findPaginated(pageSize, offset,
                    busqueda, entidad, accion, usuario, desde, hasta);
                AuditLogRepository.AuditStats stats = auditRepo.getStats(busqueda, entidad, accion, usuario, desde, hasta);
                Platform.runLater(() -> {
                    if (loadSpinner != null) { loadSpinner.setVisible(false); loadSpinner.setManaged(false); }
                    totalCount = total;
                    table.getItems().setAll(rows);
                    AnimationUtils.staggerTableRows(table);
                    updatePaginationUI();
                    updateExportButtons();
                    updateStats(stats);
                });
            } catch (SecurityException se) {
                Platform.runLater(() -> {
                    if (loadSpinner != null) { loadSpinner.setVisible(false); loadSpinner.setManaged(false); }
                    table.getItems().clear();
                    if (lblTotal != null) lblTotal.setText("Acceso denegado — se requiere rol Administrador");
                    updatePaginationDisabled();
                    updateExportButtons();
                });
            } catch (Exception ex) {
                log.error("Error al cargar registros de auditoría", ex);
                Platform.runLater(() -> {
                    if (loadSpinner != null) { loadSpinner.setVisible(false); loadSpinner.setManaged(false); }
                    Scene scene = table.getScene();
                    if (scene != null)
                        NotificacionUtil.error(scene, "No se pudo cargar el registro de auditoría");
                });
            }
        });
    }

    private void updateStats(AuditLogRepository.AuditStats stats) {
        if (lblStatTotal        != null) lblStatTotal.setText(String.valueOf(stats.total()));
        if (lblStatLogins       != null) lblStatLogins.setText(String.valueOf(stats.logins()));
        if (lblStatFallidos     != null) lblStatFallidos.setText(String.valueOf(stats.loginsFallidos()));
        if (lblStatEliminaciones != null) lblStatEliminaciones.setText(String.valueOf(stats.eliminaciones()));
    }

    private void updateExportButtons() {
        boolean empty = table.getItems().isEmpty();
        if (btnExportPdf   != null) btnExportPdf.setDisable(empty);
        if (btnExportExcel != null) btnExportExcel.setDisable(empty);
        if (btnExportCsv   != null) btnExportCsv.setDisable(empty);
    }

    private String getEntidadValue() {
        if (entidadFilter == null) return null;
        String sel = entidadFilter.getSelectionModel().getSelectedItem();
        return (sel == null || sel.equals("Todas")) ? null : sel;
    }

    private String getAccionValue() {
        if (accionFilter == null) return null;
        String sel = accionFilter.getSelectionModel().getSelectedItem();
        return (sel == null || sel.equals("Todas")) ? null : sel;
    }

    private String getUsuarioValue() {
        if (usuarioFilter == null) return null;
        String sel = usuarioFilter.getSelectionModel().getSelectedItem();
        return (sel == null || sel.equals("Todos")) ? null : sel;
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
        if (usuarioFilter != null) usuarioFilter.getSelectionModel().selectFirst();
        if (accionFilter  != null) accionFilter.getSelectionModel().selectFirst();
        if (desdeField    != null) desdeField.setValue(null);
        if (hastaField    != null) hastaField.setValue(null);
        if (entidadFilter != null) entidadFilter.getSelectionModel().selectFirst();
        STICKY.put("search", ""); STICKY.put("usuario", "Todos");
        STICKY.put("entidad", "Todas"); STICKY.put("accion", "Todas");
        STICKY.put("desde", ""); STICKY.put("hasta", "");
        currentPage = 0;
        loadData();
    }

    @FXML private void onRefresh() { loadData(); }

    @FXML private void onFiltroFecha() { onFilter(); }

    @FXML private void onPresetHoy() {
        LocalDate hoy = LocalDate.now();
        aplicarPreset(hoy, hoy);
    }

    @FXML private void onPresetSemana() {
        aplicarPreset(LocalDate.now().minusDays(6), LocalDate.now());
    }

    @FXML private void onPresetMes() {
        LocalDate hoy = LocalDate.now();
        aplicarPreset(hoy.withDayOfMonth(1), hoy);
    }

    private void aplicarPreset(LocalDate desde, LocalDate hasta) {
        if (desdeField != null) desdeField.setValue(desde);
        if (hastaField  != null) hastaField.setValue(hasta);
        onFilter();
    }

    @FXML private void onExportarPdf() { exportar(() -> reporteService.exportAuditoriaPdf(
        getAllFilteredLogs(), getEntidadValue(), searchField != null ? searchField.getText() : null,
        desdeField != null ? desdeField.getValue() : null,
        hastaField != null ? hastaField.getValue() : null)); }

    @FXML private void onExportarExcel() { exportar(() -> reporteService.exportAuditoriaExcel(getAllFilteredLogs())); }

    @FXML private void onExportarCsv() { exportar(() -> reporteService.exportAuditoriaCsv(getAllFilteredLogs())); }

    private List<AuditLog> getAllFilteredLogs() throws Exception {
        String busqueda = searchField  != null ? searchField.getText()  : null;
        String entidad  = getEntidadValue();
        String accion   = getAccionValue();
        String usuario  = getUsuarioValue();
        LocalDate desde = desdeField   != null ? desdeField.getValue()  : null;
        LocalDate hasta = hastaField   != null ? hastaField.getValue()  : null;
        return auditRepo.findPaginated(50_000, 0, busqueda, entidad, accion, usuario, desde, hasta);
    }

    private void exportar(Callable<File> task) {
        Scene scene = table.getScene();
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
