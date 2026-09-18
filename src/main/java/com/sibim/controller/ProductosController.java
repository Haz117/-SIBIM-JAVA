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
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppColors;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import org.kordamp.ikonli.javafx.FontIcon;
import com.sibim.util.NotificacionUtil;
import com.sibim.util.PaginationUtils;
import com.sibim.util.SearchUtils;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.util.Duration;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
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

    private static final Map<String, String> CAT_ICON = Map.of(
        "Mobiliario",                "mdi2s-sofa-outline",
        "Vehículos",                 "mdi2c-car-outline",
        "Equipo de Cómputo",         "mdi2l-laptop",
        "Equipo de Oficina",         "mdi2p-printer",
        "Herramientas y Maquinaria", "mdi2w-wrench-outline",
        "Equipo Audiovisual",        "mdi2c-camera-outline"
    );

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
    @FXML private javafx.scene.control.DatePicker desdeRegFilter;
    @FXML private javafx.scene.control.DatePicker hastaRegFilter;

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
    private javafx.animation.Timeline skeletonPulse;

    @FXML
    public void initialize() {
        canEdit = SessionManager.isAdmin() || SessionManager.isSecretario();
        if (btnToggleFiltros != null && filterBar != null)
            DialogUtil.makeCollapsible("bienes.filtros.colapsado", btnToggleFiltros, filterBar);
        if (btnToggleResumen != null && resumenBox != null)
            DialogUtil.makeCollapsible("bienes.resumen.colapsado", btnToggleResumen, resumenBox,
                "Mostrar resumen", "Ocultar resumen");
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
        bulkBarManager = new ProductosBulkBar(bulkBar, lblBulkCount, btnBulkArea,
            btnBulkResguardante, btnBulkMarcarEtiquetado, btnComparar, () -> canEdit);
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
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        ProductosColumnSetup.configureFoto(colFoto, table, THUMBNAIL_CACHE, log);
        ProductosColumnSetup.configureNombre(colNombre);
        ProductosColumnSetup.configureCodigo(colCodigo);
        ProductosColumnSetup.configureCategoria(colCategoria, CAT_ICON);
        ProductosColumnSetup.configureArea(colArea);
        ProductosColumnSetup.configureStockYValor(colStock, colValor);
        ProductosColumnSetup.configureStockMinMax(colStockMin, colStockMax, this::saveStockThresholds);
        ProductosColumnSetup.configureEstado(colEstado);
        ProductosColumnSetup.configureRowFactory(table, () -> pendingHighlightId);
        setupTableListeners();
        table.setContextMenu(ProductosContextMenu.build(
            table, canEdit, reporteService, movimientoService, log,
            this::showProductDetail, this::showMovimientoTimeline, this::exportarEtiquetasQr,
            this::exportarEtiquetaFisica, this::onEdit, this::onDelete));
        setupEmptyState();
        setupPagination();
        // Clic derecho en encabezado → toggle columnas secundarias
        DialogUtil.setupColumnVisibilityMenu("bienes.cols", table,
            List.of(colFoto, colNombre, colStock, colEstado));
    }

    private void setupTableListeners() {
        // Ctrl/Shift-click to pick several rows for "Exportar seleccionados" —
        // Editar/Dar de baja stay single-item actions (see the listener below).
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Selection → enable/disable action buttons
        table.getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener<Producto>) c -> {
            int n = table.getSelectionModel().getSelectedItems().size();
            if (btnMovimiento != null && canEdit) btnMovimiento.setDisable(n != 1);
            if (btnQr        != null) btnQr.setDisable(n != 1);
            if (btnEditar   != null && canEdit) btnEditar.setDisable(n != 1);
            if (btnEliminar != null && canEdit) btnEliminar.setDisable(n != 1);
            if (btnExportarSeleccion != null) btnExportarSeleccion.setDisable(n == 0);
            updateSelectionLabel(lblSeleccionados, n);
            bulkBarManager.update(n);
        });
        if (btnMovimiento != null && canEdit) btnMovimiento.setDisable(true);
        if (btnQr        != null) btnQr.setDisable(true);
        if (btnEditar   != null && canEdit) btnEditar.setDisable(true);
        if (btnEliminar != null && canEdit) btnEliminar.setDisable(true);
        if (btnExportarSeleccion != null) btnExportarSeleccion.setDisable(true);

        // JavaFX doesn't show tooltips on disabled nodes by default —
        // Tooltip.install() uses a separate mechanism that works regardless.
        if (btnEditar        != null && canEdit) Tooltip.install(btnEditar,        new Tooltip("Selecciona un bien para editarlo"));
        if (btnEliminar      != null && canEdit) Tooltip.install(btnEliminar,      new Tooltip("Selecciona un bien para darlo de baja"));
        if (btnMovimiento    != null && canEdit) Tooltip.install(btnMovimiento,    new Tooltip("Selecciona un bien para registrar un movimiento"));
        if (btnExportarSeleccion != null)        Tooltip.install(btnExportarSeleccion, new Tooltip("Selecciona uno o más bienes para exportarlos"));

        // Delete key on table — only when exactly one row is selected, same
        // as the "Dar de baja" button (a formal baja needs a motivo per bien,
        // it doesn't make sense as a bulk action from a bare Delete keypress).
        table.setOnKeyPressed(ev -> {
            if (ev.getCode() == javafx.scene.input.KeyCode.DELETE
                    && canEdit && table.getSelectionModel().getSelectedItems().size() == 1) {
                onDelete(); ev.consume();
            } else if (ev.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                table.getSelectionModel().clearSelection(); ev.consume();
            }
        });

        // Double-click: edit if allowed, otherwise show detail
        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) {
                if (canEdit) onEdit();
                else showProductDetail(table.getSelectionModel().getSelectedItem());
            }
        });
    }

    private void setupEmptyState() {
        // Smart empty state (set programmatically so we can update the message)
        FontIcon emptyIcon = new FontIcon("mdi2p-package-variant");
        emptyIcon.setIconSize(52);
        emptyIcon.getStyleClass().add("empty-icon-lg");
        emptyStateMsg = new Label("No hay bienes registrados en el sistema");
        emptyStateMsg.getStyleClass().add("empty-state-msg");
        btnEmptyLimpiar = new Button("Limpiar filtros");
        btnEmptyLimpiar.getStyleClass().add("btn-secondary");
        btnEmptyLimpiar.setOnAction(e -> onClearFilters());
        btnEmptyLimpiar.setVisible(false); btnEmptyLimpiar.setManaged(false);
        emptyStateHint = new Label(canEdit ? "Presiona Ctrl+N para agregar el primer bien" : "");
        emptyStateHint.getStyleClass().add("empty-state-hint");
        VBox emptyState = new VBox(12, emptyIcon, emptyStateMsg, btnEmptyLimpiar, emptyStateHint);
        emptyState.setAlignment(Pos.CENTER);
        emptyState.getStyleClass().add("empty-state-pane");
        emptyState.setMaxWidth(380);
        emptyState.setPadding(new Insets(32, 24, 32, 24));
        // Spring-in when placeholder becomes visible; reset transforms when hidden
        emptyState.visibleProperty().addListener((obs, wasVisible, isVisible) -> {
            if (isVisible && !wasVisible) AnimationUtils.springIn(emptyState);
            else if (!isVisible) { emptyState.setOpacity(1); emptyState.setScaleX(1); emptyState.setScaleY(1); }
        });
        emptyStatePlaceholder = emptyState;
        table.setPlaceholder(emptyState);
    }

    private VBox buildSkeletonPlaceholder() {
        VBox box = new VBox(4);
        box.setPadding(new Insets(8));
        for (int i = 0; i < 7; i++) {
            Label bar = new Label();
            bar.getStyleClass().add("skeleton");
            bar.setPrefHeight(44);
            bar.setMaxWidth(Double.MAX_VALUE);
            box.getChildren().add(bar);
        }
        skeletonPulse = new Timeline(
            new KeyFrame(Duration.millis(0),   new KeyValue(box.opacityProperty(), 0.7)),
            new KeyFrame(Duration.millis(800),  new KeyValue(box.opacityProperty(), 0.4)),
            new KeyFrame(Duration.millis(1600), new KeyValue(box.opacityProperty(), 0.7))
        );
        skeletonPulse.setCycleCount(Timeline.INDEFINITE);
        skeletonPulse.play();
        return box;
    }

    private void setupPagination() {
        pageSizeBox.setItems(FXCollections.observableArrayList(25, 50, 100, 250, 500, Integer.MAX_VALUE));
        pageSizeBox.setConverter(new javafx.util.StringConverter<>() {
            public String toString(Integer n)   { return n == null ? "" : n == Integer.MAX_VALUE ? "Todos" : String.valueOf(n); }
            public Integer fromString(String s) { return "Todos".equals(s) ? Integer.MAX_VALUE : Integer.parseInt(s); }
        });
        pageSizeBox.setValue(25);
        pageSizeBox.setOnAction(e -> {
            pageSize = pageSizeBox.getValue();
            currentPage = 0;
            loadPage();
        });
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
        if (!loading.compareAndSet(false, true)) {
            // Ya hay una carga en curso — sólo marcamos refreshing para que
            // la tarea activa muestre el toast "Lista actualizada" al terminar.
            refreshing = true;
            return;
        }
        table.setPlaceholder(buildSkeletonPlaceholder());
        spinner.setVisible(true); spinner.setManaged(true);

        ProductosFilterState snap = snapshotFilters();
        String busqueda     = snap.busqueda();
        String catId        = snap.catId();
        String area         = snap.area();
        String resguardante = snap.resguardante();
        EstadoProducto estado = snap.estado();
        java.time.LocalDate desdeReg = snap.desdeReg();
        java.time.LocalDate hastaReg = snap.hastaReg();

        Task<Void> task = new Task<>() {
            private List<Producto> pageData;
            private int count;
            private com.sibim.repository.ProductoRepository.InventarioStats stats;
            private List<String> resguardantes;

            @Override protected Void call() throws Exception {
                resguardantes = productoService.getResguardantes();
                count = productoService.countFiltrado(busqueda, catId, area, resguardante, estado, filterSinEtiquetar, desdeReg, hastaReg);
                pageData = productoService.getPaginated(busqueda, catId, area, resguardante, estado,
                    filterSinEtiquetar, pageSize, currentPage * pageSize, desdeReg, hastaReg);
                stats = productoService.getStats();
                return null;
            }

            @Override protected void succeeded() {
                loading.set(false);
                if (skeletonPulse != null) { skeletonPulse.stop(); skeletonPulse = null; }
                table.setPlaceholder(emptyStatePlaceholder);
                refreshResguardanteOptions(resguardantes);
                totalFiltered = count;
                filteredData.setAll(pageData);
                updateTablePage();
                chipsManager.refresh(busqueda, catId, area, resguardante, estado, desdeReg, hastaReg);
                updateStats(stats);
                spinner.setVisible(false); spinner.setManaged(false);
                if (refreshing) { NotificacionUtil.info(table.getScene(), "Lista actualizada"); refreshing = false; }
                // Highlight a product selected via the search palette (Ctrl+K)
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
            }

            @Override protected void failed() {
                loading.set(false);
                if (skeletonPulse != null) { skeletonPulse.stop(); skeletonPulse = null; }
                table.setPlaceholder(emptyStatePlaceholder);
                spinner.setVisible(false); spinner.setManaged(false);
                NotificacionUtil.errorConAccion(table.getScene(),
                    "No se pudo cargar los bienes. Verifica la conexión.", "Reintentar", () -> loadData());
            }
        };
        com.sibim.util.AppExecutor.submit(task);
    }

    /** Loads a single page in the background using current filter state. */
    private void loadPage() {
        if (!loading.compareAndSet(false, true)) { refreshing = true; return; }
        spinner.setVisible(true); spinner.setManaged(true);

        ProductosFilterState snap = snapshotFilters();
        String busqueda     = snap.busqueda();
        String catId        = snap.catId();
        String area         = snap.area();
        String resguardante = snap.resguardante();
        EstadoProducto estado = snap.estado();
        java.time.LocalDate desdeReg = snap.desdeReg();
        java.time.LocalDate hastaReg = snap.hastaReg();
        int offset = snap.offset();
        // Persist sticky filters
        STICKY.put("search", searchField.getText() != null ? searchField.getText() : "");
        STICKY.put("area", area != null ? area : "");

        Task<Void> task = new Task<>() {
            List<Producto> page;
            int count;

            @Override protected Void call() throws Exception {
                page = productoService.getPaginated(busqueda, catId, area, resguardante, estado, filterSinEtiquetar, pageSize, offset, desdeReg, hastaReg);
                count = productoService.countFiltrado(busqueda, catId, area, resguardante, estado, filterSinEtiquetar, desdeReg, hastaReg);
                return null;
            }

            @Override protected void succeeded() {
                loading.set(false);
                totalFiltered = count;
                filteredData.setAll(page);
                updateTablePage();
                chipsManager.refresh(busqueda, catId, area, resguardante, estado, desdeReg, hastaReg);
                spinner.setVisible(false); spinner.setManaged(false);
                if (refreshing) { NotificacionUtil.info(table.getScene(), "Lista actualizada"); refreshing = false; }
            }

            @Override protected void failed() {
                loading.set(false);
                spinner.setVisible(false); spinner.setManaged(false);
                NotificacionUtil.errorConAccion(table.getScene(),
                    "No se pudo cargar los bienes. Verifica la conexión.", "Reintentar", () -> loadPage());
            }
        };
        com.sibim.util.AppExecutor.submit(task);
    }

    private void refreshResguardanteOptions(List<String> options) {
        String current = resguardanteFilter.getValue();
        resguardanteFilter.getItems().setAll(new java.util.ArrayList<>());
        resguardanteFilter.getItems().add(null);
        resguardanteFilter.getItems().addAll(options);
        if (current != null && resguardanteFilter.getItems().contains(current))
            resguardanteFilter.setValue(current);
    }

    private void updateStats(com.sibim.repository.ProductoRepository.InventarioStats stats) {
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
        java.time.LocalDate desdeReg = desdeRegFilter != null ? desdeRegFilter.getValue() : null;
        java.time.LocalDate hastaReg = hastaRegFilter != null ? hastaRegFilter.getValue() : null;
        return new ProductosFilterState(busqueda, catId, area, resguardante, estado,
                filterSinEtiquetar, desdeReg, hastaReg, currentPage, pageSize);
    }

    private void applyFilters() {
        currentPage = 0;
        loadPage();
    }

    private void updateTablePage() {
        PaginationUtils.updatePageServer(table, filteredData, currentPage, pageSize, totalFiltered,
            lblTotal, lblPage, btnPrev, btnNext, "resultado", "resultados");
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
        com.sibim.util.AppExecutor.submit(task);
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
        // Baja patrimonial (soft-delete), not a physical DELETE — the record
        // and its full movement history stay in the database for auditoría;
        // the bien just stops showing up in the active inventory. Requires a
        // motivo since a baja is a formal administrative act.
        BajaDialogResult resultado = showBajaDialog(seleccionado.getNombre());
        if (resultado == null) return;

        String nombre = seleccionado.getNombre();
        String idBaja = seleccionado.getId();
        final BajaDialogResult r = resultado;
        final Producto sel = seleccionado;
        Runnable doDelete = () -> DialogUtil.runAsync(
            () -> {
                if (r.tipoDestino() != null) {
                    productoService.darDeBaja(idBaja, r.motivo(),
                        r.tipoDestino(), r.dictamen(), r.numeroActa(), r.fechaDictamen());
                } else {
                    productoService.darDeBaja(idBaja, r.motivo());
                }
            },
            () -> {
                loadData();
                NotificacionUtil.exitoConAccionCountdown(table.getScene(),
                    "Bien \"" + nombre + "\" dado de baja",
                    "Deshacer",
                    () -> DialogUtil.runAsync(
                        () -> productoService.reactivar(idBaja),
                        () -> { loadData(); NotificacionUtil.info(table.getScene(), "\"" + nombre + "\" reactivado al inventario"); },
                        e2 -> NotificacionUtil.error(table.getScene(), "No se pudo deshacer la baja")
                    )
                );
                // Populate baja fields from the dialog result so the acta
                // reflects the data just saved without a second DB round-trip
                sel.setFechaBaja(java.time.LocalDate.now());
                sel.setMotivoBaja(r.motivo());
                sel.setTipoDestinoBaja(r.tipoDestino());
                sel.setDictamenBaja(r.dictamen());
                sel.setNumeroActaBaja(r.numeroActa());
                sel.setFechaDictamen(r.fechaDictamen());
                DialogUtil.runAsync(
                    () -> reporteService.exportActaBaja(sel),
                    file -> DialogUtil.showExportResultDialog(table.getScene(), file),
                    ex -> log.warn("No se pudo generar el acta de baja", ex)
                );
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

    /** Simple record to carry the baja dialog result. */
    private record BajaDialogResult(
        String motivo,
        String tipoDestino,
        String dictamen,
        String numeroActa,
        java.time.LocalDate fechaDictamen
    ) {}

    /**
     * Shows the enhanced "Dar de baja" dialog with motivo (required) and
     * optional committee/dictamen fields. Returns null if the user cancelled.
     */
    private BajaDialogResult showBajaDialog(String nombreBien) {
        javafx.scene.control.Dialog<BajaDialogResult> dlg = new javafx.scene.control.Dialog<>();
        dlg.setTitle("Dar de baja");
        dlg.getDialogPane().getButtonTypes().addAll(
            javafx.scene.control.ButtonType.OK,
            javafx.scene.control.ButtonType.CANCEL
        );
        DialogUtil.applyOwner(dlg);
        DialogUtil.applyStylesheet(dlg.getDialogPane());

        // Header
        javafx.scene.layout.HBox header = DialogUtil.gradientHeader(
            "mdi2d-delete-outline",
            "Dar de baja",
            "¿Dar de baja \"" + nombreBien + "\"?\nQuedará fuera del inventario activo, pero su historial se conserva.",
            AppColors.WARNING_D, AppColors.WARNING_DD
        );

        // ── Form fields ───────────────────────────────────────────────
        javafx.scene.control.TextField tfMotivo = new javafx.scene.control.TextField();
        tfMotivo.setPromptText("Motivo de la baja (obligatorio)");
        tfMotivo.setPrefWidth(360);

        javafx.scene.control.ComboBox<String> cbDestino = new javafx.scene.control.ComboBox<>();
        cbDestino.getItems().addAll("", "Destrucción", "Donación", "Subasta",
            "Transferencia a otro ente", "Otro");
        cbDestino.setValue("");
        cbDestino.setPrefWidth(360);
        cbDestino.setPromptText("Tipo de destino (opcional)");

        javafx.scene.control.TextField tfDictamen = new javafx.scene.control.TextField();
        tfDictamen.setPromptText("Dictamen / Resolución (opcional)");
        tfDictamen.setPrefWidth(360);

        javafx.scene.control.TextField tfNumeroActa = new javafx.scene.control.TextField();
        tfNumeroActa.setPromptText("No. de Acta (opcional)");
        tfNumeroActa.setPrefWidth(360);

        javafx.scene.control.DatePicker dpFechaDictamen = new javafx.scene.control.DatePicker();
        dpFechaDictamen.setPromptText("Fecha del dictamen (opcional)");
        dpFechaDictamen.setPrefWidth(360);

        // Disable OK if motivo is blank
        javafx.scene.Node btnOk = dlg.getDialogPane().lookupButton(javafx.scene.control.ButtonType.OK);
        btnOk.setDisable(true);
        tfMotivo.textProperty().addListener((obs, o, n) ->
            btnOk.setDisable(n == null || n.isBlank()));

        // Layout
        VBox form = new VBox(10);
        form.setPadding(new javafx.geometry.Insets(20, 24, 8, 24));
        form.getChildren().addAll(
            new javafx.scene.control.Label("Motivo de la baja *"),
            tfMotivo,
            new javafx.scene.control.Label("Tipo de destino"),
            cbDestino,
            new javafx.scene.control.Label("Dictamen / Resolución"),
            tfDictamen,
            new javafx.scene.control.Label("No. de Acta"),
            tfNumeroActa,
            new javafx.scene.control.Label("Fecha del dictamen"),
            dpFechaDictamen
        );

        dlg.getDialogPane().setContent(new VBox(header, form));
        dlg.getDialogPane().setPrefWidth(440);

        // Map OK to result
        dlg.setResultConverter(bt -> {
            if (bt != javafx.scene.control.ButtonType.OK) return null;
            String motivo = tfMotivo.getText().trim();
            String destLabel = cbDestino.getValue();
            String tipoDestino = switch (destLabel == null ? "" : destLabel) {
                case "Destrucción"             -> "DESTRUCCION";
                case "Donación"                -> "DONACION";
                case "Subasta"                 -> "SUBASTA";
                case "Transferencia a otro ente" -> "TRANSFERENCIA_ENTE";
                case "Otro"                    -> "OTRO";
                default                        -> null;
            };
            String dictamen   = tfDictamen.getText().isBlank()   ? null : tfDictamen.getText().trim();
            String numeroActa = tfNumeroActa.getText().isBlank() ? null : tfNumeroActa.getText().trim();
            java.time.LocalDate fechaDictamen = dpFechaDictamen.getValue();
            return new BajaDialogResult(motivo, tipoDestino, dictamen, numeroActa, fechaDictamen);
        });

        return dlg.showAndWait().orElse(null);
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
            List.copyOf(table.getSelectionModel().getSelectedItems()), reporteService, this::onExportSeleccionCsv);
    }

    @FXML
    private void onExportSeleccionExcel() {
        ProductosExporter.exportSeleccionExcel(table.getScene(),
            List.copyOf(table.getSelectionModel().getSelectedItems()), reporteService, this::onExportSeleccionExcel);
    }

    // ── Bulk actions ─────────────────────────────────────────────────────────

    @FXML
    private void onBulkCambiarArea() {
        List<Producto> sel = List.copyOf(table.getSelectionModel().getSelectedItems());
        if (sel.size() < 2 || !canEdit) return;
        ProductosBulkDialog.showCambiarArea(
            sel, table.getScene(), productoService, table,
            () -> { refreshing = true; loadData(); },
            this::onBulkCambiarArea);
    }

    @FXML
    private void onBulkCambiarResguardante() {
        List<Producto> sel = List.copyOf(table.getSelectionModel().getSelectedItems());
        if (sel.size() < 2 || !canEdit) return;
        ProductosBulkDialog.showCambiarResguardante(
            sel, table.getScene(), productoService, table,
            () -> { refreshing = true; loadData(); },
            this::onBulkCambiarResguardante);
    }

    @FXML
    private void onBulkMarcarEtiquetado() {
        List<Producto> sel = List.copyOf(table.getSelectionModel().getSelectedItems());
        if (sel.size() < 2 || !canEdit) return;
        ProductosBulkDialog.showMarcarEtiquetado(
            sel, table.getScene(), productoService, table,
            () -> { refreshing = true; loadData(); },
            this::onBulkMarcarEtiquetado);
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
        List<Producto> sel = List.copyOf(table.getSelectionModel().getSelectedItems());
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
        List<Producto> sel = List.copyOf(table.getSelectionModel().getSelectedItems());
        if (sel.isEmpty()) return;
        exportarEtiquetasQr(sel);
    }

    private void exportarEtiquetasQr(List<Producto> productos) {
        ProductosExporter.exportEtiquetasQr(table.getScene(), productos, reporteService);
    }

    @FXML
    private void onBulkEtiquetaFisica() {
        List<Producto> sel = List.copyOf(table.getSelectionModel().getSelectedItems());
        if (sel.isEmpty()) sel = List.copyOf(table.getItems());
        exportarEtiquetaFisica(sel);
    }

    private void exportarEtiquetaFisica(List<Producto> productos) {
        ProductosExporter.exportEtiquetaFisica(table.getScene(), productos, reporteService);
    }

    // ── Filter presets ───────────────────────────────────────────────────────

    @FXML
    private void onGuardarPreset() {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("Guardar filtro");
        dlg.setHeaderText("Nombre para este acceso rápido");
        dlg.setContentText("Nombre:");
        DialogUtil.applyOwner(dlg);
        DialogUtil.applyStylesheet(dlg.getDialogPane());
        dlg.showAndWait().map(String::trim).filter(n -> !n.isBlank())
           .ifPresent(name -> presetPanel.saveCurrentAs(name, table.getScene()));
    }

    private void saveStockThresholds(Producto p) {
        DialogUtil.runAsync((com.sibim.util.DialogUtil.CheckedRunnable) () -> productoService.save(p),
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
                    // Don't just show an error and drop everything the user
                    // typed — reopen the same dialog pre-filled with what
                    // they entered (p already has every field set) so a
                    // failed save (BD caída, validación) doesn't force
                    // re-typing the whole form from scratch.
                    NotificacionUtil.error(table.getScene(),
                        (e instanceof ProductoService.ValidationException ? e.getMessage() : "No se pudo guardar el bien")
                            + " — revisa los datos e inténtalo de nuevo");
                    Platform.runLater(() -> showProductDialog(p));
                }
            ));

        } catch (Exception e) {
            log.error("Error al abrir el formulario de bien", e);
            NotificacionUtil.error(table.getScene(), "Error al abrir el formulario. Verifica la conexión a la base de datos.");
        }
    }

    private static void updateSelectionLabel(Label lbl, int n) {
        if (lbl == null) return;
        if (n > 0) {
            lbl.setText("· " + n + (n == 1 ? " seleccionado" : " seleccionados"));
            lbl.setVisible(true);
            lbl.setManaged(true);
        } else {
            lbl.setVisible(false);
            lbl.setManaged(false);
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
