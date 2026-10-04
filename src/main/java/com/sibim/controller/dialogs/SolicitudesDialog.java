package com.sibim.controller.dialogs;

import com.sibim.config.Areas;
import com.sibim.model.Producto;
import com.sibim.model.Solicitud;
import com.sibim.service.SolicitudService;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDate;
import java.util.List;

/**
 * Requests for préstamos and resguardos. The administrator gets the inbox
 * (approve / reject); an área gets what it asked for with Patrimonio's answer.
 * The request form opens from Bienes, on the selected bien.
 */
public final class SolicitudesDialog {

    private SolicitudesDialog() {}

    private static final SolicitudService SERVICE = new SolicitudService();

    /** Loads and shows the list that fits the caller's role. */
    public static void show(Scene scene, Runnable onCambio) {
        if (!SolicitudService.disponible()) {
            NotificacionUtil.advertencia(scene, "Las solicitudes requieren conexión con el servidor");
            return;
        }
        boolean admin = SessionManager.isAdmin();
        DialogUtil.runAsync(
            () -> admin ? SERVICE.pendientes() : SERVICE.deMisAreas(),
            lista -> mostrarLista(lista, admin, onCambio),
            e -> NotificacionUtil.error(scene, "No se pudieron cargar las solicitudes"));
    }

