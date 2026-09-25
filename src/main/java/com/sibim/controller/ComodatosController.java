package com.sibim.controller;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Comodato;
import com.sibim.model.Producto;
import com.sibim.service.ComodatoService;
import com.sibim.service.ProductoService;
import com.sibim.session.NavigationContext;
import com.sibim.session.SessionManager;
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
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.io.File;
import java.time.LocalDate;
import java.util.List;

public class ComodatosController extends BaseDocumentController<Comodato> {

    @FXML private Label   lblStatVigentes;
    @FXML private Label   lblStatVencidos;
    @FXML private Label   lblStatConcluidos;
    @FXML private Label   lblStatTotal;
    @FXML private VBox    statCardVigentes;
    @FXML private VBox    statCardVencidos;
    @FXML private VBox    statCardConcluidos;
    @FXML private VBox    statCardTotal;
    @FXML private TableColumn<Comodato, String> colNumero;
    @FXML private TableColumn<Comodato, String> colBien;
    @FXML private TableColumn<Comodato, String> colEntidad;
    @FXML private TableColumn<Comodato, String> colContacto;
    @FXML private TableColumn<Comodato, String> colFechaInicio;
    @FXML private TableColumn<Comodato, String> colFechaFin;
    @FXML private TableColumn<Comodato, String> colEstado;
    @FXML private Button         btnNuevo;
    @FXML private ComboBox<String> estadoFilter;

    private final ComodatoService  service         = new ComodatoService();
    private final ProductoService  productoService = new ProductoService();
    private List<Comodato> allData = List.of();

    // ── BaseDocumentController hooks ─────────────────────────────────────────

