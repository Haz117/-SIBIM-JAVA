package com.sibim.controller;

import com.sibim.config.Areas;
import com.sibim.model.Resguardo;
import com.sibim.model.ResguardoItem;
import com.sibim.model.Producto;
import com.sibim.repository.ProductoRepository;
import com.sibim.service.ResguardoService;
import com.sibim.session.NavigationContext;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppColors;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.EmptyStateUtil;
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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ResguardosController extends BaseDocumentController<Resguardo> {

    @FXML private Label   lblStatTotal;
    @FXML private Label   lblStatActivos;
    @FXML private Label   lblStatCancelados;
    @FXML private Label   lblStatBienes;
    @FXML private VBox    statCardTotal;
    @FXML private VBox    statCardActivos;
    @FXML private VBox    statCardCancelados;
    @FXML private VBox    statCardBienes;
    @FXML private TableColumn<Resguardo, String> colNumero;
    @FXML private TableColumn<Resguardo, String> colResguardante;
    @FXML private TableColumn<Resguardo, String> colArea;
    @FXML private TableColumn<Resguardo, String> colFecha;
    @FXML private TableColumn<Resguardo, String> colBienes;
    @FXML private TableColumn<Resguardo, String> colEstado;
    @FXML private Button     btnNuevo;
    @FXML private Button     btnCancelar;

    private final ResguardoService   service      = new ResguardoService();
    private final ProductoRepository productoRepo = new ProductoRepository();
    private List<Resguardo> allData = new ArrayList<>();
    private String statQuickFilter = null;

    // ── BaseDocumentController hooks ─────────────────────────────────────────

    @Override
    protected void setupColumns() {
        colNumero.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNumero()));
        colResguardante.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getResguardanteNombre()));
        colArea.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getResguardanteArea() != null ? c.getValue().getResguardanteArea() : "—"));
        colFecha.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getCreadoEn() != null
                ? FormatUtils.formatDate(c.getValue().getCreadoEn().toLocalDate()) : "—"));
        colEstado.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                Resguardo r = getTableRow().getItem();
                Label badge = new Label(r.getEstado());
                badge.getStyleClass().add(Resguardo.ESTADO_ACTIVO.equals(r.getEstado())
                    ? "cell-badge-ok" : "cell-badge-muted");
                setGraphic(badge);
            }
        });
        colEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEstado()));

        colBienes.setCellValueFactory(c -> new SimpleStringProperty(
            String.valueOf(c.getValue().getItems().size())));
    }

    @Override
    protected void onInitialize() {
        boolean canCreate = SessionManager.isAdmin() || SessionManager.isSecretario();
        if (btnNuevo != null) { btnNuevo.setVisible(canCreate); btnNuevo.setManaged(canCreate); }
        setupDateFilterBar(
            () -> com.sibim.service.ReporteService.getInstance().exportResguardosExcel(exportTarget()),
            () -> com.sibim.service.ReporteService.getInstance().exportResguardosCsv(exportTarget())
        );
        setupSearchListener();

        if (btnCancelar != null)
            btnCancelar.disableProperty().bind(
                table.getSelectionModel().selectedItemProperty()
                    .map(r -> r == null || !r.isActivo()).orElse(true));

        table.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.ESCAPE) {
                table.getSelectionModel().clearSelection(); ev.consume(); return;
            }
            Resguardo sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            switch (ev.getCode()) {
                case ENTER  -> { mostrarDetalle(sel); ev.consume(); }
                case DELETE -> { onCancelar(); ev.consume(); }
                default     -> {}
            }
        });

        if (rootPane != null && canCreate) {
            rootPane.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
                if (ev.getCode() == KeyCode.N && ev.isControlDown()) {
                    onNuevoResguardo(); ev.consume();
                }
            });
        }

        restoreFilterPrefs();
        Platform.runLater(() -> { if (searchField != null) searchField.requestFocus(); });
    }

    @Override
    protected List<Resguardo> fetchAll() throws Exception { return service.getAll(); }

    @Override
    protected void onDataLoaded(List<Resguardo> list) {
        allData = list;
        applyFilter();
        long activos    = list.stream().filter(Resguardo::isActivo).count();
        long cancelados = list.stream().filter(r -> !r.isActivo()).count();
        long bienes     = list.stream().filter(Resguardo::isActivo)
                              .mapToLong(r -> r.getItems().size()).sum();
        AnimationUtils.animateCount(lblStatTotal,      (long) list.size(), 700);
        AnimationUtils.animateCount(lblStatActivos,    activos,            700);
        AnimationUtils.animateCount(lblStatCancelados, cancelados,         700);
        AnimationUtils.animateCount(lblStatBienes,     bienes,             700);
        AnimationUtils.staggeredFadeInUp(
            List.of(statCardTotal, statCardActivos, statCardCancelados, statCardBienes), 280, 55);
        setupStatCardFilters();
    }

    @Override
    protected File doExportPdf(Resguardo item) throws Exception { return service.exportarPdf(item); }

    @Override
    protected String getLoadErrorMessage() { return "No se pudieron cargar los resguardos"; }
    @Override protected String emptyStateIcon()     { return "mdi2b-badge-account-outline"; }
    @Override protected String emptyStateTitle()    { return "Sin resguardos registrados"; }
    @Override protected String emptyStateSubtitle() { return "Asigna bienes a servidores públicos desde la sección Bienes"; }

    @Override
    protected boolean isFilterActive() {
        return super.isFilterActive() || statQuickFilter != null;
    }

    @Override
    protected void clearFilters() {
        super.clearFilters();
        statQuickFilter = null;
        updateStatHighlight();
    }

    private void setupStatCardFilters() {
        makeStatFilter(statCardTotal,      null);
        makeStatFilter(statCardActivos,    Resguardo.ESTADO_ACTIVO);
        makeStatFilter(statCardCancelados, Resguardo.ESTADO_CANCELADO);
        updateStatHighlight();
    }

    private void makeStatFilter(VBox card, String estado) {
        if (card == null) return;
        card.getStyleClass().add("rich-stat-card-clickable");
        card.setOnMouseClicked(e -> {
            statQuickFilter = estado != null && estado.equals(statQuickFilter) ? null : estado;
            updateStatHighlight();
            applyFilter();
        });
    }

    private void updateStatHighlight() {
        for (VBox c : List.of(statCardTotal, statCardActivos, statCardCancelados, statCardBienes)) {
            if (c != null) c.getStyleClass().remove("rich-stat-card-filter-active");
        }
        VBox active = statQuickFilter == null ? null
            : Resguardo.ESTADO_ACTIVO.equals(statQuickFilter) ? statCardActivos
            : Resguardo.ESTADO_CANCELADO.equals(statQuickFilter) ? statCardCancelados
            : null;
        if (active != null) active.getStyleClass().add("rich-stat-card-filter-active");
    }

    @Override
    protected void onTableDoubleClick(Resguardo item) { mostrarDetalle(item); }

    @Override
    protected ContextMenu buildContextMenu() {
        ContextMenu cm = new ContextMenu();
        MenuItem miDetalle = new MenuItem("Ver detalle");
        miDetalle.setGraphic(new FontIcon("mdi2e-eye-outline"));
        miDetalle.setOnAction(e -> {
            Resguardo sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) mostrarDetalle(sel);
        });
        MenuItem miPdf = new MenuItem("Exportar PDF");
        miPdf.setGraphic(new FontIcon("mdi2f-file-pdf-box"));
        miPdf.setOnAction(e -> onExportarPdf());
        MenuItem miCancelar = new MenuItem("Dar de baja");
        miCancelar.setGraphic(new FontIcon("mdi2d-delete-circle-outline"));
        miCancelar.setOnAction(e -> onCancelar());
        cm.getItems().addAll(miDetalle, miPdf, new SeparatorMenuItem(), miCancelar);
        addLoteExportItem(cm);
        return cm;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    @Override
    protected void applyFilter() {
        String q = searchField != null ? searchField.getText() : "";
        LocalDate desde = dpDesde != null ? dpDesde.getValue() : null;
        LocalDate hasta = dpHasta != null ? dpHasta.getValue() : null;
        List<Resguardo> filtered = allData.stream()
            .filter(r -> statQuickFilter == null || statQuickFilter.equals(r.getEstado()))
            .filter(r -> {
                if (q == null || q.isBlank()) return true;
                String lq = q.toLowerCase();
                return r.getResguardanteNombre().toLowerCase().contains(lq)
                    || r.getNumero().toLowerCase().contains(lq)
                    || (r.getResguardanteArea() != null && r.getResguardanteArea().toLowerCase().contains(lq));
            })
            .filter(r -> {
                LocalDate f = r.getCreadoEn() != null ? r.getCreadoEn().toLocalDate() : null;
                if (desde != null && (f == null || f.isBefore(desde))) return false;
                if (hasta != null && (f == null || f.isAfter(hasta))) return false;
                return true;
            })
            .toList();
        data.setAll(filtered);
        updateCount(filtered.size(), allData.size());
        saveFilterPrefs(q, desde, hasta);
    }

    // ── FXML actions ─────────────────────────────────────────────────────────

    @FXML
    private void onNuevoResguardo() {
        javafx.scene.Scene scene = rootPane.getScene();
        DialogUtil.runAsyncWithProgress(scene, "Cargando bienes activos…",
            () -> productoRepo.findAll().stream()
                .filter(p -> p.getFechaBaja() == null)
                .sorted((a, b) -> a.getNombre().compareTo(b.getNombre()))
                .toList(),
            productos -> mostrarDialogoNuevo(productos, scene),
            e -> NotificacionUtil.error(scene, "No se pudo cargar el inventario"));
    }

    // TableColumn<ResguardoItem,?>... varargs to addAll() triggers Java's inherent
    // generic-array-creation warning — inescapable with this API, not a real risk here.
    @SuppressWarnings("unchecked")
    private void mostrarDialogoNuevo(List<Producto> productos, javafx.scene.Scene scene) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Nuevo Resguardo de Bienes");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(700);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2c-clipboard-account-outline",
            "Nuevo Resguardo", "Documento oficial de resguardo patrimonial",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        GridPane form = DialogUtil.formGrid(130);
        int row = 0;
        TextField fNombre = new TextField(); fNombre.setPromptText("Nombre completo");
        fNombre.getStyleClass().add("form-input");
        TextField fCargo  = new TextField(); fCargo.setPromptText("Cargo o puesto");
        fCargo.getStyleClass().add("form-input");
        ComboBox<String> areaCombo = new ComboBox<>(
            FXCollections.observableArrayList(new java.util.ArrayList<>(Areas.getAllAreaNames())));
        areaCombo.setEditable(true); areaCombo.setMaxWidth(Double.MAX_VALUE);
        areaCombo.getStyleClass().add("form-input");

        Label lblNombreHint = new Label("Campo requerido");
        lblNombreHint.getStyleClass().addAll("field-hint", "field-hint-error");
        lblNombreHint.setVisible(false); lblNombreHint.setManaged(false);
        fNombre.focusedProperty().addListener((obs, was, now) -> {
            if (!now) {
                boolean empty = fNombre.getText().isBlank();
                lblNombreHint.setVisible(empty); lblNombreHint.setManaged(empty);
                if (empty) fNombre.getStyleClass().add("field-error");
            }
        });
        fNombre.textProperty().addListener((o, a, b) -> {
            if (!b.isBlank()) {
                fNombre.getStyleClass().remove("field-error");
                lblNombreHint.setVisible(false); lblNombreHint.setManaged(false);
            }
        });

        form.add(DialogUtil.fieldLabel("Resguardante *"), 0, row); form.add(new VBox(2, fNombre, lblNombreHint), 1, row++);
        form.add(DialogUtil.fieldLabel("Cargo"),          0, row); form.add(fCargo,     1, row++);
        form.add(DialogUtil.fieldLabel("Área / Dirección"), 0, row); form.add(areaCombo, 1, row++);

        Label lblBienes = new Label("Bienes a resguardar *");
        lblBienes.getStyleClass().add("dialog-field-label");

        ObservableList<Producto> productosObs = FXCollections.observableArrayList(productos);
        ComboBox<Producto> productoCombo = new ComboBox<>(productosObs);
        productoCombo.setMaxWidth(Double.MAX_VALUE);
        productoCombo.getStyleClass().add("form-input");
        productoCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Producto p) { return p == null ? "" : p.getNombre() + " (" + p.getCodigo() + ")"; }
            @Override public Producto fromString(String s) { return null; }
        });
        productoCombo.setPromptText("Seleccionar bien…");
        productoCombo.setEditable(true);
        javafx.scene.control.TextField editorField = productoCombo.getEditor();
        if (editorField != null) {
            editorField.textProperty().addListener((obs, o, n) -> {
                if (n == null || n.isBlank()) { productoCombo.setItems(productosObs); }
                else {
                    String lq = n.toLowerCase();
                    productoCombo.setItems(productosObs.filtered(p ->
                        p.getNombre().toLowerCase().contains(lq)
                        || (p.getCodigo() != null && p.getCodigo().toLowerCase().contains(lq))));
                }
            });
        }

        Button btnAgregar = new Button("+ Agregar");
        btnAgregar.getStyleClass().add("btn-primary");

        ObservableList<ResguardoItem> itemsAgregados = FXCollections.observableArrayList();
        TableView<ResguardoItem> itemsTable = new TableView<>(itemsAgregados);
        itemsTable.setPrefHeight(160);
        itemsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<ResguardoItem, String> colItemNombre = new TableColumn<>("Bien");
        colItemNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProductoNombre()));
        TableColumn<ResguardoItem, String> colItemCodigo = new TableColumn<>("Código");
        colItemCodigo.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getProductoCodigo() != null ? c.getValue().getProductoCodigo() : "—"));
        colItemCodigo.setMaxWidth(100);
        TableColumn<ResguardoItem, String> colItemArea = new TableColumn<>("Área");
        colItemArea.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getArea() != null ? c.getValue().getArea() : "—"));
        TableColumn<ResguardoItem, Void> colItemDel = new TableColumn<>("");
        colItemDel.setMaxWidth(40);
        colItemDel.setCellFactory(col -> new TableCell<>() {
            private final Button btnDel = new Button();
            { btnDel.setGraphic(new FontIcon("mdi2d-delete-outline"));
              btnDel.getStyleClass().add("btn-secondary");
              btnDel.setOnAction(e -> itemsAgregados.remove(getTableRow().getItem())); }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : btnDel);
            }
        });
        itemsTable.getColumns().addAll(colItemNombre, colItemCodigo, colItemArea, colItemDel);

        btnAgregar.setOnAction(e -> {
            Producto sel = productoCombo.getValue();
            if (sel == null) return;
            if (itemsAgregados.stream().anyMatch(i -> sel.getId().equals(i.getProductoId()))) {
                NotificacionUtil.advertencia(scene, "Ese bien ya está en la lista"); return;
            }
            ResguardoItem item = new ResguardoItem();
            item.setProductoId(sel.getId()); item.setProductoNombre(sel.getNombre());
            item.setProductoCodigo(sel.getCodigo()); item.setArea(sel.getArea());
            item.setValorUnitario(sel.getPrecioVenta()); item.setNumeroSerie(sel.getNumeroSerie());
            itemsAgregados.add(item);
            productoCombo.setValue(null);
            if (productoCombo.getEditor() != null) productoCombo.getEditor().clear();
        });

        Button btnCargarArea = new Button("Cargar todos del área");
        btnCargarArea.setGraphic(new FontIcon("mdi2i-import"));
        btnCargarArea.getStyleClass().add("btn-secondary");
        btnCargarArea.setOnAction(e -> {
            String area = areaCombo.getValue();
            if (area == null || area.isBlank()) {
                NotificacionUtil.advertencia(scene, "Selecciona un área primero"); return;
            }
            productos.stream()
                .filter(p -> area.equalsIgnoreCase(p.getArea()))
                .filter(p -> itemsAgregados.stream().noneMatch(i -> p.getId().equals(i.getProductoId())))
                .forEach(p -> {
                    ResguardoItem item = new ResguardoItem();
                    item.setProductoId(p.getId()); item.setProductoNombre(p.getNombre());
                    item.setProductoCodigo(p.getCodigo()); item.setArea(p.getArea());
                    item.setValorUnitario(p.getPrecioVenta()); item.setNumeroSerie(p.getNumeroSerie());
                    itemsAgregados.add(item);
                });
            if (itemsAgregados.isEmpty())
                NotificacionUtil.advertencia(scene, "No hay bienes en esa área");
        });

        TextField fObs = new TextField(); fObs.setPromptText("Observaciones (opcional)");
        fObs.getStyleClass().add("form-input");

        Label lblError = new Label();
        lblError.getStyleClass().add("form-error-label");
        lblError.setVisible(false);

        HBox selectorRow = new HBox(8, productoCombo, btnAgregar);
        HBox.setHgrow(productoCombo, Priority.ALWAYS);
        selectorRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10, header, form,
            lblBienes, selectorRow, btnCargarArea, itemsTable,
            DialogUtil.fieldLabel("Observaciones"), fObs, lblError);
        content.setPadding(new Insets(0, 16, 16, 16));
        DialogUtil.setScrollableContent(dialog.getDialogPane(), content);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setText("Guardar resguardo");
        okBtn.getStyleClass().add("dialog-ok-btn");

        AnimationUtils.staggeredFadeInUp(List.of(header, form, itemsTable), 280, 70);
        Platform.runLater(fNombre::requestFocus);

        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            lblError.setVisible(false);
            if (fNombre.getText().isBlank()) {
                fNombre.getStyleClass().add("field-error");
                lblNombreHint.setVisible(true); lblNombreHint.setManaged(true);
                fNombre.requestFocus();
                lblError.setText("El nombre del resguardante es obligatorio"); lblError.setVisible(true);
                AnimationUtils.shake(lblError); ev.consume(); return;
            }
            if (itemsAgregados.isEmpty()) {
                lblError.setText("Agrega al menos un bien al resguardo"); lblError.setVisible(true);
                AnimationUtils.shake(lblError); ev.consume(); return;
            }
        });

        dialog.showAndWait().filter(bt -> bt == ButtonType.OK).ifPresent(bt ->
            DialogUtil.runAsync(
                () -> service.crear(fNombre.getText().trim(), fCargo.getText().trim(),
                    areaCombo.getValue(), new ArrayList<>(itemsAgregados), fObs.getText().trim()),
                resguardo -> {
                    NotificacionUtil.exito(scene, "Resguardo " + resguardo.getNumero() + " creado");
                    loadData();
                    if (ConfirmacionUtil.confirmar("Exportar PDF",
                            "¿Deseas abrir el PDF del resguardo ahora?"))
                        exportarPdfAsync(resguardo, scene);
                },
                e -> NotificacionUtil.error(scene, "No se pudo guardar el resguardo: "
                    + (e.getMessage() != null ? e.getMessage() : "Error desconocido"))
            )
        );
    }

    private void mostrarDetalle(Resguardo r) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Resguardo " + r.getNumero());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(620);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2b-badge-account-outline",
            "Resguardo " + r.getNumero(),
            r.getResguardanteNombre() + (r.getResguardanteCargo() != null && !r.getResguardanteCargo().isBlank()
                ? " · " + r.getResguardanteCargo() : ""),
            AppColors.PRIMARY, AppColors.PRIMARY_D);
        DialogUtil.addCopyButton(header, r.getNumero());

        GridPane meta = DialogUtil.formGrid(140);
        String[][] infoRows = {
            {"Área:",          r.getResguardanteArea()  != null ? r.getResguardanteArea()  : "—"},
            {"Fecha:",         r.getCreadoEn()          != null ? FormatUtils.formatDate(r.getCreadoEn().toLocalDate()) : "—"},
            {"Registrado por:",r.getCreadoPorNombre()   != null ? r.getCreadoPorNombre()   : "—"},
            {"Estado:",        r.getEstado()},
            {"Observaciones:", r.getObservaciones()     != null && !r.getObservaciones().isBlank()
                ? r.getObservaciones() : "—"},
        };
        int i = 0;
        for (String[] row : infoRows) {
            Label k = new Label(row[0]); k.getStyleClass().add("dlg-detail-label"); k.setMinWidth(135);
            Label v = new Label(row[1]); v.getStyleClass().add("dlg-detail-value"); v.setWrapText(true);
            meta.add(k, 0, i); meta.add(v, 1, i++);
        }

        Label lblBienes = new Label("BIENES EN RESGUARDO");
        lblBienes.getStyleClass().add("dash-section-label");

        ObservableList<ResguardoItem> items = FXCollections.observableArrayList(r.getItems());
        TableView<ResguardoItem> itemsTable = new TableView<>(items);
        itemsTable.setPrefHeight(180);
        itemsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        itemsTable.getStyleClass().add("data-table");

        TableColumn<ResguardoItem, String> cNombre = new TableColumn<>("Bien");
        cNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProductoNombre()));
        TableColumn<ResguardoItem, String> cCodigo = new TableColumn<>("Código");
        cCodigo.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getProductoCodigo() != null ? c.getValue().getProductoCodigo() : "—"));
        cCodigo.setMaxWidth(110);
        TableColumn<ResguardoItem, String> cArea = new TableColumn<>("Área");
        cArea.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getArea() != null ? c.getValue().getArea() : "—"));
        TableColumn<ResguardoItem, String> cValor = new TableColumn<>("Valor");
        cValor.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getValorUnitario() != null
                ? FormatUtils.formatCurrency(c.getValue().getValorUnitario()) : "—"));
        cValor.setMaxWidth(110);
        itemsTable.getColumns().addAll(cNombre, cCodigo, cArea, cValor);

        itemsTable.setPlaceholder(EmptyStateUtil.build(
            "mdi2b-badge-account-outline",
            "Sin bienes en este resguardo",
            "Agrega bienes desde la sección Inventario"));

        Label hintVer = new Label("Doble clic en un bien para verlo en el inventario");
        hintVer.getStyleClass().add("table-count-label");

        itemsTable.setOnMouseClicked(ev -> {
            if (ev.getClickCount() == 2) {
                ResguardoItem sel = itemsTable.getSelectionModel().getSelectedItem();
                if (sel != null && sel.getProductoId() != null) {
                    dialog.close();
                    NavigationContext.setPendingProductId(sel.getProductoId());
                    if (MainController.getInstance() != null) MainController.getInstance().navigateTo("productos");
                }
            }
        });

        VBox content = new VBox(12, header, meta, lblBienes, itemsTable, hintVer);
        content.setPadding(new Insets(0, 16, 16, 16));
        dialog.getDialogPane().setContent(content);
        AnimationUtils.staggeredFadeInUp(List.of(header, meta, itemsTable), 260, 65);
        dialog.showAndWait();
    }

    @FXML
    private void onCancelar() {
        Resguardo sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        if (!sel.isActivo()) { NotificacionUtil.advertencia(rootPane.getScene(), "El resguardo ya está cancelado"); return; }
        if (!ConfirmacionUtil.confirmar("Cancelar resguardo",
                "¿Cancelar el resguardo " + sel.getNumero() + "?\nEsta acción no se puede deshacer."))
            return;
        DialogUtil.runAsync(
            () -> { service.cancelar(sel.getId()); return null; },
            v -> { NotificacionUtil.exito(rootPane.getScene(), "Resguardo cancelado"); loadData(); },
            e -> NotificacionUtil.error(rootPane.getScene(), "No se pudo cancelar el resguardo")
        );
    }
}
