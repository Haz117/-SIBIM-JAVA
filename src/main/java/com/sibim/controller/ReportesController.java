package com.sibim.controller;

import com.sibim.model.Producto;
import com.sibim.service.ProductoService;
import com.sibim.service.ReporteService;
import com.sibim.util.AccessibilityUtils;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppExecutor;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.VBox;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static com.sibim.controller.ReporteFila.csv;
import static com.sibim.controller.ReporteFila.excel;
import static com.sibim.controller.ReporteFila.pdf;

public class ReportesController {

    @FXML private VBox       periodCard;
    @FXML private VBox       listaListados;
    @FXML private VBox       listaFormatos;
    @FXML private DatePicker desdeField;
    @FXML private DatePicker hastaField;
    @FXML private ProgressIndicator spinner;
    @FXML private Button btnPresetHoy;
    @FXML private Button btnPresetSemana;
    @FXML private Button btnPresetMes;
    @FXML private Button btnPresetAnio;
    @FXML private Button btnPresetTodo;
    @FXML private Label helpTiposReporte;
    @FXML private Label helpPeriodo;
    @FXML private Label lblSubtitulo;
    @FXML private Label lblAlcancePeriodo;
    // Horizontal bars: long área names read on one line instead of slanted.
    @FXML private BarChart<Number, String> areaChart;
    @FXML private ProgressIndicator chartSpinner;
    @FXML private VBox chartEmptyState;
    @FXML private BarChart<Number, String> categoriaChart;
    @FXML private ProgressIndicator categoriaChartSpinner;
    @FXML private VBox categoriaChartEmptyState;

    private final ReporteService   reporteService  = ReporteService.getInstance();
    private final ProductoService  productoService = new ProductoService();
    private final StringProperty   periodoTexto    = new SimpleStringProperty("Todo el historial");
    private final Map<KeyCombination, Runnable> atajos = new LinkedHashMap<>();
    private GraficaBarras graficaAreas;
    private GraficaBarras graficaCategorias;
    private boolean updatingFromPreset = false;

    @FXML
    public void initialize() {
        spinner.setVisible(false);
        spinner.setManaged(false);
        graficaAreas      = new GraficaBarras(areaChart, chartSpinner, chartEmptyState);
        graficaCategorias = new GraficaBarras(categoriaChart, categoriaChartSpinner, categoriaChartEmptyState);
        construirReportes();

        desdeField.setConverter(FormatUtils.datePickerConverter());
        hastaField.setConverter(FormatUtils.datePickerConverter());
        desdeField.valueProperty().addListener((o, a, b) -> alCambiarFecha());
        hastaField.valueProperty().addListener((o, a, b) -> alCambiarFecha());
        // Always open on the whole history: a remembered "Hoy" left the charts empty.
        onReportTodo();
        DialogUtil.enableClickToShowTooltip(helpTiposReporte);
        DialogUtil.enableClickToShowTooltip(helpPeriodo);

        // Alt+1-5 for date presets — Ctrl+1-5 is already claimed by
        // MainController for sidebar navigation (Ctrl+7 = Reportes).
        atajos.put(new KeyCodeCombination(KeyCode.DIGIT1, KeyCombination.ALT_DOWN), this::onReportHoy);
        atajos.put(new KeyCodeCombination(KeyCode.DIGIT2, KeyCombination.ALT_DOWN), this::onReportSemana);
        atajos.put(new KeyCodeCombination(KeyCode.DIGIT3, KeyCombination.ALT_DOWN), this::onReportMes);
        atajos.put(new KeyCodeCombination(KeyCode.DIGIT4, KeyCombination.ALT_DOWN), this::onReportAnio);
        atajos.put(new KeyCodeCombination(KeyCode.DIGIT5, KeyCombination.ALT_DOWN), this::onReportTodo);
        periodCard.sceneProperty().addListener((obs, antes, ahora) -> {
            // Taken back when the page is left, or Alt+1-5 would keep firing on other screens.
            if (antes != null) atajos.keySet().forEach(antes.getAccelerators()::remove);
            if (ahora != null) ahora.getAccelerators().putAll(atajos);
        });

        AnimationUtils.staggeredFadeInUp(List.of(periodCard, listaListados, listaFormatos), 300, 70);
    }

