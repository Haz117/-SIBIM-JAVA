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
    @FXML private Button btnLimpiar;
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
    @FXML private javafx.scene.control.MenuButton btnExportar;
    @FXML private ProgressIndicator loadSpinner;
    @FXML private Button btnRefresh;
    @FXML private Button btnResetColumns;
    @FXML private ComboBox<Integer> pageSizeBox;
    @FXML private HBox paginationBar;

    /** Column widths and sort, kept between sessions. */
    private static final Preferences STICKY =
        Preferences.userRoot().node("sibim/filters/auditoria");

    /** The filters, kept while SIBIM is open (leaving the screen and coming
     *  back) but not between sessions: remembered in the registry, a filter
     *  set days ago greeted the next visit with "4 registros" and no sign why. */
    private static final java.util.Map<String, String> FILTROS = new java.util.concurrent.ConcurrentHashMap<>();

    static final String TODAS_ENTIDADES = "Todas las entidades";
    static final String TODAS_ACCIONES  = "Todas las acciones";
    static final String TODOS_USUARIOS  = "Todos los usuarios";

    /** What each "entidad" stored in the bitácora is called on screen. */
    static String etiquetaEntidad(String entidad) {
        if (entidad == null) return "";
        return switch (entidad) {
            case "sesion" -> "Sesiones";
            case "producto" -> "Bienes";
            case "movimiento" -> "Movimientos";
            case "categoria" -> "Categorías";
            case "usuario" -> "Usuarios";
            case "resguardo" -> "Resguardos";
            case "area_resguardo" -> "Resguardos de área";
            case "prestamo" -> "Préstamos";
            case "comodato" -> "Comodatos";
            case "acta" -> "Actas E/R";
            case "area" -> "Áreas";
            case "mantenimiento" -> "Mantenimiento";
            case "configuracion" -> "Configuración";
            case "solicitud" -> "Solicitudes";
            case "conteo" -> "Conteos físicos";
            case "backup" -> "Respaldos";
            case "sistema" -> "Sistema";
            default -> Character.toUpperCase(entidad.charAt(0)) + entidad.substring(1).replace('_', ' ');
        };
    }

    /** What each "accion" stored in the bitácora reads as on screen. */
    static String etiquetaAccion(String accion) {
        if (accion == null || accion.isBlank()) return "";
        return switch (accion.toLowerCase()) {
            case "login" -> "Inicio de sesión";
            case "logout" -> "Cierre de sesión";
            case "login_fallido" -> "Acceso fallido";
            case "operador" -> "Quién usa la cuenta";
            case "crear" -> "Alta";
            case "actualizar" -> "Actualización";
            case "eliminar" -> "Eliminación";
            case "baja" -> "Baja";
            case "reactivar" -> "Reactivación";
            case "cambio_precio" -> "Cambio de precio";
            case "codigo_reasignado" -> "Código reasignado";
            case "transferencia_aprobada" -> "Transferencia aprobada";
            case "transferencia_rechazada" -> "Transferencia rechazada";
            case "transferencia_recibida" -> "Transferencia recibida";
            case "cancelar" -> "Cancelación";
            case "devolver" -> "Devolución";
            case "concluir" -> "Conclusión";
            case "rescindir" -> "Rescisión";
            case "restaurar" -> "Restauración";
            case "aprobar" -> "Aprobación";
            case "rechazar" -> "Rechazo";
            default -> Character.toUpperCase(accion.charAt(0)) + accion.substring(1).replace('_', ' ');
        };
    }

    /** The pill's tone: done or let in (verde), changed (dorado), taken out
     *  or refused (guinda), the rest neutral — the institutional palette, not
     *  the bright alert colors that filled whole cells. */
    static String tonoAccion(String accion) {
        if (accion == null) return "audit-pill-neutro";
        return switch (accion.toLowerCase()) {
            case "login", "crear", "reactivar", "operador", "transferencia_recibida", "transferencia_aprobada",
                 "aprobar", "devolver", "concluir", "restaurar" -> "audit-pill-verde";
            case "baja", "eliminar", "login_fallido", "transferencia_rechazada", "cancelar", "rescindir",
                 "rechazar" -> "audit-pill-guinda";
            case "actualizar", "cambio_precio", "codigo_reasignado" -> "audit-pill-dorado";
            default -> "audit-pill-neutro";
        };
    }

    /** Each loadData() takes a number; a response that isn't the latest is
     *  dropped. Several reads go out together (opening the screen, a combo
     *  filled in the background, quick changes), and one that came back late
     *  put another filter's rows under the current one. */
    private long consulta;
    /** While the combos are being filled or restored, selecting doesn't reload. */
    private boolean ajustando;

    private final AuditLogRepository auditRepo    = new AuditLogRepository();
    private final ReporteService      reporteService = new ReporteService();

    private int currentPage = 0;
    private int pageSize    = DEFAULT_PAGE_SIZE;
    private int totalCount  = 0;

    private PauseTransition searchDebounce;

    @FXML
    public void initialize() {
        ajustando = true;
        setupColumns();
        setupComboFilter(entidadFilter, TODAS_ENTIDADES, "entidad", auditRepo::findDistinctEntidades,
            AuditoriaController::etiquetaEntidad);
        setupComboFilter(accionFilter, TODAS_ACCIONES, "accion", auditRepo::findDistinctAcciones,
            AuditoriaController::etiquetaAccion);
        setupComboFilter(usuarioFilter, TODOS_USUARIOS, "usuario", auditRepo::findDistinctUsuarios, u -> u);
        setupPageSizeBox();
        setupResumenToggle();

        searchDebounce = new PauseTransition(Duration.millis(280));
        searchDebounce.setOnFinished(e -> {
            guardarFiltros();
            currentPage = 0;
            loadData();
        });

        // The filters of this session (see FILTROS).
        if (searchField != null) searchField.setText(FILTROS.getOrDefault("search", ""));
        try {
            if (desdeField != null && FILTROS.containsKey("desde")) desdeField.setValue(LocalDate.parse(FILTROS.get("desde")));
            if (hastaField != null && FILTROS.containsKey("hasta")) hastaField.setValue(LocalDate.parse(FILTROS.get("hasta")));
        } catch (Exception ignored) {
            log.debug("Filtro de fecha de Auditoría no válido; se ignora", ignored);
        }

        if (loadSpinner != null) { loadSpinner.setVisible(false); loadSpinner.setManaged(false); }
        updateExportButtons();

        AnimationUtils.fadeInDown(filterBar, 220, 0);
        ajustando = false;
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

    @FunctionalInterface
    private interface Valores { List<String> leer() throws Exception; }

    /** A filter combo whose options are the values the bitácora actually has
     *  (a fixed list knew 7 of the 17 entidades SIBIM logs), shown by
     *  {@code etiqueta}; {@code todas} is "no filter". Filled in the background
     *  and set back to this session's choice without reloading the table. */
    private void setupComboFilter(ComboBox<String> combo, String todas, String clave,
                                  Valores valores, java.util.function.Function<String, String> etiqueta) {
        if (combo == null) return;
        javafx.util.Callback<ListView<String>, ListCell<String>> celdas = lv -> new ListCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : todas.equals(v) ? v : etiqueta.apply(v));
            }
        };
        combo.setCellFactory(celdas);
        combo.setButtonCell(celdas.call(null));
        combo.getItems().setAll(todas);
        combo.getSelectionModel().selectFirst();
        AppExecutor.submit(() -> {
            try {
                List<String> lista = valores.leer();
                Platform.runLater(() -> {
                    boolean antes = ajustando;
                    ajustando = true;
                    List<String> items = new ArrayList<>();
                    items.add(todas);
                    items.addAll(lista);
                    combo.getItems().setAll(items);
                    String elegido = FILTROS.get(clave);
                    if (elegido != null && items.contains(elegido)) combo.getSelectionModel().select(elegido);
                    else combo.getSelectionModel().selectFirst();
                    ajustando = antes;
                    // A filter of this session that the first read didn't have yet.
                    if (elegido != null && items.contains(elegido)) { currentPage = 0; loadData(); }
                });
            } catch (Exception ignored) {
                log.debug("No se pudieron leer las opciones del filtro '{}' de Auditoría", clave, ignored);
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
        long mia = ++consulta;
        actualizarLimpiar();

        AppExecutor.submit(() -> {
            try {
                int total = auditRepo.countFiltrado(busqueda, entidad, accion, usuario, desde, hasta);
                List<AuditLog> rows = auditRepo.findPaginated(pageSize, offset,
                    busqueda, entidad, accion, usuario, desde, hasta);
                AuditLogRepository.AuditStats stats = auditRepo.getStats(busqueda, entidad, accion, usuario, desde, hasta);
                Platform.runLater(() -> {
                    if (mia != consulta) return;   // a newer read is on its way
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
                    if (mia != consulta) return;
                    if (loadSpinner != null) { loadSpinner.setVisible(false); loadSpinner.setManaged(false); }
                    table.getItems().clear();
                    if (lblTotal != null) lblTotal.setText("Acceso denegado — se requiere rol Administrador");
                    updatePaginationDisabled();
                    updateExportButtons();
                });
            } catch (Exception ex) {
                log.error("Error al cargar registros de auditoría", ex);
                Platform.runLater(() -> {
                    if (mia != consulta) return;
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
        if (btnExportar != null) btnExportar.setDisable(empty);
    }

    private static String valor(ComboBox<String> combo, String todas) {
        if (combo == null) return null;
        String sel = combo.getSelectionModel().getSelectedItem();
        return (sel == null || sel.equals(todas)) ? null : sel;
    }

    private String getEntidadValue() { return valor(entidadFilter, TODAS_ENTIDADES); }
    private String getAccionValue()  { return valor(accionFilter, TODAS_ACCIONES); }
    private String getUsuarioValue() { return valor(usuarioFilter, TODOS_USUARIOS); }

    /** Remembers the filters for this session (see FILTROS). A combo still
     *  waiting for its options keeps what was saved instead of erasing it. */
    private void guardarFiltros() {
        guardar("search", searchField != null && !searchField.getText().isBlank() ? searchField.getText() : null);
        if (entidadFilter != null && entidadFilter.getItems().size() > 1) guardar("entidad", getEntidadValue());
        if (accionFilter  != null && accionFilter.getItems().size()  > 1) guardar("accion",  getAccionValue());
        if (usuarioFilter != null && usuarioFilter.getItems().size() > 1) guardar("usuario", getUsuarioValue());
        guardar("desde", desdeField != null && desdeField.getValue() != null ? desdeField.getValue().toString() : null);
        guardar("hasta", hastaField != null && hastaField.getValue() != null ? hastaField.getValue().toString() : null);
    }

    private static void guardar(String clave, String valor) {
        if (valor == null) FILTROS.remove(clave); else FILTROS.put(clave, valor);
    }

    /** "Limpiar filtros" only while there is something to clear. */
    private void actualizarLimpiar() {
        if (btnLimpiar == null) return;
        boolean hay = (searchField != null && !searchField.getText().isBlank())
            || getEntidadValue() != null || getAccionValue() != null || getUsuarioValue() != null
            || (desdeField != null && desdeField.getValue() != null)
            || (hastaField != null && hastaField.getValue() != null);
        btnLimpiar.setVisible(hay);
        btnLimpiar.setManaged(hay);
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
        if (ajustando) return;
        guardarFiltros();
        currentPage = 0;
        loadData();
    }

    @FXML private void onLimpiar() {
        ajustando = true;
        if (searchField   != null) searchField.clear();
        if (usuarioFilter != null) usuarioFilter.getSelectionModel().selectFirst();
        if (accionFilter  != null) accionFilter.getSelectionModel().selectFirst();
        if (desdeField    != null) desdeField.setValue(null);
        if (hastaField    != null) hastaField.setValue(null);
        if (entidadFilter != null) entidadFilter.getSelectionModel().selectFirst();
        ajustando = false;
        FILTROS.clear();
        currentPage = 0;
        loadData();
    }

    @FXML private void onRefresh() { loadData(); }

    /** "Desde" after "Hasta" found nothing and said nothing: the two are swapped. */
    @FXML private void onFiltroFecha() {
        if (ajustando) return;
        if (desdeField != null && hastaField != null && desdeField.getValue() != null && hastaField.getValue() != null
                && desdeField.getValue().isAfter(hastaField.getValue())) {
            ajustando = true;
            LocalDate d = desdeField.getValue();
            desdeField.setValue(hastaField.getValue());
            hastaField.setValue(d);
            ajustando = false;
        }
        onFilter();
    }

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
        ajustando = true;   // one read, not one per date picker
        if (desdeField != null) desdeField.setValue(desde);
        if (hastaField  != null) hastaField.setValue(hasta);
        ajustando = false;
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
