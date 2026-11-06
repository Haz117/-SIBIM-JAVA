package com.sibim.controller;

import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.service.MovimientoService;
import com.sibim.service.ProductoService;
import com.sibim.session.SessionManager;
import com.sibim.util.AccessibilityUtils;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;

import java.util.List;
import java.util.function.Predicate;

/**
 * "Pendientes de tu área", at the top of the Inicio of an área account: what
 * is missing from its bienes (photo, ubicación, estado físico) and what
 * Patrimonio sent it to confirm, each one a click away from being fixed.
 * Patrimonio sees the pendientes of the whole inventory in Alertas.
 */
final class MiAreaPanel {

    /** Something a bien of the área lacks, which the área itself can fill in. */
    record Pendiente(String icono, String estilo, String titulo, String accion, Predicate<Producto> falta) {}

    static final List<Pendiente> PENDIENTES = List.of(
        new Pendiente("mdi2c-camera-off", "qa-card-icon-amber", "sin foto",
            "Agrégales una foto", p -> vacio(p.getFotoUrl())),
        new Pendiente("mdi2m-map-marker-question-outline", "qa-card-icon-blue", "sin ubicación",
            "Indica dónde están", p -> vacio(p.getUbicacion())),
        new Pendiente("mdi2c-clipboard-alert-outline", "qa-card-icon-violet", "sin estado físico",
            "Registra cómo están", p -> vacio(p.getEstadoFisico())));

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
        // The three steps on one line, the one to do now with its button: five cards of the
        // same size didn't say which came first nor that it was a path of three.
        pintarAvance(d.bienes().size(), revisados, etiquetados,
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
        for (Pendiente p : PENDIENTES) {
            List<Producto> faltan = d.bienes().stream().filter(p.falta()).toList();
            if (faltan.isEmpty()) continue;
            tarjetas.getChildren().add(tarjeta(p.icono(), p.estilo(),
                FormatUtils.plural(faltan.size(), "bien", "bienes") + " " + p.titulo(), p.accion(),
                () -> abrirLista(p, faltan)));
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
    private void pintarAvance(int total, long revisados, long etiquetados, Runnable revisar, Runnable etiquetar) {
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
                         : revisados + " de " + total + " · di si son de tu área y llena lo que falte",
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

    /** The bienes that lack {@code p}, with "Actualizar datos" one click away. */
    private void abrirLista(Pendiente p, List<Producto> faltan) {
        Dialog<ButtonType> dlg = DialogUtil.createButtonDialog(760);
        dlg.setTitle("Bienes " + p.titulo());
        DialogPane pane = dlg.getDialogPane();
        pane.getButtonTypes().setAll(ButtonType.CLOSE);
        pane.setHeader(DialogUtil.gradientHeader(p.icono(), "Bienes " + p.titulo(),
            FormatUtils.plural(faltan.size(), "bien", "bienes") + " — elige uno y da «Actualizar datos»",
            AppColors.PRIMARY_D, AppColors.INDIGO));

        TableView<Producto> tabla = new TableView<>(javafx.collections.FXCollections.observableArrayList(faltan));
        tabla.getStyleClass().add("data-table");
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.getColumns().add(columna("Código", 110, Producto::getCodigo));
        tabla.getColumns().add(columna("Bien", 260, Producto::getNombre));
        tabla.getColumns().add(columna("Ubicación", 160, Producto::getUbicacion));
        tabla.getColumns().add(columna("Estado físico", 110, Producto::getEstadoFisico));
        tabla.setPrefHeight(380);
        tabla.setPlaceholder(new Label("Ya no queda ninguno: ¡listo!"));

        Button actualizar = new Button("Actualizar datos");
        actualizar.setGraphic(new FontIcon("mdi2p-pencil-outline"));
        actualizar.getStyleClass().add("btn-primary");
        actualizar.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull());
        Runnable abrir = () -> {
            Producto sel = tabla.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            Scene escena = pane.getScene();
            GuardarBien.actualizarDatos(escena, productos, sel, log, saved -> {
                int i = tabla.getItems().indexOf(sel);
                if (i < 0) return;
                if (p.falta().test(saved)) tabla.getItems().set(i, saved);
                else tabla.getItems().remove(i);
            });
        };
        actualizar.setOnAction(e -> abrir.run());
        tabla.setRowFactory(tv -> {
            TableRow<Producto> row = new TableRow<>();
            row.setOnMouseClicked(e -> { if (e.getClickCount() == 2 && !row.isEmpty()) abrir.run(); });
            return row;
        });
        tabla.setOnKeyPressed(e -> { if (e.getCode() == javafx.scene.input.KeyCode.ENTER) abrir.run(); });

        Label ayuda = new Label("Doble clic o Enter en un bien también lo abre.");
        ayuda.getStyleClass().add("muted-sm");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox acciones = new HBox(10, ayuda, espacio, actualizar);
        acciones.setAlignment(Pos.CENTER_LEFT);
        VBox contenido = new VBox(10, tabla, acciones);
        contenido.setPadding(new Insets(16, 20, 12, 20));
        // DialogPane gives its content the "content" class, whose CSS padding (0) would win over ours.
        pane.setContent(new StackPane(contenido));
        javafx.application.Platform.runLater(() -> {
            tabla.requestFocus();
            if (!tabla.getItems().isEmpty()) tabla.getSelectionModel().selectFirst();
        });
        dlg.showAndWait();
        cargar();
    }

    private static TableColumn<Producto, String> columna(String titulo, double ancho,
                                                         java.util.function.Function<Producto, String> valor) {
        TableColumn<Producto, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
            vacio(valor.apply(cd.getValue())) ? "—" : valor.apply(cd.getValue())));
        c.setPrefWidth(ancho);
        return c;
    }

    static boolean vacio(String s) { return s == null || s.isBlank(); }
}
