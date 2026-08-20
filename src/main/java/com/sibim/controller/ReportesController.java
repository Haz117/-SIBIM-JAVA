package com.sibim.controller;

import com.sibim.service.ReporteService;
import com.sibim.util.AnimationUtils;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.time.LocalDate;

public class ReportesController {

    private static final Logger log = LoggerFactory.getLogger(ReportesController.class);

    @FXML private VBox       periodCard;
    @FXML private GridPane   reportGrid;
    @FXML private DatePicker desdeField;
    @FXML private DatePicker hastaField;
    @FXML private ProgressIndicator spinner;
    @FXML private Button btnPresetHoy;
    @FXML private Button btnPresetSemana;
    @FXML private Button btnPresetMes;
    @FXML private Button btnPresetAnio;
    @FXML private Button btnPresetTodo;

    private final ReporteService reporteService = new ReporteService();
    private boolean updatingFromPreset = false;

    @FXML
    public void initialize() {
        spinner.setVisible(false);
        spinner.setManaged(false);
        desdeField.valueProperty().addListener((o, a, b) -> { if (!updatingFromPreset) clearPresetActive(); });
        hastaField.valueProperty().addListener((o, a, b) -> { if (!updatingFromPreset) clearPresetActive(); });
        onReportMes(); // default to current month

        if (periodCard != null) AnimationUtils.fadeInUp(periodCard, 300,  0);
        if (reportGrid != null) AnimationUtils.staggeredFadeInUp(reportGrid.getChildren(), 300, 70);
    }

    private void setPresetActive(Button active) {
        for (Button b : new Button[]{btnPresetHoy, btnPresetSemana, btnPresetMes, btnPresetAnio, btnPresetTodo}) {
            if (b != null) b.getStyleClass().remove("btn-preset-active");
        }
        if (active != null) active.getStyleClass().add("btn-preset-active");
    }

    private void clearPresetActive() { setPresetActive(null); }

    private void applyPreset(Button source, LocalDate desde, LocalDate hasta) {
        updatingFromPreset = true;
        desdeField.setValue(desde);
        hastaField.setValue(hasta);
        updatingFromPreset = false;
        setPresetActive(source);
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

    // ─── Inventario ───
    @FXML private void onInventarioPdf(ActionEvent event) {
        if (!validarFechas()) return;
        exportar(event, () -> reporteService.exportInventarioPdf(getDesde(), getHasta()));
    }
    @FXML private void onInventarioExcel(ActionEvent event) {
        if (!validarFechas()) return;
        exportar(event, () -> reporteService.exportInventarioExcel(getDesde(), getHasta()));
    }
    @FXML private void onInventarioCsv(ActionEvent event) {
        if (!validarFechas()) return;
        exportar(event, () -> reporteService.exportInventarioCsv(getDesde(), getHasta()));
    }

    // ─── Movimientos ───
    @FXML private void onMovimientosPdf(ActionEvent event) {
        if (!validarFechas()) return;
        exportar(event, () -> reporteService.exportMovimientosPdf(getDesde(), getHasta()));
    }
    @FXML private void onMovimientosExcel(ActionEvent event) {
        if (!validarFechas()) return;
        exportar(event, () -> reporteService.exportMovimientosExcel(getDesde(), getHasta()));
    }
    @FXML private void onMovimientosCsv(ActionEvent event) {
        if (!validarFechas()) return;
        exportar(event, () -> reporteService.exportMovimientosCsv(getDesde(), getHasta()));
    }

    // ─── Alertas ───
    @FXML private void onAlertasPdf(ActionEvent event)   { exportar(event, () -> reporteService.exportAlertasPdf()); }
    @FXML private void onAlertasExcel(ActionEvent event) { exportar(event, () -> reporteService.exportAlertasExcel()); }
    @FXML private void onAlertasCsv(ActionEvent event)   { exportar(event, () -> reporteService.exportAlertasCsv()); }

    // ─── Distribución ───
    @FXML private void onDistribucionPdf(ActionEvent event)   { exportar(event, () -> reporteService.exportDistribucionPdf()); }
    @FXML private void onDistribucionExcel(ActionEvent event) { exportar(event, () -> reporteService.exportDistribucionExcel()); }
    @FXML private void onDistribucionCsv(ActionEvent event)   { exportar(event, () -> reporteService.exportDistribucionCsv()); }

    private LocalDate getDesde() { return desdeField.getValue(); }
    private LocalDate getHasta() { return hastaField.getValue(); }

    private boolean validarFechas() {
        LocalDate desde = desdeField.getValue();
        LocalDate hasta  = hastaField.getValue();
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            NotificacionUtil.advertencia(spinner.getScene(),
                "La fecha inicial debe ser anterior a la fecha final");
            AnimationUtils.shake(desdeField);
            return false;
        }
        return true;
    }

