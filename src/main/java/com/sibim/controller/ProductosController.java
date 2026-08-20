package com.sibim.controller;

import com.sibim.config.Areas;
import com.sibim.controller.dialogs.ConteoFisicoDialog;
import com.sibim.controller.dialogs.ProductoDialogFactory;
import com.sibim.model.Categoria;
import com.sibim.model.Producto;
import com.sibim.model.enums.EstadoProducto;
import com.sibim.repository.CategoriaRepository;
import com.sibim.service.MovimientoService;
import com.sibim.service.ProductoService;
import com.sibim.service.ReporteService;
import com.sibim.session.NavigationContext;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import com.sibim.util.PaginationUtils;
import com.sibim.util.SearchUtils;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    @FXML private VBox rootPane;
    @FXML private TextField searchField;
    @FXML private ComboBox<Categoria> categoriaFilter;
    @FXML private ComboBox<String> areaFilter;
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
    @FXML private ComboBox<Integer> pageSizeBox;
    @FXML private Label lblPage;
    @FXML private Button btnPrev;
    @FXML private Button btnNext;
    @FXML private Button btnEditar;
    @FXML private Button btnEliminar;
    @FXML private Button btnClearSearch;
    @FXML private ProgressIndicator spinner;
    @FXML private Label lblStatTotal;
    @FXML private Label lblStatValor;
    @FXML private Label lblStatAlertas;
    @FXML private VBox statCardTotal;
    @FXML private VBox statCardValor;
    @FXML private VBox cardAlertas;

    private final ProductoService productoService = new ProductoService();
    private final CategoriaRepository categoriaRepo = new CategoriaRepository();
    private final ReporteService reporteService = new ReporteService();
    private final MovimientoService movimientoService = new MovimientoService();

    private ObservableList<Producto> allData = FXCollections.observableArrayList();
    private ObservableList<Producto> filteredData = FXCollections.observableArrayList();
    private int currentPage = 0;
    private int pageSize = 25;
    private boolean refreshing = false;
    private boolean canEdit = false;
    private String pendingHighlightId;
    private ToggleGroup estadoChipGroup;
    private Label emptyStateMsg;
    private Label emptyStateHint;
    private Button btnEmptyLimpiar;

    @FXML
    public void initialize() {
        canEdit = SessionManager.isAdmin() || SessionManager.isSecretario();
        setupTable();
        setupFilters();
        setupStatusChips();
        if (btnEditar   != null) { btnEditar.setVisible(canEdit);   btnEditar.setManaged(canEdit); }
        if (btnEliminar != null) { btnEliminar.setVisible(canEdit); btnEliminar.setManaged(canEdit); }
        if (rootPane != null && canEdit) {
            rootPane.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, ev -> {
                if (ev.getCode() == javafx.scene.input.KeyCode.N && ev.isControlDown()) {
                    onNuevoBien(); ev.consume();
                } else if (ev.getCode() == javafx.scene.input.KeyCode.E && ev.isControlDown()
                        && table.getSelectionModel().getSelectedItem() != null) {
                    onEdit(); ev.consume();
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
    }

    private void setupTable() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        // Image thumbnail column
        colFoto.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFotoUrl()));
        colFoto.setCellFactory(col -> new TableCell<>() {
            private final ImageView iv = new ImageView();
            private final Label lbl = new Label("📷");
            private final StackPane box;
            {
                iv.setFitWidth(38); iv.setFitHeight(38); iv.setPreserveRatio(true);
                lbl.getStyleClass().add("foto-placeholder");
                box = new StackPane(lbl, iv);
                box.setPrefSize(40, 40); box.setMinSize(40, 40); box.setMaxSize(40, 40);
                box.getStyleClass().add("foto-cell-box");
            }
            @Override
            protected void updateItem(String url, boolean empty) {
                super.updateItem(url, empty);
                setGraphic(null);
                if (empty) return;
                if (url != null && !url.isBlank()) {
                    try {
                        Image cached = THUMBNAIL_CACHE.computeIfAbsent(url,
                            u -> new Image(Path.of(u).toUri().toString(), 38, 38, true, true, true));
                        iv.setImage(cached);
                        iv.setVisible(true); lbl.setVisible(false);
                    } catch (Exception ex) { log.warn("No se pudo cargar thumbnail: {}", url, ex); iv.setVisible(false); lbl.setVisible(true); }
                } else {
                    iv.setImage(null); iv.setVisible(false); lbl.setVisible(true);
                }
                setGraphic(box);
                setAlignment(Pos.CENTER);
                setPadding(new Insets(3, 7, 3, 7));
            }
        });

        colNombre.setCellValueFactory(new PropertyValueFactory<>("nombre"));
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
        colCodigo.setCellValueFactory(new PropertyValueFactory<>("codigo"));
        colCodigo.setCellFactory(col -> new TableCell<>() {
            {
                setTooltip(new Tooltip("Clic para copiar el código"));
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("codigo-cell");
                if (empty || item == null) { setText(null); return; }
                setText(item);
                getStyleClass().add("codigo-cell");
                setOnMouseClicked(e -> {
                    javafx.scene.input.Clipboard cb = javafx.scene.input.Clipboard.getSystemClipboard();
                    javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
                    cc.putString(item);
                    cb.setContent(cc);
                    NotificacionUtil.info(getScene(), "Código copiado: " + item);
                });
            }
        });
        colCategoria.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getCategoriaNombre() != null
                ? c.getValue().getCategoriaNombre() : ""));

        // Categoria badge cell
        colCategoria.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setGraphic(null); setText(null);
                if (empty || value == null || value.isBlank()) return;
                Producto p = getTableRow() != null ? (Producto) getTableRow().getItem() : null;
                badge.setText(value);
                badge.getStyleClass().add("cat-badge");
                badge.setStyle(p != null && p.getCategoriaColor() != null
                    ? "-fx-background-color: " + p.getCategoriaColor() + "22; -fx-text-fill: " + p.getCategoriaColor() + ";"
                    : "-fx-background-color: #EEF2FF; -fx-text-fill: #4338CA;");
                setGraphic(badge);
            }
        });

        colArea.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getArea() != null ? c.getValue().getArea() : ""));
        colArea.setCellFactory(col -> new TableCell<>() {
            private final Tooltip tip = new Tooltip();
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) { setText(null); setTooltip(null); return; }
                setText(item);
                tip.setText(item);
                setTooltip(tip);
            }
        });
        colStock.setCellValueFactory(new PropertyValueFactory<>("stockActual"));
        colValor.setCellValueFactory(c ->
            new SimpleStringProperty(FormatUtils.formatCurrency(c.getValue().getPrecioVenta())));
        colEstado.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getEstado().getEtiqueta()));

        // Status badge cell
        colEstado.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            { badge.getStyleClass().add("cell-badge"); }
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setGraphic(null);
                if (empty || value == null || getTableRow() == null || getTableRow().getItem() == null) return;
                Producto p = getTableRow().getItem();
                badge.setText(value);
                badge.getStyleClass().removeAll("cell-badge-success","cell-badge-warning","cell-badge-danger","cell-badge-purple");
                badge.getStyleClass().add(switch (p.getEstado()) {
                    case AGOTADO    -> "cell-badge-danger";
                    case BAJO_STOCK -> "cell-badge-warning";
                    case VENCIDO    -> "cell-badge-purple";
                    default         -> "cell-badge-success";
                });
                setGraphic(badge); setText(null);
            }
        });

        // Stock number coloring
        colStock.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer value, boolean empty) {
                super.updateItem(value, empty);
                setText(null);
                getStyleClass().removeAll("stock-ok","stock-warn","stock-low");
                if (empty || value == null) return;
                setText(String.valueOf(value));
                if (getTableRow() != null && getTableRow().getItem() != null) {
                    getStyleClass().add(switch (getTableRow().getItem().getEstado()) {
                        case AGOTADO    -> "stock-low";
                        case BAJO_STOCK -> "stock-warn";
                        default         -> "stock-ok";
                    });
                }
            }
        });

        // Row tint via CSS classes (preserves hover/selected states)
        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Producto p, boolean empty) {
                super.updateItem(p, empty);
                getStyleClass().removeAll("row-danger","row-warning","row-vencido","row-new");
                if (!empty && p != null) {
                    switch (p.getEstado()) {
                        case AGOTADO    -> getStyleClass().add("row-danger");
                        case BAJO_STOCK -> getStyleClass().add("row-warning");
                        case VENCIDO    -> getStyleClass().add("row-vencido");
                        default -> {}
                    }
                    if (p.getId() != null && p.getId().equals(pendingHighlightId))
                        getStyleClass().add("row-new");
                }
            }
        });

        // Selection → enable/disable action buttons
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            boolean s = sel != null;
            if (btnEditar   != null && canEdit) btnEditar.setDisable(!s);
            if (btnEliminar != null && canEdit) btnEliminar.setDisable(!s);
        });
        if (btnEditar   != null && canEdit) btnEditar.setDisable(true);
        if (btnEliminar != null && canEdit) btnEliminar.setDisable(true);

        // Delete key on table
        table.setOnKeyPressed(ev -> {
            if (ev.getCode() == javafx.scene.input.KeyCode.DELETE
                    && canEdit && table.getSelectionModel().getSelectedItem() != null) {
                onDelete(); ev.consume();
            }
        });

        // Double-click: edit if allowed, otherwise show detail
        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) {
                if (canEdit) onEdit();
                else showProductDetail(table.getSelectionModel().getSelectedItem());
            }
        });

        // Context menu
        ContextMenu cm = new ContextMenu();
        MenuItem cmDetalle  = new MenuItem("👁  Ver detalle");
        cmDetalle.setOnAction(e -> {
            Producto sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) showProductDetail(sel);
        });
        cm.getItems().add(cmDetalle);
        if (canEdit) {
            cm.getItems().add(new SeparatorMenuItem());
            MenuItem cmEditar   = new MenuItem("✏  Editar");
            MenuItem cmEliminar = new MenuItem("🗑  Dar de baja");
            cmEditar.setOnAction(e -> onEdit());
            cmEliminar.setOnAction(e -> onDelete());
            cm.getItems().addAll(cmEditar, cmEliminar);
        }
        table.setContextMenu(cm);

        // Smart empty state (set programmatically so we can update the message)
        Label emptyIcon = new Label("📦");
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

        // Pagination
        pageSizeBox.setItems(FXCollections.observableArrayList(10, 25, 50, 100));
        pageSizeBox.setValue(25);
        pageSizeBox.setOnAction(e -> {
            pageSize = pageSizeBox.getValue();
            currentPage = 0;
            updateTablePage();
        });
    }

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
            () -> categoriaRepo.findAll(),
            cats -> {
                categoriaFilter.getItems().add(null);
                categoriaFilter.getItems().addAll(cats);
                categoriaFilter.setConverter(new javafx.util.StringConverter<>() {
                    public String toString(Categoria c) { return c == null ? "Todas" : c.getNombre(); }
                    public Categoria fromString(String s) { return null; }
                });
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
        areaFilter.valueProperty().addListener((obs, o, n) -> { currentPage = 0; applyFilters(); });

        String pendingArea = NavigationContext.consumePendingAreaFilter();
        if (pendingArea != null && areaFilter.getItems().contains(pendingArea)) {
            areaFilter.setValue(pendingArea);
        }
    }

    private void loadData() {
        if (spinner != null) { spinner.setVisible(true); spinner.setManaged(true); }
        Task<List<Producto>> task = new Task<>() {
            @Override protected List<Producto> call() throws Exception {
                return productoService.getAll();
            }
            @Override protected void succeeded() {
                allData.setAll(getValue());
                updateStats();
                applyFilters();
                if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
                if (refreshing) { NotificacionUtil.info(table.getScene(), "Lista actualizada"); refreshing = false; }
            }
            @Override protected void failed() {
                if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
                NotificacionUtil.error(table.getScene(), "No se pudo cargar los bienes. Verifica la conexión.");
            }
        };
        com.sibim.util.AppExecutor.submit(task);
    }

    private void updateStats() {
        int total = allData.size();
        BigDecimal valor = allData.stream()
            .map(p -> {
                BigDecimal precio = p.getPrecioVenta() != null ? p.getPrecioVenta() : BigDecimal.ZERO;
                return precio.multiply(BigDecimal.valueOf(p.getStockActual()));
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        long alertas = allData.stream()
            .filter(p -> p.getEstado() == EstadoProducto.AGOTADO
                      || p.getEstado() == EstadoProducto.BAJO_STOCK
                      || p.getEstado() == EstadoProducto.VENCIDO)
            .count();

        if (lblStatTotal   != null) AnimationUtils.animateCount(lblStatTotal,   total,              700);
        if (lblStatValor   != null) AnimationUtils.animateCount(lblStatValor,   valor.longValue(),  880,
            v -> FormatUtils.formatCurrency(BigDecimal.valueOf(v)));
        if (lblStatAlertas != null) AnimationUtils.animateCount(lblStatAlertas, alertas,            580);

        if (cardAlertas != null) {
            cardAlertas.getStyleClass().removeAll("rich-stat-card-alert-active");
            if (alertas > 0) cardAlertas.getStyleClass().add("rich-stat-card-alert-active");
        }

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
        String query = searchField.getText().toLowerCase();
        Categoria cat = categoriaFilter.getValue();
        String area = areaFilter.getValue();
        String estado = getSelectedEstado();

        filteredData.setAll(allData.stream()
            .filter(p -> query.isBlank()
                || p.getNombre().toLowerCase().contains(query)
                || p.getCodigo().toLowerCase().contains(query)
                || (p.getProveedor() != null && p.getProveedor().toLowerCase().contains(query))
                || (p.getUbicacion() != null && p.getUbicacion().toLowerCase().contains(query)))
            .filter(p -> cat == null || cat.getId().equals(p.getCategoriaId()))
            .filter(p -> area == null || area.equals(p.getArea()))
            .filter(p -> "Todos".equals(estado) || p.getEstado().getEtiqueta().equalsIgnoreCase(estado))
            .sorted(java.util.Comparator.comparing(Producto::getNombre, String.CASE_INSENSITIVE_ORDER))
            .toList());

        currentPage = 0;
        updateTablePage();

        boolean hasFilters = !query.isBlank() || cat != null || area != null || !"Todos".equals(estado);
        if (btnClearFilters != null) {
            btnClearFilters.setVisible(hasFilters);
            btnClearFilters.setManaged(hasFilters);
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

    private void updateTablePage() {
        PaginationUtils.updatePage(table, filteredData, currentPage, pageSize,
            lblTotal, lblPage, btnPrev, btnNext, "resultado", "resultados");
    }

    @FXML private void onPrev() { if (currentPage > 0) { currentPage--; updateTablePage(); } }
    @FXML private void onNext() { currentPage++; updateTablePage(); }
    @FXML private void onRefresh() { refreshing = true; loadData(); }

    @FXML
    private void onConteoFisico() {
        ConteoFisicoDialog.show(new ArrayList<>(allData), movimientoService, () -> { refreshing = true; loadData(); });
    }

    @FXML
    private void onVerBajas() {
        DialogUtil.runAsync(
            () -> productoService.getAllIncludingBaja().stream().filter(Producto::isDadoDeBaja).toList(),
            this::showBajasDialog,
            e -> NotificacionUtil.error(table.getScene(), "No se pudo cargar la lista de bienes dados de baja")
        );
    }

    private void showBajasDialog(List<Producto> bajas) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(560);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("🗑", "Bienes Dados de Baja",
            "Fuera del inventario activo — su historial se conserva",
            "#EF4444", "#B91C1C");

        VBox list = new VBox(8);
        list.setPadding(new Insets(4, 4, 4, 4));
        if (bajas.isEmpty()) {
            Label empty = new Label("No hay bienes dados de baja");
            empty.getStyleClass().add("muted");
            list.getChildren().add(empty);
        }
        for (Producto p : bajas) {
            HBox row = new HBox(12);
            row.getStyleClass().add("dlg-detail-header");
            row.setPadding(new Insets(10, 14, 10, 14));
            row.setAlignment(Pos.CENTER_LEFT);

            VBox info = new VBox(2);
            Label nombre = new Label(p.getNombre() + "  [" + p.getCodigo() + "]");
            nombre.getStyleClass().add("dlg-detail-name");
            Label detalle = new Label(p.getArea() + " · baja: " + FormatUtils.formatDate(p.getFechaBaja())
                + (p.getMotivoBaja() != null && !p.getMotivoBaja().isBlank() ? " · " + p.getMotivoBaja() : ""));
            detalle.getStyleClass().add("muted-sm");
            detalle.setWrapText(true);
            info.getChildren().addAll(nombre, detalle);
            HBox.setHgrow(info, Priority.ALWAYS);

            Button btnReactivar = new Button("↺ Reactivar");
            btnReactivar.getStyleClass().add("btn-secondary");
            btnReactivar.setOnAction(e -> {
                DialogUtil.runAsync(
                    () -> productoService.reactivar(p.getId()),
                    () -> {
                        list.getChildren().remove(row);
                        loadData();
                        NotificacionUtil.exito(dialog.getDialogPane().getScene(),
                            "Bien \"" + p.getNombre() + "\" reactivado");
                    },
                    e2 -> NotificacionUtil.error(dialog.getDialogPane().getScene(), "No se pudo reactivar el bien")
                );
            });
            row.getChildren().addAll(info, btnReactivar);
            list.getChildren().add(row);
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(360);
        scroll.getStyleClass().add("page-scroll");

        if (!list.getChildren().isEmpty())
            AnimationUtils.staggeredFadeInUp(new java.util.ArrayList<>(list.getChildren()), 240, 40);
        dialog.getDialogPane().setContent(new VBox(0, header, scroll));
        dialog.showAndWait();
    }

    @FXML
    private void onClearFilters() {
        searchField.clear();
        categoriaFilter.setValue(null);
        areaFilter.setValue(null);
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
            "🗑",
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
                allData.remove(seleccionado);
                updateStats();
                applyFilters();
                NotificacionUtil.exitoConAccion(table.getScene(),
                    "Bien \"" + nombre + "\" dado de baja",
                    "↶ Deshacer",
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
        DialogUtil.runAsync(
            () -> reporteService.exportInventarioCsv(null, null),
            file -> {
                NotificacionUtil.exito(table.getScene(), "CSV exportado correctamente");
                openFile(file);
            },
            e -> NotificacionUtil.error(table.getScene(), "No se pudo exportar el CSV")
        );
    }

    @FXML
    private void onExportExcel() {
        DialogUtil.runAsync(
            () -> reporteService.exportInventarioExcel(null, null),
            file -> {
                NotificacionUtil.exito(table.getScene(), "Excel exportado correctamente");
                openFile(file);
            },
            e -> NotificacionUtil.error(table.getScene(), "No se pudo exportar el Excel")
        );
    }

    private void showProductDialog(Producto existing) {
        try {
            List<Categoria> cats = categoriaRepo.findAll();
            Optional<Producto> result = ProductoDialogFactory.show(existing, cats, THUMBNAIL_CACHE, log);
            boolean isNew = existing == null;
            result.ifPresent(p -> DialogUtil.runAsync(
                () -> productoService.save(p),
                saved -> {
                    if (isNew) {
                        allData.add(saved);
                        NotificacionUtil.exito(table.getScene(), "Bien registrado exitosamente");
                    } else {
                        NotificacionUtil.exito(table.getScene(), "Bien actualizado correctamente");
                    }
                    pendingHighlightId = saved.getId();
                    updateStats();
                    applyFilters();
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

    private javafx.scene.layout.ColumnConstraints colConstraint(double width, boolean grow) {
        javafx.scene.layout.ColumnConstraints cc = new javafx.scene.layout.ColumnConstraints();
        cc.setPrefWidth(width);
        if (grow) cc.setHgrow(javafx.scene.layout.Priority.ALWAYS);
        return cc;
    }

    private void showProductDetail(Producto p) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Detalle del Bien");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(520);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        VBox root = new VBox(14);
        root.setPadding(new Insets(4, 0, 0, 0));

        // ── Header card ────────────────────────────────────────────────
        HBox headerCard = new HBox(14);
        headerCard.setAlignment(Pos.CENTER_LEFT);
        headerCard.setPadding(new Insets(14, 18, 14, 18));
        headerCard.getStyleClass().add("dlg-detail-header");

        // Thumbnail / category monogram
        StackPane thumbPane = new StackPane();
        thumbPane.setMinSize(52, 52); thumbPane.setMaxSize(52, 52);
        boolean photoLoaded = false;
        if (p.getFotoUrl() != null && !p.getFotoUrl().isBlank()) {
            try {
                ImageView iv = new ImageView(
                    new Image(Path.of(p.getFotoUrl()).toUri().toString(), 52, 52, true, true, true));
                iv.setFitWidth(52); iv.setFitHeight(52); iv.setPreserveRatio(true);
                thumbPane.getChildren().add(iv);
                thumbPane.getStyleClass().add("dlg-thumb-photo");
                photoLoaded = true;
            } catch (Exception ex) { log.warn("No se pudo cargar thumbnail de detalle: {}", p.getFotoUrl(), ex); }
        }
        if (!photoLoaded) {
            String monogramBg  = p.getCategoriaColor() != null ? p.getCategoriaColor() + "22" : "#EEF2FF";
            String monogramFg  = p.getCategoriaColor() != null ? p.getCategoriaColor() : "#4338CA";
            String monogramTxt = p.getCategoriaNombre() != null && !p.getCategoriaNombre().isBlank()
                ? String.valueOf(p.getCategoriaNombre().charAt(0)).toUpperCase() : "B";
            Label monogram = new Label(monogramTxt);
            monogram.getStyleClass().add("dlg-monogram");
            monogram.setStyle("-fx-text-fill: " + monogramFg + ";");
            thumbPane.getChildren().add(monogram);
            thumbPane.getStyleClass().add("dlg-thumb-monogram");
            thumbPane.setStyle("-fx-background-color: " + monogramBg + ";");
        }

        // Name + meta row
        VBox nameSection = new VBox(5);
        HBox.setHgrow(nameSection, Priority.ALWAYS);

        Label nameLbl = new Label(p.getNombre());
        nameLbl.getStyleClass().add("dlg-detail-name");
        nameLbl.setWrapText(true); nameLbl.setMaxWidth(320);

        Label statusBadge = new Label(p.getEstado().getEtiqueta());
        statusBadge.getStyleClass().add(switch (p.getEstado()) {
            case AGOTADO    -> "dlg-status-danger";
            case BAJO_STOCK -> "dlg-status-warn";
            case VENCIDO    -> "dlg-status-purple";
            default         -> "dlg-status-ok";
        });
        Label codeLbl = new Label("# " + p.getCodigo());
        codeLbl.getStyleClass().add("dlg-detail-code");

        HBox meta = new HBox(8, codeLbl, statusBadge);
        meta.setAlignment(Pos.CENTER_LEFT);
        nameSection.getChildren().addAll(nameLbl, meta);
        headerCard.getChildren().addAll(thumbPane, nameSection);

        // ── Detail grid ────────────────────────────────────────────────
        GridPane g = new GridPane();
        g.setHgap(16); g.setVgap(9);
        g.setPadding(new Insets(0, 0, 4, 0));
        g.getColumnConstraints().addAll(colConstraint(140, false), colConstraint(300, true));

        String stockClass = switch (p.getEstado()) {
            case AGOTADO    -> "dlg-detail-stock-low";
            case BAJO_STOCK -> "dlg-detail-stock-warn";
            default         -> "dlg-detail-stock-ok";
        };

        Object[][] rows = {
            {"Categoría",       p.getCategoriaNombre() != null ? p.getCategoriaNombre() : "—", null},
            {"Área",            p.getArea() != null ? p.getArea() : "—", null},
            {"Resguardante",    p.getResguardante() != null && !p.getResguardante().isBlank() ? p.getResguardante() : "—", null},
            {"Stock actual",    String.valueOf(p.getStockActual()), stockClass},
            {"Stock mín / máx", p.getStockMinimo() + " / " + p.getStockMaximo(), null},
            {"Unidad",          p.getUnidad() != null ? p.getUnidad().getEtiqueta() : "—", null},
            {"Precio compra",   FormatUtils.formatCurrency(p.getPrecioCompra()), null},
            {"Precio venta",    FormatUtils.formatCurrency(p.getPrecioVenta()), null},
            {"Valor total",     FormatUtils.formatCurrency(p.getValorTotal()), "dlg-detail-total"},
            {"Proveedor",       p.getProveedor() != null ? p.getProveedor() : "—", null},
            {"Ubicación",       p.getUbicacion() != null ? p.getUbicacion() : "—", null},
            {"Vencimiento",     FormatUtils.formatDate(p.getFechaVencimiento()), null},
        };
        for (int i = 0; i < rows.length; i++) {
            Label key = DialogUtil.fieldLabel((String) rows[i][0]);
            Label val = new Label((String) rows[i][1]);
            val.setWrapText(true); val.setMaxWidth(280);
            if (rows[i][2] != null) val.getStyleClass().add((String) rows[i][2]);
            g.add(key, 0, i); g.add(val, 1, i);
        }

        root.getChildren().addAll(headerCard, g);
        AnimationUtils.staggeredFadeInUp(root.getChildren(), 270, 70);
        dialog.getDialogPane().setContent(root);
        dialog.showAndWait();
    }

    private void openFile(File file) {
        try { Desktop.getDesktop().open(file); }
        catch (Exception ex) {
            log.warn("No se pudo abrir el archivo {}", file, ex);
            if (table != null && table.getScene() != null)
                NotificacionUtil.advertencia(table.getScene(), "Guardado en: " + file.getAbsolutePath());
        }
    }
}
