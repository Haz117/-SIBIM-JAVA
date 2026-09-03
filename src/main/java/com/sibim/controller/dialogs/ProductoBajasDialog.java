package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.service.MovimientoService;
import com.sibim.service.ProductoService;
import com.sibim.service.ReporteService;
import com.sibim.util.AnimationUtils;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/** Dialog listing all bienes dados de baja, with search, detail on double-click,
 *  context menu, and per-row reactivation. */
public final class ProductoBajasDialog {

    private ProductoBajasDialog() {}

    private static final ReporteService reporteService = new ReporteService();

    public static void show(List<Producto> bajas, ProductoService productoService,
                            MovimientoService movimientoService, Logger log,
                            Runnable onReactivar) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(640);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2d-delete-circle-outline", "Bienes Dados de Baja",
            "Fuera del inventario activo — su historial se conserva",
            "#EF4444", "#B91C1C");

        // ── search bar ──────────────────────────────────────────────────────
        TextField searchField = new TextField();
        searchField.setPromptText("Buscar por nombre, código o área…");
        searchField.getStyleClass().add("search-field");
        FontIcon searchIcon = new FontIcon("mdi2m-magnify");
        searchIcon.getStyleClass().add("search-icon");
        HBox searchBar = new HBox(8, searchIcon, searchField);
        searchBar.setAlignment(Pos.CENTER_LEFT);
        searchBar.setPadding(new Insets(10, 14, 6, 14));
        HBox.setHgrow(searchField, Priority.ALWAYS);

        // ── list container ───────────────────────────────────────────────────
        VBox list = new VBox(8);
        list.setPadding(new Insets(4, 4, 4, 4));

        Label emptyLabel = new Label("No hay bienes dados de baja");
        emptyLabel.getStyleClass().add("muted");

        Label noMatchLabel = new Label("Ningún bien coincide con la búsqueda");
        noMatchLabel.getStyleClass().add("muted");
        noMatchLabel.setVisible(false);
        noMatchLabel.setManaged(false);

        // Build row nodes once; filter by toggling managed/visible
        List<HBox> rows = new ArrayList<>(bajas.size());
        for (Producto p : bajas) {
            HBox row = buildRow(p, productoService, movimientoService, log, list, dialog, onReactivar);
            rows.add(row);
            list.getChildren().add(row);
        }

        if (bajas.isEmpty()) {
            list.getChildren().add(emptyLabel);
        } else {
            list.getChildren().add(noMatchLabel);
        }

        // ── filter logic ─────────────────────────────────────────────────────
        searchField.textProperty().addListener((obs, old, query) -> {
            String q = query == null ? "" : query.strip().toLowerCase();
            int visible = 0;
            for (int i = 0; i < bajas.size(); i++) {
                Producto p  = bajas.get(i);
                HBox    row = rows.get(i);
                boolean match = q.isEmpty()
                    || p.getNombre().toLowerCase().contains(q)
                    || p.getCodigo().toLowerCase().contains(q)
                    || (p.getArea() != null && p.getArea().toLowerCase().contains(q));
                row.setVisible(match);
                row.setManaged(match);
                if (match) visible++;
            }
            noMatchLabel.setVisible(!q.isEmpty() && visible == 0);
            noMatchLabel.setManaged(!q.isEmpty() && visible == 0);
        });

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(360);
        scroll.getStyleClass().add("dlg-tabs-scroll");

        if (!rows.isEmpty())
            AnimationUtils.staggeredFadeInUp(new ArrayList<>(rows), 240, 40);

        // ── export bar ───────────────────────────────────────────────────────
        Label lblCount = new Label(bajas.size() + (bajas.size() == 1 ? " bien" : " bienes"));
        lblCount.getStyleClass().add("muted-sm");
        Button btnPdf = new Button("PDF");
        btnPdf.setGraphic(new FontIcon("mdi2f-file-pdf-box"));
        btnPdf.setContentDisplay(ContentDisplay.LEFT);
        btnPdf.getStyleClass().add("btn-secondary");
        Button btnExcel = new Button("Excel");
        btnExcel.setGraphic(new FontIcon("mdi2f-file-excel-outline"));
        btnExcel.setContentDisplay(ContentDisplay.LEFT);
        btnExcel.getStyleClass().add("btn-secondary");
        Button btnCsv = new Button("CSV");
        btnCsv.setGraphic(new FontIcon("mdi2f-file-delimited-outline"));
        btnCsv.setContentDisplay(ContentDisplay.LEFT);
        btnCsv.getStyleClass().add("btn-secondary");
        btnPdf.setDisable(bajas.isEmpty()); btnExcel.setDisable(bajas.isEmpty()); btnCsv.setDisable(bajas.isEmpty());
        btnPdf.setOnAction(e -> exportar(dialog, bajas, () -> reporteService.exportBajasPdf(bajas)));
        btnExcel.setOnAction(e -> exportar(dialog, bajas, () -> reporteService.exportBajasExcel(bajas)));
        btnCsv.setOnAction(e -> exportar(dialog, bajas, () -> reporteService.exportBajasCsv(bajas)));
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox exportBar = new HBox(8, lblCount, spacer, btnPdf, btnExcel, btnCsv);
        exportBar.setAlignment(Pos.CENTER_LEFT);
        exportBar.setPadding(new Insets(6, 14, 6, 14));

        dialog.getDialogPane().setContent(new VBox(0, header, searchBar, exportBar, scroll));
        javafx.application.Platform.runLater(searchField::requestFocus);
        dialog.showAndWait();
    }

    private static void exportar(Dialog<?> dialog, List<Producto> bajas,
                                  java.util.concurrent.Callable<java.io.File> task) {
        DialogUtil.runAsyncWithProgress(dialog.getDialogPane().getScene(), "Generando reporte…",
            task,
            file -> {
                if (file == null) {
                    NotificacionUtil.advertencia(dialog.getDialogPane().getScene(), "No hay bienes dados de baja para exportar");
                    return;
                }
                DialogUtil.showExportResultDialog(dialog.getDialogPane().getScene(), file);
            },
            e -> NotificacionUtil.error(dialog.getDialogPane().getScene(), "Error al generar el reporte")
        );
    }

    private static HBox buildRow(Producto p, ProductoService productoService,
                                 MovimientoService movimientoService, Logger log,
                                 VBox list, Dialog<?> dialog, Runnable onReactivar) {
        HBox row = new HBox(12);
        row.getStyleClass().add("dlg-detail-header");
        row.setPadding(new Insets(10, 14, 10, 14));
        row.setAlignment(Pos.CENTER_LEFT);

        VBox info = new VBox(2);
        Label nombre = new Label(p.getNombre() + "  [" + p.getCodigo() + "]");
        nombre.getStyleClass().add("dlg-detail-name");
        Label detalle = new Label(p.getArea() + " · baja: " + FormatUtils.formatDate(p.getFechaBaja())
            + (p.getMotivoBaja() != null && !p.getMotivoBaja().isBlank() ? " · " + p.getMotivoBaja() : ""));
        detalle.getStyleClass().add("muted-sm");
        detalle.setWrapText(true);
        info.getChildren().addAll(nombre, detalle);
        HBox.setHgrow(info, Priority.ALWAYS);

        Button btnReactivar = new Button("Reactivar");
        btnReactivar.setGraphic(new FontIcon("mdi2r-restore"));
        btnReactivar.setContentDisplay(ContentDisplay.LEFT);
        btnReactivar.getStyleClass().add("btn-secondary");
        btnReactivar.setOnAction(e -> doReactivar(p, row, list, dialog, productoService, onReactivar));

        row.getChildren().addAll(info, btnReactivar);

        // double-click → detail
        row.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                ProductoDetailDialog.show(p, dialog.getDialogPane().getScene(), movimientoService, log);
                e.consume();
            }
        });

        // context menu
        MenuItem menuDetalle    = new MenuItem("Ver detalle");
        menuDetalle.setGraphic(new FontIcon("mdi2i-information-outline"));
        menuDetalle.setOnAction(e ->
            ProductoDetailDialog.show(p, dialog.getDialogPane().getScene(), movimientoService, log));

        MenuItem menuReactivar  = new MenuItem("Reactivar");
        menuReactivar.setGraphic(new FontIcon("mdi2r-restore"));
        menuReactivar.setOnAction(e -> doReactivar(p, row, list, dialog, productoService, onReactivar));

        ContextMenu ctx = new ContextMenu(menuDetalle, new SeparatorMenuItem(), menuReactivar);
        row.setOnContextMenuRequested(e -> ctx.show(row, e.getScreenX(), e.getScreenY()));

        return row;
    }

    private static void doReactivar(Producto p, HBox row, VBox list, Dialog<?> dialog,
                                    ProductoService productoService, Runnable onReactivar) {
        DialogUtil.runAsync(
            () -> productoService.reactivar(p.getId()),
            () -> {
                AnimationUtils.fadeOut(row, 200, () -> list.getChildren().remove(row));
                onReactivar.run();
                NotificacionUtil.exito(dialog.getDialogPane().getScene(),
                    "Bien \"" + p.getNombre() + "\" reactivado");
            },
            e -> NotificacionUtil.error(dialog.getDialogPane().getScene(), "No se pudo reactivar el bien")
        );
    }
}