    // ── Catálogo de reportes ───────────────────────────────────────────────────

    /** Every report of the screen, once: add a row here to offer a new one.
     *  The áreas get the listings of their own bienes and the formats to ask for
     *  a baja, in PDF; the reports that audit the whole inventory, and the
     *  Excel/CSV copies, are Patrimonio's (the services enforce both). */
    private void construirReportes() {
        boolean control = com.sibim.session.Permisos.veReportesDeControl();

        List<Node> listados = new ArrayList<>();
        listados.add(ReporteFila.crear("mdi2p-package-variant", "indigo", "Inventario General",
            "Listado de los bienes patrimoniales con cantidad, valor unitario y estado actual.",
            periodoTexto, "Exportar", formatos(
                delPeriodo(reporteService::exportInventarioPdf),
                delPeriodo(reporteService::exportInventarioExcel),
                delPeriodo(reporteService::exportInventarioCsv))));
        if (control) listados.add(ReporteFila.crear("mdi2s-swap-vertical", "teal", "Registro de Movimientos",
            "Historial de entradas, salidas, ajustes y transferencias.",
            periodoTexto, "Exportar", formatos(
                delPeriodo(reporteService::exportMovimientosPdf),
                delPeriodo(reporteService::exportMovimientosExcel),
                delPeriodo(reporteService::exportMovimientosCsv))));
        listados.add(ReporteFila.crear("mdi2s-sitemap", "green", "Distribución por Área",
            "Bienes agrupados por secretaría y dirección del Ayuntamiento.",
            null, "Exportar", formatos(
                completo(reporteService::exportDistribucionPdf),
                completo(reporteService::exportDistribucionExcel),
                completo(reporteService::exportDistribucionCsv))));
        listados.add(ReporteFila.crear("mdi2b-bell-alert", "amber", "Alertas y pendientes",
            "Bienes con garantía vencida o próxima a vencer y pendientes patrimoniales.",
            null, "Exportar", formatos(
                completo(reporteService::exportAlertasPdf),
                completo(reporteService::exportAlertasExcel),
                completo(reporteService::exportAlertasCsv))));
        if (control) listados.add(ReporteFila.crear("mdi2d-delete-circle-outline", "slate", "Bienes Dados de Baja",
            "Bienes fuera del inventario activo; su historial se conserva para auditoría.",
            null, "Exportar", formatos(
                completo(reporteService::exportBajasPdf),
                completo(reporteService::exportBajasExcel),
                completo(reporteService::exportBajasCsv))));

        List<Node> oficiales = new ArrayList<>();
        if (control) {
            oficiales.add(ReporteFila.crear("mdi2c-clipboard-text-outline", "red", "Entrega-Recepción (ANEXO V.4)",
                "Todos los bienes con resguardante, valor de adquisición, depreciación acumulada, valor en libros y condición. Para actos formales de entrega-recepción.",
                null, null, List.of(pdf(completo(
                    () -> reporteService.exportEntregaRecepcionPdf(productoService.getAll()))))));
            oficiales.add(ReporteFila.crear("mdi2c-car-outline", "indigo", "Parque Vehicular (V.6)",
                "Una ficha por vehículo con identificación, condición y lista de verificación de componentes. Solo incluye bienes con marca registrada.",
                null, null, List.of(pdf(completo(this::exportarParqueVehicular)))));
            oficiales.add(ReporteFila.crear("mdi2s-shield-lock-outline", "purple", "Auditoría Consolidada",
                "Resguardos activos por área, préstamos abiertos (vencidos primero) y resumen de operaciones. Para auditorías y cambios de administración.",
                null, null, List.of(pdf(completo(reporteService::exportAuditoriaPdf)))));
        }
        // Every área has the two formats a baja needs; only Patrimonio registers the baja itself.
        oficiales.add(ReporteFila.crear("mdi2f-file-remove-outline", "amber", "Solicitud de Baja",
            "Lo llena y firma el área que tiene el bien para pedir su baja a Patrimonio. Una página por bien.",
            null, null, List.of(new ReporteFila.Formato("PDF", "mdi2f-file-pdf-box", "report-export-icon-pdf",
                () -> formatoDeBaja("Solicitud de baja", "la solicitud de baja", reporteService::exportSolicitudBaja)))));
        oficiales.add(ReporteFila.crear("mdi2f-file-certificate-outline", "slate", "Dictamen Técnico de Baja",
            "Lo llena el área técnica que revisó el bien: estado, diagnóstico, si procede la baja y destino final. Se anexa a la solicitud de baja firmada.",
            null, "Generar", List.of(
                new ReporteFila.Formato("De un bien (por código)…", "mdi2m-magnify", "report-export-icon-pdf",
                    () -> formatoDeBaja("Dictamen técnico de baja", "el dictamen técnico de baja", reporteService::exportDictamenBaja)),
                new ReporteFila.Formato("Formato en blanco", "mdi2f-file-outline", "report-export-icon-pdf",
                    completo(reporteService::exportDictamenBajaEnBlanco)))));

        if (!com.sibim.session.Permisos.exportaHojasDeCalculo())
            lblSubtitulo.setText("Listados de tus bienes y formatos de baja, en PDF");
        if (!control)
            lblAlcancePeriodo.setText("Aplica al Inventario y a las gráficas · vacío = todo el historial");
        listaListados.getChildren().setAll(listados);
        listaFormatos.getChildren().setAll(oficiales);
        for (VBox lista : List.of(listaListados, listaFormatos)) {
            List<Node> filas = lista.getChildren();
            filas.get(filas.size() - 1).getStyleClass().add("report-row-last");
        }
    }

