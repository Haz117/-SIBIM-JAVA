package com.sibim.controller;

import com.sibim.model.Comodato;
import com.sibim.model.Producto;
import org.kordamp.ikonli.javafx.FontIcon;
import com.sibim.service.ComodatoService;
import com.sibim.service.EmailService;
import com.sibim.service.MovimientoService;
import com.sibim.service.ProductoService;
import com.sibim.service.ReporteService;
import com.sibim.controller.dialogs.ProductoDetailDialog;
import com.sibim.util.AccessibilityUtils;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppExecutor;
import com.sibim.util.DialogUtil;
import com.sibim.util.EmptyStateUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import com.sibim.util.SearchUtils;
import com.sibim.util.SkeletonUtil;
import com.sibim.MainApp;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.util.Duration;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

public class AlertasController implements Refreshable {

    private static final Logger log = LoggerFactory.getLogger(AlertasController.class);
    private static final PseudoClass ELEGIDA = PseudoClass.getPseudoClass("selected");

    @FXML private VBox  rootPane;
    @FXML private ProgressIndicator spinner;
    @FXML private Label lblActualizado;

    @FXML private VBox    statCardGarantiasSum;
    @FXML private Label   lblSumGarantias;
    @FXML private Label   lblSumGarantiasDetalle;
    @FXML private VBox    statCardMantenimientoSum;
    @FXML private Label   lblSumMantenimiento;
    @FXML private VBox    statCardComodatosSum;
    @FXML private Label   lblSumComodatos;
    @FXML private VBox    statCardPendientesSum;
    @FXML private Label   lblSumPendientes;
    @FXML private Label   lblSumPendientesDetalle;

    @FXML private Label lblPanelTitulo;
    @FXML private Label lblPanelConteo;
    @FXML private Label lblPanelSubtitulo;
    @FXML private TextField searchField;
    @FXML private Button btnClearSearch;
    @FXML private Button btnResetColumns;
    @FXML private HBox panelExtras;
    @FXML private StackPane panelTablas;

    @FXML private TableView<Producto> tableGarantias;
    @FXML private TableColumn<Producto, String> colGaNombre;
    @FXML private TableColumn<Producto, String> colGaCodigo;
    @FXML private TableColumn<Producto, String> colGaArea;
    @FXML private TableColumn<Producto, String> colGaFecha;
    @FXML private TableColumn<Producto, String> colGaDias;

    @FXML private TableView<Producto>           tableMantenimiento;
    @FXML private TableColumn<Producto, String> colMantNombre;
    @FXML private TableColumn<Producto, String> colMantCodigo;
    @FXML private TableColumn<Producto, String> colMantArea;
    @FXML private TableColumn<Producto, String> colMantFecha;
    @FXML private TableColumn<Producto, String> colMantNotas;

    private static final Preferences STICKY =
        Preferences.userRoot().node("sibim/filters/alertas");

    private final ProductoService  productoService  = new ProductoService();
    private final MovimientoService movimientoService = new MovimientoService();
    private final ReporteService   reporteService   = ReporteService.getInstance();
    private final ComodatoService  comodatoService  = new ComodatoService();

    private AlertasDataLoader dataLoader;

    private List<Producto> allGarantias         = List.of();
    private List<Producto> allMantenimiento     = List.of();
    private List<Comodato> allComodatosVencidos = List.of();
    private List<AlertasPatrimonialesSection.Pendiente> allPendientes = List.of();

    private AlertasComodatosSection comodatos;
    private AlertasPatrimonialesSection patrimoniales;

    /** One category of the screen: its summary card works as the tab that
     *  brings its table to the panel. */
    private static final class Pestana {
        final VBox tarjeta;
        final String titulo;
        final String singular, plural;
        final Node contenido;
        final Node extras;
        String subtitulo;
        int mostrados, total;

        Pestana(VBox tarjeta, String titulo, String subtitulo, String singular, String plural,
                Node contenido, Node extras) {
            this.tarjeta = tarjeta;
            this.titulo = titulo;
            this.subtitulo = subtitulo;
            this.singular = singular;
            this.plural = plural;
            this.contenido = contenido;
            this.extras = extras;
        }
    }

    private Pestana pGarantias, pMantenimiento, pComodatos, pPendientes;
    private List<Pestana> pestanas = List.of();
    private Pestana elegida;
    /** Until the user picks a tab, the screen opens on the first one with something in it. */
    private boolean elegidaPorUsuario;

    private Timeline autoRefresh;
    private EventHandler<KeyEvent> keyFilter;
    private boolean  dataLoaded = false;