    private void exportar(ActionEvent event, ExportTask task) {
        Button source = event != null && event.getSource() instanceof Button b ? b : null;
        if (source != null) source.setDisable(true);
        spinner.setVisible(true);
        spinner.setManaged(true);
        DialogUtil.runAsync(
            task::run,
            file -> {
                if (source != null) source.setDisable(false);
                spinner.setVisible(false);
                spinner.setManaged(false);
                showExportDialog(file);
            },
            e -> {
                if (source != null) source.setDisable(false);
                spinner.setVisible(false);
                spinner.setManaged(false);
                NotificacionUtil.error(spinner.getScene(), "Error al generar el reporte");
            }
        );
    }

    private void showExportDialog(java.io.File file) {
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dialog = new javafx.scene.control.Dialog<>();
        dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(460);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("✓", "Reporte generado",
            "El archivo fue exportado exitosamente.", "#047857", "#065F46");

        String name  = file.getName();
        String ext   = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toUpperCase() : "";
        long   bytes = file.length();
        String size  = bytes < 1024 ? bytes + " B"
            : bytes < 1024 * 1024 ? (bytes / 1024) + " KB"
            : String.format("%.1f MB", bytes / (1024.0 * 1024));

        Label iconLbl  = new Label("PDF".equals(ext) ? "📄" : "XLSX".equals(ext) ? "📊" : "📋");
        iconLbl.setStyle("-fx-font-size: 26px;");
        Label nameLbl  = new Label(name);
        nameLbl.getStyleClass().add("dlg-detail-value");
        nameLbl.setWrapText(true);
        Label sizeLbl  = new Label(size + " · " + file.getParent());
        sizeLbl.getStyleClass().add("muted-sm");
        sizeLbl.setWrapText(true);

        VBox info = new VBox(3, nameLbl, sizeLbl);
        HBox.setHgrow(info, javafx.scene.layout.Priority.ALWAYS);
        HBox fileCard = new HBox(14, iconLbl, info);
        fileCard.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        fileCard.setPadding(new javafx.geometry.Insets(14, 18, 14, 18));
        fileCard.getStyleClass().add("dlg-detail-header");

        Button btnAbrir   = new Button("📂  Abrir");
        Button btnCarpeta = new Button("🗁  Carpeta");
        Button btnCopiar  = new Button("⎘  Copiar ruta");
        Button btnImpr    = new Button("🖨  Imprimir");
        btnAbrir.getStyleClass().add("btn-primary");
        btnCarpeta.getStyleClass().add("btn-secondary");
        btnCopiar.getStyleClass().add("btn-secondary");
        btnImpr.getStyleClass().add("btn-secondary");
        btnImpr.setVisible("PDF".equals(ext));
        btnImpr.setManaged("PDF".equals(ext));

        btnAbrir.setOnAction(e -> { try { Desktop.getDesktop().open(file); } catch (Exception ex) { log.debug("No se pudo abrir", ex); } });
        btnCarpeta.setOnAction(e -> { try { Desktop.getDesktop().open(file.getParentFile()); } catch (Exception ex) { log.debug("No se pudo abrir carpeta", ex); } });
        btnCopiar.setOnAction(e -> {
            var content = new javafx.scene.input.ClipboardContent();
            content.putString(file.getAbsolutePath());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
            NotificacionUtil.info(spinner.getScene(), "Ruta copiada al portapapeles");
        });
        btnImpr.setOnAction(e -> {
            try { Desktop.getDesktop().print(file); }
            catch (Exception ex) { NotificacionUtil.error(spinner.getScene(), "No se pudo enviar a la impresora"); }
        });

        HBox actions = new HBox(8, btnAbrir, btnImpr, btnCarpeta, btnCopiar);
        actions.setPadding(new javafx.geometry.Insets(10, 18, 8, 18));
        actions.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        AnimationUtils.staggeredFadeInUp(java.util.List.of(header, fileCard, actions), 260, 60);
        dialog.getDialogPane().setContent(new VBox(0, header, fileCard, actions));
        dialog.showAndWait();
    }

    @FunctionalInterface
    interface ExportTask {
        File run() throws Exception;
    }
}