    private static void mostrarLista(List<Solicitud> solicitudes, boolean admin, Runnable onCambio) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(720);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2t-timer-sand",
            admin ? "Solicitudes por atender" : "Mis solicitudes",
            admin ? "Préstamos y resguardos que piden las áreas: al aprobar se crea el documento"
                  : "Lo que tu área ha pedido a Patrimonio. Pide uno nuevo desde Bienes: clic derecho sobre el bien",
            AppColors.PRIMARY_D, AppColors.INDIGO);

        VBox list = new VBox(6);
        list.setPadding(new Insets(4));
        if (solicitudes.isEmpty()) {
            Label empty = new Label(admin ? "No hay solicitudes por atender" : "Tu área no ha enviado solicitudes");
            empty.getStyleClass().add("muted");
            list.getChildren().add(empty);
        }
        for (Solicitud s : solicitudes) list.getChildren().add(fila(s, admin, list, dialog, onCambio));

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(420);
        scroll.getStyleClass().add("dlg-tabs-scroll");

        AnimationUtils.staggeredFadeInUp(List.of(header, scroll), 260, 70);
        dialog.getDialogPane().setContent(new VBox(0, header, scroll));
        dialog.showAndWait();
    }

    private static HBox fila(Solicitud s, boolean admin, VBox list, Dialog<ButtonType> dialog, Runnable onCambio) {
        HBox row = new HBox(12);
        row.getStyleClass().add("dlg-detail-header");
        row.setPadding(new Insets(10, 14, 10, 14));
        row.setAlignment(Pos.CENTER_LEFT);

        Label titulo = new Label(s.tipoTexto() + " · " + s.productoNombre()
            + (s.productoCodigo() != null ? "  [" + s.productoCodigo() + "]" : ""));
        titulo.getStyleClass().add("dlg-detail-value");
        titulo.setWrapText(true);

        String detalleTxt = s.area()
            + (s.esPrestamo() ? " → " + s.areaDestino() : "")
            + " · " + (s.esPrestamo() ? "Responsable: " : "Resguardante: ") + s.responsable()
            + (s.cargo() != null ? " (" + s.cargo() + ")" : "")
            + (s.fechaDevolucion() != null ? " · Devolución: " + FormatUtils.formatDate(s.fechaDevolucion()) : "")
            + (s.motivo() != null ? " · " + s.motivo() : "")
            + "\nPedido por " + (s.solicitante() != null ? s.solicitante() : "—")
            + " el " + FormatUtils.formatDateTime(s.creadoEn());
        Label detalle = new Label(detalleTxt);
        detalle.getStyleClass().add("muted-sm");
        detalle.setWrapText(true);

        VBox info = new VBox(3, titulo, detalle);
        HBox.setHgrow(info, Priority.ALWAYS);
        row.getChildren().add(info);

        if (!admin) {
            Label estado = new Label(s.isPendiente() ? "En espera"
                : Solicitud.ESTADO_APROBADA.equals(s.estado()) ? "Aprobada" : "Rechazada");
            estado.getStyleClass().addAll("cell-badge", s.isPendiente() ? "cell-badge-warning"
                : Solicitud.ESTADO_APROBADA.equals(s.estado()) ? "cell-badge-success" : "cell-badge-danger");
            estado.setMinWidth(Region.USE_PREF_SIZE);
            row.getChildren().add(estado);
            String respuesta = Solicitud.ESTADO_APROBADA.equals(s.estado()) && s.documento() != null
                ? "Documento: " + s.documento()
                : s.respuesta() != null ? "Motivo: " + s.respuesta() : null;
            if (respuesta != null) {
                Label r = new Label(respuesta);
                r.getStyleClass().add("muted-sm");
                r.setWrapText(true);
                info.getChildren().add(r);
            }
            return row;
        }

        Button btnAprobar  = new Button("Aprobar");
        Button btnRechazar = new Button("Rechazar");
        btnAprobar.setGraphic(new FontIcon("mdi2c-check-circle-outline"));
        btnRechazar.setGraphic(new FontIcon("mdi2c-close-circle-outline"));
        btnAprobar.getStyleClass().add("btn-primary");
        btnRechazar.getStyleClass().add("btn-danger");
        btnAprobar.setMinWidth(Region.USE_PREF_SIZE);
        btnRechazar.setMinWidth(Region.USE_PREF_SIZE);

        btnAprobar.setOnAction(e -> {
            btnAprobar.setDisable(true); btnRechazar.setDisable(true);
            DialogUtil.runAsync(
                () -> SERVICE.aprobar(s.id()),
                folio -> {
                    list.getChildren().remove(row);
                    if (onCambio != null) onCambio.run();
                    NotificacionUtil.exito(dialog.getDialogPane().getScene(),
                        s.tipoTexto() + " " + folio + " creado: imprímelo desde su sección");
                },
                ex -> {
                    btnAprobar.setDisable(false); btnRechazar.setDisable(false);
                    NotificacionUtil.error(dialog.getDialogPane().getScene(), "No se pudo aprobar: " + causa(ex));
                });
        });

        btnRechazar.setOnAction(e -> {
            TextInputDialog motivo = new TextInputDialog();
            motivo.setTitle("Rechazar solicitud");
            motivo.setHeaderText("¿Por qué se rechaza? El área verá este motivo.");
            motivo.setContentText("Motivo:");
            DialogUtil.applyOwner(motivo);
            DialogUtil.conEncabezado(motivo, "mdi2c-close-circle-outline");
            String texto = motivo.showAndWait().map(String::trim).orElse(null);
            if (texto == null) return;
            btnAprobar.setDisable(true); btnRechazar.setDisable(true);
            DialogUtil.runAsync(
                () -> { SERVICE.rechazar(s.id(), texto); return null; },
                ok -> {
                    list.getChildren().remove(row);
                    if (onCambio != null) onCambio.run();
                    NotificacionUtil.info(dialog.getDialogPane().getScene(), "Solicitud rechazada");
                },
                ex -> {
                    btnAprobar.setDisable(false); btnRechazar.setDisable(false);
                    NotificacionUtil.error(dialog.getDialogPane().getScene(), "No se pudo rechazar: " + causa(ex));
                });
        });

        HBox acciones = new HBox(8, btnAprobar, btnRechazar);
        acciones.setAlignment(Pos.CENTER_RIGHT);
        row.getChildren().add(acciones);
        return row;
    }

    private static String causa(Exception e) {
        return e instanceof IllegalArgumentException || e instanceof IllegalStateException
            || e instanceof SecurityException ? e.getMessage() : "revisa la conexión e inténtalo de nuevo";
    }
}
