package com.sibim.controller;

import com.sibim.model.Producto;
import com.sibim.service.MovimientoService;
import com.sibim.service.ReporteService;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableView;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;

import java.util.List;
import java.util.function.Consumer;

/**
 * Builds the right-click context menu for the Bienes table — kept out of
 * {@link ProductosController} since it's pure wiring with no state of its
 * own beyond what's passed in.
 */
final class ProductosContextMenu {

    private ProductosContextMenu() {}

    static ContextMenu build(
            TableView<Producto> table,
            boolean canEdit,
            ReporteService reporteService,
            MovimientoService movimientoService,
            Logger log,
            Consumer<Producto> showDetail,
            Consumer<Producto> showTimeline,
            Consumer<List<Producto>> exportEtiquetasQr,
            Consumer<List<Producto>> exportEtiquetaFisica,
            Runnable onEdit,
            Runnable onDelete) {

        ContextMenu cm = new ContextMenu();

        MenuItem cmDetalle = new MenuItem("Ver detalle");
        cmDetalle.setGraphic(new FontIcon("mdi2e-eye-outline"));
        cmDetalle.setOnAction(e -> {
            Producto sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) showDetail.accept(sel);
        });
        cm.getItems().add(cmDetalle);

        MenuItem cmFicha = new MenuItem("Imprimir ficha técnica");
        cmFicha.setGraphic(new FontIcon("mdi2f-file-document-outline"));
        cmFicha.setOnAction(e -> {
            Producto sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) {
                DialogUtil.runAsyncWithProgress(table.getScene(), "Generando ficha…",
                    () -> reporteService.exportFichaTecnica(sel, movimientoService.getByProducto(sel.getId())),
                    file -> DialogUtil.showExportResultDialog(table.getScene(), file),
                    ex -> { log.error("Error ficha técnica", ex); NotificacionUtil.error(table.getScene(), "No se pudo generar la ficha técnica"); });
            }
        });

        MenuItem cmSolicitudBaja = new MenuItem("Solicitud de baja (PDF)");
        cmSolicitudBaja.setGraphic(new FontIcon("mdi2f-file-remove-outline"));
        cmSolicitudBaja.setOnAction(e -> {
            Producto sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) {
                DialogUtil.runAsyncWithProgress(table.getScene(), "Generando solicitud de baja…",
                    () -> reporteService.exportSolicitudBaja(List.of(sel)),
                    file -> DialogUtil.showExportResultDialog(table.getScene(), file),
                    ex -> { log.error("Error solicitud de baja", ex); NotificacionUtil.error(table.getScene(), "No se pudo generar la solicitud de baja"); });
            }
        });

        MenuItem cmDictamenBaja = new MenuItem("Dictamen técnico de baja (PDF)");
        cmDictamenBaja.setGraphic(new FontIcon("mdi2f-file-certificate-outline"));
        cmDictamenBaja.setOnAction(e -> {
            Producto sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) {
                DialogUtil.runAsyncWithProgress(table.getScene(), "Generando dictamen técnico de baja…",
                    () -> reporteService.exportDictamenBaja(List.of(sel)),
                    file -> DialogUtil.showExportResultDialog(table.getScene(), file),
                    ex -> { log.error("Error dictamen de baja", ex); NotificacionUtil.error(table.getScene(), "No se pudo generar el dictamen técnico de baja"); });
            }
        });

        MenuItem cmHistorial = new MenuItem("Ver historial de movimientos");
        cmHistorial.setGraphic(new FontIcon("mdi2h-history"));
        cmHistorial.setOnAction(e -> {
            Producto sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) showTimeline.accept(sel);
        });

        MenuItem cmEtiquetaQr = new MenuItem("Imprimir etiqueta QR");
        cmEtiquetaQr.setGraphic(new FontIcon("mdi2q-qrcode"));
        cmEtiquetaQr.setOnAction(e -> {
            Producto sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) exportEtiquetasQr.accept(List.of(sel));
        });

        MenuItem cmEtiquetaFisica = new MenuItem("Imprimir etiqueta física");
        cmEtiquetaFisica.setGraphic(new FontIcon("mdi2t-tag-outline"));
        cmEtiquetaFisica.setOnAction(e -> {
            Producto sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) exportEtiquetaFisica.accept(List.of(sel));
        });

        cm.getItems().add(new SeparatorMenuItem());
        cm.getItems().addAll(cmFicha, cmSolicitudBaja, cmDictamenBaja, cmHistorial, cmEtiquetaQr, cmEtiquetaFisica);

        // Areas ask Patrimonio for a préstamo or a resguardo of their own bien.
        if (!com.sibim.session.SessionManager.isAdmin() && com.sibim.service.SolicitudService.disponible()) {
            MenuItem cmPedirPrestamo = new MenuItem("Solicitar préstamo…");
            cmPedirPrestamo.setGraphic(new FontIcon("mdi2c-cube-send"));
            cmPedirPrestamo.setOnAction(e -> {
                Producto sel = table.getSelectionModel().getSelectedItem();
                if (sel != null) com.sibim.controller.dialogs.SolicitudesDialog.solicitar(
                    com.sibim.model.Solicitud.TIPO_PRESTAMO, sel, table.getScene());
            });
            MenuItem cmPedirResguardo = new MenuItem("Solicitar resguardo…");
            cmPedirResguardo.setGraphic(new FontIcon("mdi2b-badge-account-outline"));
            cmPedirResguardo.setOnAction(e -> {
                Producto sel = table.getSelectionModel().getSelectedItem();
                if (sel != null) com.sibim.controller.dialogs.SolicitudesDialog.solicitar(
                    com.sibim.model.Solicitud.TIPO_RESGUARDO, sel, table.getScene());
            });
            cm.getItems().add(new SeparatorMenuItem());
            cm.getItems().addAll(cmPedirPrestamo, cmPedirResguardo);
        }

        if (canEdit) {
            cm.getItems().add(new SeparatorMenuItem());
            MenuItem cmEditar = new MenuItem("Editar");
            cmEditar.setGraphic(new FontIcon("mdi2p-pencil"));
            MenuItem cmEliminar = new MenuItem("Dar de baja");
            cmEliminar.setGraphic(new FontIcon("mdi2d-delete-outline"));
            cmEditar.setOnAction(e -> onEdit.run());
            cmEliminar.setOnAction(e -> onDelete.run());
            cm.getItems().addAll(cmEditar, cmEliminar);
        }

        return cm;
    }
}