    @FXML
    public void initialize() {
        dataLoader = new AlertasDataLoader(productoService, comodatoService);

        setupColumns();
        setupPestanas();
        setupColumnResetButton();
        setupSearchField();
        setupTableInteractions();

        loadData();

        AnimationUtils.staggeredFadeInUp(List.of(statCardGarantiasSum, statCardMantenimientoSum,
            statCardComodatosSum, statCardPendientesSum), 250, 60);
        Platform.runLater(searchField::requestFocus);

        keyFilter = ev -> {
            if (ev.getCode() == KeyCode.F && ev.isControlDown()) {
                searchField.requestFocus();
                searchField.selectAll();
                ev.consume();
            }
        };
        rootPane.addEventFilter(KeyEvent.KEY_PRESSED, keyFilter);
        autoRefresh = new Timeline(new KeyFrame(Duration.minutes(5), e -> loadData()));
        autoRefresh.setCycleCount(Timeline.INDEFINITE);
        autoRefresh.play();
    }

    // ── Setup helpers ──────────────────────────────────────────────────────────

    private void setupColumns() {
        AlertasColumnSetup.configureMantenimiento(colMantNombre, colMantCodigo, colMantArea, colMantFecha, colMantNotas);
        AlertasColumnSetup.configureGarantias(colGaNombre, colGaCodigo, colGaFecha, colGaDias);
        colGaArea.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getArea() != null ? c.getValue().getArea() : ""));
        tableGarantias.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tableMantenimiento.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    /** The four categories, in the order of their cards. Garantías and
     *  Mantenimiento have their tables in the FXML; Comodatos and Pendientes
     *  patrimoniales build theirs. */
    private void setupPestanas() {
        comodatos = new AlertasComodatosSection(() -> alertaOkNode("Sin comodatos vencidos"));
        patrimoniales = new AlertasPatrimonialesSection(
            () -> alertaOkNode("Todos los bienes tienen resguardante y etiqueta"),
            this::verDetalle,
            area -> {
                // Same jump the Organigrama uses: Bienes filtered by that área.
                com.sibim.session.NavigationContext.setPendingAreaFilter(area);
                MainController main = MainController.getInstance();
                if (main != null) main.navigateTo("productos");
            });
        panelTablas.getChildren().addAll(comodatos.tabla(), patrimoniales.contenido());

        pGarantias = new Pestana(statCardGarantiasSum, "Garantías por vencer",
            "Ya vencidas o que vencen en los próximos 30 días · doble clic en una fila para ver el detalle",
            "bien", "bienes", tableGarantias, null);
        pMantenimiento = new Pestana(statCardMantenimientoSum, "Mantenimiento próximo",
            "Revisiones de mantenimiento programadas en los próximos 30 días",
            "bien", "bienes", tableMantenimiento, null);
        pComodatos = new Pestana(statCardComodatosSum, "Comodatos vencidos",
            "Comodatos cuya fecha límite ha sido superada sin devolución",
            "comodato", "comodatos", comodatos.tabla(), null);
        pPendientes = new Pestana(statCardPendientesSum, "Pendientes patrimoniales",
            "Bienes sin resguardante asignado o sin etiqueta física",
            "bien", "bienes", patrimoniales.contenido(), patrimoniales.barraVistas());
        pestanas = List.of(pGarantias, pMantenimiento, pComodatos, pPendientes);

        for (Pestana p : pestanas) {
            p.tarjeta.setOnMouseClicked(e -> { elegidaPorUsuario = true; elegir(p); });
            AccessibilityUtils.asButton(p.tarjeta, "Ver " + p.titulo.toLowerCase());
        }
        elegir(pGarantias);
    }

    private void elegir(Pestana p) {
        elegida = p;
        for (Pestana otra : pestanas) {
            boolean activa = otra == p;
            otra.tarjeta.pseudoClassStateChanged(ELEGIDA, activa);
            otra.contenido.setVisible(activa);
            otra.contenido.setManaged(activa);
        }
        panelExtras.getChildren().clear();
        if (p.extras != null) panelExtras.getChildren().add(p.extras);
        panelExtras.setVisible(p.extras != null);
        panelExtras.setManaged(p.extras != null);
        refrescarPanel();
    }

    private void refrescarPanel() {
        lblPanelTitulo.setText(elegida.titulo);
        lblPanelSubtitulo.setText(elegida.subtitulo);
        // "3 bienes", or "1 / 3" while the search narrows the list down.
        lblPanelConteo.setText(elegida.mostrados == elegida.total
            ? FormatUtils.plural(elegida.total, elegida.singular, elegida.plural)
            : elegida.mostrados + " / " + elegida.total);
    }

    private void setupColumnResetButton() {
        List<Runnable> restaurar = new ArrayList<>();
        restaurar.add(DialogUtil.captureColumnReset(tableGarantias, null));
        restaurar.add(DialogUtil.captureColumnReset(tableMantenimiento, null));
        restaurar.add(DialogUtil.captureColumnReset(comodatos.tabla(), null));
        for (TableView<?> t : patrimoniales.tablas()) restaurar.add(DialogUtil.captureColumnReset(t, null));
        btnResetColumns.setOnAction(e -> restaurar.forEach(Runnable::run));
    }

    private void setupSearchField() {
        searchField.textProperty().addListener((obs, o, n) -> btnClearSearch.setVisible(!n.isBlank()));
        SearchUtils.setupSearchHistory("sibim/search-history/alertas", searchField,
            () -> applySearch(searchField.getText()));
        SearchUtils.debounce(searchField, 260, q -> { STICKY.put("search", q != null ? q : ""); applySearch(q); });
        String savedSearch = STICKY.get("search", "");
        if (!savedSearch.isBlank()) searchField.setText(savedSearch);
    }

    private void setupTableInteractions() {
        alAbrir(tableGarantias, AlertasDialogs::showGarantiaInfo);
        alAbrir(tableMantenimiento, this::verDetalle);
    }

    /** Double click, Enter or the context menu open the row; Escape clears the selection. */
    private void alAbrir(TableView<Producto> tabla, Consumer<Producto> abrir) {
        tabla.setOnMouseClicked(e -> {
            Producto sel = tabla.getSelectionModel().getSelectedItem();
            if (e.getClickCount() == 2 && sel != null) abrir.accept(sel);
        });
        tabla.setOnKeyPressed(ev -> {
            Producto sel = tabla.getSelectionModel().getSelectedItem();
            if (ev.getCode() == KeyCode.ENTER && sel != null) {
                abrir.accept(sel); ev.consume();
            } else if (ev.getCode() == KeyCode.ESCAPE) {
                tabla.getSelectionModel().clearSelection(); ev.consume();
            }
        });
        tabla.setContextMenu(AlertasContextMenus.buildGarantias(tabla, abrir, this::imprimirFicha));
    }

    private void verDetalle(Producto p) {
        ProductoDetailDialog.show(p, rootPane.getScene(), movimientoService, log);
    }

    // ── Data loading ───────────────────────────────────────────────────────────

    private void loadData() { loadData(false); }

    private void loadData(boolean showToast) {
        if (!dataLoaded) {
            tableGarantias.setPlaceholder(SkeletonUtil.skeletonRows(3));
            tableMantenimiento.setPlaceholder(SkeletonUtil.skeletonRows(4));
        } else {
            verSpinner(true);
        }
        dataLoader.load(
            data -> {
                dataLoaded = true;
                verSpinner(false);
                allGarantias         = data.garantias();
                allMantenimiento     = data.mantenimiento();
                allComodatosVencidos = data.comodatosVencidos();
                allPendientes        = data.pendientesPatrimoniales();

                final List<Producto> ga = data.garantias();
                AppExecutor.submit(() -> new EmailService().enviarAlertas(ga));

                updateSumCards();
                applySearch(searchField.getText());
                if (!elegidaPorUsuario)
                    elegir(pestanas.stream().filter(p -> p.total > 0).findFirst().orElse(pGarantias));
                lblActualizado.setText("Actualizado " + FormatUtils.formatTime(LocalTime.now()));
                if (showToast && rootPane.getScene() != null)
                    NotificacionUtil.info(rootPane.getScene(), "Alertas actualizadas");
            },
            e -> {
                verSpinner(false);
                log.error("Error al cargar alertas", e);
                Scene scene = rootPane.getScene();
                if (scene == null && MainApp.getPrimaryStage() != null)
                    scene = MainApp.getPrimaryStage().getScene();
                if (scene != null)
                    NotificacionUtil.errorConAccion(scene,
                        "No se pudo cargar las alertas de inventario", "Reintentar", () -> loadData(false));
            }
        );
    }

    private void verSpinner(boolean visible) {
        spinner.setVisible(visible);
        spinner.setManaged(visible);
    }

    /** Narrows every category down to the bienes matching the search. */
    private void applySearch(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();

        mostrar(pGarantias, tableGarantias, allGarantias, q, "Sin garantías vencidas ni por vencer");
        mostrar(pMantenimiento, tableMantenimiento, allMantenimiento, q, "Sin revisiones en los próximos 30 días");
        AnimationUtils.staggerTableRows(tableGarantias);

        pComodatos.total = allComodatosVencidos.size();
        pComodatos.mostrados = comodatos.mostrar(allComodatosVencidos, q);

        pPendientes.total = allPendientes.size();
        pPendientes.mostrados = patrimoniales.mostrar(allPendientes, q);
        if (!allPendientes.isEmpty()) pPendientes.subtitulo =
            AlertasPatrimonialesSection.contar(allPendientes, AlertasPatrimonialesSection.SIN_RESGUARDANTE)
            + " sin resguardante asignado · "
            + AlertasPatrimonialesSection.contar(allPendientes, AlertasPatrimonialesSection.SIN_ETIQUETAR)
            + " sin etiqueta física";

        refrescarPanel();
    }

    private void mostrar(Pestana pestana, TableView<Producto> tabla, List<Producto> todos,
                         String q, String mensajeAlCorriente) {
        List<Producto> visibles = filter(todos, q);
        tabla.setPlaceholder(q.isBlank() || todos.isEmpty()
            ? alertaOkNode(mensajeAlCorriente) : EmptyStateUtil.buildSearch(q));
        tabla.getItems().setAll(visibles);
        pestana.total = todos.size();
        pestana.mostrados = visibles.size();
    }

    private List<Producto> filter(List<Producto> source, String q) {
        if (q.isBlank()) return source;
        return source.stream()
            .filter(p -> p.getNombre().toLowerCase().contains(q)
                      || p.getCodigo().toLowerCase().contains(q)
                      || (p.getArea() != null && p.getArea().toLowerCase().contains(q)))
            .toList();
    }

    private void updateSumCards() {
        int nGa = allGarantias.size();
        AnimationUtils.animateCount(lblSumGarantias, nGa, 600);
        long vencidas = allGarantias.stream()
            .filter(p -> p.getFechaVencimiento() != null
                && p.getFechaVencimiento().isBefore(LocalDate.now()))
            .count();
        lblSumGarantiasDetalle.setText(
            vencidas + (vencidas == 1 ? " vencida" : " vencidas")
            + " · " + (nGa - vencidas) + " próximas");
        AnimationUtils.animateCount(lblSumMantenimiento, allMantenimiento.size(), 600);
        AnimationUtils.animateCount(lblSumComodatos, allComodatosVencidos.size(), 600);
        AnimationUtils.animateCount(lblSumPendientes, allPendientes.size(), 600);
        lblSumPendientesDetalle.setText(
            AlertasPatrimonialesSection.contar(allPendientes, AlertasPatrimonialesSection.SIN_RESGUARDANTE)
            + " sin resguardante · "
            + AlertasPatrimonialesSection.contar(allPendientes, AlertasPatrimonialesSection.SIN_ETIQUETAR)
            + " sin etiquetar");
    }

    // ── FXML handlers ──────────────────────────────────────────────────────────

    @FXML private void onClearSearch() {
        searchField.clear();
        searchField.requestFocus();
        STICKY.remove("search");
    }

    @FXML private void onRefresh() { loadData(true); }

    private boolean sinAlertas() {
        // The exports list warranties and pendientes patrimoniales.
        if (allGarantias.isEmpty() && allPendientes.isEmpty()) {
            NotificacionUtil.advertencia(rootPane.getScene(), "No hay alertas para exportar");
            return true;
        }
        return false;
    }

    private void exportar(String label, java.util.concurrent.Callable<File> task) {
        if (sinAlertas()) return;
        Scene scene = rootPane.getScene();
        DialogUtil.runAsyncWithProgress(scene, label, task,
            file -> DialogUtil.showExportResultDialog(scene, file),
            ex -> NotificacionUtil.error(scene, "No se pudo exportar"));
    }

    @FXML private void onExportarPdf()   { exportar("Generando PDF…",   reporteService::exportAlertasPdf); }
    @FXML private void onExportarExcel() { exportar("Generando Excel…", reporteService::exportAlertasExcel); }
    @FXML private void onExportarCsv()   { exportar("Generando CSV…",   reporteService::exportAlertasCsv); }

    private void imprimirFicha(Producto p) {
        Scene scene = rootPane.getScene();
        DialogUtil.runAsyncWithProgress(scene, "Generando ficha técnica…",
            () -> {
                var movs = movimientoService.getByProducto(p.getId());
                return reporteService.exportFichaTecnica(p, movs);
            },
            file -> DialogUtil.showExportResultDialog(scene, file),
            ex -> NotificacionUtil.error(scene, "No se pudo generar la ficha técnica")
        );
    }

    /** Stops the auto-refresh timer and cleans up listeners before the view is discarded. */
    public void stopAutoRefresh() {
        if (autoRefresh != null) autoRefresh.stop();
        if (keyFilter != null) {
            rootPane.removeEventFilter(KeyEvent.KEY_PRESSED, keyFilter);
            keyFilter = null;
        }
    }

    private static Node alertaOkNode(String msg) {
        FontIcon icon = new FontIcon("mdi2c-check-circle-outline");
        icon.setIconSize(40);
        icon.getStyleClass().add("alert-ok-icon");
        Label lbl = new Label(msg);
        lbl.getStyleClass().add("alert-ok-label");
        VBox box = new VBox(10, icon, lbl);
        box.setAlignment(javafx.geometry.Pos.CENTER);
        box.setPadding(new Insets(24));
        return box;
    }
}
