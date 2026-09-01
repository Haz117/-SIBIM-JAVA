package com.sibim.controller;

import com.sibim.config.Areas;
import com.sibim.controller.dialogs.ConteoFisicoDialog;
import com.sibim.controller.dialogs.ImportacionBienesDialog;
import com.sibim.controller.dialogs.ProductoBajasDialog;
import com.sibim.controller.dialogs.ProductoDetailDialog;
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
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.QrUtils;
import org.kordamp.ikonli.javafx.FontIcon;
import com.sibim.util.NotificacionUtil;
import com.sibim.util.PaginationUtils;
import com.sibim.util.SearchUtils;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProductosController {

    private static final Logger log = LoggerFactory.getLogger(ProductosController.class);

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
        "Equipo de Oficina",         "mdi2p-printer-outline",
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
    @FXML private FlowPane presetsBar;
    @FXML private HBox presetsHeader;
    @FXML private Button btnGuardarPreset;
    @FXML private Button btnToggleFiltros;
    @FXML private HBox bulkBar;
    @FXML private Label lblBulkCount;
    @FXML private Button btnBulkArea;
    @FXML private Button btnBulkResguardante;
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
    @FXML private VBox statCardTotal;
    @FXML private VBox statCardValor;
    @FXML private VBox cardAlertas;
    @FXML private Label helpAlertas;
    @FXML private Label helpResguardante;
    @FXML private Label helpTotal;
    @FXML private Label helpValor;

    private final ProductoService productoService = new ProductoService();
    private final CategoriaService categoriaService = new CategoriaService();
    private final ReporteService reporteService = new ReporteService();
    private final MovimientoService movimientoService = new MovimientoService();

    private int totalFiltered = 0;
    private ObservableList<Producto> filteredData = FXCollections.observableArrayList();
    private int currentPage = 0;
    private int pageSize = 25;
    private boolean refreshing = false;
    private final AtomicBoolean loading = new AtomicBoolean(false);
    private boolean canEdit = false;
    private FilterPresetPanel presetPanel;
    private String pendingHighlightId;
    private ToggleGroup estadoChipGroup;
    private Label emptyStateMsg;
    private Label emptyStateHint;
    private Button btnEmptyLimpiar;

    @FXML
    public void initialize() {
        canEdit = SessionManager.isAdmin() || SessionManager.isSecretario();
        if (btnToggleFiltros != null && filterBar != null)
            DialogUtil.makeCollapsible("bienes.filtros.colapsado", btnToggleFiltros, filterBar);
        setupTable();
        setupFilters();
        setupStatusChips();
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
                    if (searchField != null) { searchField.requestFocus(); searchField.selectAll(); }
                    ev.consume();
                }
            });
        }
        if (btnClearSearch != null) {
            searchField.textProperty().addListener((obs, o, n) -> btnClearSearch.setVisible(!n.isBlank()));
            btnClearSearch.setOnAction(e -> { searchField.clear(); searchField.requestFocus(); });
        }
        loadData();
        AnimationUtils.staggeredFadeInUp(
            java.util.List.of(statCardTotal, statCardValor, cardAlertas), 300, 55);
        Platform.runLater(() -> { if (searchField != null) searchField.requestFocus(); });
        if (helpAlertas      != null) DialogUtil.enableClickToShowTooltip(helpAlertas);
        if (helpResguardante != null) DialogUtil.enableClickToShowTooltip(helpResguardante);
        if (helpTotal        != null) DialogUtil.enableClickToShowTooltip(helpTotal);
        if (helpValor        != null) DialogUtil.enableClickToShowTooltip(helpValor);
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
        ProductosColumnSetup.configureEstado(colEstado);
        ProductosColumnSetup.configureRowFactory(table, () -> pendingHighlightId);
        setupTableListeners();
        setupContextMenu();
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
            updateBulkBar(n);
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

    private void setupContextMenu() {
        ContextMenu cm = new ContextMenu();
        MenuItem cmDetalle  = new MenuItem("Ver detalle");
        cmDetalle.setGraphic(new FontIcon("mdi2e-eye-outline"));
        cmDetalle.setOnAction(e -> {
            Producto sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) showProductDetail(sel);
        });
        cm.getItems().add(cmDetalle);
        MenuItem cmFicha = new MenuItem("Imprimir ficha técnica");
        cmFicha.setGraphic(new FontIcon("mdi2f-file-document-outline"));
        cmFicha.setOnAction(e -> {
            Producto sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) {
                DialogUtil.runAsyncWithProgress(table.getScene(), "Generando ficha…",
                    () -> reporteService.exportFichaTecnica(sel, movimientoService.getByProducto(sel.getId())),
                    file -> DialogUtil.showExportResultDialog(table.getScene(), file),
                    ex -> { log.error("Error ficha técnica", ex); NotificacionUtil.error(table.getScene(), "No se pudo generar la ficha técnica"); });
            }
        });
        cm.getItems().add(new SeparatorMenuItem());
        cm.getItems().add(cmFicha);
        if (canEdit) {
            cm.getItems().add(new SeparatorMenuItem());
            MenuItem cmEditar   = new MenuItem("Editar");
            cmEditar.setGraphic(new FontIcon("mdi2p-pencil"));
            MenuItem cmEliminar = new MenuItem("Dar de baja");
            cmEliminar.setGraphic(new FontIcon("mdi2d-delete-outline"));
            cmEditar.setOnAction(e -> onEdit());
            cmEliminar.setOnAction(e -> onDelete());
            cm.getItems().addAll(cmEditar, cmEliminar);
        }
        table.setContextMenu(cm);
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
        table.setPlaceholder(emptyState);
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
                categoriaFilter.setCellFactory(lv -> categoriaListCell());
                categoriaFilter.setButtonCell(categoriaListCell());
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
        areaFilter.setCellFactory(lv -> areaListCell());
        areaFilter.setButtonCell(areaListCell());
        areaFilter.valueProperty().addListener((obs, o, n) -> { currentPage = 0; applyFilters(); });

        if (resguardanteFilter != null) {
            resguardanteFilter.setConverter(new javafx.util.StringConverter<>() {
                public String toString(String r) { return r == null ? "Todos los resguardantes" : r; }
                public String fromString(String s) { return null; }
            });
            resguardanteFilter.valueProperty().addListener((obs, o, n) -> { currentPage = 0; applyFilters(); });
        }

        String pendingArea = NavigationContext.consumePendingAreaFilter();
        if (pendingArea != null && areaFilter.getItems().contains(pendingArea)) {
            areaFilter.setValue(pendingArea);
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
        if (spinner != null) { spinner.setVisible(true); spinner.setManaged(true); }

        // Read filter values on the FX thread before spawning the background task
        String busqueda = searchField != null ? searchField.getText().toLowerCase().strip() : "";
        String catId = categoriaFilter != null && categoriaFilter.getValue() != null
            ? categoriaFilter.getValue().getId() : null;
        String area = areaFilter != null && areaFilter.getValue() != null ? areaFilter.getValue() : null;
        String resguardante = resguardanteFilter != null ? resguardanteFilter.getValue() : null;
        EstadoProducto estado = parseEstado(getSelectedEstado());

        Task<Void> task = new Task<>() {
            private List<Producto> pageData;
            private int count;
            private com.sibim.repository.ProductoRepository.InventarioStats stats;
            private List<String> resguardantes;

            @Override protected Void call() throws Exception {
                resguardantes = productoService.getResguardantes();
                count = productoService.countFiltrado(busqueda, catId, area, resguardante, estado);
                pageData = productoService.getPaginated(busqueda, catId, area, resguardante, estado,
                    pageSize, currentPage * pageSize);
                stats = productoService.getStats();
                return null;
            }

            @Override protected void succeeded() {
                loading.set(false);
                refreshResguardanteOptions(resguardantes);
                totalFiltered = count;
                filteredData.setAll(pageData);
                updateTablePage();
                updateHasFiltersUi(busqueda, catId, area, resguardante, estado);
                updateStats(stats);
                if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
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
                if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
                NotificacionUtil.errorConAccion(table.getScene(),
                    "No se pudo cargar los bienes. Verifica la conexión.", "Reintentar", () -> loadData());
            }
        };
        com.sibim.util.AppExecutor.submit(task);
    }

    /** Loads a single page in the background using current filter state. */
    private void loadPage() {
        if (!loading.compareAndSet(false, true)) { refreshing = true; return; }
        if (spinner != null) { spinner.setVisible(true); spinner.setManaged(true); }

        // Read filter values on the FX thread before spawning the background task
        String busqueda = searchField != null ? searchField.getText().toLowerCase().strip() : "";
        String catId = categoriaFilter != null && categoriaFilter.getValue() != null
            ? categoriaFilter.getValue().getId() : null;
        String area = areaFilter != null && areaFilter.getValue() != null ? areaFilter.getValue() : null;
        String resguardante = resguardanteFilter != null ? resguardanteFilter.getValue() : null;
        EstadoProducto estado = parseEstado(getSelectedEstado());
        int offset = currentPage * pageSize;

        Task<Void> task = new Task<>() {
            List<Producto> page;
            int count;

            @Override protected Void call() throws Exception {
                page = productoService.getPaginated(busqueda, catId, area, resguardante, estado, pageSize, offset);
                count = productoService.countFiltrado(busqueda, catId, area, resguardante, estado);
                return null;
            }

            @Override protected void succeeded() {
                loading.set(false);
                totalFiltered = count;
                filteredData.setAll(page);
                updateTablePage();
                updateHasFiltersUi(busqueda, catId, area, resguardante, estado);
                if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
                if (refreshing) { NotificacionUtil.info(table.getScene(), "Lista actualizada"); refreshing = false; }
            }

            @Override protected void failed() {
                loading.set(false);
                if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
                NotificacionUtil.errorConAccion(table.getScene(),
                    "No se pudo cargar los bienes. Verifica la conexión.", "Reintentar", () -> loadPage());
            }
        };
        com.sibim.util.AppExecutor.submit(task);
    }

    private void refreshResguardanteOptions(List<String> options) {
        if (resguardanteFilter == null) return;
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

    private void applyFilters() {
        currentPage = 0;
        loadPage();
    }

    /** Updates the "has filters" UI elements (clear button, preset button, total label, empty state).
     *  Called after page loads complete so the UI reflects the current filter state. */
    private void updateHasFiltersUi(String busqueda, String catId, String area,
            String resguardante, EstadoProducto estado) {
        boolean hasFilters = !busqueda.isBlank() || catId != null || area != null
            || resguardante != null || estado != null;
        if (btnClearFilters != null) {
            btnClearFilters.setVisible(hasFilters);
            btnClearFilters.setManaged(hasFilters);
        }
        if (btnGuardarPreset != null) {
            btnGuardarPreset.setVisible(hasFilters);
            btnGuardarPreset.setManaged(hasFilters);
        }
        if (lblTotalAll != null) {
            if (hasFilters) {
                lblTotalAll.setText("de " + totalFiltered + " total");
                lblTotalAll.setVisible(true);
                lblTotalAll.setManaged(true);
            } else {
                lblTotalAll.setVisible(false);
                lblTotalAll.setManaged(false);
            }
        }
        if (emptyStateMsg != null)
            emptyStateMsg.setText(hasFilters
                ? "No se encontraron bienes con esos filtros"
                : "No hay bienes registrados en el sistema");
        if (btnEmptyLimpiar != null) {
            btnEmptyLimpiar.setVisible(hasFilters);
            btnEmptyLimpiar.setManaged(hasFilters);
        }
        if (emptyStateHint != null) {
            emptyStateHint.setVisible(!hasFilters && canEdit);
            emptyStateHint.setManaged(!hasFilters && canEdit);
        }
    }

    private static EstadoProducto parseEstado(String etiqueta) {
        if (etiqueta == null || "Todos".equalsIgnoreCase(etiqueta)) return null;
        for (EstadoProducto e : EstadoProducto.values()) {
            if (e.getEtiqueta().equalsIgnoreCase(etiqueta)) return e;
        }
        return null;
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
        String busqueda = searchField != null ? searchField.getText().toLowerCase().strip() : "";
        String catId = categoriaFilter != null && categoriaFilter.getValue() != null
            ? categoriaFilter.getValue().getId() : null;
        String area = areaFilter != null && areaFilter.getValue() != null ? areaFilter.getValue() : null;
        String resguardante = resguardanteFilter != null ? resguardanteFilter.getValue() : null;
        EstadoProducto estado = parseEstado(getSelectedEstado());

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
        if (resguardanteFilter != null) resguardanteFilter.setValue(null);
        if (estadoChipGroup != null)
            estadoChipGroup.getToggles().stream()
                .filter(t -> "Todos".equals(((ToggleButton) t).getText()))
                .findFirst().ifPresent(t -> t.setSelected(true));
        currentPage = 0;
        applyFilters();
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
        List<Categoria> cats;
        try { cats = categoriaService.findAll(); }
        catch (Exception e) { NotificacionUtil.error(table.getScene(), "No se pudieron cargar las categorías"); return; }
        ImportacionBienesDialog.show(table.getScene(), cats, productoService, () -> { refreshing = true; loadData(); });
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
        Optional<String> motivo = ConfirmacionUtil.confirmarConMotivo(
            "mdi2d-delete-outline",
            "Dar de baja",
            "¿Dar de baja \"" + seleccionado.getNombre() + "\"?\nQuedará fuera del inventario activo, pero su historial se conserva.",
            "Dar de baja", true,
            "Motivo de la baja (obligatorio)"
        );
        if (motivo.isEmpty()) return;
        String nombre = seleccionado.getNombre();
        String idBaja = seleccionado.getId();
        Runnable doDelete = () -> DialogUtil.runAsync(
            () -> productoService.darDeBaja(idBaja, motivo.get()),
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

    @FXML
    private void onExportCsv() {
        DialogUtil.runAsyncWithProgress(table.getScene(), "Generando CSV…",
            () -> reporteService.exportInventarioCsv(null, null),
            file -> DialogUtil.showExportResultDialog(table.getScene(), file),
            e -> NotificacionUtil.errorConAccion(table.getScene(), "No se pudo exportar el CSV", "Reintentar", this::onExportCsv)
        );
    }

    @FXML
    private void onExportExcel() {
        DialogUtil.runAsyncWithProgress(table.getScene(), "Generando Excel…",
            () -> reporteService.exportInventarioExcel(null, null),
            file -> DialogUtil.showExportResultDialog(table.getScene(), file),
            e -> NotificacionUtil.errorConAccion(table.getScene(), "No se pudo exportar el Excel", "Reintentar", this::onExportExcel)
        );
    }

    @FXML
    private void onExportSeleccionCsv() {
        List<Producto> seleccion = List.copyOf(table.getSelectionModel().getSelectedItems());
        if (seleccion.isEmpty()) return;
        DialogUtil.runAsyncWithProgress(table.getScene(), "Generando CSV…",
            () -> reporteService.exportInventarioCsv(seleccion),
            file -> DialogUtil.showExportResultDialog(table.getScene(), file),
            e -> NotificacionUtil.errorConAccion(table.getScene(), "No se pudo exportar el CSV", "Reintentar", this::onExportSeleccionCsv)
        );
    }

    @FXML
    private void onExportSeleccionExcel() {
        List<Producto> seleccion = List.copyOf(table.getSelectionModel().getSelectedItems());
        if (seleccion.isEmpty()) return;
        DialogUtil.runAsyncWithProgress(table.getScene(), "Generando Excel…",
            () -> reporteService.exportInventarioExcel(seleccion),
            file -> DialogUtil.showExportResultDialog(table.getScene(), file),
            e -> NotificacionUtil.errorConAccion(table.getScene(), "No se pudo exportar el Excel", "Reintentar", this::onExportSeleccionExcel)
        );
    }

    // ── Bulk actions ─────────────────────────────────────────────────────────

    private boolean bulkBarVisible = false;

    private void updateBulkBar(int n) {
        if (bulkBar == null) return;
        boolean show = n >= 2;
        if (lblBulkCount != null && show)
            lblBulkCount.setText(n + " bienes seleccionados");
        if (btnBulkArea        != null) { btnBulkArea.setVisible(canEdit);        btnBulkArea.setManaged(canEdit); }
        if (btnBulkResguardante != null) { btnBulkResguardante.setVisible(canEdit); btnBulkResguardante.setManaged(canEdit); }
        if (show == bulkBarVisible) return;
        bulkBarVisible = show;
        if (show) {
            bulkBar.setOpacity(0);
            bulkBar.setTranslateY(12);
            bulkBar.setVisible(true);
            bulkBar.setManaged(true);
            var ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(180), bulkBar);
            ft.setToValue(1);
            var tt = new javafx.animation.TranslateTransition(javafx.util.Duration.millis(180), bulkBar);
            tt.setToY(0);
            new javafx.animation.ParallelTransition(ft, tt).play();
        } else {
            var ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(140), bulkBar);
            ft.setToValue(0);
            ft.setOnFinished(e -> { bulkBar.setVisible(false); bulkBar.setManaged(false); });
            ft.play();
        }
    }

    @FXML
    private void onBulkCambiarArea() {
        List<Producto> sel = List.copyOf(table.getSelectionModel().getSelectedItems());
        if (sel.size() < 2 || !canEdit) return;
        var areaNames = new java.util.ArrayList<>(Areas.getAllAreaNames());
        ChoiceDialog<String> dlg = new ChoiceDialog<>(areaNames.get(0), areaNames);
        dlg.setTitle("Cambiar área");
        dlg.setHeaderText("Nueva área para " + sel.size() + " bienes seleccionados");
        dlg.setContentText("Área:");
        DialogUtil.applyOwner(dlg);
        DialogUtil.applyStylesheet(dlg.getDialogPane());
        dlg.showAndWait().ifPresent(area ->
            DialogUtil.runAsyncWithProgress(table.getScene(), "Actualizando área…",
                () -> {
                    for (Producto p : sel) { p.setArea(area); productoService.save(p); }
                    return sel.size();
                },
                count -> {
                    refreshing = true; loadData();
                    NotificacionUtil.exito(table.getScene(), count + " bien(es) movidos a \"" + area + "\"");
                },
                e -> NotificacionUtil.error(table.getScene(), "No se pudo cambiar el área")
            )
        );
    }

    @FXML
    private void onBulkCambiarResguardante() {
        List<Producto> sel = List.copyOf(table.getSelectionModel().getSelectedItems());
        if (sel.size() < 2 || !canEdit) return;
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("Cambiar resguardante");
        dlg.setHeaderText("Nuevo resguardante para " + sel.size() + " bienes seleccionados");
        dlg.setContentText("Nombre:");
        DialogUtil.applyOwner(dlg);
        DialogUtil.applyStylesheet(dlg.getDialogPane());
        dlg.showAndWait().map(String::trim).filter(s -> !s.isBlank()).ifPresent(nombre ->
            DialogUtil.runAsyncWithProgress(table.getScene(), "Actualizando resguardante…",
                () -> {
                    for (Producto p : sel) { p.setResguardante(nombre); productoService.save(p); }
                    return sel.size();
                },
                count -> {
                    refreshing = true; loadData();
                    NotificacionUtil.exito(table.getScene(), count + " bien(es) asignados a \"" + nombre + "\"");
                },
                e -> NotificacionUtil.error(table.getScene(), "No se pudo cambiar el resguardante")
            )
        );
    }

    @FXML
    private void onBulkResguardoPdf() {
        java.util.List<Producto> seleccionados = new java.util.ArrayList<>(table.getSelectionModel().getSelectedItems());
        if (seleccionados.isEmpty()) return;
        // Tomar el primer resguardante y área no-nulos del lote
        String resguardante = seleccionados.stream()
            .map(Producto::getResguardante)
            .filter(r -> r != null && !r.isBlank())
            .findFirst().orElse("Sin resguardante");
        String area = seleccionados.stream()
            .map(Producto::getArea)
            .filter(a -> a != null && !a.isBlank())
            .findFirst().orElse("");
        DialogUtil.runAsync(
            () -> reporteService.exportarResguardoPdf(resguardante, area, seleccionados),
            file -> DialogUtil.showExportResultDialog(table.getScene(), file),
            e -> NotificacionUtil.error(table.getScene(), "Error al generar el resguardo PDF")
        );
    }

    @FXML
    private void onImprimirQr() {
        Producto sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        Image qrImg = QrUtils.generateQr(sel.getCodigo(), 300);
        if (qrImg == null) { NotificacionUtil.error(table.getScene(), "No se pudo generar el QR"); return; }

        ButtonType savePng = new ButtonType("Guardar PNG", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Código QR — " + sel.getNombre());
        dlg.initOwner(table.getScene().getWindow());
        dlg.getDialogPane().getButtonTypes().addAll(savePng, ButtonType.CLOSE);
        dlg.getDialogPane().getStylesheets().addAll(table.getScene().getStylesheets());

        javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(qrImg);
        iv.setFitWidth(260); iv.setFitHeight(260); iv.setPreserveRatio(true);
        Label lblCodigo = new Label(sel.getCodigo());
        lblCodigo.getStyleClass().add("dlg-detail-value");
        Label lblNombre = new Label(sel.getNombre());
        lblNombre.getStyleClass().add("muted-sm");

        VBox content = new VBox(8, iv, lblCodigo, lblNombre);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(16));
        dlg.getDialogPane().setContent(content);

        Optional<ButtonType> result = dlg.showAndWait();
        if (result.isPresent() && result.get() == savePng) {
            FileChooser fc = new FileChooser();
            fc.setTitle("Guardar código QR como imagen");
            fc.setInitialFileName("QR_" + sel.getCodigo() + ".png");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imagen PNG (*.png)", "*.png"));
            File dest = fc.showSaveDialog(table.getScene().getWindow());
            if (dest != null) {
                try {
                    saveQrAsPng(qrImg, dest);
                    DialogUtil.showExportResultDialog(table.getScene(), dest);
                } catch (Exception e) {
                    log.error("Error guardando QR para {}", sel.getCodigo(), e);
                    NotificacionUtil.error(table.getScene(), "No se pudo guardar el QR");
                }
            }
        }
    }

    private static void saveQrAsPng(Image img, File dest) throws java.io.IOException {
        int w = (int) img.getWidth();
        int h = (int) img.getHeight();
        javafx.scene.image.PixelReader pr = img.getPixelReader();
        java.awt.image.BufferedImage bi = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                javafx.scene.paint.Color c = pr.getColor(x, y);
                int rgb = ((int)(c.getRed() * 255) << 16)
                        | ((int)(c.getGreen() * 255) << 8)
                        | (int)(c.getBlue() * 255);
                bi.setRGB(x, y, rgb);
            }
        }
        javax.imageio.ImageIO.write(bi, "PNG", dest);
    }

    @FXML
    private void onDeseleccionar() {
        table.getSelectionModel().clearSelection();
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

    // ── Internal helpers ─────────────────────────────────────────────────────

    private void showProductDetail(Producto p) {
        ProductoDetailDialog.show(p, table.getScene(), movimientoService, log);
    }

    private void showProductDialog(Producto existing) {
        try {
            List<Categoria> cats = categoriaService.findAll();
            Optional<Producto> result = ProductoDialogFactory.show(existing, cats, THUMBNAIL_CACHE, log);
            boolean isNew = existing == null;
            result.ifPresent(p -> DialogUtil.runAsync(
                () -> productoService.save(p),
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

    private ListCell<Categoria> categoriaListCell() {
        return new ListCell<>() {
            @Override protected void updateItem(Categoria c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null) {
                    setText("Todas las categorías"); setGraphic(null);
                    return;
                }
                String icono = c.getIcono();
                if (icono != null && !icono.isBlank()) {
                    Label badge = new Label(icono + "  " + c.getNombre());
                    badge.getStyleClass().add("combo-cell-label");
                    setGraphic(badge); setText(null);
                } else {
                    setText(c.getNombre()); setGraphic(null);
                }
            }
        };
    }

    private ListCell<String> areaListCell() {
        return new ListCell<>() {
            @Override protected void updateItem(String area, boolean empty) {
                super.updateItem(area, empty);
                if (empty || area == null) {
                    setText("Todas las áreas"); setGraphic(null);
                    return;
                }
                String icon = areaIcon(area);
                FontIcon fi = new FontIcon(icon);
                fi.setIconSize(13);
                fi.getStyleClass().add("area-filter-icon");
                Label lbl = new Label("  " + area, fi);
                lbl.getStyleClass().add("combo-cell-label");
                setGraphic(lbl); setText(null);
            }
        };
    }

    private static String areaIcon(String area) {
        if (area == null) return "mdi2o-office-building-outline";
        String lo = area.toLowerCase();
        if (lo.startsWith("secretar")) return "mdi2b-briefcase-outline";
        if (lo.startsWith("despacho") || lo.startsWith("presidencia")) return "mdi2s-star-outline";
        if (lo.contains("recursos humanos")) return "mdi2a-account-group-outline";
        if (lo.contains("tecnolog")) return "mdi2m-monitor-multiple";
        if (lo.contains("seguridad")) return "mdi2s-shield-outline";
        if (lo.contains("obras") || lo.contains("servicio")) return "mdi2w-wrench-outline";
        if (lo.contains("finanz") || lo.contains("tesorer") || lo.contains("contab") || lo.contains("presupuest")) return "mdi2c-currency-usd";
        if (lo.contains("bienes")) return "mdi2p-package-variant";
        if (lo.contains("juridic") || lo.contains("contralo")) return "mdi2s-scale-balance";
        if (lo.contains("bienestar") || lo.contains("social") || lo.contains("salud")) return "mdi2h-heart-outline";
        if (lo.contains("educac") || lo.contains("cultura")) return "mdi2b-book-outline";
        if (lo.contains("deporte")) return "mdi2s-soccer";
        if (lo.contains("turismo") || lo.contains("economic")) return "mdi2c-chart-line";
        if (lo.contains("transparencia") || lo.contains("acceso")) return "mdi2e-eye-outline";
        if (lo.contains("comunicac") || lo.contains("marketing")) return "mdi2m-microphone-outline";
        if (lo.contains("planeac") || lo.contains("evaluac")) return "mdi2c-clipboard-text-outline";
        if (lo.contains("indigena") || lo.contains("pueblos")) return "mdi2l-leaf";
        if (lo.startsWith("direcci")) return "mdi2f-folder-outline";
        if (lo.contains("unidad") || lo.contains("coordinac") || lo.contains("subdi")) return "mdi2t-text-box-outline";
        return "mdi2o-office-building-outline";
    }
}
