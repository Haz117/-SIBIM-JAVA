package com.sibim.controller.dialogs;

import com.sibim.model.Solicitud;
import com.sibim.service.MovimientoService;
import com.sibim.service.ProductoService;
import com.sibim.service.SolicitudService;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * Requests the áreas send to Patrimonio. The administrator gets the inbox; an
 * área gets what it asked for with Patrimonio's answer.
 * <ul>
 *   <li>Préstamos and resguardos (from before the áreas stopped sending them):
 *       approve — the document is created — or reject.</li>
 *   <li>Bajas (5-nov-2026): look at the bien, then "Dar de baja" — the usual
 *       baja form, with the área's motivo written — or "No procede", with the
 *       reason the área sees. The área hands in the signed formato at Recursos
 *       Materiales y Patrimonio: "Recibí el formato" notes its folio, which
 *       the baja form then brings; both sides see whether it was handed in.</li>
 * </ul>
 */
public final class SolicitudesDialog {

    private SolicitudesDialog() {}

    private static final Logger log = LoggerFactory.getLogger(SolicitudesDialog.class);
    private static final SolicitudService SERVICE = new SolicitudService();

    /** What the Préstamos and Resguardos screens show. */
    public static final Set<String> PRESTAMOS_Y_RESGUARDOS = Set.of(Solicitud.TIPO_PRESTAMO, Solicitud.TIPO_RESGUARDO);
    /** What the Bajas screen and the bell show. */
    public static final Set<String> BAJAS = Set.of(Solicitud.TIPO_BAJA);

    /** Préstamos and resguardos, as the list that fits the caller's role. */
    public static void show(Scene scene, Runnable onCambio) {
        show(scene, PRESTAMOS_Y_RESGUARDOS, onCambio);
    }

    /** Loads and shows the requests of these kinds, as the list that fits the caller's role. */
    public static void show(Scene scene, Set<String> tipos, Runnable onCambio) {
        if (!SolicitudService.disponible()) {
            NotificacionUtil.advertencia(scene, "Las solicitudes requieren conexión con el servidor");
            return;
        }
        boolean admin = SessionManager.isAdmin();
        DialogUtil.runAsync(
            () -> (admin ? SERVICE.pendientes() : SERVICE.deMisAreas()).stream()
                .filter(s -> tipos.contains(s.tipo())).toList(),
            lista -> mostrarLista(lista, admin, tipos.equals(BAJAS), onCambio),
            e -> NotificacionUtil.error(scene, "No se pudieron cargar las solicitudes"));
    }

    private static void mostrarLista(List<Solicitud> solicitudes, boolean admin, boolean bajas, Runnable onCambio) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(760);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        String titulo, subtitulo, vacio;
        if (bajas) {
            titulo = admin ? "Solicitudes de baja" : "Mis solicitudes de baja";
            subtitulo = admin ? "Las áreas piden dar de baja estos bienes: revisa cada uno y decide. El formato firmado llega en papel"
                              : "Lo que tu área pidió dar de baja y la respuesta de Patrimonio. Pide otra desde Bienes › Solicitar baja";
            vacio = admin ? "No hay solicitudes de baja por revisar" : "Tu área no ha pedido bajas";
        } else {
            titulo = admin ? "Solicitudes por atender" : "Mis solicitudes";
            subtitulo = admin ? "Préstamos y resguardos que piden las áreas: al aprobar se crea el documento"
                              : "Lo que tu área ha pedido a Patrimonio";
            vacio = admin ? "No hay solicitudes por atender" : "Tu área no ha enviado solicitudes";
        }
        HBox header = DialogUtil.gradientHeader(bajas ? "mdi2f-file-remove-outline" : "mdi2t-timer-sand",
            titulo, subtitulo, AppColors.PRIMARY_D, AppColors.INDIGO);

