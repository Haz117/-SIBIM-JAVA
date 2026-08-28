package com.sibim.controller;

import com.sibim.controller.dialogs.MovimientoDetailDialog;
import com.sibim.controller.dialogs.MovimientoDialogFactory;
import com.sibim.controller.dialogs.PendientesTransferenciasDialog;
import com.sibim.controller.dialogs.ProductoDetailDialog;
import org.kordamp.ikonli.javafx.FontIcon;
import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.service.MovimientoService;
import com.sibim.service.ProductoService;
import com.sibim.service.ReporteService;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppExecutor;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import com.sibim.util.PaginationUtils;
import com.sibim.util.SearchUtils;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.util.Duration;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MovimientosController {

    private static final Logger log = LoggerFactory.getLogger(MovimientosController.class);

    // Tracks estado of current user's transfers across navigations (static = survives
    // controller re-instantiation when the user navigates away and back).
    // key=movimientoId, value=last-known estado string.
    private static final java.util.concurrent.ConcurrentHashMap<String, String> ESTADO_TRACK =
        new java.util.concurrent.ConcurrentHashMap<>();

    @FXML private VBox rootPane;
    @FXML private javafx.scene.layout.FlowPane filterBar;
    @FXML private Button btnToggleFiltros;
    @FXML private TextField searchField;
    @FXML private HBox tipoChipsBar;
    @FXML private DatePicker desdeFilter;
    @FXML private DatePicker hastaFilter;
    @FXML private TableView<Movimiento> table;
    @FXML private TableColumn<Movimiento, String> colProducto;
    @FXML private TableColumn<Movimiento, String> colTipo;
    @FXML private TableColumn<Movimiento, Integer> colCantidad;
    @FXML private TableColumn<Movimiento, String> colStock;
    @FXML private TableColumn<Movimiento, String> colMotivo;
    @FXML private TableColumn<Movimiento, String> colUsuario;
    @FXML private TableColumn<Movimiento, String> colFecha;
    @FXML private TableColumn<Movimiento, String> colEstado;
    @FXML private Label lblTotal;
    @FXML private Label lblTotalAll;
    @FXML private Label lblSeleccionados;
    @FXML private javafx.scene.control.MenuButton btnExportarSeleccion;
    @FXML private Button btnClearFilters;
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
    @FXML private Button btnClearSearch;
    @FXML private Button btnPendientes;
    @FXML private Label helpAjustes;
    @FXML private Label helpTotalMov;
    @FXML private Label helpEntradas;
    @FXML private Label helpSalidas;
    @FXML private ComboBox<String> categoriaFilter;

    private final MovimientoService movimientoService = new MovimientoService();
    private final ProductoService productoService = new ProductoService();
    private final ReporteService reporteService = new ReporteService();

    private ObservableList<Movimiento> allData = FXCollections.observableArrayList();
    private ObservableList<Movimiento> filteredData = FXCollections.observableArrayList();
    private int currentPage = 0;
    private int pageSize = 25;
    private ToggleGroup tipoChipGroup;
    private String pendingHighlightId;
    private Label emptyStateMsg;
    private Label emptyStateHint;
    private Button btnEmptyLimpiar;
    /** productoId → nombre de categoría, para el filtro "Categoría" — Movimiento
     *  no trae la categoría del bien, así que se resuelve del lado del cliente
     *  contra el catálogo de productos ya cargado en memoria. */
    private java.util.Map<String, String> categoriaPorProducto = java.util.Map.of();
    private boolean refreshing = false;
    private final AtomicBoolean loading           = new AtomicBoolean(false);
    private final AtomicBoolean loadingCategorias = new AtomicBoolean(false);

    @FXML
    public void initialize() {
        if (btnToggleFiltros != null && filterBar != null)
            DialogUtil.makeCollapsible("movimientos.filtros.colapsado", btnToggleFiltros, filterBar);
        setupTable();
        // Default a los últimos 90 días — evita cargar toda la historia
        // de movimientos en el arranque. Los listeners se conectan en
        // setupFilters(), así que asignar aquí no dispara ninguna carga prematura.
        if (desdeFilter != null && desdeFilter.getValue() == null)
            desdeFilter.setValue(java.time.LocalDate.now().minusDays(89));
        if (hastaFilter != null && hastaFilter.getValue() == null)
            hastaFilter.setValue(java.time.LocalDate.now());
        setupFilters();
        setupTipoChips();
        setupPagination();
        if (helpAjustes   != null) DialogUtil.enableClickToShowTooltip(helpAjustes);
        if (helpTotalMov  != null) DialogUtil.enableClickToShowTooltip(helpTotalMov);
        if (helpEntradas  != null) DialogUtil.enableClickToShowTooltip(helpEntradas);
        if (helpSalidas   != null) DialogUtil.enableClickToShowTooltip(helpSalidas);
        loadCategoriaFilter();

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
                }
            });
        }

        table.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);

        // Selection → enable/disable action buttons + status label
        table.getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener<com.sibim.model.Movimiento>) c -> {
            int n = table.getSelectionModel().getSelectedItems().size();
            if (btnDelete            != null) btnDelete.setDisable(n != 1);
            if (btnExportarSeleccion != null) btnExportarSeleccion.setDisable(n == 0);
            updateSelectionLabel(n);
        });

        // Delete key on table
        table.setOnKeyPressed(ev -> {
            if (ev.getCode() == javafx.scene.input.KeyCode.DELETE
                    && table.getSelectionModel().getSelectedItem() != null) {
                onDelete(); ev.consume();
            } else if (ev.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                table.getSelectionModel().clearSelection(); ev.consume();
            }
        });

        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null)
                MovimientoDetailDialog.show(table.getSelectionModel().getSelectedItem(), table.getScene(), productoService, movimientoService, log);
        });

        // Context menu
        ContextMenu cm = new ContextMenu();
        MenuItem cmDetalle = new MenuItem("Ver detalle");
        cmDetalle.setGraphic(new FontIcon("mdi2e-eye-outline"));
        cmDetalle.setOnAction(e -> {
            Movimiento sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) MovimientoDetailDialog.show(sel, table.getScene(), productoService, movimientoService, log);
        });
        cm.getItems().add(cmDetalle);
        cm.getItems().add(new SeparatorMenuItem());
        MenuItem cmVerBien = new MenuItem("Ver ficha del bien");
        cmVerBien.setGraphic(new FontIcon("mdi2f-file-document-outline"));
        cmVerBien.setOnAction(e -> {
            Movimiento sel = table.getSelectionModel().getSelectedItem();
            if (sel == null || sel.getProductoId() == null) return;
            DialogUtil.runAsyncWithProgress(table.getScene(), "Cargando bien…",
                () -> productoService.findById(sel.getProductoId()),
                opt -> opt.ifPresent(p -> ProductoDetailDialog.show(p, table.getScene(), movimientoService, log)),
                ex -> { log.error("Error cargando ficha del bien desde movimientos", ex); NotificacionUtil.error(table.getScene(), "No se pudo cargar el bien"); });
        });
        cm.getItems().add(cmVerBien);
        boolean canDelete = SessionManager.isAdmin() || SessionManager.isSecretario();
        if (canDelete) {
            cm.getItems().add(new SeparatorMenuItem());
            MenuItem cmEliminar = new MenuItem("Eliminar");
            cmEliminar.setGraphic(new FontIcon("mdi2d-delete-outline"));
            cmEliminar.setOnAction(e -> onDelete());
            cm.getItems().add(cmEliminar);
        }
        table.setContextMenu(cm);

        if (btnClearSearch != null) {
            searchField.textProperty().addListener((obs, o, n) -> btnClearSearch.setVisible(!n.isBlank()));
            btnClearSearch.setOnAction(e -> { searchField.clear(); searchField.requestFocus(); });
        }
        if (SessionManager.isAdmin()) {
            if (btnPendientes != null) { btnPendientes.setVisible(true); btnPendientes.setManaged(true); }
            loadPendientesCount();
        } else {
            if (btnPendientes != null) { btnPendientes.setVisible(false); btnPendientes.setManaged(false); }
        }
        loadData();
        AnimationUtils.staggeredFadeInUp(
            java.util.List.of(statCardTotal, statCardEntrada, statCardSalida, statCardAjuste), 300, 55);
        Platform.runLater(() -> { if (searchField != null) searchField.requestFocus(); });
    }

    private void setupTable() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        colProducto.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getProductoNombre() != null ? c.getValue().getProductoNombre() : ""));
        colProducto.setCellFactory(col -> new TableCell<>() {
            private final Tooltip tip = new Tooltip();
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setTooltip(null); return; }
                setText(item);
                tip.setText(item);
                setTooltip(tip);
            }
        });
        colTipo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTipo().getEtiqueta()));
        colCantidad.setCellValueFactory(new PropertyValueFactory<>("cantidad"));
        colStock.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getStockAnterior() + " → " + c.getValue().getStockNuevo()));
        colStock.setCellFactory(col -> new TableCell<>() {
            private final Label lblAntes   = new Label();
            private final Label lblArrow   = new Label();
            private final Label lblDespues = new Label();
            private final HBox  box        = new HBox(4, lblAntes, lblArrow, lblDespues);
            {
                lblAntes.getStyleClass().add("stock-before");
                box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(null); setText(null);
                if (empty || item == null || getTableRow() == null) return;
                Movimiento m = getTableRow().getItem();
                if (m == null) return;
                int antes = m.getStockAnterior(), despues = m.getStockNuevo();
                lblAntes.setText(String.valueOf(antes));
                lblArrow.setText(antes < despues ? "↑" : (antes > despues ? "↓" : "·"));
                lblArrow.getStyleClass().setAll(
                    antes < despues ? "stock-arrow-up" : (antes > despues ? "stock-arrow-down" : "stock-arrow-neutral"));
                lblDespues.setText(String.valueOf(despues));
                lblDespues.getStyleClass().setAll(
                    despues <= 0 ? "stock-after-empty" : (despues < antes ? "stock-after-warn" : "stock-after-ok"));
                setGraphic(box);
            }
        });
        colMotivo.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getMotivo() != null ? c.getValue().getMotivo() : ""));
        colMotivo.setCellFactory(col -> new TableCell<>() {
            private final Tooltip tip = new Tooltip();
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) { setText(null); setTooltip(null); return; }
                setText(item);
                tip.setText(item);
                setTooltip(tip);
            }
        });
        colUsuario.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUsuarioNombre()));
        colUsuario.setCellFactory(col -> new TableCell<>() {
            private final Tooltip tip = new Tooltip();
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setTooltip(null); return; }
                setText(item);
                tip.setText(item);
                setTooltip(tip);
            }
        });
        colFecha.setCellValueFactory(c ->
            new SimpleStringProperty(FormatUtils.formatDateTime(c.getValue().getCreadoEn())));

        // Tipo badge cell
        colTipo.setCellFactory(DialogUtil.badgeCellFactory(item -> switch (item) {
            case "Entrada"       -> "cell-badge-success";
            case "Salida"        -> "cell-badge-danger";
            case "Ajuste"        -> "cell-badge-warning";
            case "Transferencia" -> "cell-badge-blue";
            default              -> "cell-badge-purple";
        }));

        // Cantidad coloring based on tipo
        colCantidad.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Integer value, boolean empty) {
                super.updateItem(value, empty);
                setText(null); getStyleClass().removeAll("stock-ok","stock-low","stock-warn");
                if (empty || value == null) return;
                setText(String.valueOf(value));
                if (getTableRow() != null && getTableRow().getItem() != null) {
                    String tipo = ((Movimiento) getTableRow().getItem()).getTipo().getEtiqueta();
                    if ("Entrada".equals(tipo)) getStyleClass().add("stock-ok");
                    else if ("Salida".equals(tipo)) getStyleClass().add("stock-low");
                    else getStyleClass().add("stock-warn");
                }
            }
        });

        // Row tints by movement type; new-row flash via pendingHighlightId
        table.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Movimiento m, boolean empty) {
                super.updateItem(m, empty);
                getStyleClass().removeAll("row-entrada","row-salida","row-ajuste","row-transferencia","row-new");
                if (!empty && m != null) {
                    String clase = switch (m.getTipo().getEtiqueta()) {
                        case "Entrada"       -> "row-entrada";
                        case "Salida"        -> "row-salida";
                        case "Ajuste"        -> "row-ajuste";
                        case "Transferencia" -> "row-transferencia";
                        default              -> null;
                    };
                    if (clase != null) getStyleClass().add(clase);
                    if (m.getId() != null && m.getId().equals(pendingHighlightId))
                        getStyleClass().add("row-new");
                }
            }
        });

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
        table.setPlaceholder(emptyState);

        // Estado badge — only meaningful for TRANSFERENCIA rows
        colEstado.setCellValueFactory(c -> {
            Movimiento m = c.getValue();
            if (m.getTipo() != com.sibim.model.enums.TipoMovimiento.TRANSFERENCIA) return new SimpleStringProperty("");
            return new SimpleStringProperty(switch (m.getEstado()) {
                case Movimiento.ESTADO_PENDIENTE -> "Pendiente";
                case Movimiento.ESTADO_RECHAZADO -> "Rechazada";
                default -> "";
            });
        });
        colEstado.setCellFactory(DialogUtil.badgeCellFactory(item -> switch (item) {
            case "Pendiente" -> "cell-badge-warning";
            case "Rechazada" -> "cell-badge-danger";
            default          -> "cell-badge-hidden";
        }));

        // Clic derecho en encabezado → toggle columnas secundarias
        DialogUtil.setupColumnVisibilityMenu("movimientos.cols", table,
            List.of(colProducto, colTipo, colCantidad));
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
        desdeFilter.valueProperty().addListener((o, a, b) -> { currentPage = 0; loadData(); setActivePreset(null); });
        hastaFilter.valueProperty().addListener((o, a, b) -> { currentPage = 0; loadData(); setActivePreset(null); });
        if (categoriaFilter != null) {
            categoriaFilter.setConverter(new javafx.util.StringConverter<>() {
                public String toString(String s)   { return s == null ? "Todas las categorías" : s; }
                public String fromString(String s) { return null; }
            });
            categoriaFilter.valueProperty().addListener((o, a, b) -> { currentPage = 0; applyFilters(); });
        }
    }

    /** Loads the product catalog once to (a) resolve each movimiento's
     *  categoría for the "Categoría" filter, since Movimiento itself doesn't
     *  carry it, and (b) populate the filter's dropdown with the distinct
     *  category names actually in use. */
    private void loadCategoriaFilter() {
        if (categoriaFilter == null) return;
        if (!loadingCategorias.compareAndSet(false, true)) return;
        AppExecutor.submit(new Task<List<Producto>>() {
            @Override protected List<Producto> call() throws Exception { return productoService.getAll(); }
            @Override protected void succeeded() {
                loadingCategorias.set(false);
                List<Producto> productos = getValue();
                categoriaPorProducto = productos.stream()
                    .filter(p -> p.getCategoriaNombre() != null)
                    .collect(java.util.stream.Collectors.toMap(
                        Producto::getId, Producto::getCategoriaNombre, (a, b) -> a));
                List<String> nombres = categoriaPorProducto.values().stream()
                    .distinct().sorted().toList();
                List<String> opciones = new java.util.ArrayList<>(nombres.size() + 1);
                opciones.add(null);
                opciones.addAll(nombres);
                categoriaFilter.setItems(FXCollections.observableArrayList(opciones));
            }
            @Override protected void failed() { loadingCategorias.set(false); }
        });
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
            updateTablePage();
        });
    }

    private void loadData() {
        if (!loading.compareAndSet(false, true)) {
            refreshing = true;
            return;
        }
        if (spinner != null) { spinner.setVisible(true); spinner.setManaged(true); }
        java.time.LocalDate desde = desdeFilter != null && desdeFilter.getValue() != null
            ? desdeFilter.getValue() : java.time.LocalDate.now().minusDays(89);
        java.time.LocalDate hasta = hastaFilter != null && hastaFilter.getValue() != null
            ? hastaFilter.getValue() : java.time.LocalDate.now();
        AppExecutor.submit(new Task<List<Movimiento>>() {
            @Override protected List<Movimiento> call() throws Exception { return movimientoService.getByDateRange(desde, hasta); }
            @Override protected void succeeded() {
                loading.set(false);
                List<Movimiento> nuevos = getValue();
                checkEstadoCambios(nuevos);
                allData.setAll(nuevos);
                currentPage = 0;
                applyFilters();
                if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
                if (refreshing) { NotificacionUtil.info(table.getScene(), "Lista actualizada"); refreshing = false; }
            }
            @Override protected void failed() {
                loading.set(false);
                if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
                NotificacionUtil.errorConAccion(table.getScene(),
                    "No se pudo cargar los movimientos", "Reintentar", () -> loadData());
            }
        });
    }

    private String getSelectedTipo() {
        if (tipoChipGroup == null) return "Todos";
        Toggle t = tipoChipGroup.getSelectedToggle();
        return t == null ? "Todos" : ((ToggleButton) t).getText();
    }

    private void applyFilters() {
        if (table == null) return;
        if (desdeFilter != null && hastaFilter != null
            && desdeFilter.getValue() != null && hastaFilter.getValue() != null
            && desdeFilter.getValue().isAfter(hastaFilter.getValue())) {
            NotificacionUtil.advertencia(table.getScene(), "La fecha inicial debe ser anterior a la fecha final");
            return;
        }
        String query = searchField != null ? searchField.getText().toLowerCase() : "";
        String tipo = getSelectedTipo();
        LocalDate desde = desdeFilter != null ? desdeFilter.getValue() : null;
        LocalDate hasta = hastaFilter != null ? hastaFilter.getValue() : null;
        String categoria = categoriaFilter != null ? categoriaFilter.getValue() : null;

        filteredData.setAll(allData.stream()
            .filter(m -> query.isBlank()
                || (m.getProductoNombre() != null && m.getProductoNombre().toLowerCase().contains(query))
                || (m.getReferencia() != null && m.getReferencia().toLowerCase().contains(query))
                || (m.getMotivo() != null && m.getMotivo().toLowerCase().contains(query)))
            .filter(m -> "Todos".equals(tipo) || m.getTipo().getEtiqueta().equals(tipo))
            .filter(m -> categoria == null || categoria.equals(categoriaPorProducto.get(m.getProductoId())))
            .toList());

        boolean hasFilters = !query.isBlank() || !tipo.equals("Todos") || desde != null || hasta != null || categoria != null;
        if (emptyStateMsg != null)
            emptyStateMsg.setText(hasFilters
                ? "No se encontraron movimientos con esos filtros"
                : "No hay movimientos registrados en el sistema");
        if (btnEmptyLimpiar != null) {
            btnEmptyLimpiar.setVisible(hasFilters);
            btnEmptyLimpiar.setManaged(hasFilters);
        }
        if (btnClearFilters != null) {
            btnClearFilters.setVisible(hasFilters);
            btnClearFilters.setManaged(hasFilters);
        }
        if (lblTotalAll != null) {
            if (hasFilters) {
                lblTotalAll.setText("de " + allData.size() + " total");
                lblTotalAll.setVisible(true);
                lblTotalAll.setManaged(true);
            } else {
                lblTotalAll.setVisible(false);
                lblTotalAll.setManaged(false);
            }
        }
        if (emptyStateHint != null) {
            boolean canAddMov = SessionManager.isAdmin() || SessionManager.isSecretario();
            emptyStateHint.setVisible(!hasFilters && canAddMov);
            emptyStateHint.setManaged(!hasFilters && canAddMov);
        }

        updateMovStats();
        updateTablePage();
    }

    private void updateMovStats() {
        long total    = filteredData.size();
        long entradas = filteredData.stream().filter(m -> "Entrada".equals(m.getTipo().getEtiqueta())).count();
        long salidas  = filteredData.stream().filter(m -> "Salida".equals(m.getTipo().getEtiqueta())).count();
        long ajustes  = filteredData.stream().filter(m ->
            "Ajuste".equals(m.getTipo().getEtiqueta()) || "Transferencia".equals(m.getTipo().getEtiqueta())).count();
        if (lblStatTotalMov != null) AnimationUtils.animateCount(lblStatTotalMov, total,    600);
        if (lblStatEntradas != null) AnimationUtils.animateCount(lblStatEntradas, entradas, 540);
        if (lblStatSalidas  != null) AnimationUtils.animateCount(lblStatSalidas,  salidas,  540);
        if (lblStatAjustes  != null) AnimationUtils.animateCount(lblStatAjustes,  ajustes,  540);
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
        PaginationUtils.updatePage(table, filteredData, currentPage, pageSize,
            lblTotal, lblPage, btnPrev, btnNext, "movimiento", "movimientos");
    }

    @FXML private void onPrev() { if (currentPage > 0) { currentPage--; updateTablePage(); } }
    @FXML private void onNext() { currentPage++; updateTablePage(); }
    @FXML private void onRefresh() { loadData(); if (SessionManager.isAdmin()) loadPendientesCount(); }

    @FXML
    private void onVerPendientes() {
        DialogUtil.runAsync(
            () -> movimientoService.getPendientesTransferencias(),
            pendientes -> PendientesTransferenciasDialog.show(pendientes, table.getScene(), movimientoService, this::loadData, this::loadPendientesCount),
            e -> NotificacionUtil.error(table.getScene(), "No se pudieron cargar las transferencias pendientes")
        );
    }

    /** Compares the new movement list against the last-known estados for the
     *  current user's transfers. Shows a toast if any changed to APROBADO or
     *  RECHAZADO since the previous load. */
    private void checkEstadoCambios(List<Movimiento> nuevos) {
        var user = SessionManager.getCurrentUser();
        if (user == null) return;
        String myId = user.getId();
        boolean firstLoad = ESTADO_TRACK.isEmpty();

        for (Movimiento m : nuevos) {
            if (m.getTipo() != com.sibim.model.enums.TipoMovimiento.TRANSFERENCIA) continue;
            if (!myId.equals(m.getUsuarioId())) continue;
            String prev = ESTADO_TRACK.get(m.getId());
            String curr = m.getEstado();
            if (!firstLoad && prev != null && !prev.equals(curr)) {
                if (Movimiento.ESTADO_APROBADO.equals(curr)) {
                    NotificacionUtil.exito(table.getScene(),
                        "Tu transferencia de \"" + m.getProductoNombre() + "\" fue aprobada");
                } else if (Movimiento.ESTADO_RECHAZADO.equals(curr)) {
                    NotificacionUtil.error(table.getScene(),
                        "Tu transferencia de \"" + m.getProductoNombre() + "\" fue rechazada");
                }
            }
            ESTADO_TRACK.put(m.getId(), curr);
        }
        // Seed on first load so subsequent refreshes can detect changes
        if (firstLoad) {
            nuevos.stream()
                .filter(m -> m.getTipo() == com.sibim.model.enums.TipoMovimiento.TRANSFERENCIA
                          && myId.equals(m.getUsuarioId()))
                .forEach(m -> ESTADO_TRACK.put(m.getId(), m.getEstado()));
        }
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

    @FXML
    private void onClearFilters() {
        searchField.clear();
        desdeFilter.setValue(null);
        hastaFilter.setValue(null);
        if (categoriaFilter != null) categoriaFilter.setValue(null);
        setActivePreset(null);
        if (tipoChipGroup != null)
            tipoChipGroup.getToggles().stream()
                .filter(t -> "Todos".equals(((ToggleButton) t).getText()))
                .findFirst().ifPresent(t -> t.setSelected(true));
        currentPage = 0;
        applyFilters();
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
                allData.remove(sel);
                applyFilters();
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
        DialogUtil.runAsyncWithProgress(table.getScene(), "Generando CSV…",
            () -> reporteService.exportMovimientosCsv(
                desdeFilter != null ? desdeFilter.getValue() : null,
                hastaFilter != null ? hastaFilter.getValue() : null),
            file -> { NotificacionUtil.exito(table.getScene(), "CSV exportado correctamente"); openFile(file); },
            e -> NotificacionUtil.error(table.getScene(), "No se pudo exportar el CSV")
        );
    }

    @FXML
    private void onExportExcel() {
        DialogUtil.runAsyncWithProgress(table.getScene(), "Generando Excel…",
            () -> reporteService.exportMovimientosExcel(
                desdeFilter != null ? desdeFilter.getValue() : null,
                hastaFilter != null ? hastaFilter.getValue() : null),
            file -> { NotificacionUtil.exito(table.getScene(), "Excel exportado correctamente"); openFile(file); },
            e -> NotificacionUtil.error(table.getScene(), "No se pudo exportar el Excel")
        );
    }

    public void showMovimientoDialog(String preProductoId, TipoMovimiento preTipo) {
        showMovimientoDialog(preProductoId, preTipo, null);
    }

    private void showMovimientoDialog(String preProductoId, TipoMovimiento preTipo, MovimientoDialogFactory.Result retryFrom) {
        try {
            List<Producto> productos = productoService.getAll();
            MovimientoDialogFactory.show(productos, preProductoId, preTipo, retryFrom).ifPresent(r ->
                DialogUtil.runAsync(
                    () -> movimientoService.registrar(
                        r.producto().getId(), r.tipo(), r.cantidad(), r.motivo(), r.referencia(), r.areaDestino()),
                    m -> {
                        if (table != null) {
                            allData.add(0, m);
                            currentPage = 0;
                            pendingHighlightId = m.getId();
                            applyFilters();
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

                            // Animate the matching stat card + total
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
                        // Reopen pre-filled with what the user entered (r has
                        // it all) instead of just erroring and losing it —
                        // same reasoning as ProductosController#showProductDialog.
                        if (table != null && table.getScene() != null)
                            NotificacionUtil.error(table.getScene(),
                                (e instanceof MovimientoService.ValidationException ? e.getMessage() : "No se pudo registrar el movimiento")
                                    + " — revisa los datos e inténtalo de nuevo");
                        Platform.runLater(() -> showMovimientoDialog(preProductoId, preTipo, r));
                    }
                ));
        } catch (Exception e) {
            log.error("Error al abrir el formulario de movimiento", e);
            if (table != null && table.getScene() != null)
                NotificacionUtil.error(table.getScene(), "Error al abrir el formulario. Verifica la conexión a la base de datos.");
        }
    }

    @FXML
    private void onExportSeleccionCsv() {
        java.util.List<com.sibim.model.Movimiento> sel = java.util.List.copyOf(table.getSelectionModel().getSelectedItems());
        if (sel.isEmpty()) return;
        DialogUtil.runAsyncWithProgress(table.getScene(), "Generando CSV…",
            () -> reporteService.exportMovimientosCsv(sel),
            file -> { NotificacionUtil.exito(table.getScene(), sel.size() + " movimiento(s) exportado(s) a CSV"); openFile(file); },
            e -> NotificacionUtil.errorConAccion(table.getScene(), "No se pudo exportar el CSV", "Reintentar", this::onExportSeleccionCsv)
        );
    }

    @FXML
    private void onExportSeleccionExcel() {
        java.util.List<com.sibim.model.Movimiento> sel = java.util.List.copyOf(table.getSelectionModel().getSelectedItems());
        if (sel.isEmpty()) return;
        DialogUtil.runAsyncWithProgress(table.getScene(), "Generando Excel…",
            () -> reporteService.exportMovimientosExcel(sel),
            file -> { NotificacionUtil.exito(table.getScene(), sel.size() + " movimiento(s) exportado(s) a Excel"); openFile(file); },
            e -> NotificacionUtil.errorConAccion(table.getScene(), "No se pudo exportar el Excel", "Reintentar", this::onExportSeleccionExcel)
        );
    }

    private void updateSelectionLabel(int n) {
        if (lblSeleccionados == null) return;
        if (n > 0) {
            lblSeleccionados.setText("· " + n + (n == 1 ? " seleccionado" : " seleccionados"));
            lblSeleccionados.setVisible(true);
            lblSeleccionados.setManaged(true);
        } else {
            lblSeleccionados.setVisible(false);
            lblSeleccionados.setManaged(false);
        }
    }

    private void openFile(File file) {
        try { Desktop.getDesktop().open(file); }
        catch (Exception e) {
            log.warn("No se pudo abrir el archivo {}", file, e);
            if (table != null && table.getScene() != null)
                NotificacionUtil.advertencia(table.getScene(), "Guardado en: " + file.getAbsolutePath());
        }
    }

}
