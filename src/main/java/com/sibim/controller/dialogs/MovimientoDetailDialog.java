package com.sibim.controller.dialogs;

import com.sibim.model.Movimiento;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.service.ProductoService;
import com.sibim.util.AnimationUtils;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;

/** Read-only detail dialog for a {@link Movimiento}.
 *  Extracted from MovimientosController to keep it under 700 lines. */
public final class MovimientoDetailDialog {

    private MovimientoDetailDialog() {}

    public static void show(Movimiento m, javafx.scene.Scene scene,
                            ProductoService productoService,
                            com.sibim.service.MovimientoService movimientoService,
                            Logger log) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(470);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        String tipoIcon = switch (m.getTipo()) {
            case ENTRADA       -> "mdi2a-arrow-up-bold-circle-outline";
            case SALIDA        -> "mdi2a-arrow-down-bold-circle-outline";
            case AJUSTE        -> "mdi2s-swap-horizontal";
            case TRANSFERENCIA -> "mdi2a-arrow-right-bold-circle-outline";
        };
        String color1 = switch (m.getTipo()) {
            case ENTRADA       -> "#059669";
            case SALIDA        -> "#DC2626";
            case AJUSTE        -> "#D97706";
            case TRANSFERENCIA -> "#2563EB";
        };
        String color2 = switch (m.getTipo()) {
            case ENTRADA       -> "#047857";
            case SALIDA        -> "#B91C1C";
            case AJUSTE        -> "#B45309";
            case TRANSFERENCIA -> "#1D4ED8";
        };

        HBox header = DialogUtil.gradientHeader(tipoIcon,
            m.getTipo().getEtiqueta() + "  —  " + m.getCantidad() + " uds.",
            m.getProductoNombre(),
            color1, color2);

        GridPane grid = DialogUtil.formGrid(120);
        int r = 0;

        // Stock change row
        HBox stockRow = new HBox(10);
        stockRow.setAlignment(Pos.CENTER_LEFT);
        Label antes = new Label(String.valueOf(m.getStockAnterior()));
        antes.getStyleClass().add("dlg-stock-val");
        Label arrowLbl = new Label("→");
        boolean up = m.getStockNuevo() > m.getStockAnterior();
        boolean down = m.getStockNuevo() < m.getStockAnterior();
        arrowLbl.getStyleClass().add(up ? "dlg-stock-arrow-up" : down ? "dlg-stock-arrow-down" : "dlg-stock-arrow");
        Label despues = new Label(String.valueOf(m.getStockNuevo()));
        despues.getStyleClass().add(m.getStockNuevo() <= 0 ? "dlg-stock-new-empty"
            : up ? "dlg-stock-new-ok" : "dlg-stock-new-warn");
        stockRow.getChildren().addAll(antes, arrowLbl, despues);

        Label fProducto = new Label(m.getProductoNombre());
        fProducto.setWrapText(true);
        Label fMotivo    = new Label(m.getMotivo()     != null && !m.getMotivo().isBlank()     ? m.getMotivo()     : "—");
        Label fRef       = new Label(m.getReferencia() != null && !m.getReferencia().isBlank() ? m.getReferencia() : "—");
        Label fUsuario   = new Label(m.getUsuarioNombre());
        Label fFecha     = new Label(FormatUtils.formatDateTime(m.getCreadoEn()));

        for (Label l : new Label[]{fProducto, fMotivo, fRef, fUsuario, fFecha})
            l.getStyleClass().add("dlg-detail-value");

        Hyperlink linkVerBien = new Hyperlink("Ver ficha →");
        linkVerBien.getStyleClass().add("muted-sm");
        if (m.getProductoId() != null) {
            linkVerBien.setOnAction(ev -> {
                dialog.close();
                DialogUtil.runAsyncWithProgress(scene, "Cargando bien…",
                    () -> productoService.findById(m.getProductoId()),
                    opt -> opt.ifPresent(p -> ProductoDetailDialog.show(p, scene, movimientoService, log)),
                    ex -> { log.error("Error cargando bien desde movimiento detail", ex); NotificacionUtil.error(scene, "No se pudo cargar el bien"); });
            });
        } else {
            linkVerBien.setDisable(true);
        }
        HBox bienRow = new HBox(10, fProducto, linkVerBien);
        bienRow.setAlignment(Pos.CENTER_LEFT);
        grid.add(DialogUtil.fieldLabel("Bien"),           0, r); grid.add(bienRow,    1, r++);
        grid.add(DialogUtil.fieldLabel("Stock"),          0, r); grid.add(stockRow,  1, r++);
        if (m.getTipo() == TipoMovimiento.TRANSFERENCIA && m.getAreaDestino() != null) {
            HBox areaRow = new HBox(8);
            areaRow.setAlignment(Pos.CENTER_LEFT);
            Label areaOrigenLbl = new Label(m.getAreaOrigen() != null ? m.getAreaOrigen() : "—");
            areaOrigenLbl.getStyleClass().add("dlg-detail-value");
            Label areaArrow = new Label("→");
            areaArrow.getStyleClass().add("dlg-stock-arrow");
            Label areaDestinoLbl = new Label(m.getAreaDestino());
            areaDestinoLbl.getStyleClass().add("dlg-detail-value");
            areaRow.getChildren().addAll(areaOrigenLbl, areaArrow, areaDestinoLbl);
            grid.add(DialogUtil.fieldLabel("Área"), 0, r); grid.add(areaRow, 1, r++);
        }
        grid.add(DialogUtil.fieldLabel("Motivo"),         0, r); grid.add(fMotivo,   1, r++);
        grid.add(DialogUtil.fieldLabel("Referencia"),     0, r); grid.add(fRef,      1, r++);
        grid.add(DialogUtil.fieldLabel("Registrado por"), 0, r); grid.add(fUsuario,  1, r++);
        grid.add(DialogUtil.fieldLabel("Fecha"),          0, r); grid.add(fFecha,    1, r);

        VBox content = new VBox(0, header, grid);
        AnimationUtils.staggeredFadeInUp(java.util.List.of(header, grid), 260, 70);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }
}
