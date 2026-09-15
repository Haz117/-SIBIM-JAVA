package com.sibim.controller.dialogs;

import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.service.MovimientoService;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppExecutor;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MovimientoDetailDialog {

    private static final Logger log = LoggerFactory.getLogger(MovimientoDetailDialog.class);

    private MovimientoDetailDialog() {}

    public static void show(Movimiento m, Scene scene,
                            MovimientoService movimientoService,
                            Runnable onReload) {
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

        HBox stockRow = new HBox(10);
        stockRow.setAlignment(Pos.CENTER_LEFT);
        Label antes = new Label(String.valueOf(m.getStockAnterior()));
        antes.getStyleClass().add("dlg-stock-val");
        Label arrowLbl = new Label("→");
        boolean up   = m.getStockNuevo() > m.getStockAnterior();
        boolean down = m.getStockNuevo() < m.getStockAnterior();
        arrowLbl.getStyleClass().add(up ? "dlg-stock-arrow-up" : down ? "dlg-stock-arrow-down" : "dlg-stock-arrow");
        Label despues = new Label(String.valueOf(m.getStockNuevo()));
        despues.getStyleClass().add(m.getStockNuevo() <= 0 ? "dlg-stock-new-empty"
            : up ? "dlg-stock-new-ok" : "dlg-stock-new-warn");
        stockRow.getChildren().addAll(antes, arrowLbl, despues);

        Label fProducto = new Label(m.getProductoNombre());
        fProducto.setWrapText(true);
        Label fMotivo  = new Label(m.getMotivo()     != null && !m.getMotivo().isBlank()     ? m.getMotivo()     : "—");
        Label fRef     = new Label(m.getReferencia() != null && !m.getReferencia().isBlank() ? m.getReferencia() : "—");
        Label fUsuario = new Label(m.getUsuarioNombre());
        Label fFecha   = new Label(FormatUtils.formatDateTime(m.getCreadoEn()));

        for (Label l : new Label[]{fProducto, fMotivo, fRef, fUsuario, fFecha})
            l.getStyleClass().add("dlg-detail-value");

        grid.add(DialogUtil.fieldLabel("Bien"),           0, r); grid.add(fProducto, 1, r++);
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

        Button btnHistorial = new Button("Ver historial del bien");
        btnHistorial.getStyleClass().add("btn-secondary");
        btnHistorial.setGraphic(new FontIcon("mdi2h-history"));
        btnHistorial.setContentDisplay(ContentDisplay.LEFT);
        btnHistorial.setGraphicTextGap(8);
        btnHistorial.setOnAction(e -> {
            dialog.close();
            Producto stub = new Producto();
            stub.setId(m.getProductoId());
            stub.setNombre(m.getProductoNombre());
            MovimientoTimelineDialog.show(stub, scene, movimientoService);
        });

        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        HBox footer = new HBox(8, btnHistorial, footerSpacer);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(12, 16, 4, 16));

        boolean canRevert = (SessionManager.isAdmin() || SessionManager.isSecretario())
            && m.getTipo() != TipoMovimiento.TRANSFERENCIA
            && !"RECHAZADO".equals(m.getEstado());
        if (canRevert) {
            String tipoInverso = m.getTipo() == TipoMovimiento.ENTRADA ? "salida compensatoria"
                : m.getTipo() == TipoMovimiento.SALIDA ? "entrada compensatoria" : "ajuste de reversión";
            Button btnRevertir = new Button("Revertir");
            btnRevertir.getStyleClass().add("btn-danger");
            btnRevertir.setGraphic(new FontIcon("mdi2u-undo-variant"));
            btnRevertir.setContentDisplay(ContentDisplay.LEFT);
            btnRevertir.setGraphicTextGap(8);
            btnRevertir.setOnAction(ev -> {
                TextInputDialog reasonDlg = new TextInputDialog();
                reasonDlg.setTitle("Revertir movimiento");
                reasonDlg.setHeaderText("Motivo de la reversión (opcional):");
                reasonDlg.setContentText("Razón:");
                DialogUtil.applyOwner(reasonDlg);
                DialogUtil.applyStylesheet(reasonDlg.getDialogPane());
                reasonDlg.showAndWait().ifPresent(razon -> {
                    if (!ConfirmacionUtil.confirmar("Confirmar reversión",
                            "Se creará una " + tipoInverso + " de " + m.getCantidad()
                            + " uds para \"" + m.getProductoNombre() + "\".\n"
                            + "Este movimiento no se elimina — quedará como comprobante en el historial.\n\n"
                            + "¿Continuar?")) return;
                    dialog.close();
                    AppExecutor.submit(() -> {
                        try {
                            movimientoService.revertirMovimiento(m, razon.trim());
                            Platform.runLater(() -> {
                                NotificacionUtil.exito(scene, "Movimiento revertido — se registró " + tipoInverso);
                                onReload.run();
                            });
                        } catch (MovimientoService.ValidationException ex) {
                            Platform.runLater(() -> NotificacionUtil.advertencia(scene, ex.getMessage()));
                        } catch (Exception ex) {
                            log.error("Error al revertir movimiento {}", m.getId(), ex);
                            Platform.runLater(() -> NotificacionUtil.error(scene, "No se pudo revertir el movimiento"));
                        }
                    });
                });
            });
            footer.getChildren().add(btnRevertir);
        }

        VBox content = new VBox(0, header, grid, footer);
        AnimationUtils.staggeredFadeInUp(java.util.List.of(header, grid, footer), 260, 70);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }
}
