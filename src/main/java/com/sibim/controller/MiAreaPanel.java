package com.sibim.controller;

import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.service.MovimientoService;
import com.sibim.service.ProductoService;
import com.sibim.session.SessionManager;
import com.sibim.util.AccessibilityUtils;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * "Pendientes de tu área", at the top of the Inicio of an área account: its three
 * steps with the button of the one to do now, what Patrimonio sent it to confirm,
 * its solicitudes de baja, and the bienes with data to fill in. Patrimonio sees
 * the pendientes of the whole inventory in Alertas.
 *
 * <p>9-nov-2026: «sin foto», «sin ubicación» and «sin estado físico» were three
 * cards with a list of their own (pick the bien, then «Actualizar datos») next to
 * «Revisar mis bienes», which shows the same with a «Completar» on each row: two
 * ways to the same thing for people who don't use a computer every day. Now they
 * are one card that opens «Revisar mis bienes» on «Faltan datos».
 */
final class MiAreaPanel {

    /** How many of the área's bienes lack each thing the área itself can fill in. */
    record Faltas(int bienes, int foto, int ubicacion, int estado) {
        /** "sin foto 12 · sin ubicación 3 · sin estado físico 5" (only what is missing). */
        String texto() {
            List<String> t = new ArrayList<>();
            if (foto > 0) t.add("sin foto " + foto);
            if (ubicacion > 0) t.add("sin ubicación " + ubicacion);
            if (estado > 0) t.add("sin estado físico " + estado);
            return String.join(" · ", t);
        }
    }

    /** What the active bienes lack: the same as the «Faltan datos» list of «Revisar mis bienes». */
    static Faltas faltas(List<Producto> bienes) {
        int n = 0, foto = 0, ubicacion = 0, estado = 0;
        for (Producto p : bienes) {
            if (p.getFechaBaja() != null || com.sibim.service.AvanceAreasService.conDatos(p)) continue;
            n++;
            if (vacio(p.getFotoUrl())) foto++;
            if (vacio(p.getUbicacion())) ubicacion++;
            if (vacio(p.getEstadoFisico())) estado++;
        }
        return new Faltas(n, foto, ubicacion, estado);
    }

    private record Datos(List<Producto> bienes, List<Movimiento> porRecibir, List<com.sibim.model.Solicitud> bajas) {}

    private final ProductoService productos;
    private final MovimientoService movimientos;
    private final Logger log;
    private final FlowPane tarjetas = new FlowPane(10, 10);
    private final Label resumen = new Label();
    /** The área's three steps, as Patrimonio sees them in «Avance de las áreas». */
    private final HBox avance = new HBox(0);

    MiAreaPanel(VBox raiz, ProductoService productos, MovimientoService movimientos, Logger log) {
        this.productos = productos;
        this.movimientos = movimientos;
        this.log = log;

        Region barra = new Region();
        barra.getStyleClass().add("dash-section-bar");
        Label titulo = new Label(SessionManager.isSecretario() ? "PENDIENTES DE TU SECRETARÍA" : "PENDIENTES DE TU ÁREA");
        titulo.getStyleClass().add("dash-section-label");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        resumen.getStyleClass().add("muted");
        HBox encabezado = new HBox(10, barra, titulo, espacio, resumen);
        encabezado.setAlignment(Pos.CENTER_LEFT);

        avance.getStyleClass().addAll("card", "mi-avance");
        avance.setAlignment(Pos.TOP_LEFT);
        avance.setVisible(false); avance.setManaged(false);
        raiz.getChildren().setAll(encabezado, avance, tarjetas);
        raiz.setSpacing(8);
        raiz.setVisible(true);
        raiz.setManaged(true);
    }

    void cargar() {
        DialogUtil.runAsync(
            () -> new Datos(productos.getAll(), porRecibir(), solicitudesDeBaja()),
            this::mostrar,
            e -> log.warn("No se pudieron cargar los pendientes del área", e));
    }

    /** The área's solicitudes de baja still waiting or answered in the last 30 days
     *  (5-nov-2026): Patrimonio's answer is seen here. None offline or in the demo. */
    private List<com.sibim.model.Solicitud> solicitudesDeBaja() {
        if (!com.sibim.service.SolicitudService.disponible()) return List.of();
        java.time.LocalDateTime desde = java.time.LocalDateTime.now().minusDays(30);
        try {
            return new com.sibim.service.SolicitudService().deMisAreas().stream()
                .filter(com.sibim.model.Solicitud::esBaja)
                .filter(s -> s.isPendiente() || s.resueltoEn() == null || s.resueltoEn().isAfter(desde))
                .toList();
        } catch (Exception e) { return List.of(); }
    }