    /** PDF, Excel and CSV for Patrimonio; PDF alone for the áreas. */
    private static List<ReporteFila.Formato> formatos(Runnable enPdf, Runnable enExcel, Runnable enCsv) {
        return com.sibim.session.Permisos.exportaHojasDeCalculo()
            ? List.of(pdf(enPdf), excel(enExcel), csv(enCsv))
            : List.of(pdf(enPdf));
    }

    /** A report limited to the chosen period. */
    private Runnable delPeriodo(ExportPeriodo task) {
        return () -> {
            if (validarFechas()) exportar(() -> task.run(getDesde(), getHasta()), true);
        };
    }

    /** A report that always covers the whole history. */
    private Runnable completo(ExportTask task) {
        return () -> exportar(task, false);
    }

    private File exportarParqueVehicular() throws Exception {
        var vehiculos = productoService.getAll().stream()
            .filter(p -> p.getMarca() != null && !p.getMarca().isBlank())
            .toList();
        return reporteService.exportParqueVehicularPdf(vehiculos);
    }

    // ── Período ────────────────────────────────────────────────────────────────

    private void setPresetActive(Button active) {
        for (Button b : new Button[]{btnPresetHoy, btnPresetSemana, btnPresetMes, btnPresetAnio, btnPresetTodo}) {
            b.getStyleClass().remove("btn-preset-active");
        }
        if (active != null) active.getStyleClass().add("btn-preset-active");
    }

    private void applyPreset(Button source, LocalDate desde, LocalDate hasta) {
        updatingFromPreset = true;
        desdeField.setValue(desde);
        hastaField.setValue(hasta);
        updatingFromPreset = false;
        setPresetActive(source);
        cargarGraficas();
    }

    private void alCambiarFecha() {
        applyDateRangeStyle();
        periodoTexto.set(describirPeriodo(getDesde(), getHasta()));
        if (updatingFromPreset) return;
        setPresetActive(null);
        cargarGraficas();
    }

    /** What the "período" chip of a report says. */
    static String describirPeriodo(LocalDate desde, LocalDate hasta) {
        if (desde == null && hasta == null) return "Todo el historial";
        if (desde == null) return "Hasta " + FormatUtils.formatDate(hasta);
        if (hasta == null) return "Desde " + FormatUtils.formatDate(desde);
        if (desde.equals(hasta)) return FormatUtils.formatDate(desde);
        return FormatUtils.formatDate(desde) + " – " + FormatUtils.formatDate(hasta);
    }

