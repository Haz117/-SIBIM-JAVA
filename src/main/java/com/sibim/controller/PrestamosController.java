package com.sibim.controller;

import com.sibim.config.Areas;
import com.sibim.db.DatabaseConfig;
import com.sibim.model.Prestamo;
import com.sibim.model.Producto;
import com.sibim.service.PrestamoService;
import com.sibim.service.ProductoService;
import com.sibim.service.ReporteService;
import com.sibim.session.NavigationContext;
import com.sibim.session.SessionManager;
import com.sibim.util.AccessibilityUtils;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PrestamosController extends BaseDocumentController<Prestamo> {

    @FXML private Label   lblStatActivos;
    @FXML private Label   lblStatVencidos;
    @FXML private Label   lblStatDevueltos;
    @FXML private Label   lblStatTotal;
    @FXML private VBox    statCardActivos;
    @FXML private VBox    statCardVencidos;
    @FXML private VBox    statCardDevueltos;
    @FXML private VBox    statCardTotal;
    @FXML private TableColumn<Prestamo, String> colNumero;
    @FXML private TableColumn<Prestamo, String> colBien;
    @FXML private TableColumn<Prestamo, String> colAreaOrigen;
    @FXML private TableColumn<Prestamo, String> colAreaDestino;
    @FXML private TableColumn<Prestamo, String> colResponsable;
    @FXML private TableColumn<Prestamo, String> colFechaPrevista;
    @FXML private TableColumn<Prestamo, String> colEstado;
    @FXML private Button      btnNuevo;
    @FXML private Button      btnDevolver;
    @FXML private Button      btnExportarExcel;
    @FXML private ComboBox<String>  estadoFilter;
    @FXML private ToggleButton      btnKanban;
    @FXML private HBox              kanbanBoard;

    private final PrestamoService  service         = new PrestamoService();
    private final ProductoService  productoService = new ProductoService();
    private List<Prestamo> allData   = List.of();
    private boolean        kanbanMode = false;

    // ── BaseDocumentController hooks ─────────────────────────────────────────

    @Override
    protected void setupColumns() {
        colNumero.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNumero()));
        colBien.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProductoNombre()));
        colAreaOrigen.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAreaOrigen()));
        colAreaDestino.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAreaDestino()));
        colResponsable.setCellValueFactory(c -> {
            String n = c.getValue().getResponsableNombre();
            String cargo = c.getValue().getResponsableCargo();
            return new SimpleStringProperty(cargo != null && !cargo.isBlank() ? n + " · " + cargo : n);
        });
        colFechaPrevista.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getFechaDevolucionPrevista() != null
                ? FormatUtils.formatDate(c.getValue().getFechaDevolucionPrevista()) : "—"));
        colEstado.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                Prestamo p = getTableRow().getItem();
                boolean vencido = p.isVencidoCalc() || Prestamo.ESTADO_VENCIDO.equals(p.getEstado());
                String txt = Prestamo.ESTADO_DEVUELTO.equals(p.getEstado()) ? "Devuelto"
                    : vencido ? "Vencido" : "Activo";
                String cls = Prestamo.ESTADO_DEVUELTO.equals(p.getEstado()) ? "cell-badge-muted"
                    : vencido ? "cell-badge-warning" : "cell-badge-ok";
                Label badge = new Label(txt);
                badge.getStyleClass().add(cls);
                setGraphic(badge);
            }
        });
        colEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEstado()));
        table.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Prestamo p, boolean empty) {
                super.updateItem(p, empty);
                getStyleClass().removeAll("row-warning");
                if (!empty && p != null && p.isVencidoCalc()) getStyleClass().add("row-warning");
            }
        });
    }

    @Override
    protected void onInitialize() {
        if (estadoFilter != null) {
            estadoFilter.getItems().addAll("Todos", "Activos", "Vencidos", "Devueltos");
            estadoFilter.setValue("Todos");
            estadoFilter.valueProperty().addListener((obs, o, n) -> applyFilter());
            estadoFilter.valueProperty().addListener((obs, o, n) -> updateStatHighlight(n));
            setupStatCardFilters();
        }
        boolean offline = DatabaseConfig.getLocalDataStore() != null;
        boolean canCreate = (SessionManager.isAdmin() || SessionManager.isSecretario()) && !offline;
        if (btnNuevo != null) { btnNuevo.setVisible(canCreate); btnNuevo.setManaged(canCreate); }
        if (offline) {
            Platform.runLater(() -> {
                if (rootPane.getScene() != null)
                    NotificacionUtil.advertencia(rootPane.getScene(),
                        "Préstamos no está disponible en modo offline/demo — conéctate a internet para usarlo");
            });
        }
        setupDateFilterBar(
            () -> ReporteService.getInstance().exportPrestamosExcel(exportTarget()),
            () -> ReporteService.getInstance().exportPrestamosCsv(exportTarget())
        );
        setupSearchListener();

        table.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.ESCAPE) {
                table.getSelectionModel().clearSelection(); ev.consume(); return;
            }
            Prestamo sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            switch (ev.getCode()) {
                case ENTER  -> { mostrarDetalle(sel); ev.consume(); }
                case DELETE -> { onDevolver(); ev.consume(); }
                default     -> {}
            }
        });

        if (rootPane != null && canCreate) {
            rootPane.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
                if (ev.getCode() == KeyCode.N && ev.isControlDown()) {
                    onNuevoPrestamo(); ev.consume();
                }
            });
        }

        if (btnKanban != null) {
            btnKanban.selectedProperty().addListener((obs, ov, nv) -> {
                kanbanMode = nv;
                if (table != null)       { table.setVisible(!nv); table.setManaged(!nv); }
                if (kanbanBoard != null) { kanbanBoard.setVisible(nv); kanbanBoard.setManaged(nv); }
                // Icon-only before made this ambiguous — same glyph stayed on
                // screen whether you were about to switch TO kanban or back
                // to the table, with no label to disambiguate. Swap both the
                // icon and the text so "how do I get back to the table" has
                // an obvious answer.
                btnKanban.setText(nv ? "Ver como tabla" : "Vista Kanban");
                btnKanban.setGraphic(new FontIcon(nv ? "mdi2t-table" : "mdi2v-view-column-outline"));
                btnKanban.setTooltip(new Tooltip(nv
                    ? "Volver a la vista de tabla"
                    : "Ver préstamos por columnas de estado (Activos / Vencidos / Devueltos)"));
                if (nv) buildKanbanBoard(data);
            });
        }
        if (kanbanBoard != null) { kanbanBoard.setVisible(false); kanbanBoard.setManaged(false); }

        restoreFilterPrefs();
        restoreEstadoFilter(estadoFilter);
        Platform.runLater(() -> { if (searchField != null) searchField.requestFocus(); });
    }

    @Override
    protected void setupButtonState() {
        super.setupButtonState();
        if (btnDevolver != null)
            btnDevolver.disableProperty().bind(
                table.getSelectionModel().selectedItemProperty()
                    .map(p -> p == null || Prestamo.ESTADO_DEVUELTO.equals(p.getEstado()))
                    .orElse(true));
    }

    @Override
    protected List<Prestamo> fetchAll() throws Exception {
        service.actualizarVencidos();
        return service.getAll();
    }

    @Override
    protected void onDataLoaded(List<Prestamo> list) {
        allData = list;
        applyFilter();
        long activos   = list.stream().filter(p -> Prestamo.ESTADO_ACTIVO.equals(p.getEstado())).count();
        long vencidos  = list.stream().filter(p -> Prestamo.ESTADO_VENCIDO.equals(p.getEstado()) || p.isVencidoCalc()).count();
        long devueltos = list.stream().filter(p -> Prestamo.ESTADO_DEVUELTO.equals(p.getEstado())).count();
        AnimationUtils.animateCount(lblStatActivos,   activos,            700);
        AnimationUtils.animateCount(lblStatVencidos,  vencidos,           700);
        AnimationUtils.animateCount(lblStatDevueltos, devueltos,          700);
        AnimationUtils.animateCount(lblStatTotal,     (long) list.size(), 700);
        AnimationUtils.staggeredFadeInUp(
            List.of(statCardActivos, statCardVencidos, statCardDevueltos, statCardTotal), 280, 55);
    }

    @Override
    protected File doExportPdf(Prestamo item) throws Exception { return service.exportarPdf(item); }

    @Override
    protected String getLoadErrorMessage() { return "No se pudieron cargar los préstamos"; }
    @Override protected String emptyStateIcon()     { return "mdi2c-cube-send"; }
    @Override protected String emptyStateTitle()    { return "Sin préstamos activos"; }
    @Override protected String emptyStateSubtitle() { return "Registra préstamos temporales de bienes entre áreas"; }

    @Override
    protected void onTableDoubleClick(Prestamo item) { mostrarDetalle(item); }

    @Override
    protected ContextMenu buildContextMenu() {
        ContextMenu cm = new ContextMenu();
        MenuItem miDev = new MenuItem("Registrar devolución");
        miDev.setGraphic(new FontIcon("mdi2c-check-circle-outline"));
        miDev.setOnAction(e -> onDevolver());
        MenuItem miPdf = new MenuItem("Exportar comprobante PDF");
        miPdf.setGraphic(new FontIcon("mdi2f-file-pdf-box"));
        miPdf.setOnAction(e -> onExportarPdf());
        cm.getItems().addAll(miDev, new SeparatorMenuItem(), miPdf);
        addLoteExportItem(cm);
        return cm;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void setupStatCardFilters() {
        makeStatFilter(statCardActivos,   "Activos");
        makeStatFilter(statCardVencidos,  "Vencidos");
        makeStatFilter(statCardDevueltos, "Devueltos");
        makeStatFilter(statCardTotal,     "Todos");
    }

    private void makeStatFilter(VBox card, String filterVal) {
        if (card == null || estadoFilter == null) return;
        card.getStyleClass().add("rich-stat-card-clickable");
        card.setOnMouseClicked(e -> {
            String cur = estadoFilter.getValue();
            estadoFilter.setValue(filterVal.equals(cur) ? "Todos" : filterVal);
        });
        AccessibilityUtils.asButton(card, "Filtro rápido: " + filterVal);
    }

    private void updateStatHighlight(String estado) {
        for (VBox c : List.of(statCardActivos, statCardVencidos, statCardDevueltos, statCardTotal)) {
            if (c != null) c.getStyleClass().remove("rich-stat-card-filter-active");
        }
        VBox active = switch (estado == null ? "Todos" : estado) {
            case "Activos"   -> statCardActivos;
            case "Vencidos"  -> statCardVencidos;
            case "Devueltos" -> statCardDevueltos;
            default          -> null;
        };
        if (active != null) active.getStyleClass().add("rich-stat-card-filter-active");
    }

    @Override protected boolean isFilterActive() {
        return super.isFilterActive() || (estadoFilter != null && !"Todos".equals(estadoFilter.getValue()));
    }

    @Override protected void clearFilters() {
        super.clearFilters();
        if (estadoFilter != null) estadoFilter.setValue("Todos");
    }

    @Override
    protected void applyFilter() {
        String q      = searchField != null ? searchField.getText() : "";
        String estado = estadoFilter != null ? estadoFilter.getValue() : "Todos";
        LocalDate desde = dpDesde != null ? dpDesde.getValue() : null;
        LocalDate hasta = dpHasta != null ? dpHasta.getValue() : null;
        List<Prestamo> filtered = allData.stream()
            .filter(p -> switch (estado == null ? "Todos" : estado) {
                case "Activos"   -> Prestamo.ESTADO_ACTIVO.equals(p.getEstado());
                case "Vencidos"  -> Prestamo.ESTADO_VENCIDO.equals(p.getEstado()) || p.isVencidoCalc();
                case "Devueltos" -> Prestamo.ESTADO_DEVUELTO.equals(p.getEstado());
                default          -> true;
            })
            .filter(p -> {
                if (q == null || q.isBlank()) return true;
                String lq = q.toLowerCase();
                return (p.getProductoNombre()   != null && p.getProductoNombre().toLowerCase().contains(lq))
                    || (p.getNumero()           != null && p.getNumero().toLowerCase().contains(lq))
                    || (p.getResponsableNombre() != null && p.getResponsableNombre().toLowerCase().contains(lq))
                    || (p.getAreaDestino()      != null && p.getAreaDestino().toLowerCase().contains(lq));
            })
            .filter(p -> {
                LocalDate f = p.getFechaPrestamo();
                if (desde != null && (f == null || f.isBefore(desde))) return false;
                if (hasta != null && (f == null || f.isAfter(hasta))) return false;
                return true;
            })
            .toList();
        data.setAll(filtered);
        updateCount(filtered.size(), allData.size());
        saveFilterPrefs(q, estado, desde, hasta);
        if (kanbanMode) buildKanbanBoard(data);
    }

    private void buildKanbanBoard(java.util.Collection<Prestamo> items) {
        if (kanbanBoard == null) return;
        kanbanBoard.getChildren().clear();
        kanbanBoard.setSpacing(10);
        kanbanBoard.setFillHeight(true);

        String[][] cols = {
            { "ACTIVO",   "Activos",   "kanban-col-teal",   "mdi2s-swap-horizontal" },
            { "VENCIDO",  "Vencidos",  "kanban-col-amber",  "mdi2a-alert-circle-outline" },
            { "DEVUELTO", "Devueltos", "kanban-col-indigo", "mdi2c-check-all" }
        };

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yy");
        for (String[] col : cols) {
            String estado = col[0], label = col[1], styleClass = col[2], icon = col[3];
            List<Prestamo> colItems = items.stream()
                .filter(p -> estado.equals(p.getEstado()) || (estado.equals("VENCIDO") && p.isVencidoCalc()))
                .toList();

            VBox column = new VBox(8);
            column.getStyleClass().addAll("kanban-column", styleClass);
            column.setPadding(new Insets(12));
            HBox.setHgrow(column, Priority.ALWAYS);

            HBox colHeader = new HBox(8);
            colHeader.setAlignment(Pos.CENTER_LEFT);
            FontIcon colIcon = new FontIcon(icon);
            colIcon.setIconSize(14); colIcon.getStyleClass().add("kanban-col-icon");
            Label colLabel = new Label(label); colLabel.getStyleClass().add("kanban-col-title");
            Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
            Label colCount = new Label(String.valueOf(colItems.size()));
            colCount.getStyleClass().add("kanban-col-count");
            colHeader.getChildren().addAll(colIcon, colLabel, sp, colCount);
            column.getChildren().add(colHeader);

            ScrollPane colScroll = new ScrollPane();
            colScroll.setFitToWidth(true);
            colScroll.setFitToHeight(true);
            colScroll.getStyleClass().add("kanban-col-scroll");
            VBox.setVgrow(colScroll, Priority.ALWAYS);
            VBox cards = new VBox(6);
            cards.setPadding(new Insets(4, 0, 4, 0));
            VBox.setVgrow(cards, Priority.ALWAYS);

            if (colItems.isEmpty()) {
                // A single small, pale label at the top of an otherwise empty
                // column reads as unfinished/broken rather than "no data" —
                // center an icon + message in the available space instead,
                // matching the empty-state treatment used on every other
                // list screen in the app.
                FontIcon emptyIcon = new FontIcon(icon);
                emptyIcon.setIconSize(28);
                emptyIcon.getStyleClass().add("kanban-empty-icon");
                Label empty = new Label("Sin préstamos");
                empty.getStyleClass().add("kanban-empty");
                VBox emptyBox = new VBox(8, emptyIcon, empty);
                emptyBox.setAlignment(Pos.CENTER);
                emptyBox.setFillWidth(true);
                VBox.setVgrow(emptyBox, Priority.ALWAYS);
                cards.setAlignment(Pos.CENTER);
                cards.getChildren().add(emptyBox);
            } else {
                for (Prestamo p : colItems) {
                    VBox card = new VBox(4);
                    card.getStyleClass().add("kanban-card");
                    card.setPadding(new Insets(10, 12, 10, 12));
                    card.setCursor(javafx.scene.Cursor.HAND);

                    Label lblBien  = new Label(p.getProductoNombre() != null ? p.getProductoNombre() : "—");
                    lblBien.getStyleClass().add("kanban-card-title"); lblBien.setWrapText(true);
                    Label lblFolio = new Label(p.getNumero() != null ? p.getNumero() : "—");
                    lblFolio.getStyleClass().add("kanban-card-folio");
                    Label lblResp  = new Label(p.getResponsableNombre() != null ? p.getResponsableNombre() : "—");
                    lblResp.getStyleClass().add("kanban-card-meta");
                    String fechaStr = p.getFechaDevolucionPrevista() != null
                        ? "Dev. " + p.getFechaDevolucionPrevista().format(fmt) : "";
                    Label lblFecha = new Label(fechaStr);
                    lblFecha.getStyleClass().add("kanban-card-meta");
                    if (p.isVencidoCalc() && !fechaStr.isEmpty())
                        lblFecha.getStyleClass().add("kanban-card-overdue");

                    card.getChildren().addAll(lblBien, lblFolio, lblResp);
                    if (!fechaStr.isEmpty()) card.getChildren().add(lblFecha);
                    card.setOnMouseClicked(e -> { if (e.getClickCount() >= 1) mostrarDetalle(p); });
                    AccessibilityUtils.asButton(card, "Préstamo " + lblFolio.getText() + ", " + lblBien.getText() + " — ver detalle");
                    cards.getChildren().add(card);
                }
            }
            colScroll.setContent(cards);
            column.getChildren().add(colScroll);
            kanbanBoard.getChildren().add(column);
        }
    }

    // ── FXML actions ─────────────────────────────────────────────────────────

    @FXML
    private void onNuevoPrestamo() {
        javafx.scene.Scene scene = rootPane.getScene();
        DialogUtil.runAsyncWithProgress(scene, "Cargando bienes activos…",
            () -> productoService.getAll().stream()
                .filter(p -> p.getFechaBaja() == null)
                .sorted((a, b) -> a.getNombre().compareTo(b.getNombre()))
                .toList(),
            productos -> mostrarDialogoNuevo(productos, scene),
            e -> NotificacionUtil.error(scene, "No se pudo cargar el inventario"));
    }

    private void mostrarDialogoNuevo(List<Producto> productos, javafx.scene.Scene scene) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Nuevo Préstamo Temporal");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(600);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2s-swap-horizontal",
            "Nuevo Préstamo Temporal",
            "Registra el préstamo de un bien entre áreas con fecha de devolución",
            AppColors.GREEN, AppColors.GREEN_D);

        GridPane form = DialogUtil.formGrid(170);
        int row = 0;

        ObservableList<Producto> productosObs = FXCollections.observableArrayList(productos);
        ComboBox<Producto> productoCombo = new ComboBox<>(productosObs);
        productoCombo.setMaxWidth(Double.MAX_VALUE);
        productoCombo.setPromptText("Buscar bien…");
        productoCombo.setEditable(true);
        productoCombo.getStyleClass().add("form-input");
        productoCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Producto p) { return p == null ? "" : p.getNombre() + (p.getCodigo() != null ? " (" + p.getCodigo() + ")" : ""); }
            @Override public Producto fromString(String s) { return null; }
        });
        javafx.scene.control.TextField prodEditor = productoCombo.getEditor();
        if (prodEditor != null) {
            prodEditor.textProperty().addListener((obs, o, n) -> {
                if (n == null || n.isBlank()) { productoCombo.setItems(productosObs); return; }
                String lq = n.toLowerCase();
                productoCombo.setItems(productosObs.filtered(p ->
                    p.getNombre().toLowerCase().contains(lq)
                    || (p.getCodigo() != null && p.getCodigo().toLowerCase().contains(lq))));
            });
        }
        form.add(DialogUtil.fieldLabel("Bien *"), 0, row); form.add(productoCombo, 1, row++);

        ComboBox<String> areaDestino = new ComboBox<>(
            FXCollections.observableArrayList(new java.util.ArrayList<>(Areas.getAllAreaNames())));
        areaDestino.setEditable(true); areaDestino.setMaxWidth(Double.MAX_VALUE);
        areaDestino.setPromptText("Área que recibe el bien…");
        areaDestino.getStyleClass().add("form-input");
        form.add(DialogUtil.fieldLabel("Área destino *"), 0, row); form.add(areaDestino, 1, row++);

        TextField fResponsable = new TextField(); fResponsable.setPromptText("Nombre del responsable que recibe");
        fResponsable.getStyleClass().add("form-input");
        TextField fCargo = new TextField(); fCargo.setPromptText("Cargo o puesto");
        fCargo.getStyleClass().add("form-input");
        form.add(DialogUtil.fieldLabel("Responsable *"), 0, row); form.add(fResponsable, 1, row++);
        form.add(DialogUtil.fieldLabel("Cargo"),         0, row); form.add(fCargo,       1, row++);

        DatePicker fFecha = new DatePicker(LocalDate.now().plusWeeks(2));
        fFecha.setMaxWidth(Double.MAX_VALUE);
        fFecha.getStyleClass().add("form-input");
        fFecha.setConverter(FormatUtils.datePickerConverter());
        form.add(DialogUtil.fieldLabel("Devolución prevista *"), 0, row); form.add(fFecha, 1, row++);

        TextField fMotivo = new TextField(); fMotivo.setPromptText("Motivo / uso (opcional)");
        fMotivo.getStyleClass().add("form-input");
        form.add(DialogUtil.fieldLabel("Motivo / uso"), 0, row); form.add(fMotivo, 1, row++);

        Label lblError = new Label();
        lblError.getStyleClass().add("field-error-label");
        lblError.setVisible(false);

        VBox content = new VBox(10, header, form, lblError);
        content.setPadding(new Insets(0, 16, 16, 16));
        DialogUtil.setScrollableContent(dialog.getDialogPane(), content);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setText("Registrar préstamo");
        okBtn.getStyleClass().add("dialog-ok-btn");

        AnimationUtils.staggeredFadeInUp(List.of(header, form), 280, 70);
        Platform.runLater(productoCombo::requestFocus);

        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            lblError.setVisible(false);
            if (productoCombo.getValue() == null) {
                lblError.setText("Selecciona un bien"); lblError.setVisible(true);
                AnimationUtils.shake(lblError); ev.consume(); return;
            }
            if (areaDestino.getValue() == null || areaDestino.getValue().isBlank()) {
                lblError.setText("El área destino es obligatoria"); lblError.setVisible(true);
                AnimationUtils.shake(lblError); ev.consume(); return;
            }
            if (fResponsable.getText().isBlank()) {
                lblError.setText("El nombre del responsable es obligatorio"); lblError.setVisible(true);
                AnimationUtils.shake(lblError); ev.consume(); return;
            }
            if (fFecha.getValue() == null || !fFecha.getValue().isAfter(LocalDate.now())) {
                lblError.setText("La fecha de devolución debe ser posterior a hoy"); lblError.setVisible(true);
                AnimationUtils.shake(lblError); ev.consume(); return;
            }
        });

        dialog.showAndWait().filter(bt -> bt == ButtonType.OK).ifPresent(bt -> {
            Producto prod = productoCombo.getValue();
            DialogUtil.runAsync(
                () -> service.crear(prod.getId(), areaDestino.getValue().trim(),
                    fResponsable.getText().trim(), fCargo.getText().trim(),
                    fMotivo.getText().trim(), fFecha.getValue()),
                prestamo -> {
                    NotificacionUtil.exito(scene, "Préstamo " + prestamo.getNumero() + " registrado");
                    loadData();
                    exportarPdfAsync(prestamo, scene);
                },
                e -> NotificacionUtil.error(scene, "No se pudo registrar el préstamo: "
                    + (e.getMessage() != null ? e.getMessage() : "Error"))
            );
        });
    }

    @FXML
    private void onDevolver() {
        Prestamo sel = table.getSelectionModel().getSelectedItem();
        if (sel == null || Prestamo.ESTADO_DEVUELTO.equals(sel.getEstado())) return;

        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Registrar devolución");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        DatePicker fFechaReal = new DatePicker(LocalDate.now());
        fFechaReal.setConverter(FormatUtils.datePickerConverter());
        fFechaReal.getStyleClass().add("form-input");

        GridPane form = DialogUtil.formGrid(160);
        form.add(DialogUtil.fieldLabel("Préstamo:"),          0, 0);
        form.add(new Label(sel.getNumero() + " — " + sel.getProductoNombre()), 1, 0);
        form.add(DialogUtil.fieldLabel("Fecha real devolución:"), 0, 1);
        form.add(fFechaReal, 1, 1);

        VBox content = new VBox(10, form);
        content.setPadding(new Insets(16));
        dialog.getDialogPane().setContent(content);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setText("Confirmar devolución");
        okBtn.getStyleClass().add("dialog-ok-btn");

        dialog.showAndWait().filter(bt -> bt == ButtonType.OK).ifPresent(bt ->
            DialogUtil.runAsync(
                () -> { service.devolver(sel.getId(), fFechaReal.getValue()); return null; },
                v -> { NotificacionUtil.exito(rootPane.getScene(), "Devolución registrada"); loadData(); },
                e -> NotificacionUtil.error(rootPane.getScene(), "No se pudo registrar la devolución")
            )
        );
    }

    @FXML
    private void onExportarExcel() {
        javafx.scene.Scene scene = rootPane.getScene();
        List<Prestamo> rows = data.isEmpty() ? allData : new java.util.ArrayList<>(data);
        if (rows.isEmpty()) { NotificacionUtil.advertencia(scene, "No hay préstamos para exportar"); return; }
        DialogUtil.runAsync(
            () -> service.exportarExcel(rows),
            file -> DialogUtil.showExportResultDialog(scene, file),
            e -> NotificacionUtil.error(scene, "No se pudo exportar a Excel")
        );
    }

    private void mostrarDetalle(Prestamo p) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        ButtonType btnVerBien = new ButtonType("Ver en inventario", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(btnVerBien, ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(460);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        javafx.scene.Node verBienNode = dialog.getDialogPane().lookupButton(btnVerBien);
        if (verBienNode instanceof Button verBtn) {
            verBtn.setGraphic(new FontIcon("mdi2c-cube-outline"));
            verBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
                ev.consume();
                dialog.close();
                NavigationContext.setPendingProductId(p.getProductoId());
                if (MainController.getInstance() != null) MainController.getInstance().navigateTo("productos");
            });
        }

        HBox header = DialogUtil.gradientHeader("mdi2s-swap-horizontal",
            "Detalle del Préstamo " + p.getNumero(), p.getProductoNombre(), "#166534", "#15803D");
        DialogUtil.addCopyButton(header, p.getNumero());

        GridPane g = DialogUtil.formGrid(160);
        String[][] rows = {
            {"Bien:",                p.getProductoNombre()},
            {"Código:",              p.getProductoCodigo() != null ? p.getProductoCodigo() : "—"},
            {"Área origen:",         p.getAreaOrigen()},
            {"Área destino:",        p.getAreaDestino()},
            {"Responsable:",         p.getResponsableNombre()},
            {"Cargo:",               p.getResponsableCargo() != null ? p.getResponsableCargo() : "—"},
            {"Fecha préstamo:",      p.getFechaPrestamo() != null ? FormatUtils.formatDate(p.getFechaPrestamo()) : "—"},
            {"Devolución prevista:", p.getFechaDevolucionPrevista() != null ? FormatUtils.formatDate(p.getFechaDevolucionPrevista()) : "—"},
            {"Devolución real:",     p.getFechaDevolucionReal() != null ? FormatUtils.formatDate(p.getFechaDevolucionReal()) : "Pendiente"},
            {"Motivo:",              p.getMotivo() != null ? p.getMotivo() : "—"},
            {"Estado:",              p.getEstado()},
        };
        int i = 0;
        for (String[] r : rows) {
            Label k = new Label(r[0]); k.getStyleClass().add("dlg-detail-label"); k.setMinWidth(155);
            Label v = new Label(r[1]); v.getStyleClass().add("dlg-detail-value"); v.setWrapText(true);
            g.add(k, 0, i); g.add(v, 1, i++);
        }

        VBox content = new VBox(0, header, g);
        g.setPadding(new Insets(16, 22, 16, 22));
        dialog.getDialogPane().setContent(content);
        AnimationUtils.staggeredFadeInUp(List.of(header, g), 260, 70);
        dialog.showAndWait();
    }
}