    @Override
    protected void setupColumns() {
        colNumero.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNumero()));
        colBien.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProductoNombre()));
        colEntidad.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEntidadReceptora()));
        colContacto.setCellValueFactory(c -> {
            String n    = c.getValue().getContactoNombre();
            String cargo = c.getValue().getContactoCargo();
            return new SimpleStringProperty(cargo != null && !cargo.isBlank() ? n + " · " + cargo : n);
        });
        colFechaInicio.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getFechaInicio() != null
                ? FormatUtils.formatDate(c.getValue().getFechaInicio()) : "—"));
        colFechaFin.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getFechaFin() != null
                ? FormatUtils.formatDate(c.getValue().getFechaFin()) : "Indefinida"));

        colEstado.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                Comodato c = getTableRow().getItem();
                String efective = c.getEstadoEfectivo();
                String txt = switch (efective) {
                    case Comodato.ESTADO_VIGENTE    -> "Vigente";
                    case Comodato.ESTADO_VENCIDO    -> "Vencido";
                    case Comodato.ESTADO_CONCLUIDO  -> "Concluido";
                    case Comodato.ESTADO_RESCINDIDO -> "Rescindido";
                    default -> efective;
                };
                String cls = switch (efective) {
                    case Comodato.ESTADO_VIGENTE    -> "cell-badge-success";
                    case Comodato.ESTADO_VENCIDO    -> "cell-badge-warning";
                    case Comodato.ESTADO_CONCLUIDO  -> "cell-badge-blue";
                    case Comodato.ESTADO_RESCINDIDO -> "cell-badge-danger";
                    default -> "cell-badge-blue";
                };
                Label badge = new Label(txt);
                badge.getStyleClass().add(cls);
                setGraphic(badge);
            }
        });
        colEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEstadoEfectivo()));

        table.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Comodato c, boolean empty) {
                super.updateItem(c, empty);
                getStyleClass().removeAll("row-warning");
                if (!empty && c != null && c.isVencido()) getStyleClass().add("row-warning");
            }
        });
    }

    @Override
    protected void onInitialize() {
        if (estadoFilter != null) {
            estadoFilter.getItems().addAll("Todos", "Vigentes", "Vencidos", "Concluidos", "Rescindidos");
            estadoFilter.setValue("Todos");
            estadoFilter.valueProperty().addListener((obs, o, n) -> applyFilter());
            estadoFilter.valueProperty().addListener((obs, o, n) -> updateStatHighlight(n));
            setupStatCardFilters();
        }
        boolean offline = DatabaseConfig.getLocalDataStore() != null;
        boolean canCreate = (SessionManager.isAdmin() || SessionManager.isSecretario()) && !offline;
        if (btnNuevo != null) { btnNuevo.setVisible(canCreate); btnNuevo.setManaged(canCreate); }
        setupDateFilterBar(
            () -> com.sibim.service.ReporteService.getInstance().exportComodatosExcel(exportTarget()),
            () -> com.sibim.service.ReporteService.getInstance().exportComodatosCsv(exportTarget())
        );
        setupSearchListener();

        table.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.ESCAPE) {
                table.getSelectionModel().clearSelection(); ev.consume(); return;
            }
            Comodato sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            if (ev.getCode() == KeyCode.ENTER) { mostrarDetalle(sel); ev.consume(); }
        });

        if (rootPane != null && canCreate) {
            rootPane.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
                if (ev.getCode() == KeyCode.N && ev.isControlDown()) {
                    onNuevoComodato(); ev.consume();
                }
            });
        }

        restoreFilterPrefs();
        restoreEstadoFilter(estadoFilter);
        Platform.runLater(() -> {
            if (searchField != null) searchField.requestFocus();
            if (offline && rootPane.getScene() != null)
                NotificacionUtil.advertencia(rootPane.getScene(),
                    "Comodatos no está disponible en modo offline/demo — conéctate a internet para usarlo");
        });
    }

    @Override
    protected List<Comodato> fetchAll() throws Exception {
        service.actualizarVencidos();
        return service.getAll();
    }

    @Override
    protected void onDataLoaded(List<Comodato> list) {
        allData = list;
        applyFilter();
        long vigentes   = list.stream().filter(c -> Comodato.ESTADO_VIGENTE.equals(c.getEstado()) && !c.isVencido()).count();
        long vencidos   = list.stream().filter(c -> Comodato.ESTADO_VENCIDO.equals(c.getEstado()) || c.isVencido()).count();
        long concluidos = list.stream().filter(c -> Comodato.ESTADO_CONCLUIDO.equals(c.getEstado())).count();
        AnimationUtils.animateCount(lblStatVigentes,   vigentes,           700);
        AnimationUtils.animateCount(lblStatVencidos,   vencidos,           700);
        AnimationUtils.animateCount(lblStatConcluidos, concluidos,         700);
        AnimationUtils.animateCount(lblStatTotal,      (long) list.size(), 700);
        AnimationUtils.staggeredFadeInUp(
            List.of(statCardVigentes, statCardVencidos, statCardConcluidos, statCardTotal), 280, 55);
    }

    @Override
    protected File doExportPdf(Comodato item) throws Exception { return service.exportarPdf(item); }

    @Override
    protected String getLoadErrorMessage() { return "No se pudieron cargar los comodatos"; }
    @Override protected String emptyStateIcon()     { return "mdi2c-clipboard-list-outline"; }
    @Override protected String emptyStateTitle()    { return "Sin comodatos registrados"; }
    @Override protected String emptyStateSubtitle() { return "Registra préstamos formales a entidades externas"; }

    @Override
    protected void onTableDoubleClick(Comodato item) { mostrarDetalle(item); }

    @Override
    protected ContextMenu buildContextMenu() {
        ContextMenu cm = new ContextMenu();
        MenuItem miConcluir = new MenuItem("Dar por concluido");
        miConcluir.setGraphic(new FontIcon("mdi2c-check-circle-outline"));
        miConcluir.setOnAction(e -> onConcluir());
        MenuItem miRescindir = new MenuItem("Rescindir");
        miRescindir.setGraphic(new FontIcon("mdi2c-cancel"));
        miRescindir.setOnAction(e -> onRescindir());
        MenuItem miPdf = new MenuItem("Exportar PDF");
        miPdf.setGraphic(new FontIcon("mdi2f-file-pdf-box"));
        miPdf.setOnAction(e -> onExportarPdf());
        cm.getItems().addAll(miConcluir, miRescindir, new SeparatorMenuItem(), miPdf);
        addLoteExportItem(cm);
        return cm;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void setupStatCardFilters() {
        makeStatFilter(statCardVigentes,   "Vigentes");
        makeStatFilter(statCardVencidos,   "Vencidos");
        makeStatFilter(statCardConcluidos, "Concluidos");
        makeStatFilter(statCardTotal,      "Todos");
    }

    private void makeStatFilter(VBox card, String filterVal) {
        if (card == null || estadoFilter == null) return;
        card.getStyleClass().add("rich-stat-card-clickable");
        card.setOnMouseClicked(e -> {
            String cur = estadoFilter.getValue();
            estadoFilter.setValue(filterVal.equals(cur) ? "Todos" : filterVal);
        });
    }

    private void updateStatHighlight(String estado) {
        for (VBox c : List.of(statCardVigentes, statCardVencidos, statCardConcluidos, statCardTotal)) {
            if (c != null) c.getStyleClass().remove("rich-stat-card-filter-active");
        }
        VBox active = switch (estado == null ? "Todos" : estado) {
            case "Vigentes"   -> statCardVigentes;
            case "Vencidos"   -> statCardVencidos;
            case "Concluidos" -> statCardConcluidos;
            default           -> null;
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
        List<Comodato> filtered = allData.stream()
            .filter(c -> switch (estado == null ? "Todos" : estado) {
                case "Vigentes"    -> Comodato.ESTADO_VIGENTE.equals(c.getEstado()) && !c.isVencido();
                case "Vencidos"    -> Comodato.ESTADO_VENCIDO.equals(c.getEstado()) || c.isVencido();
                case "Concluidos"  -> Comodato.ESTADO_CONCLUIDO.equals(c.getEstado());
                case "Rescindidos" -> Comodato.ESTADO_RESCINDIDO.equals(c.getEstado());
                default            -> true;
            })
            .filter(c -> {
                if (q == null || q.isBlank()) return true;
                String lq = q.toLowerCase();
                return (c.getProductoNombre() != null && c.getProductoNombre().toLowerCase().contains(lq))
                    || (c.getNumero()          != null && c.getNumero().toLowerCase().contains(lq))
                    || (c.getEntidadReceptora() != null && c.getEntidadReceptora().toLowerCase().contains(lq))
                    || (c.getContactoNombre()  != null && c.getContactoNombre().toLowerCase().contains(lq));
            })
            .filter(c -> {
                LocalDate f = c.getFechaInicio();
                if (desde != null && (f == null || f.isBefore(desde))) return false;
                if (hasta != null && (f == null || f.isAfter(hasta))) return false;
                return true;
            })
            .toList();
        data.setAll(filtered);
        updateCount(filtered.size(), allData.size());
        saveFilterPrefs(q, estado, desde, hasta);
    }

    // ── FXML actions ─────────────────────────────────────────────────────────

    @FXML
    private void onNuevoComodato() {
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
        dialog.setTitle("Nuevo Comodato");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(640);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2c-clipboard-list-outline",
            "Nuevo Comodato",
            "Registra el préstamo formal de un bien a una entidad externa",
            AppColors.DEEP_PURPLE, AppColors.PURPLE);

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
            @Override public String toString(Producto p) {
                return p == null ? "" : p.getNombre() + (p.getCodigo() != null ? " (" + p.getCodigo() + ")" : "");
            }
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
        form.add(DialogUtil.fieldLabel("Bien *"),             0, row); form.add(productoCombo, 1, row++);

        // Entidad receptora
        TextField fEntidad = new TextField(); fEntidad.setPromptText("Nombre de la asociación, escuela, dependencia…");
        fEntidad.getStyleClass().add("form-input");
        form.add(DialogUtil.fieldLabel("Entidad receptora *"), 0, row); form.add(fEntidad, 1, row++);

        // Contacto
        TextField fContacto = new TextField(); fContacto.setPromptText("Nombre del representante o titular");
        fContacto.getStyleClass().add("form-input");
        TextField fCargo = new TextField(); fCargo.setPromptText("Cargo o puesto del contacto");
        fCargo.getStyleClass().add("form-input");
        form.add(DialogUtil.fieldLabel("Contacto *"), 0, row); form.add(fContacto, 1, row++);
        form.add(DialogUtil.fieldLabel("Cargo"),      0, row); form.add(fCargo,    1, row++);

        // Domicilio
        TextField fDomicilio = new TextField(); fDomicilio.setPromptText("Domicilio de la entidad receptora (opcional)");
        fDomicilio.getStyleClass().add("form-input");
        form.add(DialogUtil.fieldLabel("Domicilio"), 0, row); form.add(fDomicilio, 1, row++);

        // Fechas
        DatePicker fFechaInicio = new DatePicker(LocalDate.now());
        fFechaInicio.setMaxWidth(Double.MAX_VALUE);
        fFechaInicio.getStyleClass().add("form-input");
        fFechaInicio.setConverter(FormatUtils.datePickerConverter());
        DatePicker fFechaFin = new DatePicker();
        fFechaFin.setMaxWidth(Double.MAX_VALUE);
        fFechaFin.getStyleClass().add("form-input");
        fFechaFin.setPromptText("Sin fecha de término (indefinido)");
        fFechaFin.setConverter(FormatUtils.datePickerConverter());
        form.add(DialogUtil.fieldLabel("Fecha inicio *"), 0, row); form.add(fFechaInicio, 1, row++);
        form.add(DialogUtil.fieldLabel("Fecha fin"),      0, row); form.add(fFechaFin,    1, row++);

        // Motivo
        TextArea fMotivo = new TextArea(); fMotivo.setPromptText("Descripción del uso o propósito (opcional)");
        fMotivo.getStyleClass().add("form-input"); fMotivo.setPrefRowCount(2); fMotivo.setWrapText(true);
        form.add(DialogUtil.fieldLabel("Motivo / uso"), 0, row); form.add(fMotivo, 1, row++);

        // Condiciones
        TextArea fCondiciones = new TextArea(); fCondiciones.setPromptText("Condiciones del comodato: cuidado, restricciones, obligaciones… (opcional)");
        fCondiciones.getStyleClass().add("form-input"); fCondiciones.setPrefRowCount(3); fCondiciones.setWrapText(true);
        form.add(DialogUtil.fieldLabel("Condiciones"), 0, row); form.add(fCondiciones, 1, row++);

        Label lblError = new Label();
        lblError.getStyleClass().add("field-error-label");
        lblError.setVisible(false);

        VBox content = new VBox(10, header, form, lblError);
        content.setPadding(new Insets(0, 16, 16, 16));
        DialogUtil.setScrollableContent(dialog.getDialogPane(), content);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setText("Registrar comodato");
        okBtn.getStyleClass().add("dialog-ok-btn");

        AnimationUtils.staggeredFadeInUp(List.of(header, form), 280, 70);
        Platform.runLater(productoCombo::requestFocus);

        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            lblError.setVisible(false);
            if (productoCombo.getValue() == null) {
                lblError.setText("Selecciona un bien"); lblError.setVisible(true);
                AnimationUtils.shake(lblError); ev.consume(); return;
            }
            if (fEntidad.getText().isBlank()) {
                lblError.setText("La entidad receptora es obligatoria"); lblError.setVisible(true);
                AnimationUtils.shake(lblError); ev.consume(); return;
            }
            if (fContacto.getText().isBlank()) {
                lblError.setText("El nombre del contacto es obligatorio"); lblError.setVisible(true);
                AnimationUtils.shake(lblError); ev.consume(); return;
            }
            if (fFechaInicio.getValue() == null) {
                lblError.setText("La fecha de inicio es obligatoria"); lblError.setVisible(true);
                AnimationUtils.shake(lblError); ev.consume(); return;
            }
            if (fFechaFin.getValue() != null && !fFechaFin.getValue().isAfter(fFechaInicio.getValue())) {
                lblError.setText("La fecha de fin debe ser posterior a la fecha de inicio"); lblError.setVisible(true);
                AnimationUtils.shake(lblError); ev.consume(); return;
            }
        });

        dialog.showAndWait().filter(bt -> bt == ButtonType.OK).ifPresent(bt -> {
            Producto prod = productoCombo.getValue();
            DialogUtil.runAsync(
                () -> service.crear(
                    prod.getId(),
                    fEntidad.getText().trim(),
                    fContacto.getText().trim(),
                    fCargo.getText().trim(),
                    fDomicilio.getText().trim(),
                    fMotivo.getText().trim(),
                    fCondiciones.getText().trim(),
                    fFechaInicio.getValue(),
                    fFechaFin.getValue()),
                comodato -> {
                    NotificacionUtil.exito(scene, "Comodato " + comodato.getNumero() + " registrado");
                    loadData();
                    exportarPdfAsync(comodato, scene);
                },
                e -> NotificacionUtil.error(scene, "No se pudo registrar el comodato: "
                    + (e.getMessage() != null ? e.getMessage() : "Error"))
            );
        });
    }

    private void onConcluir() {
        Comodato sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        if (Comodato.ESTADO_CONCLUIDO.equals(sel.getEstado()) || Comodato.ESTADO_RESCINDIDO.equals(sel.getEstado())) {
            NotificacionUtil.advertencia(rootPane.getScene(), "Este comodato ya está cerrado");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Dar por concluido");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        DatePicker fFechaReal = new DatePicker(LocalDate.now());
        fFechaReal.setConverter(FormatUtils.datePickerConverter());
        fFechaReal.getStyleClass().add("form-input");

        GridPane form = DialogUtil.formGrid(170);
        form.add(DialogUtil.fieldLabel("Comodato:"),              0, 0);
        form.add(new Label(sel.getNumero() + " — " + sel.getProductoNombre()), 1, 0);
        form.add(DialogUtil.fieldLabel("Entidad receptora:"),     0, 1);
        form.add(new Label(sel.getEntidadReceptora()),             1, 1);
        form.add(DialogUtil.fieldLabel("Fecha devolución real:"), 0, 2);
        form.add(fFechaReal,                                       1, 2);

        VBox content = new VBox(10, form);
        content.setPadding(new Insets(16));
        dialog.getDialogPane().setContent(content);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setText("Confirmar conclusión");
        okBtn.getStyleClass().add("dialog-ok-btn");

        dialog.showAndWait().filter(bt -> bt == ButtonType.OK).ifPresent(bt ->
            DialogUtil.runAsync(
                () -> { service.concluir(sel.getId(), fFechaReal.getValue()); return null; },
                v -> { NotificacionUtil.exito(rootPane.getScene(), "Comodato concluido"); loadData(); },
                e -> NotificacionUtil.error(rootPane.getScene(), "No se pudo concluir el comodato")
            )
        );
    }

    private void onRescindir() {
        Comodato sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        if (Comodato.ESTADO_CONCLUIDO.equals(sel.getEstado()) || Comodato.ESTADO_RESCINDIDO.equals(sel.getEstado())) {
            NotificacionUtil.advertencia(rootPane.getScene(), "Este comodato ya está cerrado");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Rescindir comodato");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        TextField fMotivo = new TextField();
        fMotivo.setPromptText("Motivo de la rescisión (opcional)");
        fMotivo.getStyleClass().add("form-input");

        GridPane form = DialogUtil.formGrid(170);
        form.add(DialogUtil.fieldLabel("Comodato:"),          0, 0);
        form.add(new Label(sel.getNumero() + " — " + sel.getProductoNombre()), 1, 0);
        form.add(DialogUtil.fieldLabel("Entidad receptora:"), 0, 1);
        form.add(new Label(sel.getEntidadReceptora()),         1, 1);
        form.add(DialogUtil.fieldLabel("Motivo:"),            0, 2);
        form.add(fMotivo,                                      1, 2);

        VBox content = new VBox(10, form);
        content.setPadding(new Insets(16));
        dialog.getDialogPane().setContent(content);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setText("Rescindir");
        okBtn.getStyleClass().add("dialog-ok-btn");

        dialog.showAndWait().filter(bt -> bt == ButtonType.OK).ifPresent(bt ->
            DialogUtil.runAsync(
                () -> { service.rescindir(sel.getId(), fMotivo.getText()); return null; },
                v -> { NotificacionUtil.exito(rootPane.getScene(), "Comodato rescindido"); loadData(); },
                e -> NotificacionUtil.error(rootPane.getScene(), "No se pudo rescindir el comodato")
            )
        );
    }

    private void mostrarDetalle(Comodato c) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        ButtonType btnVerBien = new ButtonType("Ver en inventario", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(btnVerBien, ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(480);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        javafx.scene.Node verBienNode = dialog.getDialogPane().lookupButton(btnVerBien);
        if (verBienNode instanceof Button verBtn) {
            verBtn.setGraphic(new FontIcon("mdi2c-cube-outline"));
            verBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
                ev.consume();
                dialog.close();
                NavigationContext.setPendingProductId(c.getProductoId());
                if (MainController.getInstance() != null) MainController.getInstance().navigateTo("productos");
            });
        }

        HBox header = DialogUtil.gradientHeader("mdi2c-clipboard-list-outline",
            "Comodato " + c.getNumero(), c.getProductoNombre(), "#4C1D95", "#6D28D9");
        DialogUtil.addCopyButton(header, c.getNumero());

        GridPane g = DialogUtil.formGrid(170);
        String estado = c.getEstadoEfectivo();
        String[][] rows = {
            {"Bien:",              c.getProductoNombre()},
            {"Código:",            c.getProductoCodigo() != null ? c.getProductoCodigo() : "—"},
            {"Entidad receptora:", c.getEntidadReceptora()},
            {"Contacto:",          c.getContactoNombre()},
            {"Cargo:",             c.getContactoCargo() != null ? c.getContactoCargo() : "—"},
            {"Domicilio:",         c.getDomicilio() != null ? c.getDomicilio() : "—"},
            {"Fecha inicio:",      c.getFechaInicio() != null ? FormatUtils.formatDate(c.getFechaInicio()) : "—"},
            {"Fecha fin:",         c.getFechaFin() != null ? FormatUtils.formatDate(c.getFechaFin()) : "Indefinida"},
            {"Devolución real:",   c.getFechaDevolucionReal() != null ? FormatUtils.formatDate(c.getFechaDevolucionReal()) : "Pendiente"},
            {"Estado:",            estado},
            {"Motivo:",            c.getMotivo() != null && !c.getMotivo().isBlank() ? c.getMotivo() : "—"},
            {"Condiciones:",       c.getCondiciones() != null && !c.getCondiciones().isBlank() ? c.getCondiciones() : "—"},
        };
        int i = 0;
        for (String[] r : rows) {
            Label k = new Label(r[0]); k.getStyleClass().add("dlg-detail-label"); k.setMinWidth(165);
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