    @FXML private void onReportHoy() {
        LocalDate hoy = LocalDate.now();
        applyPreset(btnPresetHoy, hoy, hoy);
    }
    @FXML private void onReportSemana() {
        LocalDate hoy = LocalDate.now();
        applyPreset(btnPresetSemana, hoy.minusDays(6), hoy);
    }
    @FXML private void onReportMes() {
        LocalDate hoy = LocalDate.now();
        applyPreset(btnPresetMes, hoy.withDayOfMonth(1), hoy);
    }
    @FXML private void onReportAnio() {
        LocalDate hoy = LocalDate.now();
        applyPreset(btnPresetAnio, hoy.withDayOfYear(1), hoy);
    }
    @FXML private void onReportTodo() {
        applyPreset(btnPresetTodo, null, null);
    }

    private LocalDate getDesde() { return desdeField.getValue(); }
    private LocalDate getHasta() { return hastaField.getValue(); }

    private boolean rangoInvalido() {
        return getDesde() != null && getHasta() != null && getDesde().isAfter(getHasta());
    }

    private void applyDateRangeStyle() {
        for (DatePicker campo : List.of(desdeField, hastaField)) {
            campo.getStyleClass().remove("field-error");
            if (rangoInvalido()) campo.getStyleClass().add("field-error");
        }
    }

    private boolean validarFechas() {
        if (!rangoInvalido()) return true;
        NotificacionUtil.advertencia(spinner.getScene(),
            "La fecha inicial debe ser anterior a la fecha final");
        AnimationUtils.shake(hastaField);
        return false;
    }

    // ── Exportación ────────────────────────────────────────────────────────────

    private void exportar(ExportTask task, boolean delPeriodo) {
        Scene scene = spinner.getScene();
        if (scene == null) return;
        DialogUtil.runAsyncWithProgress(scene, "Generando reporte…",
            task::run,
            file -> {
                if (file == null) {
                    NotificacionUtil.advertencia(scene, delPeriodo
                        ? "Sin datos para el período seleccionado. Prueba con un rango diferente o elige «Todo»."
                        : "No hay datos para este reporte.");
                    return;
                }
                DialogUtil.showExportResultDialog(scene, file);
            },
            e -> NotificacionUtil.errorConAccion(scene,
                "Error al generar el reporte", "Reintentar", () -> exportar(task, delPeriodo))
        );
    }

    @FunctionalInterface
    interface FormatoPorBienes {
        File generar(List<Producto> bienes) throws Exception;
    }

    /** Asks for the código(s) of the bien(es) and generates the format — one page per bien. */
    private void formatoDeBaja(String titulo, String elFormato, FormatoPorBienes formato) {
        Scene scene = spinner.getScene();
        if (scene == null) return;
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle(titulo);
        dlg.setHeaderText("Código del bien (varios, separados por coma)");
        dlg.setContentText("Código:");
        DialogUtil.applyOwner(dlg);
        DialogUtil.conEncabezado(dlg, "mdi2f-file-certificate-outline");
        dlg.showAndWait().map(String::trim).filter(t -> !t.isBlank()).ifPresent(texto -> {
            List<String> codigos = Arrays.stream(texto.split(","))
                .map(String::trim).filter(c -> !c.isBlank()).distinct().toList();
            List<String> noEncontrados = new ArrayList<>();
            DialogUtil.runAsyncWithProgress(scene, "Generando " + elFormato + "…",
                () -> {
                    List<Producto> bienes = new ArrayList<>();
                    for (String c : codigos) {
                        productoService.findByCodigo(c).ifPresentOrElse(bienes::add, () -> noEncontrados.add(c));
                    }
                    return bienes.isEmpty() ? null : formato.generar(bienes);
                },
                file -> {
                    if (!noEncontrados.isEmpty()) NotificacionUtil.advertencia(scene,
                        "No se encontró ningún bien con código " + String.join(", ", noEncontrados)
                        + (file != null ? " — el formato se generó con los demás." : "."));
                    if (file != null) DialogUtil.showExportResultDialog(scene, file);
                },
                e -> NotificacionUtil.error(scene, "No se pudo generar " + elFormato));
        });
    }

