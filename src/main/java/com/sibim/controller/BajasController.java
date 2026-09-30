package com.sibim.controller;

import com.sibim.model.Producto;
import com.sibim.service.MovimientoService;
import com.sibim.service.ProductoService;
import com.sibim.service.ReporteService;
import com.sibim.session.SessionManager;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import com.sibim.util.ConfirmacionUtil;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * "Bajas patrimoniales": every bien given de baja in the user's áreas, with the
 * solicitud folio, dictamen, destino and acta; the acta can be regenerated and
 * (admin only) a bien reactivated. A baja itself is registered from Bienes, by
 * the administrator, against the área's signed solicitud de baja.
 */
public class BajasController {

    private static final Logger log = LoggerFactory.getLogger(BajasController.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** How ProductoBajasDialog appends the solicitud's folio to the motivo. */
    static final String SEPARADOR_SOLICITUD = " · Solicitud de baja: ";

    @FXML private VBox rootPane;
    @FXML private ProgressIndicator spinner;
    @FXML private Label lblProceso, lblTotal, lblAnio, lblValor, lblSinSolicitud, lblResultados;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> areaFilter;
    @FXML private ComboBox<Integer> anioFilter;
    @FXML private TableView<Producto> table;
    @FXML private TableColumn<Producto, LocalDate> colFecha;
    @FXML private TableColumn<Producto, String> colCodigo, colNombre, colArea, colMotivo, colSolicitud,
        colDestino, colDictamen, colActa, colValor;
    @FXML private Button btnActa, btnFicha, btnReactivar;

    private final ProductoService productoService = new ProductoService();
    private final MovimientoService movimientoService = new MovimientoService();
    private final ReporteService reporteService = ReporteService.getInstance();

    private final ObservableList<Producto> bajas = FXCollections.observableArrayList();
    private final FilteredList<Producto> filtradas = new FilteredList<>(bajas, p -> true);

    /** The motivo without the appended folio. */
    static String motivo(Producto p) {
        String m = p.getMotivoBaja();
        if (m == null) return "";
        int i = m.indexOf(SEPARADOR_SOLICITUD);
        return i >= 0 ? m.substring(0, i) : m;
    }

    /** Folio of the signed solicitud de baja, or null for bajas registered before it was required. */
    static String folioSolicitud(Producto p) {
        String m = p.getMotivoBaja();
        if (m == null) return null;
        int i = m.indexOf(SEPARADOR_SOLICITUD);
        return i >= 0 ? m.substring(i + SEPARADOR_SOLICITUD.length()).trim() : null;
    }

    static String destino(String codigo) {
        if (codigo == null) return "";
        return switch (codigo) {
            case "DESTRUCCION" -> "Destrucción";
            case "DONACION" -> "Donación";
            case "SUBASTA" -> "Subasta";
            case "TRANSFERENCIA_ENTE" -> "Transferencia a otro ente";
            case "OTRO" -> "Otro";
            default -> codigo;
        };
    }

    @FXML
    public void initialize() {
        boolean admin = SessionManager.isAdmin();
        lblProceso.setText(admin
            ? "Para dar de baja: el área entrega su solicitud de baja firmada; en Bienes selecciona el bien, "
              + "pulsa \"Dar de baja\" y anota el folio de esa solicitud. Aquí quedan todas las bajas con su acta."
            : "Las bajas las registra Patrimonio (administrador). Para pedir una: en Bienes selecciona el bien, "
              + "pulsa \"Solicitar baja\", imprime el formato, fírmalo y entrégalo.");
        btnReactivar.setVisible(admin);
        btnReactivar.setManaged(admin);

        colFecha.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getFechaBaja()));
        colFecha.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(LocalDate d, boolean empty) {
                super.updateItem(d, empty);
                setText(empty || d == null ? null : d.format(FMT));
            }
        });
        colCodigo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCodigo()));
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colArea.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getArea()));
        colMotivo.setCellValueFactory(c -> new SimpleStringProperty(motivo(c.getValue())));
        colSolicitud.setCellValueFactory(c -> new SimpleStringProperty(
            Objects.requireNonNullElse(folioSolicitud(c.getValue()), "—")));
        colDestino.setCellValueFactory(c -> new SimpleStringProperty(destino(c.getValue().getTipoDestinoBaja())));
        colDictamen.setCellValueFactory(c -> new SimpleStringProperty(
            Objects.requireNonNullElse(c.getValue().getDictamenBaja(), "")));
        colActa.setCellValueFactory(c -> new SimpleStringProperty(
            Objects.requireNonNullElse(c.getValue().getNumeroActaBaja(), "")));
        colValor.setCellValueFactory(c -> new SimpleStringProperty(FormatUtils.formatCurrency(c.getValue().getValorTotal())));

        var sorted = new javafx.collections.transformation.SortedList<>(filtradas);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        colFecha.setSortType(TableColumn.SortType.DESCENDING);
        table.getSortOrder().setAll(List.of(colFecha));

        var sinSeleccion = table.getSelectionModel().selectedItemProperty().isNull();
        btnActa.disableProperty().bind(sinSeleccion);
        btnFicha.disableProperty().bind(sinSeleccion);
        btnReactivar.disableProperty().bind(sinSeleccion);
        table.setRowFactory(tv -> {
            TableRow<Producto> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) onFicha();
            });
            return row;
        });

        searchField.textProperty().addListener((o, a, b) -> aplicarFiltros());
        areaFilter.valueProperty().addListener((o, a, b) -> aplicarFiltros());
        anioFilter.valueProperty().addListener((o, a, b) -> aplicarFiltros());
        rootPane.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.F5) onRefresh(); });

        cargar();
    }

    private void cargar() {
        DialogUtil.loadAsync(spinner, rootPane.getScene(),
            () -> productoService.getAllIncludingBaja().stream().filter(Producto::isDadoDeBaja).toList(),
            lista -> {
                bajas.setAll(lista);
                String area = areaFilter.getValue();
                Integer anio = anioFilter.getValue();
                areaFilter.getItems().setAll(lista.stream().map(Producto::getArea).filter(Objects::nonNull)
                    .distinct().sorted().toList());
                anioFilter.getItems().setAll(lista.stream().map(Producto::getFechaBaja).filter(Objects::nonNull)
                    .map(LocalDate::getYear).distinct().sorted(java.util.Comparator.reverseOrder()).toList());
                if (area != null && areaFilter.getItems().contains(area)) areaFilter.setValue(area);
                if (anio != null && anioFilter.getItems().contains(anio)) anioFilter.setValue(anio);
                actualizarTarjetas();
                aplicarFiltros();
            },
            "No se pudieron cargar las bajas", log);
    }

    private void actualizarTarjetas() {
        int anioActual = LocalDate.now().getYear();
        lblTotal.setText(String.valueOf(bajas.size()));
        lblAnio.setText(String.valueOf(bajas.stream()
            .filter(p -> p.getFechaBaja() != null && p.getFechaBaja().getYear() == anioActual).count()));
        lblValor.setText(FormatUtils.formatCurrency(bajas.stream().map(Producto::getValorTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add)));
        lblSinSolicitud.setText(String.valueOf(bajas.stream().filter(p -> folioSolicitud(p) == null).count()));
    }

    private void aplicarFiltros() {
        String q = searchField.getText() == null ? "" : searchField.getText().strip().toLowerCase();
        String area = areaFilter.getValue();
        Integer anio = anioFilter.getValue();
        filtradas.setPredicate(p -> {
            if (area != null && !area.equals(p.getArea())) return false;
            if (anio != null && (p.getFechaBaja() == null || p.getFechaBaja().getYear() != anio)) return false;
            if (q.isEmpty()) return true;
            return contiene(p.getNombre(), q) || contiene(p.getCodigo(), q) || contiene(p.getMotivoBaja(), q)
                || contiene(p.getDictamenBaja(), q) || contiene(p.getNumeroActaBaja(), q) || contiene(p.getArea(), q);
        });
        lblResultados.setText(FormatUtils.plural(filtradas.size(), "resultado", "resultados"));
    }

    private static boolean contiene(String s, String q) {
        return s != null && s.toLowerCase().contains(q);
    }

    @FXML private void onRefresh() { cargar(); }

    @FXML private void onLimpiar() {
        searchField.clear();
        areaFilter.setValue(null);
        anioFilter.setValue(null);
    }

    @FXML private void onActa() {
        Producto p = table.getSelectionModel().getSelectedItem();
        if (p != null) generar("Generando acta de baja…", () -> reporteService.exportActaBaja(p));
    }

    @FXML private void onFicha() {
        Producto p = table.getSelectionModel().getSelectedItem();
        if (p != null) generar("Generando ficha técnica…",
            () -> reporteService.exportFichaTecnica(p, movimientoService.getByProducto(p.getId())));
    }

    @FXML private void onReactivar() {
        Producto p = table.getSelectionModel().getSelectedItem();
        if (p == null || !SessionManager.isAdmin()) return;
        if (!ConfirmacionUtil.confirmar("Reactivar bien",
                "¿Regresar \"" + p.getNombre() + "\" al inventario activo? Recibirá un código nuevo de su área."))
            return;
        DialogUtil.runAsync(() -> productoService.reactivar(p.getId()),
            () -> { NotificacionUtil.exito(table.getScene(), "\"" + p.getNombre() + "\" reactivado"); cargar(); },
            e -> NotificacionUtil.error(table.getScene(),
                e instanceof ProductoService.ValidationException ? e.getMessage() : "No se pudo reactivar el bien"));
    }

    @FXML private void onExportPdf()   { exportar(() -> reporteService.exportBajasPdf(List.copyOf(filtradas))); }
    @FXML private void onExportExcel() { exportar(() -> reporteService.exportBajasExcel(List.copyOf(filtradas))); }
    @FXML private void onExportCsv()   { exportar(() -> reporteService.exportBajasCsv(List.copyOf(filtradas))); }

    private void exportar(Callable<File> tarea) {
        if (filtradas.isEmpty()) {
            NotificacionUtil.advertencia(table.getScene(), "No hay bajas que exportar con los filtros actuales");
            return;
        }
        generar("Generando reporte de bajas…", tarea);
    }

    private void generar(String mensaje, Callable<File> tarea) {
        DialogUtil.runAsyncWithProgress(table.getScene(), mensaje, tarea,
            file -> DialogUtil.showExportResultDialog(table.getScene(), file),
            ex -> { log.error(mensaje, ex); NotificacionUtil.error(table.getScene(), "No se pudo generar el documento"); });
    }
}