    /** " · 1 dado de baja · 1 no procede": the área's other requests, after the formatos it owes. */
    private static String conElResto(List<com.sibim.model.Solicitud> bajas) {
        List<com.sibim.model.Solicitud> resto = bajas.stream().filter(s -> !s.faltaFormato()).toList();
        return resto.isEmpty() ? "" : " · " + BajasController.resumenMias(resto);
    }

    /** Offline (or with the server unreachable) there is nothing to confirm yet. */
    private List<Movimiento> porRecibir() {
        try { return movimientos.getPorRecibir(); }
        catch (Exception e) { return List.of(); }
    }

    private void mostrar(Datos d) {
        tarjetas.getChildren().clear();
        resumen.setText(FormatUtils.plural(d.bienes().size(), "bien", "bienes") + " a tu cargo");
        // Phases 2 and 3 of the área's avance (Organigrama): review its bienes, confirm the labels.
        String mia = SessionManager.getAccessibleAreas() != null && SessionManager.getAccessibleAreas().size() == 1
            ? SessionManager.getAccessibleAreas().iterator().next() : null;
        long revisados = d.bienes().stream().filter(Producto::isRevisadoPorArea).count();
        long etiquetados = d.bienes().stream().filter(Producto::isEtiquetado).count();
        long regresados = d.bienes().stream().filter(p -> p.getMensajePatrimonio() != null).count();
        // The three steps on one line, the one to do now with its button: five cards of the
        // same size didn't say which came first nor that it was a path of three.
        pintarAvance(d.bienes().size(), revisados, etiquetados, regresados,
            () -> com.sibim.controller.dialogs.RevisarBienesDialog.show(mia,
                com.sibim.controller.dialogs.RevisarBienesDialog.Vista.POR_REVISAR, this::cargar),
            () -> com.sibim.controller.dialogs.RevisarBienesDialog.show(mia,
                com.sibim.controller.dialogs.RevisarBienesDialog.Vista.SIN_ETIQUETA, this::cargar));
        if (!d.porRecibir().isEmpty()) {
            tarjetas.getChildren().add(tarjeta("mdi2i-inbox-arrow-down-outline", "qa-card-icon-green",
                FormatUtils.plural(d.porRecibir().size(), "bien", "bienes") + " por recibir",
                "Confirma cuando los tengas",
                () -> new PorRecibirDialog(movimientos, this::cargar).show(d.porRecibir())));
        }
        if (!d.bajas().isEmpty()) {
            // A formato not handed in yet is something the área has to do: it says so first.
            long sinFormato = d.bajas().stream().filter(com.sibim.model.Solicitud::faltaFormato).count();
            tarjetas.getChildren().add(tarjeta("mdi2f-file-remove-outline", "qa-card-icon-amber",
                sinFormato > 0 ? "Entrega " + (sinFormato == 1 ? "1 formato de baja" : sinFormato + " formatos de baja")
                               : "Tus solicitudes de baja",
                sinFormato > 0 ? "Firmados, en " + com.sibim.model.Solicitud.ENTREGAR_FORMATO_EN + conElResto(d.bajas())
                               : BajasController.resumenMias(d.bajas()) + " · Ver la respuesta de Patrimonio",
                () -> com.sibim.controller.dialogs.SolicitudesDialog.show(tarjetas.getScene(),
                    com.sibim.controller.dialogs.SolicitudesDialog.BAJAS, this::cargar)));
        }
        // One card, to the same window as the steps: each row there has its «Completar».
        Faltas f = faltas(d.bienes());
        if (f.bienes() > 0) {
            VBox datos = tarjeta("mdi2p-pencil-outline", "qa-card-icon-amber",
                FormatUtils.plural(f.bienes(), "bien con datos por completar", "bienes con datos por completar"),
                f.texto() + " · Complétalos en «Revisar mis bienes»",
                () -> com.sibim.controller.dialogs.RevisarBienesDialog.show(mia,
                    com.sibim.controller.dialogs.RevisarBienesDialog.Vista.FALTAN_DATOS, this::cargar));
            datos.setPrefWidth(360);   // what it lacks, in two lines rather than four
            tarjetas.getChildren().add(datos);
        }
        if (tarjetas.getChildren().isEmpty() && d.bienes().isEmpty()) {
            tarjetas.getChildren().add(tarjeta("mdi2c-check-circle-outline", "qa-card-icon-green", "Todo al día",
                "Tu área todavía no tiene bienes asignados", null));
        }
        tarjetas.setVisible(!tarjetas.getChildren().isEmpty());
        tarjetas.setManaged(!tarjetas.getChildren().isEmpty());
    }

