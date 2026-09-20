package com.sibim.controller;

import com.sibim.controller.dialogs.MovimientoDetailDialog;
import com.sibim.controller.dialogs.MovimientoDialogFactory;
import org.kordamp.ikonli.javafx.FontIcon;
import com.sibim.model.Movimiento;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.repository.MovimientoRepository;
import com.sibim.service.MovimientoService;
import com.sibim.service.ProductoService;
import com.sibim.service.ReporteService;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppExecutor;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import com.sibim.util.PaginationUtils;
import com.sibim.util.SearchUtils;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.util.Duration;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MovimientosController {

    private static final Logger log = LoggerFactory.getLogger(MovimientosController.class);
    private static final java.util.prefs.Preferences STICKY =
        java.util.prefs.Preferences.userRoot().node("sibim/filters/movimientos");

    @FXML private VBox rootPane;
    @FXML private javafx.scene.layout.FlowPane filterBar;
    @FXML private Button btnToggleFiltros;
    @FXML private VBox resumenBox;
    @FXML private Button btnToggleResumen;
    @FXML private TextField searchField;
    @FXML private HBox tipoChipsBar;
    @FXML private DatePicker desdeFilter;
    @FXML private DatePicker hastaFilter;
    @FXML private TableView<Movimiento> table;
    @FXML private Button btnResetColumns;
    @FXML private TableColumn<Movimiento, String> colProducto;
    @FXML private TableColumn<Movimiento, String> colTipo;
    @FXML private TableColumn<Movimiento, Integer> colCantidad;
    @FXML private TableColumn<Movimiento, String> colStock;
    @FXML private TableColumn<Movimiento, String> colMotivo;
    @FXML private TableColumn<Movimiento, String> colUsuario;
    @FXML private TableColumn<Movimiento, String> colFecha;
    @FXML private TableColumn<Movimiento, String> colEstado;
    @FXML private Label lblTotal;
    @FXML private ProgressIndicator spinner;
    @FXML private Button btnNuevo;
    @FXML private Button btnPresetHoy;
    @FXML private Button btnPresetSemana;
    @FXML private Button btnPresetMes;
    @FXML private ComboBox<Integer> pageSizeBox;
    @FXML private Label lblPage;
    @FXML private Button btnPrev;
    @FXML private Button btnNext;
    @FXML private Label lblStatTotalMov;
    @FXML private Label lblStatEntradas;
    @FXML private Label lblStatSalidas;
    @FXML private Label lblStatAjustes;
    @FXML private VBox  statCardTotal;
    @FXML private VBox  statCardEntrada;
    @FXML private VBox  statCardSalida;
    @FXML private VBox  statCardAjuste;
    @FXML private Button btnDelete;
    @FXML private javafx.scene.control.MenuButton btnExportarSeleccion;
    @FXML private Label  lblSeleccionados;
    @FXML private Button btnClearSearch;
    @FXML private Button btnPendientes;
    @FXML private Label helpAjustes;
    @FXML private Label helpResumen;
    @FXML private Label helpTipoChips;
    @FXML private Label helpFechaMov;
    @FXML private Label helpTotalMov;
    @FXML private Label helpEntradas;
    @FXML private Label helpSalidas;
    @FXML private ComboBox<String> categoriaFilter;

    private final MovimientoService movimientoService = new MovimientoService();
    private final ProductoService productoService = new ProductoService();
    private final ReporteService reporteService = new ReporteService();
    private final PendientesDialog pendientesDialog =
        new PendientesDialog(movimientoService, () -> { loadData(); loadPendientesCount(); });

    private ObservableList<Movimiento> filteredData = FXCollections.observableArrayList();
    private int currentPage = 0;
    private int pageSize = 25;
    private int totalFiltered = 0;
    private ToggleGroup tipoChipGroup;
    private String pendingHighlightId;
    private Label emptyStateMsg;
    private Label emptyStateHint;
    private Button btnEmptyLimpiar;
    private boolean refreshing = false;
    private final AtomicBoolean loading = new AtomicBoolean(false);
    private VBox emptyStatePlaceholder;
    private Timeline skeletonPulse;

    @FXML
    public void initialize() {
        if (btnToggleFiltros != null && filterBar != null)
            DialogUtil.makeCollapsible("movimientos.filtros.colapsado", btnToggleFiltros, filterBar);
        if (btnToggleResumen != null && resumenBox != null)
            DialogUtil.makeCollapsible("movimientos.resumen.colapsado", btnToggleResumen, resumenBox,
                "Mostrar resumen", "Ocultar resumen");
        setupTable();
        setupFilters();
        setupTipoChips();
        setupPagination();
        if (helpAjustes   != null) DialogUtil.enableClickToShowTooltip(helpAjustes);
        if (helpTotalMov  != null) DialogUtil.enableClickToShowTooltip(helpTotalMov);
        if (helpEntradas  != null) DialogUtil.enableClickToShowTooltip(helpEntradas);
        if (helpSalidas   != null) DialogUtil.enableClickToShowTooltip(helpSalidas);
        if (helpResumen   != null) DialogUtil.enableClickToShowTooltip(helpResumen);
        if (helpTipoChips != null) DialogUtil.enableClickToShowTooltip(helpTipoChips);
        if (helpFechaMov  != null) DialogUtil.enableClickToShowTooltip(helpFechaMov);

        boolean canCreate = SessionManager.isAdmin() || SessionManager.isSecretario();
        btnNuevo.setVisible(canCreate);
        btnNuevo.setManaged(canCreate);
        if (rootPane != null && canCreate) {
            rootPane.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, ev -> {
                if (ev.getCode() == javafx.scene.input.KeyCode.N && ev.isControlDown()) {
                    onNuevoMovimiento(); ev.consume();
                }
            });
        }
        if (rootPane != null) {
            rootPane.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, ev -> {
                if (ev.getCode() == javafx.scene.input.KeyCode.F && ev.isControlDown()) {
                    if (searchField != null) { searchField.requestFocus(); searchField.selectAll(); }
                    ev.consume();
                } else if (ev.getCode() == javafx.scene.input.KeyCode.E && ev.isControlDown()
                        && table.getSelectionModel().getSelectedItem() != null) {
                    MovimientoDetailDialog.show(table.getSelectionModel().getSelectedItem(), table.getScene(), movimientoService, this::loadData); ev.consume();
                }
            });
        }

        // Enable multi-select on table
        table.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);

        // Selection → enable/disable delete + export-seleccion buttons
        table.getSelectionModel().getSelectedItems().addListener(
            (javafx.collections.ListChangeListener<com.sibim.model.Movimiento>) change -> {
                int n = table.getSelectionModel().getSelectedItems().size();
                boolean hasSelection = n > 0;
                if (btnDelete           != null) btnDelete.setDisable(!hasSelection);
                if (btnExportarSeleccion != null) btnExportarSeleccion.setDisable(!hasSelection);
                if (lblSeleccionados     != null) {
                    lblSeleccionados.setText(hasSelection ? n + " seleccionado(s)" : "");
                    lblSeleccionados.setVisible(hasSelection);
                    lblSeleccionados.setManaged(hasSelection);
                }
            });

        // Initial disabled state + tooltips on selection-dependent buttons
        if (btnDelete            != null) {
            btnDelete.setDisable(true);
            javafx.scene.control.Tooltip.install(btnDelete, new javafx.scene.control.Tooltip("Selecciona un movimiento para eliminarlo"));
        }
        if (btnExportarSeleccion != null) {
            btnExportarSeleccion.setDisable(true);
            javafx.scene.control.Tooltip.install(btnExportarSeleccion, new javafx.scene.control.Tooltip("Selecciona uno o más movimientos para exportarlos"));
        }

        table.setOnKeyPressed(ev -> {
            Movimiento sel = table.getSelectionModel().getSelectedItem();
            if (ev.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                table.getSelectionModel().clearSelection(); ev.consume();
            } else if (ev.getCode() == javafx.scene.input.KeyCode.ENTER && sel != null) {
                MovimientoDetailDialog.show(sel, table.getScene(), movimientoService, this::loadData); ev.consume();
            } else if (ev.getCode() == javafx.scene.input.KeyCode.DELETE && sel != null) {
                onDelete(); ev.consume();
            }
        });

        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null)
                MovimientoDetailDialog.show(table.getSelectionModel().getSelectedItem(), table.getScene(), movimientoService, this::loadData);
        });

        boolean canDelete = SessionManager.isAdmin() || SessionManager.isSecretario();
        table.setContextMenu(MovimientosContextMenu.build(
            table, canDelete, movimientoService, this::onDelete, this::loadData));

        if (btnClearSearch != null) {
            searchField.textProperty().addListener((obs, o, n) -> btnClearSearch.setVisible(!n.isBlank()));
            btnClearSearch.setOnAction(e -> { searchField.clear(); searchField.requestFocus(); });
        }
        if (searchField != null) com.sibim.util.SearchUtils.setupSearchHistory(
            "sibim/search-history/movimientos", searchField, () -> { currentPage = 0; loadData(); });
        // Restore sticky filters from previous session
        String savedSearch = STICKY.get("search", "");
        if (!savedSearch.isBlank() && searchField != null) searchField.setText(savedSearch);
        String savedDesde = STICKY.get("desde", "");
        String savedHasta = STICKY.get("hasta", "");
        if (!savedDesde.isBlank() && desdeFilter != null)
            try { desdeFilter.setValue(java.time.LocalDate.parse(savedDesde)); } catch (Exception ignored) {}
        if (!savedHasta.isBlank() && hastaFilter != null)
            try { hastaFilter.setValue(java.time.LocalDate.parse(savedHasta)); } catch (Exception ignored) {}
        String savedCategoria = STICKY.get("categoria", "");
        if (!savedCategoria.isBlank() && categoriaFilter != null) categoriaFilter.setValue(savedCategoria);
        String savedTipo = STICKY.get("tipo", "Todos");
        if (!savedTipo.equals("Todos") && tipoChipGroup != null)
            tipoChipGroup.getToggles().stream()
                .filter(t -> savedTipo.equals(((ToggleButton) t).getText()))
                .findFirst().ifPresent(t -> t.setSelected(true));
        if (SessionManager.isAdmin()) {
            if (btnPendientes != null) { btnPendientes.setVisible(true); btnPendientes.setManaged(true); }
            loadPendientesCount();
        } else {
            if (btnPendientes != null) { btnPendientes.setVisible(false); btnPendientes.setManaged(false); }
        }
        if (com.sibim.session.NavigationContext.consumePendingNuevoMovimiento()) {
            Platform.runLater(this::onNuevoMovimiento);
        }
        loadData();
        AnimationUtils.staggeredFadeInUp(
            java.util.List.of(statCardTotal, statCardEntrada, statCardSalida, statCardAjuste), 300, 55);
        if (lblPage != null) {
            lblPage.getStyleClass().add("page-label-jump");
            lblPage.setOnMouseClicked(e -> { if (e.getClickCount() == 2) promptJumpToPage(); });
        }
        Platform.runLater(() -> { if (searchField != null) searchField.requestFocus(); });
    }

    private void setupTable() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        MovimientosColumnSetup.configureProducto(colProducto, () -> searchField != null ? searchField.getText() : "");
        MovimientosColumnSetup.configureTipo(colTipo);
        MovimientosColumnSetup.configureCantidad(colCantidad);
        MovimientosColumnSetup.configureStock(colStock);
        MovimientosColumnSetup.configureMotivo(colMotivo);
        MovimientosColumnSetup.configureUsuario(colUsuario);
        MovimientosColumnSetup.configureFecha(colFecha);
        MovimientosColumnSetup.configureEstado(colEstado);
        MovimientosColumnSetup.configureRowFactory(table, () -> pendingHighlightId);

        table.getSortOrder().clear();
        colFecha.setSortType(TableColumn.SortType.DESCENDING);
        table.getSortOrder().add(colFecha);

        // Smart empty state
        FontIcon emptyIcon = new FontIcon("mdi2s-swap-vertical");
        emptyIcon.setIconSize(52);
        emptyIcon.getStyleClass().add("empty-icon-lg");
        emptyStateMsg = new Label("No hay movimientos en el sistema");
        emptyStateMsg.getStyleClass().add("empty-state-msg");
        btnEmptyLimpiar = new Button("Limpiar filtros");
        btnEmptyLimpiar.getStyleClass().add("btn-secondary");
        btnEmptyLimpiar.setOnAction(e -> {
            searchField.clear();
            desdeFilter.setValue(null);
            hastaFilter.setValue(null);
            if (categoriaFilter != null) categoriaFilter.setValue(null);
            setActivePreset(null);
            if (tipoChipGroup != null)
                tipoChipGroup.getToggles().stream()
                    .filter(t -> "Todos".equals(((ToggleButton) t).getText()))
                    .findFirst().ifPresent(t -> t.setSelected(true));
            STICKY.put("search", ""); STICKY.put("desde", ""); STICKY.put("hasta", "");
            STICKY.put("categoria", ""); STICKY.put("tipo", "Todos");
            currentPage = 0;
            applyFilters();
        });
        btnEmptyLimpiar.setVisible(false); btnEmptyLimpiar.setManaged(false);
        boolean canAddMov = SessionManager.isAdmin() || SessionManager.isSecretario();
        emptyStateHint = new Label(canAddMov ? "Presiona Ctrl+N para registrar el primer movimiento" : "");
        emptyStateHint.getStyleClass().add("empty-state-hint");
        javafx.scene.layout.VBox emptyState = new javafx.scene.layout.VBox(12, emptyIcon, emptyStateMsg, btnEmptyLimpiar, emptyStateHint);
        emptyState.setAlignment(javafx.geometry.Pos.CENTER);
        emptyState.getStyleClass().add("empty-state-pane");
        emptyState.setMaxWidth(380);
        emptyState.setPadding(new javafx.geometry.Insets(32, 24, 32, 24));
        emptyState.visibleProperty().addListener((obs, wasVisible, isVisible) -> {
            if (isVisible && !wasVisible) com.sibim.util.AnimationUtils.springIn(emptyState);
            else if (!isVisible) { emptyState.setOpacity(1); emptyState.setScaleX(1); emptyState.setScaleY(1); }
        });
        emptyStatePlaceholder = emptyState;
        table.setPlaceholder(emptyState);
        DialogUtil.setupColumnVisibilityMenu("movimientos.cols", table,
            java.util.List.of(colProducto, colTipo, colFecha));
        DialogUtil.setupColumnReset(table, btnResetColumns, STICKY);
        DialogUtil.persistTableSort(table, STICKY, "sort");
        DialogUtil.persistColumnWidths(table, STICKY, "colW");
    }

    private void setupTipoChips() {
        tipoChipGroup = new ToggleGroup();
        String[][] chips = {
            {"Todos",         null},
            {"Entrada",       "filter-chip-green"},
            {"Salida",        "filter-chip-danger"},
            {"Ajuste",        "filter-chip-amber"},
            {"Transferencia", "filter-chip-teal"}
        };
        for (String[] entry : chips) {
            ToggleButton chip = new ToggleButton(entry[0]);
            chip.setToggleGroup(tipoChipGroup);
            chip.getStyleClass().add("filter-chip");
            if (entry[1] != null) chip.getStyleClass().add(entry[1]);
            if ("Todos".equals(entry[0])) chip.setSelected(true);
            chip.setOnAction(e -> { currentPage = 0; applyFilters(); });
            tipoChipsBar.getChildren().add(chip);
        }
    }

    private void setupFilters() {
        SearchUtils.debounce(searchField, 280, q -> { currentPage = 0; applyFilters(); });
        desdeFilter.setConverter(com.sibim.util.FormatUtils.datePickerConverter());
        hastaFilter.setConverter(com.sibim.util.FormatUtils.datePickerConverter());
        desdeFilter.valueProperty().addListener((o, a, b) -> { currentPage = 0; loadData(); setActivePreset(null); });
        hastaFilter.valueProperty().addListener((o, a, b) -> { currentPage = 0; loadData(); setActivePreset(null); });
        if (categoriaFilter != null)
            categoriaFilter.valueProperty().addListener((o, a, b) -> { currentPage = 0; applyFilters(); });
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

    // ── Data loading ──────────────────────────────────────────────────────────

    /** Loads the first page + stats + categoria options.  Called on initial
     *  load and whenever the date range changes (which affects which categories
     *  are in use). */
    private void loadData() {
        if (!loading.compareAndSet(false, true)) {
            refreshing = true;
            return;
        }
        skeletonPulse = AnimationUtils.buildSkeletonPlaceholder(table, 7);
        if (spinner != null) { spinner.setVisible(true); spinner.setManaged(true); }

        LocalDate desde = desdeFilter != null ? desdeFilter.getValue() : null;
        LocalDate hasta = hastaFilter != null ? hastaFilter.getValue() : null;
        String query      = searchField != null ? searchField.getText() : "";
        String tipo       = getSelectedTipoLabel();
        String categoria  = categoriaFilter != null ? categoriaFilter.getValue() : null;
        int    limit      = pageSize == Integer.MAX_VALUE ? Integer.MAX_VALUE : pageSize;
        int    offset     = currentPage * (pageSize == Integer.MAX_VALUE ? 0 : pageSize);

        record LoadResult(
            List<String> categorias,
            List<Movimiento> page,
            int count,
            MovimientoRepository.MovimientoStats stats) {}

        Task<LoadResult> task = new Task<>() {
            @Override protected LoadResult call() throws Exception {
                List<String> cats  = movimientoService.getCategorias(desde, hasta);
                List<Movimiento> p = movimientoService.getPaginated(
                    desde, hasta, query, tipo, categoria, limit, offset);
                int cnt = movimientoService.countFiltrado(desde, hasta, query, tipo, categoria);
                MovimientoRepository.MovimientoStats st = movimientoService.getStats(desde, hasta);
                return new LoadResult(cats, p, cnt, st);
            }
            @Override protected void succeeded() {
                loading.set(false);
                if (skeletonPulse != null) { skeletonPulse.stop(); skeletonPulse = null; }
                table.setPlaceholder(emptyStatePlaceholder);
                LoadResult r = getValue();

                // Populate categoria filter — preserve selection if still valid
                if (categoriaFilter != null) {
                    String prev = categoriaFilter.getValue();
                    categoriaFilter.getItems().setAll(new java.util.ArrayList<>());
                    categoriaFilter.getItems().add(null);
                    categoriaFilter.getItems().addAll(r.categorias());
                    if (prev != null && categoriaFilter.getItems().contains(prev))
                        categoriaFilter.setValue(prev);
                }

                totalFiltered = r.count();
                filteredData.setAll(r.page());
                AnimationUtils.staggerTableRows(table);
                updateTablePage();
                updateMovStats(r.stats());

                boolean hasFilters = hasActiveFilters();
                updateEmptyState(hasFilters);
                if (btnEmptyLimpiar != null) { btnEmptyLimpiar.setVisible(hasFilters); btnEmptyLimpiar.setManaged(hasFilters); }

                if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
                if (refreshing) { NotificacionUtil.info(table.getScene(), "Lista actualizada"); refreshing = false; }
            }
            @Override protected void failed() {
                loading.set(false);
                if (skeletonPulse != null) { skeletonPulse.stop(); skeletonPulse = null; }
                table.setPlaceholder(emptyStatePlaceholder);
                if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
                NotificacionUtil.error(table.getScene(), "No se pudo cargar los movimientos");
            }
        };
        AppExecutor.submit(task);
    }

    /** Loads only the current page (no categoria refresh, no stats reload).
     *  Called for prev/next page navigation and page-size changes. */
    private void loadPage() {
        LocalDate desde = desdeFilter != null ? desdeFilter.getValue() : null;
        LocalDate hasta = hastaFilter != null ? hastaFilter.getValue() : null;
        String query     = searchField != null ? searchField.getText() : "";
        String tipo      = getSelectedTipoLabel();
        String categoria = categoriaFilter != null ? categoriaFilter.getValue() : null;
        int    limit     = pageSize == Integer.MAX_VALUE ? Integer.MAX_VALUE : pageSize;
        int    offset    = currentPage * (pageSize == Integer.MAX_VALUE ? 0 : pageSize);
        // Persist sticky filters
        STICKY.put("search",    searchField    != null && searchField.getText() != null ? searchField.getText() : "");
        STICKY.put("desde",     desde          != null ? desde.toString()   : "");
        STICKY.put("hasta",     hasta          != null ? hasta.toString()   : "");
        STICKY.put("categoria", categoria      != null ? categoria          : "");
        STICKY.put("tipo",      tipo           != null ? tipo               : "Todos");

        record PageResult(List<Movimiento> page, int count) {}

        AppExecutor.submit(new Task<PageResult>() {
            @Override protected PageResult call() throws Exception {
                List<Movimiento> p = movimientoService.getPaginated(
                    desde, hasta, query, tipo, categoria, limit, offset);
                int cnt = movimientoService.countFiltrado(desde, hasta, query, tipo, categoria);
                return new PageResult(p, cnt);
            }
            @Override protected void succeeded() {
                PageResult r = getValue();
                totalFiltered = r.count();
                filteredData.setAll(r.page());
                AnimationUtils.staggerTableRows(table);
                updateTablePage();

                boolean hasFilters = hasActiveFilters();
                updateEmptyState(hasFilters);
                if (btnEmptyLimpiar != null) { btnEmptyLimpiar.setVisible(hasFilters); btnEmptyLimpiar.setManaged(hasFilters); }
            }
            @Override protected void failed() {
                NotificacionUtil.error(table.getScene(), "No se pudo cargar la página");
            }
        });
    }

    private String getSelectedTipoLabel() {
        if (tipoChipGroup == null) return "Todos";
        Toggle t = tipoChipGroup.getSelectedToggle();
        return t == null ? "Todos" : ((ToggleButton) t).getText();
    }

    private boolean hasActiveFilters() {
        String query    = searchField != null ? searchField.getText() : "";
        String tipo     = getSelectedTipoLabel();
        LocalDate desde = desdeFilter != null ? desdeFilter.getValue() : null;
        LocalDate hasta = hastaFilter != null ? hastaFilter.getValue() : null;
        String cat      = categoriaFilter != null ? categoriaFilter.getValue() : null;
        return !query.isBlank() || !tipo.equals("Todos") || desde != null || hasta != null || cat != null;
    }

    private void updateEmptyState(boolean hasFilters) {
        if (emptyStateMsg != null)
            emptyStateMsg.setText(hasFilters
                ? "No se encontraron movimientos con esos filtros"
                : "No hay movimientos registrados en el sistema");
        if (emptyStateHint != null) {
            boolean canAddMov = SessionManager.isAdmin() || SessionManager.isSecretario();
            emptyStateHint.setVisible(!hasFilters && canAddMov);
            emptyStateHint.setManaged(!hasFilters && canAddMov);
        }
    }

    private void applyFilters() {
        if (table == null) return;
        if (desdeFilter != null && hastaFilter != null
            && desdeFilter.getValue() != null && hastaFilter.getValue() != null
            && desdeFilter.getValue().isAfter(hastaFilter.getValue())) {
            NotificacionUtil.advertencia(table.getScene(), "La fecha inicial debe ser anterior a la fecha final");
            return;
        }
        currentPage = 0;
        loadPage();
    }

    private void updateMovStats(MovimientoRepository.MovimientoStats stats) {
        if (lblStatTotalMov != null) AnimationUtils.animateCount(lblStatTotalMov, stats.total(),    600);
        if (lblStatEntradas != null) AnimationUtils.animateCount(lblStatEntradas, stats.entradas(), 540);
        if (lblStatSalidas  != null) AnimationUtils.animateCount(lblStatSalidas,  stats.salidas(),  540);
        if (lblStatAjustes  != null) AnimationUtils.animateCount(lblStatAjustes,  stats.ajustes(),  540);
        javafx.animation.PauseTransition pop = new javafx.animation.PauseTransition(javafx.util.Duration.millis(620));
        pop.setOnFinished(e -> {
            if (statCardTotal   != null) AnimationUtils.statCardPop(statCardTotal);
            if (statCardEntrada != null) AnimationUtils.statCardPop(statCardEntrada);
            if (statCardSalida  != null) AnimationUtils.statCardPop(statCardSalida);
            if (statCardAjuste  != null) AnimationUtils.statCardPop(statCardAjuste);
        });
        pop.play();
    }

    private void updateTablePage() {
        PaginationUtils.updatePageServer(table, filteredData, currentPage, pageSize, totalFiltered,
            lblTotal, lblPage, btnPrev, btnNext, "movimiento", "movimientos");
    }

    /** Checks whether any pending-transfer movements in the current page have
     *  since changed estado (approved/rejected) and refreshes if so.
     *  With pagination we only see the current page — state changes are caught
     *  when the matching page is visible, which is acceptable. */
    private void checkEstadoCambios(List<Movimiento> page) {
        // No-op for now: the service's aprobar/rechazar workflow already
        // calls loadData() in its callback, which re-fetches fresh data.
    }

    @FXML private void onPrev() { if (currentPage > 0) { currentPage--; loadPage(); } }
    @FXML private void onNext() { currentPage++; loadPage(); }
    @FXML private void onRefresh() { loadData(); if (SessionManager.isAdmin()) loadPendientesCount(); }

    private void promptJumpToPage() {
        int totalPages = (int) Math.ceil((double) totalFiltered / Math.max(1, pageSize));
        if (totalPages <= 1) return;
        javafx.scene.control.TextInputDialog dlg = new javafx.scene.control.TextInputDialog(String.valueOf(currentPage + 1));
        dlg.setTitle("Ir a página");
        dlg.setHeaderText(null);
        dlg.setContentText("Página (1 – " + totalPages + "):");
        DialogUtil.applyOwner(dlg);
        DialogUtil.applyStylesheet(dlg.getDialogPane());
        dlg.showAndWait().ifPresent(txt -> {
            try {
                int page = Integer.parseInt(txt.trim()) - 1;
                if (page >= 0 && page < totalPages) { currentPage = page; loadPage(); }
            } catch (NumberFormatException ignored) {}
        });
    }

    @FXML
    private void onVerPendientes() {
        DialogUtil.runAsync(
            () -> movimientoService.getPendientesTransferencias(),
            this::showPendientesDialog,
            e -> NotificacionUtil.error(table.getScene(), "No se pudieron cargar las transferencias pendientes")
        );
    }

    private void loadPendientesCount() {
        DialogUtil.runAsync(
            () -> movimientoService.getPendientesTransferencias().size(),
            count -> {
                if (btnPendientes == null) return;
                btnPendientes.setText(count > 0
                    ? "Pendientes (" + count + ")"
                    : "Pendientes");
                btnPendientes.getStyleClass().removeAll("btn-secondary", "btn-warning-outline");
                btnPendientes.getStyleClass().add(count > 0 ? "btn-warning-outline" : "btn-secondary");
            },
            e -> { /* silent */ }
        );
    }

    private void showPendientesDialog(List<Movimiento> pendientes) {
        pendientesDialog.show(pendientes);
    }

    @FXML private void onPresetHoy() {
        LocalDate hoy = LocalDate.now();
        desdeFilter.setValue(hoy); hastaFilter.setValue(hoy);
        setActivePreset(btnPresetHoy);
    }
    @FXML private void onPresetSemana() {
        LocalDate hoy = LocalDate.now();
        desdeFilter.setValue(hoy.minusDays(6)); hastaFilter.setValue(hoy);
        setActivePreset(btnPresetSemana);
    }
    @FXML private void onPresetMes() {
        LocalDate hoy = LocalDate.now();
        desdeFilter.setValue(hoy.withDayOfMonth(1)); hastaFilter.setValue(hoy);
        setActivePreset(btnPresetMes);
    }
    @FXML private void onLimpiarFechas() {
        desdeFilter.setValue(null); hastaFilter.setValue(null);
        setActivePreset(null);
    }

    private void setActivePreset(Button active) {
        for (Button b : new Button[]{btnPresetHoy, btnPresetSemana, btnPresetMes}) {
            if (b == null) continue;
            b.getStyleClass().remove("btn-preset-active");
            if (b == active) b.getStyleClass().add("btn-preset-active");
        }
    }

    @FXML
    private void onNuevoMovimiento() { showMovimientoDialog(null, null); }

    @FXML
    private void onDelete() {
        Movimiento sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) {
            NotificacionUtil.advertencia(table.getScene(), "Selecciona un movimiento para eliminar");
            return;
        }
        if (!ConfirmacionUtil.confirmarEliminar("este movimiento")) return;
        Runnable doDelete = () -> DialogUtil.runAsync(
            () -> movimientoService.eliminar(sel.getId()),
            () -> {
                loadData();
                NotificacionUtil.exito(table.getScene(), "Movimiento eliminado");
            },
            e -> NotificacionUtil.error(table.getScene(),
                e instanceof MovimientoService.ValidationException ? e.getMessage() : "No se pudo eliminar el movimiento")
        );
        Node rowNode = table.lookup(".table-row-cell:selected");
        if (rowNode != null) {
            AnimationUtils.flashClass(rowNode, "row-danger", 200);
            AnimationUtils.fadeOut(rowNode, 260, doDelete);
        } else {
            doDelete.run();
        }
    }

    @FXML
    private void onExportCsv() {
        DialogUtil.runAsync(
            () -> reporteService.exportMovimientosCsv(
                desdeFilter != null ? desdeFilter.getValue() : null,
                hastaFilter != null ? hastaFilter.getValue() : null),
            file -> DialogUtil.showExportResultDialog(table.getScene(), file),
            e -> NotificacionUtil.error(table.getScene(), "No se pudo exportar el CSV")
        );
    }

    @FXML
    private void onExportExcel() {
        DialogUtil.runAsync(
            () -> reporteService.exportMovimientosExcel(
                desdeFilter != null ? desdeFilter.getValue() : null,
                hastaFilter != null ? hastaFilter.getValue() : null),
            file -> DialogUtil.showExportResultDialog(table.getScene(), file),
            e -> NotificacionUtil.error(table.getScene(), "No se pudo exportar el Excel")
        );
    }

    @FXML
    private void onClearFilters() {
        if (searchField != null) searchField.clear();
        if (desdeFilter != null) desdeFilter.setValue(null);
        if (hastaFilter != null) hastaFilter.setValue(null);
        if (categoriaFilter != null) categoriaFilter.setValue(null);
        setActivePreset(null);
        if (tipoChipGroup != null)
            tipoChipGroup.getToggles().stream()
                .filter(t -> "Todos".equals(((ToggleButton) t).getText()))
                .findFirst().ifPresent(t -> t.setSelected(true));
        STICKY.put("search", ""); STICKY.put("desde", ""); STICKY.put("hasta", "");
        STICKY.put("categoria", ""); STICKY.put("tipo", "Todos");
        currentPage = 0;
        loadData();
    }

    @FXML
    private void onExportSeleccionCsv() {
        List<Movimiento> sel = new java.util.ArrayList<>(table.getSelectionModel().getSelectedItems());
        if (sel.isEmpty()) { onExportCsv(); return; }
        DialogUtil.runAsync(
            () -> reporteService.exportMovimientosCsv(sel),
            file -> DialogUtil.showExportResultDialog(table.getScene(), file),
            e -> NotificacionUtil.error(table.getScene(), "No se pudo exportar el CSV")
        );
    }

    @FXML
    private void onExportSeleccionExcel() {
        List<Movimiento> sel = new java.util.ArrayList<>(table.getSelectionModel().getSelectedItems());
        if (sel.isEmpty()) { onExportExcel(); return; }
        DialogUtil.runAsync(
            () -> reporteService.exportMovimientosExcel(sel),
            file -> DialogUtil.showExportResultDialog(table.getScene(), file),
            e -> NotificacionUtil.error(table.getScene(), "No se pudo exportar el Excel")
        );
    }

    public void showMovimientoDialog(String preProductoId, TipoMovimiento preTipo) {
        showMovimientoDialog(preProductoId, preTipo, null);
    }

    private void showMovimientoDialog(String preProductoId, TipoMovimiento preTipo, MovimientoDialogFactory.Result retryFrom) {
        DialogUtil.runAsync(
            () -> productoService.getAll(),
            productos -> MovimientoDialogFactory.show(productos, preProductoId, preTipo, retryFrom).ifPresent(r ->
                DialogUtil.runAsync(
                    () -> movimientoService.registrar(
                        r.producto().getId(), r.tipo(), r.cantidad(), r.motivo(), r.referencia(), r.areaDestino()),
                    m -> {
                        if (table != null) {
                            currentPage = 0;
                            pendingHighlightId = m.getId();
                            loadData();
                            table.scrollTo(0);
                            new Timeline(new KeyFrame(Duration.seconds(1.8), e2 -> {
                                pendingHighlightId = null;
                                table.refresh();
                            })).play();
                            if (table.getScene() != null) {
                                if (m.getTipo() == TipoMovimiento.TRANSFERENCIA)
                                    NotificacionUtil.exitoTransferencia(table.getScene(),
                                        m.getProductoNombre(), m.getAreaOrigen(), m.getAreaDestino());
                                else
                                    NotificacionUtil.exito(table.getScene(), "Movimiento registrado correctamente");
                            }

                            if (statCardTotal != null) AnimationUtils.statCardPop(statCardTotal);
                            VBox targetCard = switch (m.getTipo()) {
                                case ENTRADA -> statCardEntrada;
                                case SALIDA  -> statCardSalida;
                                default      -> statCardAjuste;
                            };
                            if (targetCard != null) AnimationUtils.statCardPop(targetCard);
                        }
                    },
                    e -> {
                        if (table != null && table.getScene() != null)
                            NotificacionUtil.error(table.getScene(),
                                (e instanceof MovimientoService.ValidationException ? e.getMessage() : "No se pudo registrar el movimiento")
                                    + " — revisa los datos e inténtalo de nuevo");
                        Platform.runLater(() -> showMovimientoDialog(preProductoId, preTipo, r));
                    }
                )),
            e -> {
                log.error("Error al abrir el formulario de movimiento", e);
                if (table != null && table.getScene() != null)
                    NotificacionUtil.error(table.getScene(), "Error al abrir el formulario. Verifica la conexión a la base de datos.");
            }
        );
    }

}
