package com.sibim.controller;

import com.sibim.config.Areas;
import com.sibim.model.Resguardo;
import com.sibim.model.ResguardoItem;
import com.sibim.model.Producto;
import com.sibim.repository.ProductoRepository;
import com.sibim.service.ResguardoService;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.ConfirmacionUtil;
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ResguardosController extends BaseDocumentController<Resguardo> {

    @FXML private Label   lblStatTotal;
    @FXML private Label   lblStatActivos;
    @FXML private VBox    statCardTotal;
    @FXML private VBox    statCardActivos;
    @FXML private TableColumn<Resguardo, String> colNumero;
    @FXML private TableColumn<Resguardo, String> colResguardante;
    @FXML private TableColumn<Resguardo, String> colArea;
    @FXML private TableColumn<Resguardo, String> colFecha;
    @FXML private TableColumn<Resguardo, String> colEstado;
    @FXML private Button     btnNuevo;
    @FXML private Button     btnCancelar;
    @FXML private TextField  searchField;

    private final ResguardoService  service      = new ResguardoService();
    private final ProductoRepository productoRepo = new ProductoRepository();
    private List<Resguardo> allData = new ArrayList<>();

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
    }

    @Override
    protected void onInitialize() {
        boolean canCreate = SessionManager.isAdmin() || SessionManager.isSecretario();
        if (btnNuevo != null) { btnNuevo.setVisible(canCreate); btnNuevo.setManaged(canCreate); }
        if (searchField != null)
            searchField.textProperty().addListener((obs, o, n) -> applyFilter(n));
        if (btnCancelar != null)
            btnCancelar.disableProperty().bind(
                table.getSelectionModel().selectedItemProperty().isNull());
        Platform.runLater(() -> { if (searchField != null) searchField.requestFocus(); });
    }

    @Override
    protected List<Resguardo> fetchAll() throws Exception { return service.getAll(); }

    @Override
    protected void onDataLoaded(List<Resguardo> list) {
        allData = list;
        applyFilter(searchField != null ? searchField.getText() : "");
        AnimationUtils.animateCount(lblStatTotal,   (long) list.size(),                         700);
        AnimationUtils.animateCount(lblStatActivos, list.stream().filter(Resguardo::isActivo).count(), 700);
        AnimationUtils.staggeredFadeInUp(List.of(statCardTotal, statCardActivos), 280, 60);
    }

    @Override
    protected File doExportPdf(Resguardo item) throws Exception { return service.exportarPdf(item); }

    @Override
    protected String getLoadErrorMessage() { return "No se pudieron cargar los resguardos"; }

    @Override
    protected void addContextMenuItems(ContextMenu cm) {
        MenuItem miCancelar = new MenuItem("Cancelar resguardo");
        miCancelar.setGraphic(new FontIcon("mdi2c-cancel"));
        miCancelar.setOnAction(e -> onCancelar());
        cm.getItems().addAll(new SeparatorMenuItem(), miCancelar);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void applyFilter(String q) {
        if (q == null || q.isBlank()) {
            data.setAll(allData);
        } else {
            String lq = q.toLowerCase();
            data.setAll(allData.stream().filter(r ->
                r.getResguardanteNombre().toLowerCase().contains(lq)
                || r.getNumero().toLowerCase().contains(lq)
                || (r.getResguardanteArea() != null && r.getResguardanteArea().toLowerCase().contains(lq))
            ).toList());
        }
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
            "#6366F1", "#4F46E5");

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
