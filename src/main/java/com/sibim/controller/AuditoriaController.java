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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.prefs.Preferences;

/** Auditoría: who did what and when (19-nov-2026 redesign: the acciones as chips that filter,
 *  the filters in one row, the columns saying which bien, its área and what changed). */
public class AuditoriaController {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaController.class);

    private static final int DEFAULT_PAGE_SIZE = 50;
    /** How many acción chips show before «Más acciones». */
    static final int CHIPS_A_LA_VISTA = 5;

    @FXML private VBox filterBar;
    @FXML private FlowPane chipsAccion;
    @FXML private Button btnLimpiar;
    @FXML private Button btnSoloBien;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> entidadFilter;
    @FXML private ComboBox<String> usuarioFilter;
    @FXML private ComboBox<String> periodoBox;
    @FXML private HBox fechasBox;
    @FXML private DatePicker desdeField;
    @FXML private DatePicker hastaField;
    @FXML private Label lblTotal;
    @FXML private TableView<AuditLog> table;
    @FXML private TableColumn<AuditLog, LocalDateTime> colCuando;
    @FXML private TableColumn<AuditLog, String> colSobre;
    @FXML private TableColumn<AuditLog, String> colArea;
    @FXML private TableColumn<AuditLog, String> colAccion;
    @FXML private TableColumn<AuditLog, String> colQuien;
    @FXML private TableColumn<AuditLog, String> colCambio;
    @FXML private Label lblPagina;
    @FXML private Button btnPrimera;
    @FXML private Button btnAnterior;
    @FXML private Button btnSiguiente;
    @FXML private Button btnUltima;
    @FXML private MenuButton btnExportar;
    @FXML private ProgressIndicator loadSpinner;
    @FXML private Button btnRefresh;
    @FXML private Button btnResetColumns;
    @FXML private ComboBox<Integer> pageSizeBox;
    @FXML private HBox paginationBar;

    /** Column widths and sort, kept between sessions (a new node: the columns changed on 19-nov-2026). */
    private static final Preferences STICKY =
        Preferences.userRoot().node("sibim/filters/auditoria-2");

    /** The filters, kept while SIBIM is open (leaving the screen and coming
     *  back) but not between sessions: remembered in the registry, a filter
     *  set days ago greeted the next visit with "4 registros" and no sign why. */
    private static final Map<String, String> FILTROS = new java.util.concurrent.ConcurrentHashMap<>();

    static final String TODAS_ENTIDADES = "Todos los registros";
    static final String TODOS_USUARIOS  = "Todos los usuarios";

    /** The period box: the presets and «Entre fechas…», which shows the two dates. */
    static final String TODO = "Todo el historial", HOY = "Hoy", SIETE = "Últimos 7 días", MES = "Este mes",
        ENTRE = "Entre fechas…";

    /** The dates a period means today; null for «Todo» and «Entre fechas…». */
    static LocalDate[] fechasDe(String periodo, LocalDate hoy) {
        if (periodo == null) return null;
        return switch (periodo) {
            case HOY -> new LocalDate[]{hoy, hoy};
            case SIETE -> new LocalDate[]{hoy.minusDays(6), hoy};
            case MES -> new LocalDate[]{hoy.withDayOfMonth(1), hoy};
            default -> null;
        };
    }

    /** Each loadData() takes a number; a response that isn't the latest is
     *  dropped. Several reads go out together (opening the screen, a combo
     *  filled in the background, quick changes), and one that came back late
     *  put another filter's rows under the current one. */
    private long consulta;
    /** While the controls are being filled or restored, changing them doesn't reload. */
    private boolean ajustando;

    /** The acción chosen with a chip; null is all of them. */
    private String accion;
    /** «Ver todo lo de SSPCR/19»: one bien's (or resguardo's…) history, and what the chip says. */
    private String soloId, soloEtiqueta;

    private final AuditLogRepository auditRepo    = new AuditLogRepository();
    private final ReporteService      reporteService = new ReporteService();

    private int currentPage = 0;
    private int pageSize    = DEFAULT_PAGE_SIZE;
    private int totalCount  = 0;

    private PauseTransition searchDebounce;

    @FXML
    public void initialize() {
        ajustando = true;
        DialogUtil.setupColumnReset(table, btnResetColumns, STICKY);
        AuditoriaColumnSetup.configure(table, colCuando, colSobre, colArea, colAccion, colQuien, colCambio, STICKY);
        setupComboFilter(entidadFilter, TODAS_ENTIDADES, "entidad", auditRepo::findDistinctEntidades,
            EtiquetasAuditoria::entidadPlural);
        setupComboFilter(usuarioFilter, TODOS_USUARIOS, "usuario", auditRepo::findDistinctUsuarios, u -> u);
        setupPageSizeBox();
        setupPeriodo();

        searchDebounce = new PauseTransition(Duration.millis(280));
        searchDebounce.setOnFinished(e -> {
            guardarFiltros();
            currentPage = 0;
            loadData();
        });

        // The filters of this session (see FILTROS).
        searchField.setText(FILTROS.getOrDefault("search", ""));
        accion = FILTROS.get("accion");
        soloId = FILTROS.get("soloId");
        soloEtiqueta = FILTROS.get("soloEtiqueta");
        mostrarSoloBien();

        loadSpinner.setVisible(false);
        loadSpinner.setManaged(false);
        btnExportar.setDisable(true);

        AnimationUtils.fadeInDown(filterBar, 220, 0);
        ajustando = false;
        loadData();

        SearchUtils.setupSearchHistory("sibim/search-history/auditoria", searchField, () -> {
            currentPage = 0;
            loadData();
        });
        Platform.runLater(() -> searchField.requestFocus());
        searchField.sceneProperty().addListener((obs, old, scene) -> {
            if (scene == null) return;
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN),
                () -> { searchField.requestFocus(); searchField.selectAll(); });
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.LEFT, KeyCombination.CONTROL_DOWN), this::onAnterior);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.CONTROL_DOWN), this::onSiguiente);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.HOME, KeyCombination.CONTROL_DOWN), this::onPrimera);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.END, KeyCombination.CONTROL_DOWN), this::onUltima);
        });

        table.setPlaceholder(EmptyStateUtil.build("mdi2s-shield-lock-outline", "Sin registros con estos filtros",
            "Quita algún filtro, o cambia el periodo"));
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
        table.setContextMenu(AuditoriaContextMenu.build(table, this::showDetalle, this::verTodoDe));
    }

    private void showDetalle(AuditLog entry) {
        AuditoriaDetailDialog.show(entry, this::verTodoDe);
    }

    /** «Ver todo lo de SSPCR/19»: that bien's history, every acción and date, newest first. */
    void verTodoDe(AuditLog entry) {
        if (entry == null || entry.getEntidadId() == null || entry.getEntidadId().isBlank()) return;
        String numero = AuditoriaColumnSetup.lectura(entry).numero();
        soloId = entry.getEntidadId();
        soloEtiqueta = numero != null ? numero
            : EtiquetasAuditoria.entidadSingular(entry.getEntidad()) + " " + entry.getEntidadNombre();
        ajustando = true;
        accion = null;
        searchField.clear();
        periodoBox.setValue(TODO);
        ajustando = false;
        mostrarSoloBien();
        guardarFiltros();
        currentPage = 0;
        loadData();
    }

    @FXML private void onQuitarSoloBien() {
        soloId = null;
        soloEtiqueta = null;
        mostrarSoloBien();
        guardarFiltros();
        currentPage = 0;
        loadData();
    }

    private void mostrarSoloBien() {
        boolean hay = soloId != null;
        btnSoloBien.setText(hay ? "Solo " + soloEtiqueta : "");
        btnSoloBien.setVisible(hay);
        btnSoloBien.setManaged(hay);
        btnSoloBien.setAccessibleText(hay ? "Quitar el filtro: solo " + soloEtiqueta : null);
    }

    @FunctionalInterface
    private interface Valores { List<String> leer() throws Exception; }

    /** A filter combo whose options are the values the bitácora actually has
     *  (a fixed list knew 7 of the 17 entidades SIBIM logs), shown by
     *  {@code etiqueta}; {@code todas} is "no filter". Filled in the background
     *  and set back to this session's choice without reloading the table. */
    private void setupComboFilter(ComboBox<String> combo, String todas, String clave,
                                  Valores valores, java.util.function.Function<String, String> etiqueta) {
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
                    // People: type to find one (5-nov-2026); "Todos…" stays the first option.
                    if (combo == usuarioFilter && combo.getProperties().putIfAbsent("sibim.escribible", items) == null)
                        DialogUtil.makeFilterable(combo, items);
                    String elegido = FILTROS.get(clave);
                    if (elegido != null && items.contains(elegido)) combo.setValue(elegido);
                    else combo.setValue(todas);
                    ajustando = antes;
                    // A filter of this session that the first read didn't have yet.
                    if (elegido != null && items.contains(elegido)) { currentPage = 0; loadData(); }
                });
            } catch (Exception ignored) {
                log.debug("No se pudieron leer las opciones del filtro '{}' de Auditoría", clave, ignored);
            }
        });
    }

    private void setupPeriodo() {
        periodoBox.getItems().setAll(TODO, HOY, SIETE, MES, ENTRE);
        String guardado = FILTROS.getOrDefault("periodo", TODO);
        periodoBox.setValue(periodoBox.getItems().contains(guardado) ? guardado : TODO);
        try {
            if (FILTROS.containsKey("desde")) desdeField.setValue(LocalDate.parse(FILTROS.get("desde")));
            if (FILTROS.containsKey("hasta")) hastaField.setValue(LocalDate.parse(FILTROS.get("hasta")));
        } catch (Exception ignored) {
            log.debug("Filtro de fecha de Auditoría no válido; se ignora", ignored);
        }
        mostrarFechas();
        periodoBox.valueProperty().addListener((o, a, b) -> {
            mostrarFechas();
            if (ajustando) return;
            if (ENTRE.equals(b)) {
                // The two dates appear; it filters once one is set.
                Platform.runLater(desdeField::requestFocus);
                if (desdeField.getValue() == null && hastaField.getValue() == null) { guardarFiltros(); return; }
            }
            onFilter();
        });
    }

    private void mostrarFechas() {
        boolean entre = ENTRE.equals(periodoBox.getValue());
        fechasBox.setVisible(entre);
        fechasBox.setManaged(entre);
    }

    private void setupPageSizeBox() {
        pageSizeBox.getItems().addAll(25, 50, 100, 250);
        pageSizeBox.getSelectionModel().select(Integer.valueOf(DEFAULT_PAGE_SIZE));
        pageSizeBox.setOnAction(e -> {
            Integer sel = pageSizeBox.getSelectionModel().getSelectedItem();
            if (sel != null) { pageSize = sel; currentPage = 0; loadData(); }
        });
    }

    /** What the controls say now. */
    AuditLogRepository.Filtro filtro() {
        String periodo = periodoBox.getValue();
        LocalDate desde, hasta;
        if (ENTRE.equals(periodo)) { desde = desdeField.getValue(); hasta = hastaField.getValue(); }
        else {
            LocalDate[] f = fechasDe(periodo, LocalDate.now());
            desde = f == null ? null : f[0];
            hasta = f == null ? null : f[1];
        }
        return new AuditLogRepository.Filtro(searchField.getText(), valor(entidadFilter, TODAS_ENTIDADES), accion,
            valor(usuarioFilter, TODOS_USUARIOS), desde, hasta, soloId);
    }

    private void loadData() {
        loadSpinner.setVisible(true);
        loadSpinner.setManaged(true);
        AuditLogRepository.Filtro f = filtro();
        int offset = currentPage * pageSize;
        long mia = ++consulta;
        actualizarLimpiar();

        AppExecutor.submit(() -> {
            try {
                int total = auditRepo.countFiltrado(f);
                List<AuditLog> rows = auditRepo.findPaginated(pageSize, offset, f);
                LinkedHashMap<String, Integer> porAccion = auditRepo.contarPorAccion(f);
                Platform.runLater(() -> {
                    if (mia != consulta) return;   // a newer read is on its way
                    loadSpinner.setVisible(false);
                    loadSpinner.setManaged(false);
                    totalCount = total;
                    table.getItems().setAll(rows);
                    AnimationUtils.staggerTableRows(table);
                    updatePaginationUI();
                    btnExportar.setDisable(rows.isEmpty());
                    pintarChips(porAccion);
                });
            } catch (SecurityException se) {
                Platform.runLater(() -> {
                    if (mia != consulta) return;
                    loadSpinner.setVisible(false);
                    loadSpinner.setManaged(false);
                    table.getItems().clear();
                    lblTotal.setText("Acceso denegado — se requiere rol Administrador");
                    updatePaginationDisabled();
                    btnExportar.setDisable(true);
                });
            } catch (Exception ex) {
                log.error("Error al cargar registros de auditoría", ex);
                Platform.runLater(() -> {
                    if (mia != consulta) return;
                    loadSpinner.setVisible(false);
                    loadSpinner.setManaged(false);
                    Scene scene = table.getScene();
                    if (scene != null) NotificacionUtil.error(scene, "No se pudo cargar el registro de auditoría");
                });
            }
        });
    }

    /** «Todas · 3,794», then each acción with its count, the most first; the rest under
     *  «Más acciones». The one chosen is marked, and shown even if it isn't among the first. */
    private void pintarChips(LinkedHashMap<String, Integer> porAccion) {
        List<javafx.scene.Node> chips = new ArrayList<>();
        int todas = porAccion.values().stream().mapToInt(Integer::intValue).sum();
        chips.add(chip("Todas", todas, null));
        List<Map.Entry<String, Integer>> lista = new ArrayList<>(porAccion.entrySet());
        List<Map.Entry<String, Integer>> resto = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, Integer> e : lista) {
            if (i++ < CHIPS_A_LA_VISTA || e.getKey().equals(accion)) chips.add(chip(EtiquetasAuditoria.accion(e.getKey()), e.getValue(), e.getKey()));
            else resto.add(e);
        }
        if (!resto.isEmpty()) {
            MenuButton mas = new MenuButton("Más acciones");
            mas.getStyleClass().addAll("btn-preset", "audit-chip-mas");
            for (Map.Entry<String, Integer> e : resto) {
                MenuItem m = new MenuItem(EtiquetasAuditoria.accion(e.getKey()) + "  ·  " + String.format("%,d", e.getValue()));
                m.setOnAction(ev -> elegirAccion(e.getKey()));
                mas.getItems().add(m);
            }
            chips.add(mas);
        }
        // An acción chosen that the other filters leave without entries still shows, at 0, to be unticked.
        if (accion != null && !porAccion.containsKey(accion)) chips.add(chip(EtiquetasAuditoria.accion(accion), 0, accion));
        chipsAccion.getChildren().setAll(chips);
    }

    private Button chip(String texto, int cuantos, String valor) {
        Button b = new Button(texto + "  ·  " + String.format("%,d", cuantos));
        boolean activo = java.util.Objects.equals(valor, accion);
        b.getStyleClass().add(activo ? "btn-preset-active" : "btn-preset");
        b.setAccessibleText(texto + ": " + cuantos + (activo ? ", elegido" : ""));
        b.setOnAction(e -> elegirAccion(activo && valor != null ? null : valor));
        return b;
    }

    private void elegirAccion(String valor) {
        accion = valor;
        guardarFiltros();
        currentPage = 0;
        loadData();
    }

    private static String valor(ComboBox<String> combo, String todas) {
        if (combo == null) return null;
        String sel = combo.getValue();
        return (sel == null || sel.equals(todas)) ? null : sel;
    }

    /** Remembers the filters for this session (see FILTROS). A combo still
     *  waiting for its options keeps what was saved instead of erasing it. */
    private void guardarFiltros() {
        guardar("search", searchField.getText().isBlank() ? null : searchField.getText());
        if (entidadFilter.getItems().size() > 1) guardar("entidad", valor(entidadFilter, TODAS_ENTIDADES));
        if (usuarioFilter.getItems().size() > 1) guardar("usuario", valor(usuarioFilter, TODOS_USUARIOS));
        guardar("accion", accion);
        guardar("periodo", periodoBox.getValue());
        guardar("desde", desdeField.getValue() != null ? desdeField.getValue().toString() : null);
        guardar("hasta", hastaField.getValue() != null ? hastaField.getValue().toString() : null);
        guardar("soloId", soloId);
        guardar("soloEtiqueta", soloEtiqueta);
    }

    private static void guardar(String clave, String valor) {
        if (valor == null) FILTROS.remove(clave); else FILTROS.put(clave, valor);
    }

    /** "Limpiar filtros" only while there is something to clear. */
    private void actualizarLimpiar() {
        boolean hay = !searchField.getText().isBlank()
            || valor(entidadFilter, TODAS_ENTIDADES) != null || valor(usuarioFilter, TODOS_USUARIOS) != null
            || accion != null || soloId != null || !TODO.equals(periodoBox.getValue());
        btnLimpiar.setVisible(hay);
        btnLimpiar.setManaged(hay);
    }

    private void updatePaginationUI() {
        int totalPages = totalCount == 0 ? 1 : (int) Math.ceil((double) totalCount / pageSize);
        lblTotal.setText(totalCount == 1 ? "1 registro" : String.format("%,d registros", totalCount));
        lblPagina.setText("Página " + String.format("%,d", currentPage + 1) + " de " + String.format("%,d", totalPages));
        boolean isFirst = currentPage == 0;
        boolean isLast  = currentPage >= totalPages - 1;
        btnPrimera.setDisable(isFirst);
        btnAnterior.setDisable(isFirst);
        btnSiguiente.setDisable(isLast);
        btnUltima.setDisable(isLast);
    }

    private void updatePaginationDisabled() {
        lblTotal.setText("");
        lblPagina.setText("—");
        btnPrimera.setDisable(true);
        btnAnterior.setDisable(true);
        btnSiguiente.setDisable(true);
        btnUltima.setDisable(true);
    }

    @FXML private void onSearch() {
        searchDebounce.stop();
        searchDebounce.playFromStart();
    }

    @FXML private void onFilter() {
        if (ajustando) return;
        guardarFiltros();
        currentPage = 0;
        loadData();
    }

    @FXML private void onLimpiar() {
        ajustando = true;
        searchField.clear();
        usuarioFilter.setValue(TODOS_USUARIOS);
        entidadFilter.getSelectionModel().selectFirst();
        periodoBox.setValue(TODO);
        desdeField.setValue(null);
        hastaField.setValue(null);
        accion = null;
        soloId = null;
        soloEtiqueta = null;
        ajustando = false;
        mostrarSoloBien();
        FILTROS.clear();
        currentPage = 0;
        loadData();
    }

    @FXML private void onRefresh() { loadData(); }

    /** "Desde" after "Hasta" found nothing and said nothing: the two are swapped. */
    @FXML private void onFiltroFecha() {
        if (ajustando) return;
        if (desdeField.getValue() != null && hastaField.getValue() != null
                && desdeField.getValue().isAfter(hastaField.getValue())) {
            ajustando = true;
            LocalDate d = desdeField.getValue();
            desdeField.setValue(hastaField.getValue());
            hastaField.setValue(d);
            ajustando = false;
        }
        onFilter();
    }

    @FXML private void onExportarPdf() {
        AuditLogRepository.Filtro f = filtro();
        exportar(() -> reporteService.exportAuditoriaPdf(todos(f), f.entidad(), f.busqueda(), f.desde(), f.hasta()));
    }

    @FXML private void onExportarExcel() {
        AuditLogRepository.Filtro f = filtro();
        exportar(() -> reporteService.exportAuditoriaExcel(todos(f)));
    }

    @FXML private void onExportarCsv() {
        AuditLogRepository.Filtro f = filtro();
        exportar(() -> reporteService.exportAuditoriaCsv(todos(f)));
    }

    private List<AuditLog> todos(AuditLogRepository.Filtro f) throws Exception {
        return auditRepo.findPaginated(50_000, 0, f);
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
