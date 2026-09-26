package com.sibim.controller;

import com.sibim.config.Areas;
import com.sibim.controller.dialogs.AreaResguardosDialog;
import com.sibim.controller.dialogs.ConteoFisicoDialog;
import com.sibim.controller.dialogs.ImportacionBienesDialog;
import com.sibim.controller.dialogs.MovimientoTimelineDialog;
import com.sibim.controller.dialogs.ProductoBajasDialog;
import com.sibim.controller.dialogs.ProductoDetailDialog;
import com.sibim.controller.dialogs.ComparacionBienesDialog;
import com.sibim.controller.dialogs.ProductoDialogFactory;
import com.sibim.model.Categoria;
import com.sibim.model.Producto;
import com.sibim.model.enums.EstadoProducto;
import com.sibim.service.CategoriaService;
import com.sibim.service.MovimientoService;
import com.sibim.service.ProductoService;
import com.sibim.service.ReporteService;
import com.sibim.session.NavigationContext;
import com.sibim.session.SessionManager;
import com.sibim.repository.ProductoRepository;
import com.sibim.util.AccessibilityUtils;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppColors;
import com.sibim.util.AppExecutor;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import com.sibim.util.SearchUtils;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProductosController {

    private static final Logger log = LoggerFactory.getLogger(ProductosController.class);
    private static final java.util.prefs.Preferences STICKY =
        java.util.prefs.Preferences.userRoot().node("sibim/filters/productos");

    /** Bounded LRU (max 200 thumbnails) so long-running sessions browsing many
     *  different photos don't grow this cache unbounded — evicts the least
     *  recently used entry once the cap is hit. */
    private static final int THUMBNAIL_CACHE_MAX = 200;
    private static final Map<String, Image> THUMBNAIL_CACHE = Collections.synchronizedMap(
        new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
                return size() > THUMBNAIL_CACHE_MAX;
            }
        });

    @FXML private VBox rootPane;
    @FXML private TextField searchField;
    @FXML private ComboBox<Categoria> categoriaFilter;
    @FXML private ComboBox<String> areaFilter;
    @FXML private ComboBox<String> resguardanteFilter;
    @FXML private HBox statusChipsBar;
    @FXML private Button btnClearFilters;
    @FXML private TableView<Producto> table;
    @FXML private TableColumn<Producto, String> colFoto;
    @FXML private TableColumn<Producto, String> colNombre;
    @FXML private TableColumn<Producto, String> colCodigo;
    @FXML private TableColumn<Producto, String> colCategoria;
    @FXML private TableColumn<Producto, String> colArea;
    @FXML private TableColumn<Producto, Integer> colStock;
    @FXML private TableColumn<Producto, Integer> colStockMin;
    @FXML private TableColumn<Producto, Integer> colStockMax;
    @FXML private TableColumn<Producto, String> colValor;
    @FXML private TableColumn<Producto, String> colEstado;
    @FXML private Label lblTotal;
    @FXML private Label lblTotalAll;
    @FXML private Label lblSeleccionados;
    @FXML private ComboBox<Integer> pageSizeBox;
    @FXML private Label lblPage;
    @FXML private Button btnPrev;
    @FXML private Button btnNext;
    @FXML private FlowPane filterBar;
    @FXML private FlowPane activeChipsBar;
    @FXML private FlowPane presetsBar;
    @FXML private HBox presetsHeader;
    @FXML private Button btnGuardarPreset;
    @FXML private Button btnToggleFiltros;
    @FXML private VBox resumenBox;
    @FXML private Button btnToggleResumen;
    @FXML private HBox bulkBar;
    @FXML private Label lblBulkCount;
    @FXML private Button btnBulkArea;
    @FXML private Button btnBulkResguardante;
    @FXML private Button btnBulkMarcarEtiquetado;
    @FXML private Button btnComparar;
    @FXML private Button btnMovimiento;
    @FXML private Button btnQr;
    @FXML private Button btnEditar;
    @FXML private Button btnEliminar;
    @FXML private MenuButton btnExportarSeleccion;
    @FXML private Button btnNuevoBien;
    @FXML private Button btnConteoFisico;
    @FXML private Button btnClearSearch;
    @FXML private ProgressIndicator spinner;
    @FXML private Label lblStatTotal;
    @FXML private Label lblStatValor;
    @FXML private Label lblStatAlertas;
    @FXML private Label lblStatSinEtiquetar;
    @FXML private VBox statCardTotal;
    @FXML private VBox statCardValor;
    @FXML private VBox cardAlertas;
    @FXML private VBox cardSinEtiquetar;
    @FXML private Label helpAlertas;
    @FXML private Label helpResguardante;
    @FXML private Label helpTotal;
    @FXML private Label helpValor;
    @FXML private Label helpChips;
    @FXML private Label helpPresets;
    @FXML private Label helpColumnas;
    @FXML private Label helpFechaReg;
    @FXML private Label helpResumen;
    @FXML private DatePicker desdeRegFilter;
    @FXML private DatePicker hastaRegFilter;

    private final ProductoService productoService = new ProductoService();
    private final CategoriaService categoriaService = new CategoriaService();
    private final ReporteService reporteService = new ReporteService();
    private final MovimientoService movimientoService = new MovimientoService();

    private int totalFiltered = 0;
    private ObservableList<Producto> filteredData = FXCollections.observableArrayList();
    private int currentPage = 0;
    private int pageSize = 25;
    private boolean refreshing = false;
    private boolean filterSinEtiquetar = false;
    private final AtomicBoolean loading = new AtomicBoolean(false);
    private boolean canEdit = false;
    private FilterPresetPanel presetPanel;
    private ProductosChipsManager chipsManager;
    private ProductosBulkBar bulkBarManager;
    private String pendingHighlightId;
    private ToggleGroup estadoChipGroup;
    private Label emptyStateMsg;
    private Label emptyStateHint;
    private Button btnEmptyLimpiar;
    private VBox emptyStatePlaceholder;
    private ProductosDataLoader dataLoader;
    private ProductosTableManager tableManager;

    @FXML
    public void initialize() {
        canEdit = SessionManager.isAdmin() || SessionManager.isSecretario();
        if (btnToggleFiltros != null && filterBar != null)
            DialogUtil.makeCollapsible("bienes.filtros.colapsado", btnToggleFiltros, filterBar);
        if (btnToggleResumen != null && resumenBox != null)
            DialogUtil.makeCollapsible("bienes.resumen.colapsado", btnToggleResumen, resumenBox,
                "Mostrar resumen", "Ocultar resumen");
        bulkBarManager = new ProductosBulkBar(bulkBar, lblBulkCount, btnBulkArea,
            btnBulkResguardante, btnBulkMarcarEtiquetado, btnComparar, () -> canEdit);
        setupTable();
        setupFilters();
        setupStatusChips();
        chipsManager = new ProductosChipsManager(
            activeChipsBar, btnClearFilters, btnGuardarPreset, lblTotalAll,
            emptyStateMsg, btnEmptyLimpiar, emptyStateHint,
            () -> canEdit, () -> filterSinEtiquetar, () -> totalFiltered,
            estadoChipGroup, desdeRegFilter, hastaRegFilter,
            searchField, categoriaFilter, areaFilter, resguardanteFilter,
            this::applyFilters, this::onCardSinEtiquetar
        );
        presetPanel = new FilterPresetPanel(presetsBar, presetsHeader, categoriaFilter,
            searchField, areaFilter, resguardanteFilter, estadoChipGroup, this::applyFilters);
        presetPanel.load();
        if (btnMovimiento != null) { btnMovimiento.setVisible(canEdit); btnMovimiento.setManaged(canEdit); }
        if (btnEditar   != null) { btnEditar.setVisible(canEdit);   btnEditar.setManaged(canEdit); }
        if (btnEliminar != null) { btnEliminar.setVisible(canEdit); btnEliminar.setManaged(canEdit); }
        // "Nuevo Bien" and "Conteo físico" both write real inventory data
        // (the latter registers AJUSTE movements for every discrepancy) —
        // they need the same canEdit gate as Editar/Eliminar. Unlike those
        // two, these previously had no fx:id at all, so nothing ever hid
        // them: any logged-in user (including DIRECCION, who can't even see
        // "Editar"/"Eliminar") could create products or run a physical
        // count that silently adjusts stock.
        if (btnNuevoBien    != null) { btnNuevoBien.setVisible(canEdit);    btnNuevoBien.setManaged(canEdit); }
        if (btnConteoFisico != null) { btnConteoFisico.setVisible(canEdit); btnConteoFisico.setManaged(canEdit); }
        if (rootPane != null && canEdit) {
            rootPane.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, ev -> {
                if (ev.getCode() == javafx.scene.input.KeyCode.N && ev.isControlDown()) {
                    onNuevoBien(); ev.consume();
                } else if (ev.getCode() == javafx.scene.input.KeyCode.I && ev.isControlDown()) {
                    onImportarCsv(); ev.consume();
                } else if (ev.getCode() == javafx.scene.input.KeyCode.E && ev.isControlDown()
                        && table.getSelectionModel().getSelectedItem() != null) {
                    onEdit(); ev.consume();
                } else if (ev.getCode() == javafx.scene.input.KeyCode.G && ev.isControlDown()
                        && btnGuardarPreset != null && btnGuardarPreset.isVisible()) {
                    onGuardarPreset(); ev.consume();
                }
            });
        }
        if (rootPane != null) {
            rootPane.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, ev -> {
                if (ev.getCode() == javafx.scene.input.KeyCode.F && ev.isControlDown()) {
                    searchField.requestFocus(); searchField.selectAll();
                    ev.consume();
                }
            });
        }
        searchField.textProperty().addListener((obs, o, n) -> btnClearSearch.setVisible(!n.isBlank()));
        btnClearSearch.setOnAction(e -> { searchField.clear(); searchField.requestFocus(); });
        SearchUtils.setupSearchHistory("sibim/search-history/productos", searchField, this::applyFilters);
        // Restore sticky filters from previous session (skip area if a pending navigation filter was applied)
        String savedSearch = STICKY.get("search", "");
        if (!savedSearch.isBlank()) searchField.setText(savedSearch);
        String savedArea = STICKY.get("area", "");
        if (!savedArea.isBlank() && areaFilter.getValue() == null
                && areaFilter.getItems().contains(savedArea))
            areaFilter.setValue(savedArea);
        loadData();
        AnimationUtils.staggeredFadeInUp(
            java.util.List.of(statCardTotal, statCardValor, cardAlertas, cardSinEtiquetar), 300, 55);
        if (cardSinEtiquetar != null) {
            cardSinEtiquetar.getStyleClass().add("stat-card-clickable");
            cardSinEtiquetar.setOnMouseClicked(e -> onCardSinEtiquetar());
            AccessibilityUtils.asButton(cardSinEtiquetar, "Filtrar bienes sin etiqueta física");
            Tooltip.install(cardSinEtiquetar, new Tooltip("Clic para filtrar bienes sin etiqueta física"));
        }
        Platform.runLater(() -> searchField.requestFocus());
        if (helpAlertas      != null) DialogUtil.enableClickToShowTooltip(helpAlertas);
        if (helpResguardante != null) DialogUtil.enableClickToShowTooltip(helpResguardante);
        if (helpTotal        != null) DialogUtil.enableClickToShowTooltip(helpTotal);
        if (helpValor        != null) DialogUtil.enableClickToShowTooltip(helpValor);
        if (helpChips    != null) DialogUtil.enableClickToShowTooltip(helpChips);
        if (helpPresets  != null) DialogUtil.enableClickToShowTooltip(helpPresets);
        if (helpColumnas != null) DialogUtil.enableClickToShowTooltip(helpColumnas);
        if (helpFechaReg != null) DialogUtil.enableClickToShowTooltip(helpFechaReg);
        if (helpResumen  != null) DialogUtil.enableClickToShowTooltip(helpResumen);
    }

    // ── Table setup ──────────────────────────────────────────────────────────

    private void setupTable() {
        tableManager = new ProductosTableManager(
            table, colFoto, colNombre, colCodigo, colCategoria, colArea,
            colStock, colStockMin, colStockMax, colValor, colEstado,
            pageSizeBox, lblTotal, lblPage, btnPrev, btnNext, lblSeleccionados,
            btnMovimiento, btnQr, btnEditar, btnEliminar, btnExportarSeleccion,
            THUMBNAIL_CACHE, log, canEdit, () -> canEdit,
            this::onEdit, this::onDelete,
            this::showProductDetail, this::showMovimientoTimeline,
            this::exportarEtiquetasQr, this::exportarEtiquetaFisica,
            this::saveStockThresholds,
            () -> pendingHighlightId, bulkBarManager,
            () -> { pageSize = pageSizeBox.getValue(); currentPage = 0; loadPage(); },
            reporteService, movimientoService);
        tableManager.setOnClearFilters(this::onClearFilters);
        tableManager.setup();
        // expose empty-state refs set up inside TableManager
        emptyStateMsg         = tableManager.emptyStateMsg;
        emptyStateHint        = tableManager.emptyStateHint;
        btnEmptyLimpiar       = tableManager.btnEmptyLimpiar;
        emptyStatePlaceholder = tableManager.emptyStatePlaceholder;
    }

    // ── Filters & chips ──────────────────────────────────────────────────────

    private void setupStatusChips() {
        estadoChipGroup = new ToggleGroup();
        String[][] chips = {
            {"Todos",      null},
            {"Activo",     "filter-chip-green"},
            {"Bajo Stock", "filter-chip-amber"},
            {"Agotado",    "filter-chip-danger"},
            {"Vencido",    "filter-chip-purple"}
        };
        for (String[] entry : chips) {
            ToggleButton chip = new ToggleButton(entry[0]);
            chip.setToggleGroup(estadoChipGroup);
            chip.getStyleClass().add("filter-chip");
            if (entry[1] != null) chip.getStyleClass().add(entry[1]);
            if ("Todos".equals(entry[0])) chip.setSelected(true);
            chip.setOnAction(e -> { currentPage = 0; applyFilters(); });
            statusChipsBar.getChildren().add(chip);
        }
    }

    private void setupFilters() {
        DialogUtil.runAsync(
            () -> categoriaService.findAll(),
            cats -> {
                categoriaFilter.getItems().add(null);
                categoriaFilter.getItems().addAll(cats);
                categoriaFilter.setConverter(new javafx.util.StringConverter<>() {
                    public String toString(Categoria c) { return c == null ? "Todas" : c.getNombre(); }
                    public Categoria fromString(String s) { return null; }
                });
                categoriaFilter.setCellFactory(lv -> ProductosListCells.categoriaListCell());
                categoriaFilter.setButtonCell(ProductosListCells.categoriaListCell());
                // Consume category pre-filter set by Dashboard pie chart click
                String pendingCat = NavigationContext.consumePendingCategoryFilter();
                if (pendingCat != null) {
                    cats.stream()
                        .filter(c -> pendingCat.equalsIgnoreCase(c.getNombre()))
                        .findFirst()
                        .ifPresent(categoriaFilter::setValue);
                }
            },
            e -> log.error("No se pudieron cargar las categorías para el filtro", e)
        );

        SearchUtils.debounce(searchField, 280, q -> { currentPage = 0; applyFilters(); });
        categoriaFilter.valueProperty().addListener((obs, o, n) -> { currentPage = 0; applyFilters(); });

        areaFilter.getItems().add(null);
        areaFilter.getItems().addAll(Areas.getAllAreaNames());
        areaFilter.setConverter(new javafx.util.StringConverter<>() {
            public String toString(String a) { return a == null ? "Todas las áreas" : a; }
            public String fromString(String s) { return null; }
        });
        areaFilter.setCellFactory(lv -> ProductosListCells.areaListCell());
        areaFilter.setButtonCell(ProductosListCells.areaListCell());
        areaFilter.valueProperty().addListener((obs, o, n) -> { currentPage = 0; applyFilters(); });

        resguardanteFilter.setConverter(new javafx.util.StringConverter<>() {
            public String toString(String r) { return r == null ? "Todos los resguardantes" : r; }
            public String fromString(String s) { return null; }
        });
        resguardanteFilter.valueProperty().addListener((obs, o, n) -> { currentPage = 0; applyFilters(); });

        String pendingArea = NavigationContext.consumePendingAreaFilter();
        if (pendingArea != null && areaFilter.getItems().contains(pendingArea)) {
            areaFilter.setValue(pendingArea);
        }
        if (NavigationContext.consumePendingNuevoBien()) {
            Platform.runLater(this::onNuevoBien);
        }
    }

    // ── Data loading ─────────────────────────────────────────────────────────

    private void loadData() {
        if (!loading.compareAndSet(false, true)) { refreshing = true; return; }
        if (dataLoader == null) dataLoader = new ProductosDataLoader(productoService);
        tableManager.showSkeletonPlaceholder();
        spinner.setVisible(true); spinner.setManaged(true);

        ProductosFilterState snap = snapshotFilters();
        dataLoader.loadData(
            snap.busqueda(), snap.catId(), snap.area(), snap.resguardante(),
            snap.estado(), snap.soloSinEtiquetar(),
            snap.pageSize(), snap.offset(),
            snap.desdeReg(), snap.hastaReg(),
            result -> {
                loading.set(false);
                if (tableManager.skeletonPulse != null) { tableManager.skeletonPulse.stop(); tableManager.skeletonPulse = null; }
                table.setPlaceholder(emptyStatePlaceholder);
                refreshResguardanteOptions(result.resguardantes());
                totalFiltered = result.count();
                filteredData.setAll(result.pageData());
                updateTablePage();
                chipsManager.refresh(snap.busqueda(), snap.catId(), snap.area(), snap.resguardante(),
                    snap.estado(), snap.desdeReg(), snap.hastaReg());
                updateStats(result.stats());
                spinner.setVisible(false); spinner.setManaged(false);
                if (refreshing) { NotificacionUtil.info(table.getScene(), "Lista actualizada"); refreshing = false; }
                String pendingId = NavigationContext.consumePendingProductId();
                if (pendingId != null) {
                    final String id = pendingId;
                    Platform.runLater(() -> {
                        for (int i = 0; i < filteredData.size(); i++) {
                            if (id.equals(filteredData.get(i).getId())) {
                                table.getSelectionModel().clearAndSelect(i);
                                table.scrollTo(i);
                                table.requestFocus();
                                break;
                            }
                        }
                    });
                }
            },
            ex -> {
                loading.set(false);
                if (tableManager.skeletonPulse != null) { tableManager.skeletonPulse.stop(); tableManager.skeletonPulse = null; }
                table.setPlaceholder(emptyStatePlaceholder);
                spinner.setVisible(false); spinner.setManaged(false);
                NotificacionUtil.errorConAccion(table.getScene(),
                    "No se pudo cargar los bienes. Verifica la conexión.", "Reintentar", () -> loadData());
            });
    }

    /** Loads a single page in the background using current filter state. */
    private void loadPage() {
        if (!loading.compareAndSet(false, true)) { refreshing = true; return; }
        if (dataLoader == null) dataLoader = new ProductosDataLoader(productoService);
        spinner.setVisible(true); spinner.setManaged(true);

        ProductosFilterState snap = snapshotFilters();
        STICKY.put("search", searchField.getText() != null ? searchField.getText() : "");
        STICKY.put("area", snap.area() != null ? snap.area() : "");

        dataLoader.loadPage(
            snap.busqueda(), snap.catId(), snap.area(), snap.resguardante(),
            snap.estado(), snap.soloSinEtiquetar(),
            snap.pageSize(), snap.offset(),
            snap.desdeReg(), snap.hastaReg(),
            result -> {
                loading.set(false);
                totalFiltered = result.count();
                filteredData.setAll(result.page());
                updateTablePage();
                chipsManager.refresh(snap.busqueda(), snap.catId(), snap.area(), snap.resguardante(),
                    snap.estado(), snap.desdeReg(), snap.hastaReg());
                spinner.setVisible(false); spinner.setManaged(false);
                if (refreshing) { NotificacionUtil.info(table.getScene(), "Lista actualizada"); refreshing = false; }
            },
            ex -> {
                loading.set(false);
                spinner.setVisible(false); spinner.setManaged(false);
                NotificacionUtil.errorConAccion(table.getScene(),
                    "No se pudo cargar los bienes. Verifica la conexión.", "Reintentar", () -> loadPage());
            });
    }

    private void refreshResguardanteOptions(List<String> options) {
        String current = resguardanteFilter.getValue();
        resguardanteFilter.getItems().setAll(new java.util.ArrayList<>());
        resguardanteFilter.getItems().add(null);
        resguardanteFilter.getItems().addAll(options);
        if (current != null && resguardanteFilter.getItems().contains(current))
            resguardanteFilter.setValue(current);
    }

    private void updateStats(ProductoRepository.InventarioStats stats) {
        if (lblStatTotal   != null) AnimationUtils.animateCount(lblStatTotal,   stats.total(), 700);
        if (lblStatValor   != null) AnimationUtils.animateCount(lblStatValor,   stats.valorTotal().longValue(), 880,
            v -> FormatUtils.formatCurrency(BigDecimal.valueOf(v)));
        if (lblStatAlertas != null) AnimationUtils.animateCount(lblStatAlertas, stats.alertas(), 580);
        if (lblStatSinEtiquetar != null) AnimationUtils.animateCount(lblStatSinEtiquetar, stats.sinEtiquetar(), 600);

        // Pop the stat cards once their numbers finish counting
        javafx.animation.PauseTransition pop = new javafx.animation.PauseTransition(javafx.util.Duration.millis(900));
        pop.setOnFinished(e -> {
            if (statCardTotal != null) AnimationUtils.statCardPop(statCardTotal);
            if (statCardValor != null) AnimationUtils.statCardPop(statCardValor);
        });
        pop.play();
    }

    private String getSelectedEstado() {
        if (estadoChipGroup == null) return "Todos";
        Toggle t = estadoChipGroup.getSelectedToggle();
        return t == null ? "Todos" : ((ToggleButton) t).getText();
    }

    /** Snapshots all filter form values on the FX thread — safe to pass to a background Task. */
    private ProductosFilterState snapshotFilters() {
        String busqueda = searchField.getText() != null ? searchField.getText().toLowerCase().strip() : "";
        String catId = categoriaFilter.getValue() != null ? categoriaFilter.getValue().getId() : null;
        String area  = areaFilter.getValue() != null ? areaFilter.getValue() : null;
        String resguardante = resguardanteFilter.getValue();
        EstadoProducto estado = ProductosChipsManager.parseEstado(getSelectedEstado());
        LocalDate desdeReg = desdeRegFilter != null ? desdeRegFilter.getValue() : null;
        LocalDate hastaReg = hastaRegFilter != null ? hastaRegFilter.getValue() : null;
        return new ProductosFilterState(busqueda, catId, area, resguardante, estado,
                filterSinEtiquetar, desdeReg, hastaReg, currentPage, pageSize);
    }

    private void applyFilters() {
        currentPage = 0;
        loadPage();
    }

    private void updateTablePage() {
        tableManager.updateTablePage(filteredData, currentPage, pageSize, totalFiltered);
    }

    // ── FXML action handlers ─────────────────────────────────────────────────

    @FXML private void onPrev() { if (currentPage > 0) { currentPage--; loadPage(); } }
    @FXML private void onNext() { currentPage++; loadPage(); }
    @FXML private void onRefresh() { refreshing = true; loadData(); }

    @FXML
    private void onConteoFisico() {
        // Scoped to whatever is currently filtered/visible in the table
        // (búsqueda, categoría, área, estado) instead of always dumping the
        // entire inventory in — the dialog builds one non-virtualized row
        // per bien, so "todo el inventario" on a large municipio would be
        // both slow to open and impossible to actually work through in one
        // sitting. Filtering by área/categoría first is how you scope a
        // conteo to a batch — see the "?" on the button.
        // Read filter values on the FX thread before spawning the background task
        String busqueda = searchField.getText().toLowerCase().strip();
        String catId = categoriaFilter.getValue() != null
            ? categoriaFilter.getValue().getId() : null;
        String area = areaFilter.getValue() != null ? areaFilter.getValue() : null;
        String resguardante = resguardanteFilter.getValue();
        EstadoProducto estado = ProductosChipsManager.parseEstado(getSelectedEstado());

        Task<List<Producto>> task = new Task<>() {
            @Override protected List<Producto> call() throws Exception {
                return productoService.getAllFiltrado(busqueda, catId, area, resguardante, estado);
            }
            @Override protected void succeeded() {
                List<Producto> aContar = getValue();
                if (aContar.isEmpty()) {
                    NotificacionUtil.advertencia(table.getScene(),
                        "No hay bienes que coincidan con el filtro actual — ajusta la búsqueda/filtros antes de iniciar un conteo.");
                    return;
                }
                if (aContar.size() > 150 && !ConfirmacionUtil.confirmar("Conteo grande",
                        "Vas a iniciar un conteo físico de " + aContar.size() + " bienes a la vez — puede ser lento de "
                        + "cargar y difícil de terminar en una sola sesión. Considera filtrar por área o categoría primero "
                        + "para hacerlo en lotes más manejables.\n\n¿Continuar de todas formas con los " + aContar.size() + "?"))
                    return;
                ConteoFisicoDialog.show(aContar, movimientoService, () -> { refreshing = true; loadData(); });
            }
            @Override protected void failed() {
                NotificacionUtil.error(table.getScene(), "No se pudo cargar los bienes para el conteo físico");
            }
        };
        AppExecutor.submit(task);
    }

    @FXML
    private void onResguardoArea() {
        String area = areaFilter.getValue();
        AreaResguardosDialog.show(area, table.getScene());
    }

    @FXML
    private void onVerBajas() {
        DialogUtil.runAsync(
            () -> productoService.getAllIncludingBaja().stream().filter(Producto::isDadoDeBaja).toList(),
            bajas -> ProductoBajasDialog.show(bajas, productoService, movimientoService, log, () -> { refreshing = true; loadData(); }),
            e -> NotificacionUtil.error(table.getScene(), "No se pudo cargar la lista de bienes dados de baja")
        );
    }

    @FXML
    private void onClearFilters() {
        searchField.clear();
        categoriaFilter.setValue(null);
        areaFilter.setValue(null);
        resguardanteFilter.setValue(null);
        if (desdeRegFilter != null) desdeRegFilter.setValue(null);
        if (hastaRegFilter != null) hastaRegFilter.setValue(null);
        if (estadoChipGroup != null)
            estadoChipGroup.getToggles().stream()
                .filter(t -> "Todos".equals(((ToggleButton) t).getText()))
                .findFirst().ifPresent(t -> t.setSelected(true));
        if (filterSinEtiquetar) {
            filterSinEtiquetar = false;
            if (cardSinEtiquetar != null)
                cardSinEtiquetar.getStyleClass().remove("rich-stat-card-alert-active");
        }
        currentPage = 0;
        applyFilters();
    }

    @FXML
    private void onFiltroFechaReg() { currentPage = 0; loadPage(); }

    @FXML
    private void onLimpiarFechaReg() {
        if (desdeRegFilter != null) desdeRegFilter.setValue(null);
        if (hastaRegFilter != null) hastaRegFilter.setValue(null);
        currentPage = 0; loadPage();
    }

    @FXML
    private void onNuevoBien() {
        showProductDialog(null);
    }

    @FXML
    private void onImportarCsv() {
        if (!canEdit) {
            NotificacionUtil.advertencia(table.getScene(), "No tienes permiso para importar bienes");
            return;
        }
        javafx.scene.Scene scene = table.getScene();
        DialogUtil.runAsyncWithProgress(scene, "Cargando categorías…",
            categoriaService::findAll,
            cats -> ImportacionBienesDialog.show(scene, cats, productoService, () -> { refreshing = true; loadData(); }),
            e -> NotificacionUtil.error(scene, "No se pudieron cargar las categorías"));
    }

    /** Jumps to Movimientos with the selected bien pre-filled in "Nuevo
     *  Movimiento" — lets the user skip searching for it again in that
     *  dialog's product picker (same shortcut Alertas already uses to
     *  register an entrada for a stock-out item). */
    @FXML
    private void onNuevoMovimiento() {
        Producto sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) {
            NotificacionUtil.advertencia(table.getScene(), "Selecciona un bien para registrar un movimiento");
            return;
        }
        try {
            MainController main = MainController.getInstance();
            main.navigateTo("movimientos");
            if (main.getCurrentController() instanceof MovimientosController ctrl) {
                ctrl.showMovimientoDialog(sel.getId(), null);
            }
        } catch (Exception e) {
            log.error("Error al abrir el formulario de movimiento desde Bienes", e);
            NotificacionUtil.error(table.getScene(), "No se pudo abrir el formulario de movimiento");
        }
    }

    @FXML
    private void onEdit() {
        Producto sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) {
            NotificacionUtil.advertencia(table.getScene(), "Selecciona un bien para editar");
            return;
        }
        showProductDialog(sel);
    }

    @FXML
    private void onDelete() {
        Producto seleccionado = table.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            NotificacionUtil.advertencia(table.getScene(), "Selecciona un bien para dar de baja");
            return;
        }
        // Baja patrimonial (soft-delete): record + audit trail stay in DB,
        // the bien just stops appearing in active inventory.
        ProductoBajasDialog.BajaResult resultado =
            ProductoBajasDialog.showBajaInputDialog(seleccionado.getNombre()).orElse(null);
        if (resultado == null) return;

        String nombre = seleccionado.getNombre();
        String idBaja = seleccionado.getId();
        final ProductoBajasDialog.BajaResult r = resultado;
        final Producto sel = seleccionado;
        Runnable doDelete = () -> DialogUtil.runAsync(
            () -> {
                if (r.tipoDestino() != null)
                    productoService.darDeBaja(idBaja, r.motivo(), r.tipoDestino(), r.dictamen(), r.numeroActa(), r.fechaDictamen());
                else
                    productoService.darDeBaja(idBaja, r.motivo());
            },
            () -> {
                loadData();
                NotificacionUtil.exitoConAccionCountdown(table.getScene(),
                    "Bien \"" + nombre + "\" dado de baja", "Deshacer",
                    () -> undoBaja(idBaja, nombre));
                populateBajaFields(sel, r);
                exportActaBaja(sel);
            },
            e -> NotificacionUtil.error(table.getScene(),
                e instanceof ProductoService.ValidationException ? e.getMessage() : "No se pudo dar de baja el bien")
        );
        javafx.scene.Node rowNode = table.lookup(".table-row-cell:selected");
        if (rowNode != null) {
            AnimationUtils.flashClass(rowNode, "row-warning", 200);
            AnimationUtils.fadeOut(rowNode, 260, doDelete);
        } else {
            doDelete.run();
        }
    }

    private void undoBaja(String idBaja, String nombre) {
        DialogUtil.runAsync(
            () -> productoService.reactivar(idBaja),
            () -> { loadData(); NotificacionUtil.info(table.getScene(), "\"" + nombre + "\" reactivado al inventario"); },
            e  -> NotificacionUtil.error(table.getScene(), "No se pudo deshacer la baja")
        );
    }

    private void exportActaBaja(Producto sel) {
        DialogUtil.runAsync(
            () -> reporteService.exportActaBaja(sel),
            file -> DialogUtil.showExportResultDialog(table.getScene(), file),
            ex   -> log.warn("No se pudo generar el acta de baja", ex)
        );
    }

    private void populateBajaFields(Producto sel, ProductoBajasDialog.BajaResult r) {
        // Pre-fill baja fields from dialog result so the acta PDF is generated
        // immediately without a second DB round-trip.
        sel.setFechaBaja(LocalDate.now());
        sel.setMotivoBaja(r.motivo());
        sel.setTipoDestinoBaja(r.tipoDestino());
        sel.setDictamenBaja(r.dictamen());
        sel.setNumeroActaBaja(r.numeroActa());
        sel.setFechaDictamen(r.fechaDictamen());
    }


    @FXML
    private void onExportCsv() {
        ProductosExporter.exportCsv(table.getScene(), reporteService, this::onExportCsv);
    }

    @FXML
    private void onExportExcel() {
        ProductosExporter.exportExcel(table.getScene(), reporteService, this::onExportExcel);
    }

    @FXML
    private void onExportSeleccionCsv() {
        ProductosExporter.exportSeleccionCsv(table.getScene(),
            getSelectedProductos(), reporteService, this::onExportSeleccionCsv);
    }

    @FXML
    private void onExportSeleccionExcel() {
        ProductosExporter.exportSeleccionExcel(table.getScene(),
            getSelectedProductos(), reporteService, this::onExportSeleccionExcel);
    }

    // ── Bulk actions ─────────────────────────────────────────────────────────

    private List<Producto> getSelectedProductos() {
        return List.copyOf(table.getSelectionModel().getSelectedItems());
    }

    private void runBulkIfValid(java.util.function.Consumer<List<Producto>> action) {
        List<Producto> sel = getSelectedProductos();
        if (sel.size() < 2 || !canEdit) return;
        action.accept(sel);
    }

    @FXML
    private void onBulkCambiarArea() {
        runBulkIfValid(sel -> ProductosBulkDialog.showCambiarArea(
            sel, table.getScene(), movimientoService, table,
            () -> { refreshing = true; loadData(); }, this::onBulkCambiarArea));
    }

    @FXML
    private void onBulkCambiarResguardante() {
        runBulkIfValid(sel -> ProductosBulkDialog.showCambiarResguardante(
            sel, table.getScene(), productoService, table,
            () -> { refreshing = true; loadData(); }, this::onBulkCambiarResguardante));
    }

    @FXML
    private void onBulkMarcarEtiquetado() {
        runBulkIfValid(sel -> ProductosBulkDialog.showMarcarEtiquetado(
            sel, table.getScene(), productoService, table,
            () -> { refreshing = true; loadData(); }, this::onBulkMarcarEtiquetado));
    }

    private void onCardSinEtiquetar() {
        filterSinEtiquetar = !filterSinEtiquetar;
        if (cardSinEtiquetar != null) {
            if (filterSinEtiquetar) cardSinEtiquetar.getStyleClass().add("rich-stat-card-alert-active");
            else                    cardSinEtiquetar.getStyleClass().remove("rich-stat-card-alert-active");
        }
        currentPage = 0;
        applyFilters();
    }

    @FXML
    private void onComparar() {
        List<Producto> sel = getSelectedProductos();
        if (sel.size() != 2) {
            NotificacionUtil.advertencia(table.getScene(), "Selecciona exactamente 2 bienes para comparar");
            return;
        }
        ComparacionBienesDialog.show(sel.get(0), sel.get(1), table.getScene());
    }

    @FXML
    private void onBulkResguardoPdf() {
        ProductosExporter.exportResguardoPdf(table.getScene(),
            new java.util.ArrayList<>(table.getSelectionModel().getSelectedItems()), reporteService);
    }

    @FXML
    private void onImprimirQr() {
        Producto sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        ProductosExporter.showQrDialog(table.getScene(), sel, log);
    }

    @FXML
    private void onDeseleccionar() {
        table.getSelectionModel().clearSelection();
    }

    @FXML
    private void onBulkEtiquetasQr() {
        List<Producto> sel = getSelectedProductos();
        if (sel.isEmpty()) return;
        exportarEtiquetasQr(sel);
    }

    private void exportarEtiquetasQr(List<Producto> productos) {
        ProductosExporter.exportEtiquetasQr(table.getScene(), productos, reporteService);
    }

    @FXML
    private void onBulkEtiquetaFisica() {
        List<Producto> sel = getSelectedProductos();
        if (sel.isEmpty()) sel = List.copyOf(table.getItems());
        exportarEtiquetaFisica(sel);
    }

    private void exportarEtiquetaFisica(List<Producto> productos) {
        ProductosExporter.exportEtiquetaFisica(table.getScene(), productos, reporteService);
    }

    // ── Filter presets ───────────────────────────────────────────────────────

    @FXML
    private void onGuardarPreset() {
        Dialog<ButtonType> dlg = new Dialog<>();
        DialogUtil.applyOwner(dlg);
        dlg.setTitle("Guardar filtro");
        ButtonType okType = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        HBox header = DialogUtil.gradientHeader("mdi2b-bookmark-plus-outline",
            "Guardar filtro", "Acceso rápido a los filtros activos",
            AppColors.INDIGO, AppColors.PRIMARY);

        TextField tf = new TextField();
        tf.setPromptText("Nombre del acceso rápido…");
        Label lbl = new Label("Nombre:");
        lbl.getStyleClass().add("field-label");
        VBox form = new VBox(6, lbl, tf);
        form.setPadding(new Insets(16));

        dlg.getDialogPane().setContent(new VBox(0, header, form));
        dlg.getDialogPane().setPrefWidth(400);
        DialogUtil.applyStylesheet(dlg.getDialogPane());

        Button btnOk = (Button) dlg.getDialogPane().lookupButton(okType);
        btnOk.getStyleClass().add("btn-primary");
        btnOk.setDisable(true);
        tf.textProperty().addListener((obs, o, n) -> btnOk.setDisable(n.isBlank()));
        Platform.runLater(tf::requestFocus);

        dlg.showAndWait()
           .filter(bt -> bt == okType)
           .map(bt -> tf.getText().trim())
           .filter(n -> !n.isBlank())
           .ifPresent(name -> presetPanel.saveCurrentAs(name, table.getScene()));
    }

    private void saveStockThresholds(Producto p) {
        DialogUtil.runAsync((DialogUtil.CheckedRunnable) () -> productoService.save(p),
            () -> NotificacionUtil.info(table.getScene(), "Umbrales de stock actualizados"),
            ex -> {
                NotificacionUtil.error(table.getScene(), "No se pudieron guardar los umbrales de stock — " + ex.getMessage());
                loadPage();
            });
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private void showProductDetail(Producto p) {
        ProductoDetailDialog.show(p, table.getScene(), movimientoService, log);
    }

    private void showMovimientoTimeline(Producto p) {
        MovimientoTimelineDialog.show(p, table.getScene(), movimientoService);
    }

    private record CategoriasYFotos(List<Categoria> cats, List<String> fotos) {}

    private void showProductDialog(Producto existing) {
        javafx.scene.Scene scene = table.getScene();
        DialogUtil.runAsyncWithProgress(scene, "Cargando categorías…",
            () -> {
                List<Categoria> cats = categoriaService.findAll();
                List<String> existingFotos;
                if (existing != null) {
                    try { existingFotos = productoService.getFotosByProductoId(existing.getId()); }
                    catch (Exception e) { existingFotos = new java.util.ArrayList<>(); }
                } else {
                    existingFotos = new java.util.ArrayList<>();
                }
                return new CategoriasYFotos(cats, existingFotos);
            },
            r -> openProductDialog(existing, r.cats(), r.fotos()),
            e -> {
                log.error("Error al abrir el formulario de bien", e);
                NotificacionUtil.error(scene, "Error al abrir el formulario. Verifica la conexión a la base de datos.");
            });
    }

    private void openProductDialog(Producto existing, List<Categoria> cats, List<String> existingFotos) {
        try {
            Optional<Producto> result = ProductoDialogFactory.show(existing, cats, THUMBNAIL_CACHE, log, existingFotos);
            boolean isNew = existing == null;
            result.ifPresent(p -> DialogUtil.runAsync(
                () -> {
                    Producto saved = productoService.save(p);
                    productoService.saveFotos(saved.getId(), p.getFotosUrls());
                    return saved;
                },
                saved -> {
                    if (isNew) {
                        NotificacionUtil.exito(table.getScene(), "Bien registrado exitosamente");
                    } else {
                        NotificacionUtil.exito(table.getScene(), "Bien actualizado correctamente");
                    }
                    pendingHighlightId = saved.getId();
                    loadData();
                    Platform.runLater(() -> {
                        table.getSelectionModel().select(saved);
                        int idx = table.getSelectionModel().getSelectedIndex();
                        if (idx >= 0) table.scrollTo(idx);
                        table.requestFocus();
                    });
                    new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                        javafx.util.Duration.seconds(1.8), e2 -> {
                            pendingHighlightId = null;
                            table.refresh();
                        })).play();
                },
                e -> {
                    if (e instanceof ProductoService.ModificadoPorOtroException) {
                        // Reopening with the same object would fail the same way.
                        NotificacionUtil.advertencia(table.getScene(), e.getMessage());
                        loadData();
                        return;
                    }
                    // Reopen pre-filled with what the user typed — same cats already
                    // loaded, no extra DB round-trip on a failed save.
                    NotificacionUtil.error(table.getScene(),
                        (e instanceof ProductoService.ValidationException ? e.getMessage() : "No se pudo guardar el bien")
                            + " — revisa los datos e inténtalo de nuevo");
                    Platform.runLater(() -> openProductDialog(p, cats, existingFotos));
                }
            ));

        } catch (Exception e) {
            log.error("Error al abrir el formulario de bien", e);
            NotificacionUtil.error(table.getScene(), "Error al abrir el formulario. Verifica la conexión a la base de datos.");
        }
    }

    /**
     * Sets the search field to {@code codigo} and triggers a search.
     * Called by {@link MainController#handleBarcodeScan(String)} after the
     * barcode scanner fires and the Productos screen is already visible.
     */
    public void buscarPorCodigo(String codigo) {
        if (searchField == null || codigo == null) return;
        searchField.setText(codigo);
        currentPage = 0;
        applyFilters();
    }

}