    /** ① Entrar a SIBIM ─ ② Revisar tus bienes ─ ③ Confirmar etiquetas: done in green, the
     *  current one in gold with its button, the rest in gray. */
    private void pintarAvance(int total, long revisados, long etiquetados, long regresados, Runnable revisar, Runnable etiquetar) {
        avance.getChildren().clear();
        boolean hay = total > 0;
        avance.setVisible(hay); avance.setManaged(hay);
        if (!hay) return;
        boolean revisado = revisados >= total, etiquetado = etiquetados >= total;
        int actual = !revisado ? 2 : !etiquetado ? 3 : 4;
        avance.getChildren().addAll(
            paso(1, "Entrar a SIBIM", "Hecho", 1 < actual, false, null, null),
            linea(true),
            paso(2, "Revisar tus bienes",
                revisado ? "Los " + total + " revisados"
                         : revisados + " de " + total + (regresados > 0
                             // Patrimonio sent some back with a message: that is what to look at first.
                             ? " · Patrimonio te regresó " + regresados + " con un mensaje"
                             : " · di si son de tu área y llena lo que falte"),
                revisado, actual == 2, "Revisar mis bienes", revisar),
            linea(revisado),
            paso(3, "Confirmar etiquetas",
                etiquetado ? "Las " + total + " pegadas"
                           : etiquetados + " de " + total + " · marca los que ya tienen su etiqueta",
                etiquetado, actual == 3, "Confirmar etiquetas", etiquetar));
        if (actual == 4) {
            Label fin = new Label("¡Listo! Tu área terminó su revisión");
            fin.getStyleClass().add("mi-avance-fin");
            avance.getChildren().addAll(linea(true), fin);
        }
    }

    private static HBox paso(int n, String titulo, String detalle, boolean hecho, boolean enCurso,
                             String textoBoton, Runnable accion) {
        Label num = new Label(hecho ? "✓" : String.valueOf(n));
        num.getStyleClass().addAll("avance-paso", "mi-avance-paso",
            hecho ? "avance-paso-hecho" : enCurso ? "avance-paso-curso" : "avance-paso-pendiente");
        Label t = new Label(titulo);
        t.getStyleClass().add("mi-avance-titulo");
        Label dLbl = new Label(detalle);
        dLbl.getStyleClass().addAll("mi-avance-detalle", hecho ? "avance-estado-hecho" : enCurso ? "avance-estado-curso" : "avance-estado");
        dLbl.setWrapText(true);
        VBox textos = new VBox(3, t, dLbl);
        if (enCurso && accion != null) {
            Button b = new Button(textoBoton + "  →");
            b.getStyleClass().add("btn-primary");
            b.setOnAction(e -> accion.run());
            VBox.setMargin(b, new Insets(6, 0, 0, 0));
            textos.getChildren().add(b);
        }
        HBox caja = new HBox(10, num, textos);
        caja.setAlignment(Pos.TOP_LEFT);
        caja.setMinWidth(Region.USE_PREF_SIZE);
        HBox.setHgrow(caja, Priority.SOMETIMES);
        caja.setMaxWidth(320);
        return caja;
    }

    private static Region linea(boolean hecha) {
        Region l = new Region();
        l.getStyleClass().add("mi-avance-linea");
        if (hecha) l.getStyleClass().add("avance-linea-hecha");
        HBox.setHgrow(l, Priority.ALWAYS);
        HBox.setMargin(l, new Insets(13, 14, 0, 14));
        l.setMinWidth(24);
        return l;
    }

    private VBox tarjeta(String icono, String estilo, String titulo, String hint, Runnable accion) {
        FontIcon icon = new FontIcon(icono);
        icon.getStyleClass().add(estilo);
        Label lTitulo = new Label(titulo);
        lTitulo.getStyleClass().add("qa-card-title");
        Label lHint = new Label(hint);
        lHint.getStyleClass().add("qa-card-hint");
        lHint.setWrapText(true);
        VBox textos = new VBox(2, lTitulo, lHint);
        textos.setMinWidth(0);
        HBox fila = new HBox(12, icon, textos);
        fila.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(fila);
        card.getStyleClass().add("quick-action-card");
        card.setPadding(new Insets(10, 14, 10, 14));
        card.setPrefWidth(250);
        if (accion != null) {
            card.setOnMouseClicked(e -> accion.run());
            AccessibilityUtils.asButton(card, titulo + " — " + hint);
        } else {
            card.getStyleClass().add("quick-action-card-static");
        }
        return card;
    }

    static boolean vacio(String s) { return s == null || s.isBlank(); }
}