    // ── Gráficas ───────────────────────────────────────────────────────────────

    private void cargarGraficas() {
        LocalDate desde = getDesde(), hasta = getHasta();
        graficaAreas.cargar(() -> {
            List<XYChart.Data<Number, String>> barras = new ArrayList<>();
            productoService.countByArea(20, desde, hasta)
                .forEach((area, cnt) -> barras.add(new XYChart.Data<>(cnt, area)));
            return barras;
        }, this::installBarClickHandlers);
        graficaCategorias.cargar(() -> {
            List<XYChart.Data<Number, String>> barras = new ArrayList<>();
            productoService.getValorPorCategoria(20, desde, hasta)
                .forEach(cv -> barras.add(new XYChart.Data<>(cv.valor(), cv.nombre())));
            return barras;
        }, series -> {});
    }

    private void installBarClickHandlers(XYChart.Series<Number, String> series) {
        for (XYChart.Data<Number, String> data : series.getData()) {
            Node node = data.getNode();
            if (node == null) continue;
            String area = data.getYValue();
            Tooltip.install(node, new Tooltip(area + "\nClic para ver en Bienes"));
            node.setCursor(javafx.scene.Cursor.HAND);
            node.setOnMouseClicked(e -> {
                com.sibim.session.NavigationContext.setPendingAreaFilter(area);
                MainController mc = MainController.getInstance();
                if (mc != null) mc.navigateTo("productos");
            });
            AccessibilityUtils.asButton(node, area + " — ver en Bienes");
        }
    }

    /** A horizontal bar chart with its spinner and its "nothing in this period" state. */
    private static final class GraficaBarras {
        private final BarChart<Number, String> chart;
        private final ProgressIndicator spinner;
        private final VBox vacio;
        private int carga;

        GraficaBarras(BarChart<Number, String> chart, ProgressIndicator spinner, VBox vacio) {
            this.chart = chart;
            this.spinner = spinner;
            this.vacio = vacio;
        }

        /** @param datos   largest first; read off the FX thread
         *  @param listas  called once the bars are on screen */
        void cargar(Callable<List<XYChart.Data<Number, String>>> datos,
                    Consumer<XYChart.Series<Number, String>> listas) {
            int esta = ++carga;
            ver(spinner, true);
            AppExecutor.submit(() -> {
                List<XYChart.Data<Number, String>> barras;
                try {
                    barras = datos.call();
                } catch (Exception ex) {
                    barras = List.of();
                }
                List<XYChart.Data<Number, String>> resultado = barras;
                Platform.runLater(() -> {
                    // The dates changed again meanwhile: a newer load owns the chart.
                    if (esta != carga) return;
                    XYChart.Series<Number, String> series = new XYChart.Series<>();
                    // A vertical category axis draws its first entry at the bottom: add in
                    // reverse so the largest bar ends up on top.
                    resultado.forEach(d -> series.getData().add(0, d));
                    boolean hay = !resultado.isEmpty();
                    chart.setPrefHeight(alturaBarras(resultado.size()));
                    chart.getData().setAll(List.of(series));
                    ver(spinner, false);
                    ver(vacio, !hay);
                    ver(chart, hay);
                    if (hay) {
                        AnimationUtils.fadeInUp(chart, 350, 0);
                        // One extra pulse so the chart scene graph creates the bar nodes
                        Platform.runLater(() -> listas.accept(series));
                    }
                });
            });
        }

        private static void ver(Node n, boolean visible) {
            n.setVisible(visible);
            n.setManaged(visible);
        }

        /** Tall enough for one readable bar per row, whatever the number of rows. */
        private static double alturaBarras(int filas) {
            return Math.max(180, 70 + filas * 34.0);
        }
    }

    @FunctionalInterface
    interface ExportTask {
        File run() throws Exception;
    }

    @FunctionalInterface
    interface ExportPeriodo {
        File run(LocalDate desde, LocalDate hasta) throws Exception;
    }
}
