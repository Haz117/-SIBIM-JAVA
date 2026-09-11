package com.sibim.controller;

import com.sibim.config.Areas;
import com.sibim.model.Prestamo;
import com.sibim.model.Producto;
import com.sibim.repository.ProductoRepository;
import com.sibim.service.PrestamoService;
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
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.time.LocalDate;
import java.util.List;

public class PrestamosController {

    private static final Logger log = LoggerFactory.getLogger(PrestamosController.class);

    @FXML private VBox rootPane;
    @FXML private Label lblStatActivos;
    @FXML private Label lblStatVencidos;
    @FXML private Label lblStatDevueltos;
    @FXML private Label lblStatTotal;
    @FXML private VBox statCardActivos;
    @FXML private VBox statCardVencidos;
    @FXML private VBox statCardDevueltos;
    @FXML private VBox statCardTotal;
    @FXML private TableView<Prestamo> table;
    @FXML private TableColumn<Prestamo, String> colNumero;
    @FXML private TableColumn<Prestamo, String> colBien;
    @FXML private TableColumn<Prestamo, String> colAreaOrigen;
    @FXML private TableColumn<Prestamo, String> colAreaDestino;
    @FXML private TableColumn<Prestamo, String> colResponsable;
    @FXML private TableColumn<Prestamo, String> colFechaPrevista;
    @FXML private TableColumn<Prestamo, String> colEstado;
    @FXML private Button btnNuevo;
    @FXML private Button btnDevolver;
    @FXML private Button btnExportarPdf;
    @FXML private ProgressIndicator spinner;
    @FXML private ComboBox<String> estadoFilter;
    @FXML private TextField searchField;

    private final PrestamoService service       = new PrestamoService();
    private final ProductoRepository productoRepo = new ProductoRepository();
    private final ObservableList<Prestamo> data  = FXCollections.observableArrayList();
    private List<Prestamo> allData               = List.of();

    @FXML
    public void initialize() {
        setupEstadoFilter();
        setupTable();
        setupPermisos();
        setupSearch();
        setupButtonState();
        loadData();
        Platform.runLater(() -> { if (searchField != null) searchField.requestFocus(); });
    }

    private void setupEstadoFilter() {
        if (estadoFilter == null) return;
        estadoFilter.getItems().addAll("Todos", "Activos", "Vencidos", "Devueltos");
        estadoFilter.setValue("Todos");
        estadoFilter.valueProperty().addListener((obs, o, n) -> applyFilter());
    }

