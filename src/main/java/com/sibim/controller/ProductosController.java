package com.sibim.controller;

import com.sibim.model.Categoria;
import com.sibim.model.Producto;
import com.sibim.model.enums.EstadoProducto;
import com.sibim.model.enums.UnidadMedida;
import com.sibim.repository.CategoriaRepository;
import com.sibim.service.ProductoService;
import com.sibim.service.ReporteService;
import com.sibim.session.SessionManager;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.ImageUtils;
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
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ProductosController {

    private static final Logger log = LoggerFactory.getLogger(ProductosController.class);
    private static final Map<String, Image> THUMBNAIL_CACHE = new ConcurrentHashMap<>();

    @FXML private VBox rootPane;
    @FXML private TextField searchField;
    @FXML private ComboBox<Categoria> categoriaFilter;
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
    @FXML private VBox cardAlertas;

    private final ProductoService productoService = new ProductoService();
    private final CategoriaRepository categoriaRepo = new CategoriaRepository();
    private final ReporteService reporteService = new ReporteService();

    private ObservableList<Producto> allData = FXCollections.observableArrayList();
    private ObservableList<Producto> filteredData = FXCollections.observableArrayList();
    private int currentPage = 0;
    private int pageSize = 25;
    private boolean refreshing = false;
    private boolean canEdit = false;
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
        if (btnClearSearch != null) {
            searchField.textProperty().addListener((obs, o, n) -> btnClearSearch.setVisible(!n.isBlank()));
            btnClearSearch.setOnAction(e -> { searchField.clear(); searchField.requestFocus(); });
        }
        loadData();
        Platform.runLater(() -> { if (searchField != null) searchField.requestFocus(); });
    }

    private void setupTable() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
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
                    } catch (Exception ex) { iv.setVisible(false); lbl.setVisible(true); }
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
                getStyleClass().removeAll("row-danger","row-warning","row-vencido");
                if (!empty && p != null) switch (p.getEstado()) {
                    case AGOTADO    -> getStyleClass().add("row-danger");
                    case BAJO_STOCK -> getStyleClass().add("row-warning");
                    case VENCIDO    -> getStyleClass().add("row-vencido");
                    default -> {}
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
            MenuItem cmEliminar = new MenuItem("✕  Eliminar");
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
        VBox emptyState = new VBox(8, emptyIcon, emptyStateMsg, btnEmptyLimpiar, emptyStateHint);
        emptyState.setAlignment(Pos.CENTER);
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
        new Thread(task).start();
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

        if (lblStatTotal   != null) lblStatTotal.setText(String.valueOf(total));
        if (lblStatValor   != null) lblStatValor.setText(FormatUtils.formatCurrency(valor));
        if (lblStatAlertas != null) lblStatAlertas.setText(String.valueOf(alertas));

        if (cardAlertas != null) {
            cardAlertas.getStyleClass().removeAll("rich-stat-card-alert-active");
            if (alertas > 0) cardAlertas.getStyleClass().add("rich-stat-card-alert-active");
        }
    }

    private String getSelectedEstado() {
        if (estadoChipGroup == null) return "Todos";
        Toggle t = estadoChipGroup.getSelectedToggle();
        return t == null ? "Todos" : ((ToggleButton) t).getText();
    }

    private void applyFilters() {
        String query = searchField.getText().toLowerCase();
        Categoria cat = categoriaFilter.getValue();
        String estado = getSelectedEstado();

        filteredData.setAll(allData.stream()
            .filter(p -> query.isBlank()
                || p.getNombre().toLowerCase().contains(query)
                || p.getCodigo().toLowerCase().contains(query)
                || (p.getProveedor() != null && p.getProveedor().toLowerCase().contains(query))
                || (p.getUbicacion() != null && p.getUbicacion().toLowerCase().contains(query)))
            .filter(p -> cat == null || cat.getId().equals(p.getCategoriaId()))
            .filter(p -> "Todos".equals(estado) || p.getEstado().getEtiqueta().equalsIgnoreCase(estado))
            .sorted(java.util.Comparator.comparing(Producto::getNombre, String.CASE_INSENSITIVE_ORDER))
            .toList());

        currentPage = 0;
        updateTablePage();

        boolean hasFilters = !query.isBlank() || cat != null || !"Todos".equals(estado);
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
    private void onClearFilters() {
        searchField.clear();
        categoriaFilter.setValue(null);
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
            NotificacionUtil.advertencia(table.getScene(), "Selecciona un bien para eliminar");
            return;
        }
        if (!ConfirmacionUtil.confirmarEliminar(seleccionado.getNombre())) return;
        String nombre = seleccionado.getNombre();
        DialogUtil.runAsync(
            () -> productoService.delete(seleccionado.getId()),
            () -> {
                allData.remove(seleccionado);
                updateStats();
                applyFilters();
                NotificacionUtil.exito(table.getScene(), "Bien \"" + nombre + "\" eliminado correctamente");
            },
            e -> NotificacionUtil.error(table.getScene(),
                e instanceof ProductoService.ValidationException ? e.getMessage() : "No se pudo eliminar el bien")
        );
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

            boolean isNewProduct = existing == null;
            Dialog<Producto> dialog = DialogUtil.create(520);
            DialogUtil.styleOkButton(dialog.getDialogPane(),
                isNewProduct ? "#4F46E5" : "#059669");

            HBox dialogHeader = DialogUtil.gradientHeader(
                isNewProduct ? "📦" : "✏",
                isNewProduct ? "Nuevo Bien Patrimonial" : "Editar Bien",
                isNewProduct ? "Registra un nuevo bien en el inventario municipal"
                             : "Actualiza los datos de " + existing.getNombre(),
                isNewProduct ? "#4F46E5" : "#059669",
                isNewProduct ? "#7C3AED" : "#047857");

            Node okBtn = DialogUtil.getOkButton(dialog.getDialogPane());

            // ── Tab: Información General ──
            GridPane gridInfo = DialogUtil.formGrid(120);

            TextField fNombre = new TextField(existing != null ? existing.getNombre() : "");
            fNombre.setPromptText("Nombre descriptivo del bien");
            fNombre.getStyleClass().add("form-input");
            TextField fCodigo = new TextField(existing != null ? existing.getCodigo() : "");
            fCodigo.setPromptText("Código único de inventario");
            fCodigo.getStyleClass().add("form-input");
            TextArea fDesc = new TextArea(existing != null && existing.getDescripcion() != null ? existing.getDescripcion() : "");
            fDesc.setPrefRowCount(2); fDesc.setPromptText("Descripción opcional");
            fDesc.getStyleClass().add("form-input");
            ComboBox<Categoria> fCat = new ComboBox<>(FXCollections.observableArrayList(cats));
            fCat.setMaxWidth(Double.MAX_VALUE);
            fCat.setPromptText("Seleccionar categoría");
            fCat.getStyleClass().add("form-input");
            fCat.setConverter(new javafx.util.StringConverter<>() {
                public String toString(Categoria c) { return c == null ? "" : c.getNombre(); }
                public Categoria fromString(String s) { return null; }
            });
            if (existing != null) cats.stream()
                .filter(c -> c.getId().equals(existing.getCategoriaId())).findFirst().ifPresent(fCat::setValue);

            List<String> areaNames = new java.util.ArrayList<>(com.sibim.config.Areas.getAllAreaNames());
            ComboBox<String> fArea = new ComboBox<>(FXCollections.observableArrayList(areaNames));
            fArea.setMaxWidth(Double.MAX_VALUE);
            fArea.setPromptText("Área responsable");
            fArea.setValue(existing != null ? existing.getArea() : SessionManager.getCurrentUser().getArea());
            if (SessionManager.isDireccion()) fArea.setDisable(true);
            fArea.getStyleClass().add("form-input");

            TextField fProveedor = new TextField(existing != null && existing.getProveedor() != null ? existing.getProveedor() : "");
            fProveedor.setPromptText("Nombre del proveedor");
            fProveedor.getStyleClass().add("form-input");
            TextField fUbicacion = new TextField(existing != null && existing.getUbicacion() != null ? existing.getUbicacion() : "");
            fUbicacion.setPromptText("Ubicación física");
            fUbicacion.getStyleClass().add("form-input");

            // ── Image picker ──
            String[] fotoHolder = { existing != null ? existing.getFotoUrl() : null };

            ImageView imgPreview = new ImageView();
            imgPreview.setFitWidth(150); imgPreview.setFitHeight(112);
            imgPreview.setPreserveRatio(true);

            Label imgPlaceholder = new Label("📷\nSin imagen");
            imgPlaceholder.getStyleClass().add("dlg-img-placeholder");
            imgPlaceholder.setAlignment(Pos.CENTER);

            StackPane imgBox = new StackPane(imgPlaceholder, imgPreview);
            imgBox.setPrefSize(150, 112);
            imgBox.getStyleClass().add("dlg-img-box");

            Runnable loadImg = () -> {
                if (fotoHolder[0] != null && !fotoHolder[0].isBlank()) {
                    try {
                        Image img = new Image(Path.of(fotoHolder[0]).toUri().toString(), 150, 112, true, true, true);
                        imgPreview.setImage(img);
                        imgPlaceholder.setVisible(false);
                    } catch (Exception ignored) { imgPlaceholder.setVisible(true); }
                } else {
                    imgPreview.setImage(null);
                    imgPlaceholder.setVisible(true);
                }
            };
            loadImg.run();

            Button btnSelImg    = new Button("📷  Seleccionar");
            Button btnQuitarImg = new Button("✕  Quitar");
            btnSelImg.getStyleClass().add("btn-secondary");
            btnQuitarImg.getStyleClass().add("btn-secondary");
            btnQuitarImg.setDisable(fotoHolder[0] == null || fotoHolder[0].isBlank());

            btnSelImg.setOnAction(ev -> {
                FileChooser chooser = new FileChooser();
                chooser.setTitle("Seleccionar imagen del bien");
                chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Imágenes", "*.png","*.jpg","*.jpeg","*.gif","*.bmp","*.webp"));
                File file = chooser.showOpenDialog(dialog.getOwner());
                if (file != null) {
                    fotoHolder[0] = file.getAbsolutePath();
                    loadImg.run();
                    btnQuitarImg.setDisable(false);
                }
            });
            btnQuitarImg.setOnAction(ev -> {
                fotoHolder[0] = null;
                loadImg.run();
                btnQuitarImg.setDisable(true);
            });

            VBox imgSection = new VBox(6, imgBox, new HBox(6, btnSelImg, btnQuitarImg));

            int r = 0;
            gridInfo.add(DialogUtil.fieldLabel("Nombre *"),    0, r); gridInfo.add(fNombre,    1, r++);
            gridInfo.add(DialogUtil.fieldLabel("Código *"),     0, r); gridInfo.add(fCodigo,    1, r++);
            gridInfo.add(DialogUtil.fieldLabel("Descripción"), 0, r); gridInfo.add(fDesc,      1, r++);
            gridInfo.add(DialogUtil.fieldLabel("Categoría *"), 0, r); gridInfo.add(fCat,       1, r++);
            gridInfo.add(DialogUtil.fieldLabel("Área *"),       0, r); gridInfo.add(fArea,      1, r++);
            gridInfo.add(DialogUtil.fieldLabel("Proveedor"),   0, r); gridInfo.add(fProveedor, 1, r++);
            gridInfo.add(DialogUtil.fieldLabel("Ubicación"),   0, r); gridInfo.add(fUbicacion, 1, r++);
            gridInfo.add(DialogUtil.fieldLabel("Imagen"),      0, r); gridInfo.add(imgSection, 1, r);

            // ── Tab: Stock & Precios ──
            GridPane gridStock = DialogUtil.formGrid(140);

            Spinner<Integer> fStock    = new Spinner<>(0, Integer.MAX_VALUE, existing != null ? existing.getStockActual() : 0);
            fStock.setEditable(true); fStock.setMaxWidth(Double.MAX_VALUE);
            fStock.getStyleClass().add("form-input");
            DialogUtil.commitOnFocusLoss(fStock);
            Spinner<Integer> fStockMin = new Spinner<>(0, Integer.MAX_VALUE, existing != null ? existing.getStockMinimo() : 0);
            fStockMin.setEditable(true); fStockMin.setMaxWidth(Double.MAX_VALUE);
            fStockMin.getStyleClass().add("form-input");
            DialogUtil.commitOnFocusLoss(fStockMin);
            Spinner<Integer> fStockMax = new Spinner<>(0, Integer.MAX_VALUE, existing != null ? existing.getStockMaximo() : 100);
            fStockMax.setEditable(true); fStockMax.setMaxWidth(Double.MAX_VALUE);
            fStockMax.getStyleClass().add("form-input");
            DialogUtil.commitOnFocusLoss(fStockMax);
            ComboBox<UnidadMedida> fUnidad = new ComboBox<>(
                FXCollections.observableArrayList(UnidadMedida.values()));
            fUnidad.setValue(existing != null ? existing.getUnidad() : UnidadMedida.PIEZA);
            fUnidad.setMaxWidth(Double.MAX_VALUE);
            fUnidad.getStyleClass().add("form-input");
            TextField fPrecioC = new TextField(existing != null && existing.getPrecioCompra() != null
                ? existing.getPrecioCompra().toPlainString() : "0");
            fPrecioC.setPromptText("0.00");
            fPrecioC.getStyleClass().add("form-input");
            TextField fPrecioV = new TextField(existing != null && existing.getPrecioVenta() != null
                ? existing.getPrecioVenta().toPlainString() : "0");
            fPrecioV.setPromptText("0.00");
            fPrecioV.getStyleClass().add("form-input");
            DatePicker fVenc = new DatePicker(existing != null ? existing.getFechaVencimiento() : null);
            fVenc.setMaxWidth(Double.MAX_VALUE);
            fVenc.getStyleClass().add("form-input");

            int rs = 0;
            gridStock.add(DialogUtil.fieldLabel("Stock Actual"),    0, rs); gridStock.add(fStock,    1, rs++);
            gridStock.add(DialogUtil.fieldLabel("Stock Mínimo"),    0, rs); gridStock.add(fStockMin, 1, rs++);
            gridStock.add(DialogUtil.fieldLabel("Stock Máximo"),    0, rs); gridStock.add(fStockMax, 1, rs++);
            gridStock.add(DialogUtil.fieldLabel("Unidad"),          0, rs); gridStock.add(fUnidad,   1, rs++);
            gridStock.add(DialogUtil.fieldLabel("Precio Compra"),   0, rs); gridStock.add(fPrecioC,  1, rs++);
            gridStock.add(DialogUtil.fieldLabel("Precio Venta"),    0, rs); gridStock.add(fPrecioV,  1, rs++);
            gridStock.add(DialogUtil.fieldLabel("Fecha Venc."),     0, rs); gridStock.add(fVenc,     1, rs);

            // ── TabPane ──
            TabPane tabs = new TabPane();
            tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
            Tab tabInfo  = new Tab("📋  Información General", gridInfo);
            Tab tabStock = new Tab("📊  Stock y Precios", gridStock);
            tabs.getTabs().addAll(tabInfo, tabStock);
            tabs.getStyleClass().add("dlg-tabpane");

            Label lblFormError = new Label();
            lblFormError.getStyleClass().add("field-error-label");
            lblFormError.setVisible(false);
            lblFormError.setManaged(false);
            lblFormError.setWrapText(true);

            VBox dialogContent = new VBox(0, dialogHeader, tabs, lblFormError);
            dialog.getDialogPane().setContent(dialogContent);

            Runnable hideFormError = () -> { lblFormError.setVisible(false); lblFormError.setManaged(false); };
            fNombre.textProperty().addListener((o, a, b) -> { if (!b.isBlank()) fNombre.getStyleClass().remove("field-error"); hideFormError.run(); });
            fCodigo.textProperty().addListener((o, a, b) -> { if (!b.isBlank()) fCodigo.getStyleClass().remove("field-error"); hideFormError.run(); });
            fArea.valueProperty().addListener((o, a, b) -> { if (b != null && !b.isBlank()) fArea.getStyleClass().remove("field-error"); hideFormError.run(); });
            fCat.valueProperty().addListener((o, a, b) -> { if (b != null) fCat.getStyleClass().remove("field-error"); hideFormError.run(); });
            fPrecioC.textProperty().addListener((o, a, b) -> { fPrecioC.getStyleClass().remove("field-error"); hideFormError.run(); });
            fPrecioV.textProperty().addListener((o, a, b) -> { fPrecioV.getStyleClass().remove("field-error"); hideFormError.run(); });

            if (okBtn != null) {
                Runnable checkOk = () -> okBtn.setDisable(
                    fNombre.getText().isBlank() || fCodigo.getText().isBlank()
                        || fArea.getValue() == null || fArea.getValue().isBlank()
                        || fCat.getValue() == null);
                checkOk.run();
                fNombre.textProperty().addListener((o, a, b) -> checkOk.run());
                fCodigo.textProperty().addListener((o, a, b) -> checkOk.run());
                fArea.valueProperty().addListener((o, a, b) -> checkOk.run());
                fCat.valueProperty().addListener((o, a, b) -> checkOk.run());
            }

            Platform.runLater(() -> fNombre.requestFocus());
            dialog.setResultConverter(btn -> {
                if (btn != ButtonType.OK) return null;
                boolean invalid = false;
                if (fNombre.getText().isBlank()) { fNombre.getStyleClass().add("field-error"); tabs.getSelectionModel().select(0); invalid = true; }
                else fNombre.getStyleClass().remove("field-error");
                if (fCodigo.getText().isBlank()) { fCodigo.getStyleClass().add("field-error"); tabs.getSelectionModel().select(0); invalid = true; }
                else fCodigo.getStyleClass().remove("field-error");
                if (fArea.getValue() == null || fArea.getValue().isBlank()) { fArea.getStyleClass().add("field-error"); tabs.getSelectionModel().select(0); invalid = true; }
                else fArea.getStyleClass().remove("field-error");
                if (fCat.getValue() == null) { fCat.getStyleClass().add("field-error"); tabs.getSelectionModel().select(0); invalid = true; }
                else fCat.getStyleClass().remove("field-error");

                BigDecimal precioCompra = null, precioVenta = null;
                try {
                    precioCompra = new BigDecimal(fPrecioC.getText().trim());
                    fPrecioC.getStyleClass().remove("field-error");
                } catch (Exception ex) {
                    fPrecioC.getStyleClass().add("field-error"); tabs.getSelectionModel().select(1); invalid = true;
                }
                try {
                    precioVenta = new BigDecimal(fPrecioV.getText().trim());
                    fPrecioV.getStyleClass().remove("field-error");
                } catch (Exception ex) {
                    fPrecioV.getStyleClass().add("field-error"); tabs.getSelectionModel().select(1); invalid = true;
                }

                if (invalid) {
                    lblFormError.setText("Completa los campos obligatorios marcados en rojo. Los precios deben ser números válidos (ej. 1500.00).");
                    lblFormError.setVisible(true);
                    lblFormError.setManaged(true);
                    return null;
                }

                DialogUtil.commitSpinner(fStock);
                DialogUtil.commitSpinner(fStockMin);
                DialogUtil.commitSpinner(fStockMax);

                Producto p = existing != null ? existing : new Producto();
                if (p.getId() == null) p.setId(java.util.UUID.randomUUID().toString());
                p.setNombre(fNombre.getText().trim());
                p.setCodigo(fCodigo.getText().trim());
                p.setDescripcion(fDesc.getText().trim());
                if (fCat.getValue() != null) {
                    p.setCategoriaId(fCat.getValue().getId());
                    p.setCategoriaNombre(fCat.getValue().getNombre());
                    p.setCategoriaColor(fCat.getValue().getColor());
                }
                p.setPrecioCompra(precioCompra);
                p.setPrecioVenta(precioVenta);
                p.setStockActual(fStock.getValue());
                p.setStockMinimo(fStockMin.getValue());
                p.setStockMaximo(fStockMax.getValue());
                p.setUnidad(fUnidad.getValue());
                p.setProveedor(fProveedor.getText().trim());
                p.setUbicacion(fUbicacion.getText().trim());
                p.setFechaVencimiento(fVenc.getValue());
                p.setArea(fArea.getValue());
                if (fotoHolder[0] != null && !fotoHolder[0].isBlank()) {
                    try {
                        Path imgDir = imgDir();
                        Files.createDirectories(imgDir);
                        Path dest = imgDir.resolve(p.getId() + ".jpg");
                        Path src = Path.of(fotoHolder[0]);
                        if (!src.equals(dest)) {
                            ImageUtils.resizeAndSave(src.toFile(), dest.toFile());
                            THUMBNAIL_CACHE.remove(dest.toString());
                        }
                        p.setFotoUrl(dest.toString());
                    } catch (Exception ex) {
                        log.error("No se pudo procesar la imagen del bien '{}', se conserva la foto anterior", p.getNombre(), ex);
                        p.setFotoUrl(existing != null ? existing.getFotoUrl() : null);
                    }
                } else {
                    p.setFotoUrl(null);
                }
                return p;
            });

            Optional<Producto> result = dialog.showAndWait();
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
                    updateStats();
                    applyFilters();
                },
                e -> NotificacionUtil.error(table.getScene(),
                    e instanceof ProductoService.ValidationException ? e.getMessage() : "No se pudo guardar el bien")
            ));

        } catch (Exception e) {
            NotificacionUtil.error(table.getScene(), "Error al abrir el formulario");
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
            } catch (Exception ignored) {}
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
        dialog.getDialogPane().setContent(root);
        dialog.showAndWait();
    }

    private Path imgDir() {
        return Path.of(System.getProperty("user.home"), ".sibim", "imagenes");
    }

    private void openFile(File file) {
        try { Desktop.getDesktop().open(file); }
        catch (Exception ignored) {}
    }
}
