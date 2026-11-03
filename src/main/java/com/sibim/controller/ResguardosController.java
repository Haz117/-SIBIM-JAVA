package com.sibim.controller;

import com.sibim.config.Areas;
import com.sibim.model.Resguardo;
import com.sibim.model.ResguardoItem;
import com.sibim.model.Producto;
import com.sibim.service.ProductoService;
import com.sibim.service.ResguardoService;
import com.sibim.session.NavigationContext;
import com.sibim.session.SessionManager;
import com.sibim.util.AccessibilityUtils;
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
    @FXML private MenuItem   btnLote;
    @FXML private Button     btnCancelar;
    @FXML private Button     btnDetalle;
    @FXML private Button     btnPersona;
    @FXML private Label      lblPistaAcciones;

    private final ResguardoService service         = new ResguardoService();
    private final ProductoService  productoService = new ProductoService();
    private List<Resguardo> allData = new ArrayList<>();
    private String statQuickFilter = null;

    // ── BaseDocumentController hooks ─────────────────────────────────────────

    @Override
    protected void setupColumns() {
        colNumero.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNumero()));
        colResguardante.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getResguardanteNombre()));
        FichaPersona.columnaDeNombre(colResguardante);
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
        // Patrimonio and every área (secretaría or dirección), for its own bienes.
        boolean canCreate = com.sibim.session.Permisos.creaResguardos();
        if (btnNuevo != null) { btnNuevo.setVisible(canCreate); btnNuevo.setManaged(canCreate); }
        if (btnLote != null) btnLote.setVisible(canCreate);
        actualizarSolicitudes();
        setupDateFilterBar(
            () -> com.sibim.service.ReporteService.getInstance().exportResguardosExcel(exportTarget()),
            () -> com.sibim.service.ReporteService.getInstance().exportResguardosCsv(exportTarget())
        );
        setupSearchListener();

        if (btnCancelar != null) { btnCancelar.setVisible(canCreate); btnCancelar.setManaged(canCreate); }
        if (btnCancelar != null)
            btnCancelar.disableProperty().bind(
                table.getSelectionModel().selectedItemProperty()
                    .map(r -> r == null || !r.isActivo()).orElse(true));

        // One resguardo at a time; the PDF also takes several (a ZIP).
        var noUno = javafx.beans.binding.Bindings.size(table.getSelectionModel().getSelectedItems()).isNotEqualTo(1);
        if (btnDetalle != null) btnDetalle.disableProperty().bind(noUno);
        if (btnPersona != null) btnPersona.disableProperty().bind(noUno);
        if (lblPistaAcciones != null) {
            lblPistaAcciones.visibleProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
            lblPistaAcciones.managedProperty().bind(lblPistaAcciones.visibleProperty());
        }

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
        switch (NavigationContext.consumePendingAbrir()) {
            case "nuevo" -> Platform.runLater(this::onNuevoResguardo);
            case "lote"  -> Platform.runLater(this::onResguardosEnLote);
            default -> { }
        }
    }

    @FXML private Button btnSolicitudes;

    /** Patrimonio's inbox of requests, or what this área asked for. */
    @FXML
    private void onSolicitudes() {
        com.sibim.controller.dialogs.SolicitudesDialog.show(rootPane.getScene(), () -> {
            loadData();
            actualizarSolicitudes();
        });
    }

    private void actualizarSolicitudes() {
        if (btnSolicitudes == null) return;
        // The áreas no longer send requests; the button only shows while Patrimonio
        // still has some to answer from before.
        btnSolicitudes.setVisible(false); btnSolicitudes.setManaged(false);
        if (!com.sibim.service.SolicitudService.disponible() || !com.sibim.session.Permisos.atiendeSolicitudes()) return;
        DialogUtil.runAsync(
            () -> new com.sibim.service.SolicitudService().countPendientes(),
            n -> {
                btnSolicitudes.setVisible(n > 0); btnSolicitudes.setManaged(n > 0);
                btnSolicitudes.setText(n > 0 ? "Solicitudes (" + n + ")" : "Solicitudes");
                btnSolicitudes.getStyleClass().removeAll("btn-secondary", "btn-warning-outline");
                btnSolicitudes.getStyleClass().add(n > 0 ? "btn-warning-outline" : "btn-secondary");
            },
            e -> { });
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
    @Override protected String emptyStateSubtitle() { return SessionManager.isAdmin()
        ? "Asigna bienes a servidores públicos con \"Nuevo Resguardo\""
        : "Asigna los bienes de tu área a quien los tiene a su cargo con \"Nuevo Resguardo\""; }

    @Override
    protected boolean isFilterActive() {
        return super.isFilterActive() || statQuickFilter != null;
    }

    @FXML
    private void onVerDetalle() {
        Resguardo sel = table.getSelectionModel().getSelectedItem();
        if (sel != null) mostrarDetalle(sel);
    }

    @FXML
    private void onVerPersona() {
        Resguardo sel = table.getSelectionModel().getSelectedItem();
        if (sel != null) FichaPersona.abrir(sel.getResguardanteNombre(), table.getScene());
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
        AccessibilityUtils.asButton(card, "Filtro rápido: " + (estado == null ? "Todos" : estado));
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
        MenuItem miCancelar = new MenuItem("Cancelar resguardo");
        miCancelar.setGraphic(new FontIcon("mdi2d-delete-circle-outline"));
        miCancelar.setOnAction(e -> onCancelar());
        MenuItem miPersona = new MenuItem("Ver a esta persona");
        miPersona.setGraphic(new FontIcon("mdi2a-account-outline"));
        miPersona.setOnAction(e -> {
            Resguardo sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) FichaPersona.abrir(sel.getResguardanteNombre(), table.getScene());
        });
        cm.getItems().addAll(miDetalle, miPersona, miPdf);
        if (com.sibim.session.Permisos.gestionaDocumentos()) cm.getItems().addAll(new SeparatorMenuItem(), miCancelar);
        addLoteExportItem(cm);
        return cm;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    @Override
    protected void applyFilter() {
        String q = searchField != null ? searchField.getText() : "";
        LocalDate desde = dpDesde != null ? dpDesde.getValue() : null;
        LocalDate hasta = dpHasta != null ? dpHasta.getValue() : null;
        List<Resguardo> filtered = DialogUtil.coincidencias(allData, ResguardosController::textoDeBusqueda, q).stream()
            .filter(r -> statQuickFilter == null || statQuickFilter.equals(r.getEstado()))
            .filter(r -> {
                LocalDate f = r.getCreadoEn() != null ? r.getCreadoEn().toLocalDate() : null;
                if (desde != null && (f == null || f.isBefore(desde))) return false;
                if (hasta != null && (f == null || f.isAfter(hasta))) return false;
                return true;
            })
            .filter(this::coincideArea)
            .toList();
        data.setAll(filtered);
        updateCount(filtered.size(), allData.size());
        saveFilterPrefs(q, desde, hasta);
    }

    /** What the search looks in: "¿en qué resguardo está IMM/20?" is answered here too. */
    static String textoDeBusqueda(Resguardo r) {
        StringBuilder t = new StringBuilder(String.valueOf(r.getResguardanteNombre())).append(' ').append(r.getNumero())
            .append(' ').append(java.util.Objects.requireNonNullElse(r.getResguardanteArea(), ""));
        for (var item : r.getItems()) {
            t.append(' ').append(java.util.Objects.requireNonNullElse(item.getProductoCodigo(), ""))
                .append(' ').append(java.util.Objects.requireNonNullElse(item.getProductoNombre(), ""));
        }
        return t.toString();
    }

    @Override protected boolean filtraPorArea() { return true; }

    @Override protected List<String> areasDe(Resguardo r) {
        return r.getResguardanteArea() != null ? List.of(r.getResguardanteArea()) : List.of();
    }

    // ── FXML actions ─────────────────────────────────────────────────────────

    @FXML
    private void onNuevoResguardo() {
        javafx.scene.Scene scene = rootPane.getScene();
        DialogUtil.runAsyncWithProgress(scene, "Cargando bienes activos…",
            () -> productoService.getAll(),
            productos -> com.sibim.controller.dialogs.NuevoResguardoDialog.show(productos, allData, null, scene, resguardo -> {
                loadData();
                if (ConfirmacionUtil.confirmar("Exportar PDF", "¿Deseas abrir el PDF del resguardo ahora?"))
                    exportarPdfAsync(resguardo, scene);
            }),
            e -> NotificacionUtil.error(scene, "No se pudo cargar el inventario"));
    }

    @FXML
    private void onResguardosEnLote() {
        javafx.scene.Scene scene = rootPane.getScene();
        DialogUtil.runAsyncWithProgress(scene, "Cargando bienes…",
            () -> java.util.Map.entry(productoService.getAll(), service.getAll()),
            datos -> com.sibim.controller.dialogs.ResguardosEnLoteDialog.show(datos.getKey(), datos.getValue(), scene, n -> loadData()),
            e -> NotificacionUtil.error(scene, "No se pudo cargar el inventario"));
    }

    /** Resguardo general del área: every bien of one área under its titular, with the
     *  person each one is individually assigned to. Printed from the inventory as it
     *  is, never saved, so no bien is counted twice. Patrimonio prints it for any
     *  área; an área for its own (productoService only returns those). */
    @FXML
    private void onResguardoGeneral() {
        javafx.scene.Scene scene = rootPane.getScene();
        DialogUtil.runAsyncWithProgress(scene, "Cargando bienes…",
            () -> java.util.Map.entry(productoService.getAll().stream().filter(p -> p.getFechaBaja() == null).toList(),
                com.sibim.service.TitularesArea.todos(new com.sibim.repository.ConfiguracionRepository())),
            datos -> mostrarDialogoGeneral(datos.getKey(), datos.getValue(), scene),
            e -> NotificacionUtil.error(scene, "No se pudo cargar el inventario"));
    }

    private void mostrarDialogoGeneral(List<Producto> productos, java.util.Map<String, String> titulares, javafx.scene.Scene scene) {
        java.util.Map<String, List<Producto>> porArea = productos.stream().filter(p -> p.getArea() != null)
            .collect(java.util.stream.Collectors.groupingBy(Producto::getArea));
        List<String> areas = Areas.getAllAreaNames().stream().filter(porArea::containsKey).toList();
        if (areas.isEmpty()) { NotificacionUtil.advertencia(scene, "No hay bienes activos en tus áreas"); return; }

        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Resguardo general del área");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(560);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        HBox header = DialogUtil.gradientHeader("mdi2f-file-document-multiple-outline",
            "Resguardo general del área", "Todos los bienes del área, con el resguardante de cada uno, para firma del titular",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        GridPane form = DialogUtil.formGrid(130);
        ComboBox<String> areaCombo = new ComboBox<>();
        areaCombo.setPromptText("Buscar área…");
        FiltroArea.conIconos(areaCombo);
        DialogUtil.makeFilterable(areaCombo, new ArrayList<>(areas));
        areaCombo.setMaxWidth(Double.MAX_VALUE);
        areaCombo.getStyleClass().add("form-input");
        TextField fTitular = new TextField(); fTitular.setPromptText("Nombre del titular del área");
        fTitular.getStyleClass().add("form-input");
        TextField fCargo = new TextField("Titular del área"); fCargo.getStyleClass().add("form-input");
        Label lblCuantos = new Label(); lblCuantos.getStyleClass().add("table-count-label");
        Label lblOrigenTitular = new Label(); lblOrigenTitular.getStyleClass().add("muted-sm");
        lblOrigenTitular.setWrapText(true);
        areaCombo.valueProperty().addListener((o, a, area) -> {
            List<Producto> bienes = area != null ? porArea.get(area) : null;
            if (bienes == null) { lblCuantos.setText(""); lblOrigenTitular.setText(""); return; }
            lblCuantos.setText(FormatUtils.plural(bienes.size(), "bien activo", "bienes activos") + " en el área");
            fTitular.setText(titularDe(area, titulares, bienes));
            // Say where it came from: a guess has to be checked before it gets signed.
            lblOrigenTitular.setText(titulares.containsKey(area) ? "Capturado en Configuración › Áreas"
                : "Sugerido: quien tiene más bienes del área. Revísalo, o captúralo en Configuración › Áreas");
        });
        if (areas.size() == 1) areaCombo.setValue(areas.get(0));
        int row = 0;
        form.add(DialogUtil.fieldLabel("Área *"), 0, row);    form.add(new VBox(2, areaCombo, lblCuantos), 1, row++);
        form.add(DialogUtil.fieldLabel("Titular *"), 0, row); form.add(new VBox(2, fTitular, lblOrigenTitular), 1, row++);
        form.add(DialogUtil.fieldLabel("Cargo"), 0, row);     form.add(fCargo, 1, row++);

        Label nota = new Label("Cada bien sigue en el resguardo individual de su resguardante; "
            + "este documento no cambia el inventario.");
        nota.setWrapText(true);
        nota.getStyleClass().add("table-count-label");
        // The form brings its own margin (22); the rest gets the same one here: in a
        // dialog the content's own padding is 0, and setPadding on it did nothing.
        VBox.setMargin(nota, new Insets(0, 22, 16, 22));
        VBox content = new VBox(12, header, form, nota);
        dialog.getDialogPane().setContent(content);
        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setText("Generar PDF");
        okBtn.getStyleClass().add("dialog-ok-btn");
        okBtn.disableProperty().bind(areaCombo.valueProperty().isNull().or(fTitular.textProperty().isEmpty()));

        dialog.showAndWait().filter(bt -> bt == ButtonType.OK).ifPresent(bt -> {
            String area = areaCombo.getValue();
            List<Producto> bienes = porArea.get(area).stream()
                .sorted(java.util.Comparator.comparingInt(ResguardosController::numeroDeCodigo)
                    .thenComparing(p -> p.getCodigo() != null ? p.getCodigo() : ""))
                .toList();
            DialogUtil.runAsyncWithProgress(scene, "Generando resguardo general…",
                () -> com.sibim.service.ReporteService.getInstance().exportarResguardoGeneralArea(
                    area, fTitular.getText().trim(), fCargo.getText().trim(), bienes),
                file -> DialogUtil.showExportResultDialog(scene, file),
                e -> NotificacionUtil.error(scene, "No se pudo generar el resguardo general"));
        });
    }

    /** Whoever holds the most bienes of the área is, in practice, its titular. */
    static String titularDe(String area, java.util.Map<String, String> titulares, List<Producto> bienes) {
        String capturado = titulares.get(area);
        return capturado != null ? capturado : titularProbable(bienes);
    }

    static String titularProbable(List<Producto> bienes) {
        return bienes.stream().map(Producto::getResguardante).filter(r -> r != null && !r.isBlank())
            .collect(java.util.stream.Collectors.groupingBy(r -> r, java.util.stream.Collectors.counting()))
            .entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey)
            .orElse("");
    }

    /** "SGM/07" → 7, so the list follows the área's numbering; anything else goes last. */
    static int numeroDeCodigo(Producto p) {
        String c = p.getCodigo();
        int barra = c != null ? c.lastIndexOf('/') : -1;
        try { return barra >= 0 ? Integer.parseInt(c.substring(barra + 1)) : Integer.MAX_VALUE; }
        catch (NumberFormatException e) { return Integer.MAX_VALUE; }
    }

    /** The bienes of a resguardo. The name takes what the fixed columns leave: with a
     *  constrained table it took everything and left Código and Valor without width.
     *  Amounts only for whoever may see them; the área only if one is another's. */
    static TableView<ResguardoItem> tablaDeBienes(ObservableList<ResguardoItem> items, String areaDelResguardo) {
        TableView<ResguardoItem> tabla = new TableView<>(items);
        tabla.setPrefHeight(180);
        tabla.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        tabla.getStyleClass().add("data-table");

        TableColumn<ResguardoItem, String> cCodigo = new TableColumn<>("Código");
        cCodigo.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getProductoCodigo() != null ? c.getValue().getProductoCodigo() : "—"));
        cCodigo.setPrefWidth(110);
        TableColumn<ResguardoItem, String> cNombre = new TableColumn<>("Bien");
        cNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProductoNombre()));
        cNombre.setCellFactory(col -> new TableCell<>() {
            private final Tooltip completo = new Tooltip();
            {
                completo.setWrapText(true);
                completo.setMaxWidth(420);
            }
            @Override protected void updateItem(String nombre, boolean empty) {
                super.updateItem(nombre, empty);
                setText(empty ? null : nombre);
                completo.setText(nombre);
                setTooltip(empty || nombre == null ? null : completo);
            }
        });
        tabla.getColumns().add(cCodigo);
        tabla.getColumns().add(cNombre);
        double fijas = cCodigo.getPrefWidth();

        boolean deOtraArea = items.stream().anyMatch(it -> it.getArea() != null && !it.getArea().equals(areaDelResguardo));
        if (deOtraArea) {
            TableColumn<ResguardoItem, String> cArea = new TableColumn<>("Área");
            cArea.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getArea() != null ? c.getValue().getArea() : "—"));
            cArea.setPrefWidth(170);
            tabla.getColumns().add(cArea);
            fijas += cArea.getPrefWidth();
        }
        if (com.sibim.session.Permisos.veValores()) {
            TableColumn<ResguardoItem, String> cValor = new TableColumn<>("Valor");
            cValor.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getValorUnitario() != null
                ? FormatUtils.formatCurrency(c.getValue().getValorUnitario()) : "—"));
            cValor.setPrefWidth(120);
            tabla.getColumns().add(cValor);
            fijas += cValor.getPrefWidth();
        }
        // 20: the scroll bar, so no horizontal one comes out.
        cNombre.prefWidthProperty().bind(tabla.widthProperty().subtract(fijas + 20));
        return tabla;
    }

    private void mostrarDetalle(Resguardo r) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Resguardo " + r.getNumero());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(760);
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
        TableView<ResguardoItem> itemsTable = tablaDeBienes(items, r.getResguardanteArea());

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

        // One bien out (to give it de baja or transfer it) without cancelling
        // the resguardo for everything else it covers.
        HBox pie = new HBox(8, hintVer);
        pie.setAlignment(Pos.CENTER_LEFT);
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        // From here, read again: after taking a bien out it is what gets signed anew.
        Button btnPdf = new Button("PDF del resguardo");
        btnPdf.setGraphic(new FontIcon("mdi2f-file-pdf-box"));
        btnPdf.getStyleClass().add("btn-secondary");
        btnPdf.setMinWidth(Region.USE_PREF_SIZE);
        btnPdf.setOnAction(e -> DialogUtil.runAsync(
            () -> service.exportarPdf(service.findById(r.getId())),
            file -> DialogUtil.showExportResultDialog(rootPane.getScene(), file),
            ex -> NotificacionUtil.error(rootPane.getScene(), "No se pudo generar el PDF")));
        pie.getChildren().addAll(espacio, btnPdf);
        boolean[] cambio = {false};
        if (r.isActivo() && com.sibim.session.Permisos.gestionaDocumentos()) {
            Button btnQuitar = new Button("Quitar del resguardo");
            btnQuitar.setGraphic(new FontIcon("mdi2a-account-minus-outline"));
            btnQuitar.getStyleClass().add("btn-secondary");
            btnQuitar.disableProperty().bind(itemsTable.getSelectionModel().selectedItemProperty().isNull());
            btnQuitar.setOnAction(e -> {
                ResguardoItem sel = itemsTable.getSelectionModel().getSelectedItem();
                if (sel == null) return;
                String bien = (sel.getProductoCodigo() != null ? sel.getProductoCodigo() + " " : "")
                    + (sel.getProductoNombre() != null ? sel.getProductoNombre() : "");
                ConfirmacionUtil.confirmarConMotivo("mdi2a-account-minus-outline", "Quitar del resguardo",
                        "«" + bien.trim() + "» sale del resguardo " + r.getNumero() + " y queda sin resguardante. "
                            + "El resguardo anota qué se quitó y por qué; reimprímelo para que se firme de nuevo.",
                        "Quitar", true, "Motivo (ej. se dará de baja, pasa a otra área)")
                    .ifPresent(motivo -> DialogUtil.runAsync(
                        () -> service.quitarBien(r, sel, motivo),
                        cancelado -> {
                            cambio[0] = true;
                            items.remove(sel);
                            if (cancelado) {
                                NotificacionUtil.exito(rootPane.getScene(), "Era el último bien: el resguardo "
                                    + r.getNumero() + " quedó cancelado");
                                dialog.close();
                            } else {
                                NotificacionUtil.exito(rootPane.getScene(), "Bien quitado del resguardo " + r.getNumero());
                            }
                        },
                        ex -> NotificacionUtil.error(rootPane.getScene(), "No se pudo quitar el bien: "
                            + (ex.getMessage() != null ? ex.getMessage() : "error desconocido"))));
            });
            btnQuitar.setMinWidth(Region.USE_PREF_SIZE);
            pie.getChildren().add(btnQuitar);
        }

        // The form brings its own margin (22); the rest gets the same one here: in a
        // dialog the content's own padding is 0, and setPadding on it did nothing.
        VBox.setMargin(lblBienes, new Insets(0, 22, 0, 22));
        VBox.setMargin(itemsTable, new Insets(0, 22, 0, 22));
        VBox.setMargin(pie, new Insets(0, 22, 16, 22));
        VBox content = new VBox(12, header, meta, lblBienes, itemsTable, pie);
        dialog.getDialogPane().setContent(content);
        AnimationUtils.staggeredFadeInUp(List.of(header, meta, itemsTable), 260, 65);
        dialog.showAndWait();
        if (cambio[0]) loadData();
    }

    @FXML
    private void onCancelar() {
        if (!com.sibim.session.Permisos.gestionaDocumentos()) return;
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
