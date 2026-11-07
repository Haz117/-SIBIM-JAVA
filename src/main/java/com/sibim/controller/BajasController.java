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
 *
 * <p>"Definitivas" (5-nov-2026): once the destino final is carried out with its
 * document, the administrator registers the baja definitiva here; it can no
 * longer be reactivated. "En proceso" are the ones still waiting for it.
 *
 * <p>Solicitudes de baja (5-nov-2026): an área asks for a baja from Bienes;
 * the administrator sees here how many are waiting and reviews them (also from
 * the bell and Inicio › Por hacer); an área sees its own with the answer.
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
        colDestino, colDictamen, colActa, colValor, colDefinitiva;
    @FXML private Button btnActa, btnFicha, btnReactivar, btnDefinitiva;
    @FXML private ToggleButton chipTodas, chipProceso, chipDefinitivas;
    @FXML private javafx.scene.layout.HBox avisoSolicitudes;
    @FXML private Label lblSolicitudes;
    @FXML private Button btnSolicitudes;
    private final ToggleGroup chips = new ToggleGroup();

    private final ProductoService productoService = new ProductoService();
    private final MovimientoService movimientoService = new MovimientoService();
    private final ReporteService reporteService = ReporteService.getInstance();
    private final com.sibim.service.SolicitudService solicitudes = new com.sibim.service.SolicitudService();

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
            ? "Para dar de baja: en Bienes selecciona el bien, pulsa \"Dar de baja\" y anota el folio de la solicitud "
              + "firmada del área. Lo que las áreas piden por SIBIM te llega aquí y en la campana."
            : "Las bajas las registra Patrimonio (administrador). Para pedir una: en Bienes selecciona el bien y "
              + "pulsa \"Solicitar baja\": a Patrimonio le llega el aviso. Imprime el formato, fírmalo y entrégalo.");
        btnReactivar.setVisible(admin);
        btnReactivar.setManaged(admin);
        btnDefinitiva.setVisible(admin);
        btnDefinitiva.setManaged(admin);
        for (ToggleButton chip : List.of(chipTodas, chipProceso, chipDefinitivas)) {
            chip.setToggleGroup(chips);
            chip.setOnAction(e -> aplicarFiltros());
        }
        // A chip pressed again stays on: one of the three is always the view.
        chips.selectedToggleProperty().addListener((o, antes, ahora) -> { if (ahora == null && antes != null) antes.setSelected(true); });

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
        colDefinitiva.setCellValueFactory(c -> new SimpleStringProperty(definitiva(c.getValue())));

        // Eleven columns on a laptop screen: the long texts take two lines (the row has room
        // for them) and are cut after that, so the whole text is one hover away.
        List<TableColumn<Producto, String>> dosLineas = List.of(colNombre, colArea, colMotivo);
        for (TableColumn<Producto, String> col : List.of(colNombre, colArea, colMotivo, colDictamen, colDefinitiva))
            col.setCellFactory(c -> new TableCell<>() {
                { if (dosLineas.contains(col)) setWrapText(true); }
                /** Two lines at most: a long motivo doesn't make its row taller than the rest. */
                @Override protected double computePrefHeight(double ancho) {
                    double alto = super.computePrefHeight(ancho);
                    if (!isWrapText()) return alto;
                    double dos = Math.ceil(getFont().getSize() * 1.4 * 2) + snappedTopInset() + snappedBottomInset();
                    return Math.min(alto, dos);
                }
                @Override protected void updateItem(String texto, boolean empty) {
                    super.updateItem(texto, empty);
                    boolean hay = !empty && texto != null && !texto.isBlank();
                    setText(hay ? texto : null);
                    setTooltip(hay ? new Tooltip(texto) : null);
                }
            });

        var sorted = new javafx.collections.transformation.SortedList<>(filtradas);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        colFecha.setSortType(TableColumn.SortType.DESCENDING);
        table.getSortOrder().setAll(List.of(colFecha));

        // Several at once for the baja definitiva: one acta de destrucción covers a lot.
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        var sinSeleccion = table.getSelectionModel().selectedItemProperty().isNull();
        btnActa.disableProperty().bind(sinSeleccion);
        btnFicha.disableProperty().bind(sinSeleccion);
        table.getSelectionModel().getSelectedItems().addListener(
            (javafx.collections.ListChangeListener<Producto>) c -> actualizarBotones());
        actualizarBotones();
        table.setRowFactory(tv -> {
            TableRow<Producto> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) onFicha();
            });
            return row;
        });

        searchField.textProperty().addListener((o, a, b) -> aplicarFiltros());
        FiltroArea.conIconos(areaFilter);
        com.sibim.util.FiltroEscribible.instalar(areaFilter, "Todas las áreas");
        areaFilter.valueProperty().addListener((o, a, b) -> aplicarFiltros());
        anioFilter.valueProperty().addListener((o, a, b) -> aplicarFiltros());
        rootPane.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.F5) onRefresh(); });

        cargar();
        cargarSolicitudes();
        // The bell and Inicio › Por hacer open the inbox on arriving.
        if ("solicitudes".equals(com.sibim.session.NavigationContext.consumePendingAbrir())) {
            // Once the screen is shown: the inbox needs its window.
            if (rootPane.getScene() != null) javafx.application.Platform.runLater(this::onSolicitudes);
            else rootPane.sceneProperty().addListener(new javafx.beans.value.ChangeListener<>() {
                @Override public void changed(javafx.beans.value.ObservableValue<? extends javafx.scene.Scene> o,
                                              javafx.scene.Scene antes, javafx.scene.Scene ahora) {
                    if (ahora == null) return;
                    rootPane.sceneProperty().removeListener(this);
                    javafx.application.Platform.runLater(BajasController.this::onSolicitudes);
                }
            });
        }
    }

    /** The banner: how many solicitudes de baja wait for Patrimonio, or how the área's are going. */
    private void cargarSolicitudes() {
        if (!com.sibim.service.SolicitudService.disponible()) return;
        boolean admin = SessionManager.isAdmin();
        DialogUtil.runAsync(
            () -> admin ? textoPendientes(solicitudes.countPendientes(com.sibim.model.Solicitud.TIPO_BAJA))
                        : textoMias(solicitudes.deMisAreas()),
            texto -> {
                boolean hay = texto != null;
                avisoSolicitudes.setVisible(hay);
                avisoSolicitudes.setManaged(hay);
                if (!hay) return;
                lblSolicitudes.setText(texto);
                btnSolicitudes.setText(admin ? "Revisar" : "Ver");
            },
            e -> log.warn("No se pudieron contar las solicitudes de baja", e));
    }

    /** "2 solicitudes de baja por revisar: …", or null when there are none. */
    static String textoPendientes(int n) {
        if (n <= 0) return null;
        return (n == 1 ? "1 solicitud de baja por revisar" : n + " solicitudes de baja por revisar")
            + ": las áreas piden dar de baja estos bienes. Revisa cada uno y decide si procede.";
    }

    /** "Tus solicitudes de baja: 2 en espera · 1 dado de baja · 1 no procede", or null without any. */
    static String textoMias(List<com.sibim.model.Solicitud> todas) {
        List<com.sibim.model.Solicitud> bajas = todas.stream().filter(com.sibim.model.Solicitud::esBaja).toList();
        return bajas.isEmpty() ? null : "Tus solicitudes de baja: " + resumenMias(bajas);
    }

    /** "2 en espera · 1 dado de baja · 1 no procede" (also on an área's Inicio). */
    static String resumenMias(List<com.sibim.model.Solicitud> todas) {
        List<com.sibim.model.Solicitud> bajas = todas.stream().filter(com.sibim.model.Solicitud::esBaja).toList();
        long sinFormato = bajas.stream().filter(com.sibim.model.Solicitud::faltaFormato).count();
        long espera = bajas.stream().filter(com.sibim.model.Solicitud::isPendiente).count() - sinFormato;
        long dadas = bajas.stream().filter(s -> com.sibim.model.Solicitud.ESTADO_APROBADA.equals(s.estado())).count();
        long no = bajas.size() - sinFormato - espera - dadas;
        List<String> partes = new java.util.ArrayList<>();
        if (sinFormato > 0) partes.add(sinFormato + (sinFormato == 1 ? " formato por entregar" : " formatos por entregar"));
        if (espera > 0) partes.add(espera + " en espera");
        if (dadas > 0) partes.add(dadas + (dadas == 1 ? " dado de baja" : " dados de baja"));
        if (no > 0) partes.add(no + " no procede" + (no == 1 ? "" : "n"));
        return String.join(" · ", partes);
    }

    @FXML private void onSolicitudes() {
        com.sibim.controller.dialogs.SolicitudesDialog.show(rootPane.getScene(),
            com.sibim.controller.dialogs.SolicitudesDialog.BAJAS, () -> { cargar(); cargarSolicitudes(); });
    }

    private void cargar() {
        DialogUtil.loadAsync(spinner, rootPane.getScene(),
            () -> productoService.getAllIncludingBaja().stream().filter(Producto::isDadoDeBaja).toList(),
            lista -> {
                bajas.setAll(lista);
                Integer anio = anioFilter.getValue();
                com.sibim.util.FiltroEscribible.opciones(areaFilter, lista.stream().map(Producto::getArea)
                    .filter(Objects::nonNull).distinct().sorted().toList());
                anioFilter.getItems().setAll(lista.stream().map(Producto::getFechaBaja).filter(Objects::nonNull)
                    .map(LocalDate::getYear).distinct().sorted(java.util.Comparator.reverseOrder()).toList());
                if (anio != null && anioFilter.getItems().contains(anio)) anioFilter.setValue(anio);
                actualizarTarjetas();
                aplicarFiltros();
            },
            "No se pudieron cargar las bajas", log);
    }

    /** "05/11/2026 · Acta de destrucción 12", or "En proceso". */
    static String definitiva(Producto p) {
        if (p.getBajaDefinitivaFecha() == null) return "En proceso";
        String doc = p.getBajaDefinitivaDocumento();
        return p.getBajaDefinitivaFecha().format(FMT) + (doc != null && !doc.isBlank() ? " · " + doc : "");
    }

    private void actualizarBotones() {
        List<Producto> sel = table.getSelectionModel().getSelectedItems();
        Producto uno = table.getSelectionModel().getSelectedItem();
        btnReactivar.setDisable(uno == null || sel.size() > 1 || uno.isBajaDefinitiva());
        long pendientes = sel.stream().filter(p -> p != null && !p.isBajaDefinitiva()).count();
        btnDefinitiva.setDisable(pendientes == 0);
        btnDefinitiva.setText(pendientes > 1 ? "Baja definitiva (" + pendientes + ")" : "Baja definitiva");
    }

    private void actualizarTarjetas() {
        int anioActual = LocalDate.now().getYear();
        lblTotal.setText(String.valueOf(bajas.size()));
        lblAnio.setText(String.valueOf(bajas.stream()
            .filter(p -> p.getFechaBaja() != null && p.getFechaBaja().getYear() == anioActual).count()));
        lblValor.setText(FormatUtils.formatCurrency(bajas.stream().map(Producto::getValorTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add)));
        lblSinSolicitud.setText(String.valueOf(bajas.stream().filter(p -> folioSolicitud(p) == null).count()));
        long definitivas = bajas.stream().filter(Producto::isBajaDefinitiva).count();
        chipTodas.setText("Todas (" + bajas.size() + ")");
        chipProceso.setText("En proceso (" + (bajas.size() - definitivas) + ")");
        chipDefinitivas.setText("Definitivas (" + definitivas + ")");
    }

    private void aplicarFiltros() {
        String q = searchField.getText() == null ? "" : searchField.getText().strip().toLowerCase();
        String area = areaFilter.getValue();
        Integer anio = anioFilter.getValue();
        boolean soloProceso = chipProceso.isSelected(), soloDefinitivas = chipDefinitivas.isSelected();
        filtradas.setPredicate(p -> {
            if (soloProceso && p.isBajaDefinitiva()) return false;
            if (soloDefinitivas && !p.isBajaDefinitiva()) return false;
            if (area != null && !area.equals(p.getArea())) return false;
            if (anio != null && (p.getFechaBaja() == null || p.getFechaBaja().getYear() != anio)) return false;
            if (q.isEmpty()) return true;
            return contiene(p.getNombre(), q) || contiene(p.getCodigo(), q) || contiene(p.getMotivoBaja(), q)
                || contiene(p.getBajaDefinitivaDocumento(), q)
                || contiene(p.getDictamenBaja(), q) || contiene(p.getNumeroActaBaja(), q) || contiene(p.getArea(), q);
        });
        lblResultados.setText(FormatUtils.plural(filtradas.size(), "resultado", "resultados"));
    }

    private static boolean contiene(String s, String q) {
        return s != null && s.toLowerCase().contains(q);
    }

    @FXML private void onRefresh() { cargar(); cargarSolicitudes(); }

    @FXML private void onLimpiar() {
        searchField.clear();
        areaFilter.setValue(null);
        anioFilter.setValue(null);
        chipTodas.setSelected(true);
        aplicarFiltros();
    }

    @FXML private void onDefinitiva() {
        if (!SessionManager.isAdmin()) return;
        List<Producto> elegidos = table.getSelectionModel().getSelectedItems().stream()
            .filter(p -> p != null && !p.isBajaDefinitiva()).toList();
        if (elegidos.isEmpty()) return;
        BajaDefinitivaDialog.pedir(elegidos).ifPresent(datos -> DialogUtil.runAsync(
            () -> productoService.bajaDefinitiva(elegidos, datos.fecha(), datos.documento(), datos.destino()),
            n -> {
                cargar();
                NotificacionUtil.exitoConAccion(table.getScene(),
                    FormatUtils.plural(n, "baja definitiva registrada", "bajas definitivas registradas"),
                    "Deshacer", () -> DialogUtil.runAsync(() -> productoService.quitarBajaDefinitiva(elegidos),
                        this::cargar,
                        e -> NotificacionUtil.error(table.getScene(), "No se pudo deshacer: " + com.sibim.util.MensajeError.de(e))));
            },
            e -> NotificacionUtil.error(table.getScene(),
                e instanceof ProductoService.ValidationException ? e.getMessage() : "No se pudo registrar la baja definitiva")));
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
                "¿Regresar \"" + p.getNombre() + "\" al inventario activo? Vuelve con su mismo código ("
                    + p.getCodigo() + "): los números no se le dan a otro bien.", "Reactivar"))
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
