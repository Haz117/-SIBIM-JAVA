package com.sibim.controller.dialogs;

import com.sibim.model.ConteoFisico;
import com.sibim.model.ConteoItem;
import com.sibim.repository.ConteoRepository;
import com.sibim.service.ReporteConteoService;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppColors;
import com.sibim.util.AppExecutor;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

/** Lists all completed physical inventory counts for the admin and allows
 *  viewing the item-level detail or exporting each session to PDF. */
public final class HistorialConteosDialog {

    private static String estadoItemLabel(String code) {
        return switch (code != null ? code : "ENCONTRADO") {
            case "MAL_ESTADO"   -> "Mal estado";
            case "EN_OTRA_AREA" -> "En otra área";
            case "FALTANTE"     -> "Faltante";
            default             -> "Encontrado";
        };
    }

    private static final Logger log = LoggerFactory.getLogger(HistorialConteosDialog.class);

    private HistorialConteosDialog() {}

    public static void show(javafx.scene.Scene scene) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(680);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2c-clipboard-list-outline",
            "Historial de Conteos Físicos",
            "Registro de todos los levantamientos de inventario realizados",
            AppColors.CYAN, AppColors.CYAN_D);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(36, 36);
        VBox loadingBox = new VBox(spinner);
        loadingBox.setAlignment(Pos.CENTER);
        loadingBox.setPrefHeight(120);

        VBox listBox = new VBox(6);
        listBox.setPadding(new Insets(4));

        ScrollPane scroll = new ScrollPane(loadingBox);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(420);
        scroll.getStyleClass().add("dlg-tabs-scroll");

        dialog.getDialogPane().setContent(new VBox(0, header, scroll));

        AppExecutor.submit(() -> {
            List<ConteoFisico> conteos;
            try {
                conteos = new ConteoRepository().findAll(200);
            } catch (Exception e) {
                log.error("No se pudo cargar el historial de conteos", e);
                javafx.application.Platform.runLater(() -> {
                    Label err = new Label("No se pudo cargar el historial.");
                    err.getStyleClass().add("muted");
                    scroll.setContent(err);
                });
                return;
            }
            javafx.application.Platform.runLater(() -> {
                if (conteos.isEmpty()) {
                    Label empty = new Label("Aún no se han realizado conteos físicos.");
                    empty.getStyleClass().add("muted");
                    VBox emptyBox = new VBox(empty);
                    emptyBox.setAlignment(Pos.CENTER);
                    emptyBox.setPrefHeight(120);
                    scroll.setContent(emptyBox);
                    return;
                }
                for (ConteoFisico c : conteos) {
                    listBox.getChildren().add(buildRow(c, scene, dialog));
                }
                scroll.setContent(listBox);
                AnimationUtils.staggeredFadeInUp(listBox.getChildren(), 200, 45);
            });
        });

        dialog.showAndWait();
    }

    private static HBox buildRow(ConteoFisico c, javafx.scene.Scene scene,
                                  Dialog<ButtonType> parentDialog) {
        HBox row = new HBox(12);
        row.getStyleClass().add("dlg-detail-header");
        row.setPadding(new Insets(10, 14, 10, 14));
        row.setAlignment(Pos.CENTER_LEFT);

        FontIcon ico = new FontIcon("mdi2c-clipboard-check-outline");
        ico.setIconSize(22);
        ico.getStyleClass().add("nav-icon");

        VBox info = new VBox(2);
        String fechaStr = c.getCreadoEn() != null ? FormatUtils.formatDateTime(c.getCreadoEn()) : "—";
        Label titulo = new Label(fechaStr + "  ·  " + (c.getUsuarioNombre() != null ? c.getUsuarioNombre() : "—"));
        titulo.getStyleClass().add("dlg-detail-value");
        boolean hasDiscrep = c.getTotalDiscrepancias() > 0;
        Label stats = new Label(c.getTotalContados() + " bien(es) contado(s)"
            + (hasDiscrep ? "  ·  " + c.getTotalDiscrepancias() + " discrepancia(s)" : "  ·  Sin diferencias"));
        stats.getStyleClass().addAll("muted-sm", hasDiscrep ? "text-warn" : "text-ok");
        info.getChildren().addAll(titulo, stats);
        HBox.setHgrow(info, Priority.ALWAYS);

        Button btnDetalle = new Button("Ver detalle");
        btnDetalle.setGraphic(new FontIcon("mdi2e-eye-outline"));
        btnDetalle.setContentDisplay(ContentDisplay.LEFT);
        btnDetalle.getStyleClass().add("btn-secondary");
        btnDetalle.setOnAction(e -> showDetalle(c, scene));

        Button btnExport = new Button("PDF");
        btnExport.setGraphic(new FontIcon("mdi2f-file-pdf-outline"));
        btnExport.setContentDisplay(ContentDisplay.LEFT);
        btnExport.getStyleClass().add("btn-secondary");
        btnExport.setOnAction(e -> exportPdf(c, scene, btnExport));

        row.getChildren().addAll(ico, info, btnDetalle, btnExport);
        return row;
    }

    private static void showDetalle(ConteoFisico c, javafx.scene.Scene scene) {
        Dialog<ButtonType> dlg = new Dialog<>();
        DialogUtil.applyOwner(dlg);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.getDialogPane().setPrefWidth(700);
        DialogUtil.applyStylesheet(dlg.getDialogPane());

        String fechaStr = c.getCreadoEn() != null ? FormatUtils.formatDateTime(c.getCreadoEn()) : "—";
        HBox hdr = DialogUtil.gradientHeader("mdi2c-clipboard-list-outline",
            "Detalle del conteo — " + fechaStr,
            c.getUsuarioNombre() + "  ·  " + c.getTotalContados() + " bien(es)"
                + (c.getTotalDiscrepancias() > 0 ? "  ·  " + c.getTotalDiscrepancias() + " discrepancia(s)" : ""),
            AppColors.CYAN, AppColors.CYAN_D);

        ProgressIndicator sp = new ProgressIndicator();
        sp.setMaxSize(32, 32);
        VBox loadBox = new VBox(sp);
        loadBox.setAlignment(Pos.CENTER);
        loadBox.setPrefHeight(100);

        ScrollPane scroll = new ScrollPane(loadBox);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(380);
        scroll.getStyleClass().add("dlg-tabs-scroll");

        dlg.getDialogPane().setContent(new VBox(0, hdr, scroll));

        AppExecutor.submit(() -> {
            List<ConteoItem> items;
            try { items = new ConteoRepository().findItems(c.getId()); }
            catch (Exception ex) {
                log.error("No se pudo cargar el detalle del conteo {}", c.getId(), ex);
                javafx.application.Platform.runLater(() -> {
                    Label err = new Label("No se pudo cargar el detalle.");
                    err.getStyleClass().add("muted");
                    scroll.setContent(err);
                });
                return;
            }
            javafx.application.Platform.runLater(() -> {
                VBox list = new VBox(5);
                list.setPadding(new Insets(4));

                Label colHdr = new Label("BIEN                               SIST.   CONT.   DIFF   ESTADO    INCIDENCIA");
                colHdr.getStyleClass().add("nav-section-label");
                list.getChildren().add(colHdr);

                for (ConteoItem it : items) {
                    int diff = it.getStockContado() - it.getStockSistema();
                    String estadoItemCode = it.getEstadoConteo();
                    boolean tieneIncidencia = estadoItemCode != null && !estadoItemCode.equals("ENCONTRADO");

                    HBox mainRow = new HBox(10);
                    mainRow.getStyleClass().add("dlg-detail-header");
                    mainRow.setPadding(new Insets(6, 12, 6, 12));
                    mainRow.setAlignment(Pos.CENTER_LEFT);

                    VBox itInfo = new VBox(1);
                    Label nombre = new Label(it.getProductoNombre());
                    nombre.getStyleClass().add("dlg-detail-value");
                    Label area = new Label(it.getArea() != null ? it.getArea() : "Sin área");
                    area.getStyleClass().add("muted-sm");
                    itInfo.getChildren().addAll(nombre, area);
                    HBox.setHgrow(itInfo, Priority.ALWAYS);

                    Label lSist = new Label(String.valueOf(it.getStockSistema()));
                    lSist.setMinWidth(45);
                    Label lCont = new Label(String.valueOf(it.getStockContado()));
                    lCont.setMinWidth(45);
                    Label lDiff = new Label(diff == 0 ? "—" : (diff > 0 ? "+" + diff : String.valueOf(diff)));
                    lDiff.setMinWidth(40);
                    lDiff.getStyleClass().add(diff == 0 ? "dlg-stock-new-ok" : "dlg-stock-new-warn");

                    Label lStatus = new Label(diff == 0 ? "OK" : (it.isAjustado() ? "Ajustado" : "Pendiente"));
                    lStatus.getStyleClass().add(diff == 0 ? "cell-badge-success"
                        : it.isAjustado() ? "cell-badge-warning" : "cell-badge-danger");

                    Label lIncidencia = new Label(tieneIncidencia ? estadoItemLabel(estadoItemCode) : "—");
                    lIncidencia.getStyleClass().add(tieneIncidencia ? "text-warn" : "muted");
                    lIncidencia.setMinWidth(80);

                    mainRow.getChildren().addAll(itInfo, lSist, lCont, lDiff, lStatus, lIncidencia);

                    VBox rowWrapper = new VBox(0, mainRow);
                    if (it.getNota() != null && !it.getNota().isBlank()) {
                        Label lNota = new Label("📝 " + it.getNota());
                        lNota.getStyleClass().add("muted-sm");
                        lNota.setWrapText(true);
                        VBox.setMargin(lNota, new Insets(0, 12, 4, 60));
                        rowWrapper.getChildren().add(lNota);
                    }
                    list.getChildren().add(rowWrapper);
                }
                if (items.isEmpty()) {
                    Label empty = new Label("No hay items registrados para este conteo.");
                    empty.getStyleClass().add("muted");
                    list.getChildren().add(empty);
                }
                scroll.setContent(list);
                AnimationUtils.staggeredFadeInUp(list.getChildren(), 180, 30);
            });
        });

        dlg.showAndWait();
    }

    private static void exportPdf(ConteoFisico c, javafx.scene.Scene scene, Button btnExport) {
        btnExport.setDisable(true);
        btnExport.setText("…");
        AppExecutor.submit(() -> {
            try {
                List<ConteoItem> items = new ConteoRepository().findItems(c.getId());
                File pdf = new ReporteConteoService().exportarConteoPdf(c, items);
                javafx.application.Platform.runLater(() -> {
                    btnExport.setDisable(false);
                    btnExport.setText("PDF");
                    DialogUtil.showExportResultDialog(scene, pdf);
                });
            } catch (Exception ex) {
                log.error("No se pudo exportar el conteo {}", c.getId(), ex);
                javafx.application.Platform.runLater(() -> {
                    btnExport.setDisable(false);
                    btnExport.setText("PDF");
                    NotificacionUtil.error(scene, "No se pudo generar el PDF del conteo");
                });
            }
        });
    }
}