        VBox list = new VBox(6);
        list.setPadding(new Insets(4));
        if (solicitudes.isEmpty()) {
            Label empty = new Label(vacio);
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

    /** "Dado de baja" / "No procede" for a baja; "Aprobada" / "Rechazada" otherwise. */
    static String estadoTexto(Solicitud s) {
        if (s.faltaFormato()) return "Falta el formato";
        if (s.isPendiente()) return "En espera";
        boolean aprobada = Solicitud.ESTADO_APROBADA.equals(s.estado());
        if (s.esBaja()) return aprobada ? "Dado de baja" : "No procede";
        return aprobada ? "Aprobada" : "Rechazada";
    }

    /** The second line of a row: who has it, why, who asked and when. */
    static String detalleTexto(Solicitud s) {
        String que = s.esBaja()
            ? s.area() + (s.motivo() != null ? " · Motivo: " + s.motivo() : "")
            : s.area()
                + (s.esPrestamo() ? " → " + s.areaDestino() : "")
                + " · " + (s.esPrestamo() ? "Responsable: " : "Resguardante: ") + s.responsable()
                + (s.cargo() != null ? " (" + s.cargo() + ")" : "")
                + (s.fechaDevolucion() != null ? " · Devolución: " + FormatUtils.formatDate(s.fechaDevolucion()) : "")
                + (s.motivo() != null ? " · " + s.motivo() : "");
        return que + "\nPedido por " + (s.solicitante() != null ? s.solicitante() : "—")
            + " el " + FormatUtils.formatDateTime(s.creadoEn());
    }

    /** Whether the signed formato of a baja was handed in; null when it doesn't apply. */
    static String formatoTexto(Solicitud s) {
        if (!s.esBaja()) return null;
        if (s.formatoRecibidoEn() != null)
            return "Formato firmado recibido el " + FormatUtils.formatDate(s.formatoRecibidoEn().toLocalDate())
                + (s.formatoFolio() != null ? " · folio " + s.formatoFolio() : "");
        return s.isPendiente() ? "Falta entregar el formato firmado en " + Solicitud.ENTREGAR_FORMATO_EN : null;
    }

    private static Label lineaFormato(Solicitud s) {
        String texto = formatoTexto(s);
        if (texto == null) return null;
        Label l = new Label(texto);
        l.getStyleClass().add("muted-sm");
        l.setWrapText(true);
        FontIcon icono = new FontIcon(s.formatoRecibidoEn() != null ? "mdi2f-file-check-outline" : "mdi2f-file-alert-outline");
        if (s.formatoRecibidoEn() == null) icono.getStyleClass().add("notif-item-icon-warning");
        l.setGraphic(icono);
        return l;
    }

    private static HBox fila(Solicitud s, boolean admin, VBox list, Dialog<ButtonType> dialog, Runnable onCambio) {
        HBox row = new HBox(12);
        row.getStyleClass().add("dlg-detail-header");
        row.setPadding(new Insets(10, 14, 10, 14));
        row.setAlignment(Pos.CENTER_LEFT);

        Label titulo = new Label((s.esBaja() ? "" : s.tipoTexto() + " · ") + s.productoNombre()
            + (s.productoCodigo() != null ? "  [" + s.productoCodigo() + "]" : ""));
        titulo.getStyleClass().add("dlg-detail-value");
        titulo.setWrapText(true);

        Label detalle = new Label(detalleTexto(s));
        detalle.getStyleClass().add("muted-sm");
        detalle.setWrapText(true);

        VBox info = new VBox(3, titulo, detalle);
        HBox.setHgrow(info, Priority.ALWAYS);
        row.getChildren().add(info);
        Label formato = lineaFormato(s);
        if (formato != null) info.getChildren().add(formato);

        if (!admin) {
            boolean aprobada = Solicitud.ESTADO_APROBADA.equals(s.estado());
            Label estado = new Label(estadoTexto(s));
            estado.getStyleClass().addAll("cell-badge", s.isPendiente() ? "cell-badge-warning"
                : aprobada ? "cell-badge-success" : "cell-badge-danger");
            estado.setMinWidth(Region.USE_PREF_SIZE);
            row.getChildren().add(estado);
            String respuesta = aprobada && s.documento() != null
                ? (s.esBaja() ? "" : "Documento: ") + s.documento()
                : s.respuesta() != null ? "Patrimonio: " + s.respuesta() : null;
            if (respuesta != null) {
                Label r = new Label(respuesta);
                r.getStyleClass().add("muted-sm");
                r.setWrapText(true);
                info.getChildren().add(r);
            }
            return row;
        }

        HBox acciones = s.esBaja() ? accionesBaja(s, row, formato, list, dialog, onCambio)
                                   : accionesPrestamoOResguardo(s, row, list, dialog, onCambio);
        acciones.setAlignment(Pos.CENTER_RIGHT);
        // A baja's three buttons go under its text: beside it, the motivo was left a narrow column.
        if (s.esBaja()) {
            VBox.setMargin(acciones, new Insets(6, 0, 0, 0));
            info.getChildren().add(acciones);
        } else {
            row.getChildren().add(acciones);
        }
        return row;
    }

    private static HBox accionesPrestamoOResguardo(Solicitud s, HBox row, VBox list, Dialog<ButtonType> dialog,
                                                    Runnable onCambio) {
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
        btnRechazar.setOnAction(e -> rechazar(s, "Rechazar solicitud", "¿Por qué se rechaza? El área verá este motivo.",
            "Solicitud rechazada", row, list, dialog, onCambio, btnAprobar, btnRechazar));
        return new HBox(8, btnAprobar, btnRechazar);
    }

    private static HBox accionesBaja(Solicitud s, HBox row, Label formato, VBox list, Dialog<ButtonType> dialog,
                                     Runnable onCambio) {
        Button btnFormato = new Button("Recibí el formato");
        Button btnVer = new Button("Ver el bien");
        Button btnBaja = new Button("Dar de baja");
        Button btnNo = new Button("No procede");
        btnFormato.setGraphic(new FontIcon("mdi2f-file-check-outline"));
        btnVer.setGraphic(new FontIcon("mdi2e-eye-outline"));
        btnBaja.setGraphic(new FontIcon("mdi2d-delete-outline"));
        btnNo.setGraphic(new FontIcon("mdi2c-close-circle-outline"));
        btnFormato.getStyleClass().add("btn-secondary");
        btnVer.getStyleClass().add("btn-secondary");
        btnBaja.getStyleClass().add("btn-danger");
        btnNo.getStyleClass().add("btn-secondary");
        for (Button b : List.of(btnFormato, btnVer, btnBaja, btnNo)) b.setMinWidth(Region.USE_PREF_SIZE);
        btnFormato.setTooltip(new Tooltip("Anota el folio u oficio del formato firmado que entregó el área"));
        boolean recibido = s.formatoRecibidoEn() != null;
        btnFormato.setVisible(!recibido);
        btnFormato.setManaged(!recibido);
        // The folio of the signed formato, once it's handed in: the baja form brings it.
        String[] folio = { s.formatoFolio() };
        ProductoService productos = new ProductoService();

        btnFormato.setOnAction(e -> {
            Scene scene = dialog.getDialogPane().getScene();
            TextInputDialog pedir = new TextInputDialog();
            pedir.setTitle("Formato recibido");
            pedir.setHeaderText("El área entregó firmado el formato de solicitud de baja de «" + s.productoNombre() + "»");
            pedir.setContentText("Folio u oficio:");
            DialogUtil.applyOwner(pedir);
            DialogUtil.conEncabezado(pedir, "mdi2f-file-check-outline");
            String texto = pedir.showAndWait().map(String::trim).filter(t -> !t.isEmpty()).orElse(null);
            if (texto == null) return;
            btnFormato.setDisable(true);
            DialogUtil.runAsync(
                () -> SERVICE.recibirFormato(s.id(), texto),
                () -> {
                    folio[0] = texto;
                    btnFormato.setVisible(false);
                    btnFormato.setManaged(false);
                    if (formato != null) {
                        formato.setText("Formato firmado recibido hoy · folio " + texto);
                        formato.setGraphic(new FontIcon("mdi2f-file-check-outline"));
                    }
                    NotificacionUtil.exito(scene, "Formato recibido: ya puedes dar de baja el bien con ese folio");
                },
                ex -> {
                    btnFormato.setDisable(false);
                    NotificacionUtil.error(scene, "No se pudo guardar: " + causa(ex));
                });
        });

        btnVer.setOnAction(e -> DialogUtil.runAsync(
            () -> productos.findById(s.productoId()).orElse(null),
            p -> {
                if (p == null) NotificacionUtil.advertencia(dialog.getDialogPane().getScene(), "El bien ya no existe");
                else ProductoDetailDialog.show(p, dialog.getDialogPane().getScene(), new MovimientoService(), log);
            },
            ex -> NotificacionUtil.error(dialog.getDialogPane().getScene(), "No se pudo abrir el bien")));

        btnBaja.setOnAction(e -> {
            Scene scene = dialog.getDialogPane().getScene();
            List<Button> botones = List.of(btnFormato, btnVer, btnBaja, btnNo);
            for (Button b : botones) b.setDisable(true);
            Runnable reactivar = () -> { for (Button b : botones) b.setDisable(false); };
            DialogUtil.runAsync(
                () -> productos.findById(s.productoId()).orElse(null),
                p -> {
                    if (p == null) {
                        reactivar.run();
                        NotificacionUtil.advertencia(scene, "El bien ya no existe");
                        return;
                    }
                    if (p.isDadoDeBaja()) {
                        // Given de baja some other way: the request only needs closing.
                        DialogUtil.runAsync(() -> SolicitudService.bajaRegistrada(p.getId(), p.getMotivoBaja(), p.getNumeroActaBaja()),
                            () -> { quitar(row, list, onCambio); NotificacionUtil.info(scene, "Ese bien ya estaba dado de baja"); },
                            ex -> reactivar.run());
                        return;
                    }
                    var datos = ProductoBajasDialog.showBajaInputDialog(p, scene, s.motivo(), folio[0]).orElse(null);
                    if (datos == null) { reactivar.run(); return; }
                    DialogUtil.runAsync(
                        () -> productos.darDeBaja(p.getId(), datos.motivo(), datos.tipoDestino(), datos.dictamen(),
                            datos.numeroActa(), datos.fechaDictamen()),
                        () -> {
                            quitar(row, list, onCambio);
                            NotificacionUtil.exito(scene, "«" + p.getNombre() + "» dado de baja: el área ya ve la respuesta");
                        },
                        ex -> {
                            reactivar.run();
                            NotificacionUtil.error(scene, ex instanceof ProductoService.ValidationException
                                ? ex.getMessage() : "No se pudo dar de baja: " + causa(ex));
                        });
                },
                ex -> { reactivar.run(); NotificacionUtil.error(scene, "No se pudo abrir el bien"); });
        });

        btnNo.setOnAction(e -> rechazar(s, "No procede la baja",
            "¿Por qué no procede? El área verá este motivo.", "La solicitud quedó como «No procede»",
            row, list, dialog, onCambio, btnFormato, btnVer, btnBaja, btnNo));
        return new HBox(8, btnFormato, btnVer, btnBaja, btnNo);
    }

    private static void rechazar(Solicitud s, String tituloVentana, String pregunta, String hecho, HBox row, VBox list,
                                 Dialog<ButtonType> dialog, Runnable onCambio, Button... botones) {
        TextInputDialog motivo = new TextInputDialog();
        motivo.setTitle(tituloVentana);
        motivo.setHeaderText(pregunta);
        motivo.setContentText("Motivo:");
        DialogUtil.applyOwner(motivo);
        DialogUtil.conEncabezado(motivo, "mdi2c-close-circle-outline");
        String texto = motivo.showAndWait().map(String::trim).orElse(null);
        if (texto == null) return;
        for (Button b : botones) b.setDisable(true);
        DialogUtil.runAsync(
            () -> { SERVICE.rechazar(s.id(), texto); return null; },
            ok -> {
                quitar(row, list, onCambio);
                NotificacionUtil.info(dialog.getDialogPane().getScene(), hecho);
            },
            ex -> {
                for (Button b : botones) b.setDisable(false);
                NotificacionUtil.error(dialog.getDialogPane().getScene(), "No se pudo guardar: " + causa(ex));
            });
    }

    private static void quitar(HBox row, VBox list, Runnable onCambio) {
        list.getChildren().remove(row);
        if (list.getChildren().isEmpty()) {
            Label listo = new Label("No queda nada por revisar");
            listo.getStyleClass().add("muted");
            list.getChildren().add(listo);
        }
        if (onCambio != null) onCambio.run();
    }

    private static String causa(Exception e) {
        return e instanceof IllegalArgumentException || e instanceof IllegalStateException
            || e instanceof SecurityException ? e.getMessage() : "revisa la conexión e inténtalo de nuevo";
    }
}