    private void setupTable() {
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

        // Row color for overdue
        table.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Prestamo p, boolean empty) {
                super.updateItem(p, empty);
                getStyleClass().removeAll("row-warning");
                if (!empty && p != null && p.isVencidoCalc())
                    getStyleClass().add("row-warning");
            }
        });

        table.setItems(data);
        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null)
                mostrarDetalle(table.getSelectionModel().getSelectedItem());
        });

        ContextMenu cm = new ContextMenu();
        MenuItem miDev  = new MenuItem("Registrar devolución");
        miDev.setGraphic(new FontIcon("mdi2c-check-circle-outline"));
        miDev.setOnAction(e -> onDevolver());
        MenuItem miPdf  = new MenuItem("Exportar comprobante PDF");
        miPdf.setGraphic(new FontIcon("mdi2f-file-pdf-box"));
        miPdf.setOnAction(e -> onExportarPdf());
        cm.getItems().addAll(miDev, new SeparatorMenuItem(), miPdf);
        table.setContextMenu(cm);
    }

    private void setupPermisos() {
        boolean canCreate = SessionManager.isAdmin() || SessionManager.isSecretario();
        if (btnNuevo != null) { btnNuevo.setVisible(canCreate); btnNuevo.setManaged(canCreate); }
    }

    private void setupSearch() {
        if (searchField == null) return;
        searchField.textProperty().addListener((obs, o, n) -> applyFilter());
    }

    private void setupButtonState() {
        javafx.beans.value.ObservableValue<Prestamo> sel =
            table.getSelectionModel().selectedItemProperty();
        if (btnDevolver != null)
            btnDevolver.disableProperty().bind(
                table.getSelectionModel().selectedItemProperty()
                    .map(p -> p == null || Prestamo.ESTADO_DEVUELTO.equals(p.getEstado()))
                    .orElse(true));
        if (btnExportarPdf != null)
            btnExportarPdf.disableProperty().bind(sel.isNull());
    }

    private void loadData() {
        if (spinner != null) { spinner.setVisible(true); spinner.setManaged(true); }
        com.sibim.util.AppExecutor.submit(() -> {
            try {
                service.actualizarVencidos();
                List<Prestamo> list = service.getAll();
                Platform.runLater(() -> {
                    allData = list;
                    applyFilter();
                    updateStats(list);
                    AnimationUtils.staggeredFadeInUp(
                        List.of(statCardActivos, statCardVencidos, statCardDevueltos, statCardTotal),
                        280, 55);
                    if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
                });
            } catch (Exception e) {
                log.error("Error cargando préstamos", e);
                Platform.runLater(() -> {
                    if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
                    NotificacionUtil.error(rootPane.getScene(), "No se pudieron cargar los préstamos");
                });
            }
        });
    }

    private void applyFilter() {
        String q      = searchField != null ? searchField.getText() : "";
        String estado = estadoFilter != null ? estadoFilter.getValue() : "Todos";
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
                return p.getProductoNombre().toLowerCase().contains(lq)
                    || p.getNumero().toLowerCase().contains(lq)
                    || p.getResponsableNombre().toLowerCase().contains(lq)
                    || p.getAreaDestino().toLowerCase().contains(lq);
            }).toList();
        data.setAll(filtered);
    }

    private void updateStats(List<Prestamo> list) {
        long activos   = list.stream().filter(p -> Prestamo.ESTADO_ACTIVO.equals(p.getEstado())).count();
        long vencidos  = list.stream().filter(p -> Prestamo.ESTADO_VENCIDO.equals(p.getEstado()) || p.isVencidoCalc()).count();
        long devueltos = list.stream().filter(p -> Prestamo.ESTADO_DEVUELTO.equals(p.getEstado())).count();
        AnimationUtils.animateCount(lblStatActivos,   activos,        v -> String.valueOf(v));
        AnimationUtils.animateCount(lblStatVencidos,  vencidos,       v -> String.valueOf(v));
        AnimationUtils.animateCount(lblStatDevueltos, devueltos,      v -> String.valueOf(v));
        AnimationUtils.animateCount(lblStatTotal,     (long) list.size(), v -> String.valueOf(v));
    }

    @FXML
    private void onRefresh() { loadData(); }

    @FXML
    private void onNuevoPrestamo() {
        javafx.scene.Scene scene = rootPane.getScene();
        DialogUtil.runAsyncWithProgress(scene, "Cargando bienes activos…",
            () -> productoRepo.findAll().stream()
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
            "#166534", "#15803D");

        GridPane form = DialogUtil.formGrid(170);
        int row = 0;

        // Bien
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

        form.add(DialogUtil.fieldLabel("Bien *"), 0, row);
        form.add(productoCombo, 1, row++);

        // Área destino
        ComboBox<String> areaDestino = new ComboBox<>(
            FXCollections.observableArrayList(new java.util.ArrayList<>(Areas.getAllAreaNames())));
        areaDestino.setEditable(true);
        areaDestino.setMaxWidth(Double.MAX_VALUE);
        areaDestino.setPromptText("Área que recibe el bien…");
        areaDestino.getStyleClass().add("form-input");
        form.add(DialogUtil.fieldLabel("Área destino *"), 0, row);
        form.add(areaDestino, 1, row++);

        // Responsable
        TextField fResponsable = new TextField(); fResponsable.setPromptText("Nombre del responsable que recibe");
        fResponsable.getStyleClass().add("form-input");
        TextField fCargo = new TextField(); fCargo.setPromptText("Cargo o puesto");
        fCargo.getStyleClass().add("form-input");
        form.add(DialogUtil.fieldLabel("Responsable *"), 0, row);
        form.add(fResponsable, 1, row++);
        form.add(DialogUtil.fieldLabel("Cargo"), 0, row);
        form.add(fCargo, 1, row++);

        // Fecha devolución
        DatePicker fFecha = new DatePicker(LocalDate.now().plusWeeks(2));
        fFecha.setMaxWidth(Double.MAX_VALUE);
        fFecha.getStyleClass().add("form-input");
        fFecha.setConverter(FormatUtils.datePickerConverter());
        form.add(DialogUtil.fieldLabel("Devolución prevista *"), 0, row);
        form.add(fFecha, 1, row++);

        // Motivo
        TextField fMotivo = new TextField(); fMotivo.setPromptText("Motivo / uso (opcional)");
        fMotivo.getStyleClass().add("form-input");
        form.add(DialogUtil.fieldLabel("Motivo / uso"), 0, row);
        form.add(fMotivo, 1, row++);

        Label lblError = new Label();
        lblError.getStyleClass().add("form-error-label");
        lblError.setVisible(false);

        VBox content = new VBox(10, header, form, lblError);
        content.setPadding(new Insets(0, 16, 16, 16));
        dialog.getDialogPane().setContent(content);

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
                    DialogUtil.runAsync(
                        () -> service.exportarPdf(prestamo),
                        file -> {
                            if (file == null) return;
                            try { Desktop.getDesktop().open(file); }
                            catch (Exception e) { NotificacionUtil.advertencia(scene, "PDF: " + file.getAbsolutePath()); }
                        },
                        e -> log.warn("Error generando PDF del préstamo", e)
                    );
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
        form.add(DialogUtil.fieldLabel("Préstamo:"), 0, 0);
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
    private void onExportarPdf() {
        Prestamo sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        javafx.scene.Scene scene = rootPane.getScene();
        DialogUtil.runAsync(
            () -> service.exportarPdf(sel),
            file -> {
                if (file == null) return;
                try { Desktop.getDesktop().open(file); }
                catch (Exception e) { NotificacionUtil.advertencia(scene, "PDF: " + file.getAbsolutePath()); }
            },
            e -> NotificacionUtil.error(scene, "No se pudo generar el PDF")
        );
    }

    private void mostrarDetalle(Prestamo p) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(460);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2s-swap-horizontal",
            "Detalle del Préstamo " + p.getNumero(),
            p.getProductoNombre(),
            "#166534", "#15803D");

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
