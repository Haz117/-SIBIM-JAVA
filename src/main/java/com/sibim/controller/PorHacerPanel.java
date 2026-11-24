package com.sibim.controller;

import com.sibim.model.Producto;
import com.sibim.model.Resguardo;
import com.sibim.repository.ConfiguracionRepository;
import com.sibim.service.CalidadDatosService;
import com.sibim.service.CalidadDatosService.PorHacer;
import com.sibim.service.ProductoService;
import com.sibim.service.ResguardoService;
import com.sibim.session.NavigationContext;
import com.sibim.util.AccessibilityUtils;
import com.sibim.util.DialogUtil;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Inicio, for Patrimonio: what the inventory still needs, with its real count and one
 *  click to the screen that fixes it. Those screens live under "Más", and nothing said
 *  there was anything to do in them. The áreas have "Pendientes de tu área" instead. */
final class PorHacerPanel {

    private record Datos(List<Producto> bienes, List<Resguardo> resguardos, Set<String> distintas, Map<String, String> config,
                         int solicitudesBaja, int pcsAtrasadas) {}

    private final VBox seccion;

    PorHacerPanel(VBox seccion) {
        this.seccion = seccion;
        CalidadDatosService calidad = new CalidadDatosService();
        DialogUtil.runAsync(
            () -> new Datos(new ProductoService().getAll(), new ResguardoService().getAll(),
                calidad.personasDistintas(), new ConfiguracionRepository().findAll(), solicitudesBaja(), pcsAtrasadas()),
            d -> pintar(conEquipos(d.pcsAtrasadas(), conSolicitudes(d.solicitudesBaja(),
                CalidadDatosService.porHacer(d.bienes(), d.resguardos(), d.distintas(), d.config())))),
            e -> { /* It is a shortcut: without it, Inicio is what it was. */ });
    }

    /** The solicitudes de baja the áreas sent (5-nov-2026); 0 offline, in the demo or if they can't be counted. */
    private static int solicitudesBaja() {
        try {
            return new com.sibim.service.SolicitudService().countPendientes(com.sibim.model.Solicitud.TIPO_BAJA);
        } catch (Exception e) {
            return 0;
        }
    }

    /** PCs used in the last 60 days that still run an older SIBIM; 0 if it can't be told. */
    private static int pcsAtrasadas() {
        try {
            return new com.sibim.service.AccesosEquipoService()
                .desactualizados(com.sibim.util.UpdateChecker.currentVersion(), 60).size();
        } catch (Exception e) {
            return 0;
        }
    }

    /** Last: it is fixed by publishing the update, not in the inventory. */
    static List<PorHacer> conEquipos(int n, List<PorHacer> resto) {
        if (n <= 0) return resto;
        List<PorHacer> todo = new java.util.ArrayList<>(resto);
        todo.add(new PorHacer(n, n == 1 ? "computadora con una versión anterior de SIBIM" : "computadoras con una versión anterior de SIBIM",
            "Ver equipos", "configuracion", "equipos"));
        return todo;
    }

    /** An área waiting for an answer goes first. */
    static List<PorHacer> conSolicitudes(int n, List<PorHacer> resto) {
        if (n <= 0) return resto;
        List<PorHacer> todo = new java.util.ArrayList<>();
        todo.add(new PorHacer(n, n == 1 ? "solicitud de baja de un área por revisar" : "solicitudes de baja de las áreas por revisar",
            "Revisar solicitudes", "bajas", "solicitudes"));
        todo.addAll(resto);
        return todo;
    }

    private void pintar(List<PorHacer> lista) {
        seccion.getChildren().clear();
        boolean hay = !lista.isEmpty();
        seccion.setVisible(hay);
        seccion.setManaged(hay);
        if (!hay) return;

        Region barra = new Region();
        barra.getStyleClass().add("dash-section-bar");
        Label titulo = new Label("POR HACER");
        titulo.getStyleClass().add("dash-section-label");
        Label pista = new Label("Lo que le falta al inventario: da clic para resolverlo");
        pista.getStyleClass().add("muted-sm");
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        Button plegar = new Button();
        plegar.getStyleClass().add("btn-ghost");
        plegar.setGraphic(new FontIcon("mdi2c-chevron-up"));
        HBox encabezado = new HBox(10, barra, titulo, pista, hueco, plegar);
        encabezado.setAlignment(Pos.CENTER_LEFT);

        // By kind of work and in the order it is best done, the first one marked
        // (12-nov-2026: fifteen cards of the same weight and no order; «2 proveedores»,
        // a minute's work, looked like «2,960 sin etiquetar», weeks of it).
        VBox cuerpo = enColumnas() ? columnas(lista) : null;
        if (cuerpo != null) {
            DialogUtil.makeCollapsible("inicio.porhacer.colapsado", plegar, cuerpo, "Mostrar", "Ocultar");
            seccion.getChildren().setAll(encabezado, cuerpo);
            return;
        }
        cuerpo = new VBox(6);
        boolean primera = true;
        for (Grupo g : Grupo.values()) {
            List<PorHacer> deEste = lista.stream().filter(p -> grupo(p) == g)
                .sorted(java.util.Comparator.comparingInt(PorHacerPanel::orden)).toList();
            if (deEste.isEmpty()) continue;
            Label nombre = new Label(g.titulo);
            nombre.getStyleClass().add("por-hacer-grupo");
            HBox fila = new HBox(8, nombre);
            if (g.pista != null) {
                Label pistaGrupo = new Label(g.pista);
                pistaGrupo.getStyleClass().add("muted-sm");
                fila.getChildren().add(pistaGrupo);
            }
            fila.setAlignment(Pos.CENTER_LEFT);
            FlowPane tarjetas = new FlowPane(10, 10);
            List<javafx.scene.Node> deLaFila = new java.util.ArrayList<>();
            for (PorHacer p : deEste) {
                VBox t = tarjeta(p);
                if (primera) {
                    Label empieza = new Label("Empieza aquí");
                    empieza.getStyleClass().add("dash-section-badge");
                    t.getChildren().add(0, empieza);
                    primera = false;
                }
                deLaFila.add(t);
            }
            conVerMas(tarjetas, g, deLaFila);
            if (!cuerpo.getChildren().isEmpty()) fila.setPadding(new javafx.geometry.Insets(6, 0, 0, 0));
            cuerpo.getChildren().addAll(fila, tarjetas);
        }
        DialogUtil.makeCollapsible("inicio.porhacer.colapsado", plegar, cuerpo, "Mostrar", "Ocultar");
        seccion.getChildren().setAll(encabezado, cuerpo);
    }

    /** On a wide screen the kinds go side by side, each a column of rows (17-nov-2026: the cards of
     *  205 px left most of each row empty, «Se arreglan en un momento» was one card on a whole row
     *  and the inventory's numbers fell below the first screen). */
    static boolean enColumnas() {
        return javafx.stage.Screen.getPrimary().getVisualBounds().getWidth()
            >= 1280 + 220 * com.sibim.util.AccessibilityUtils.getTextScaleIndex();
    }

    /** «Atiende primero» across the top, then one column per kind, «Empieza aquí» on the first. */
    private static VBox columnas(List<PorHacer> lista) {
        VBox cuerpo = new VBox(10);
        boolean[] primera = { true };
        List<PorHacer> urgentes = lista.stream().filter(p -> grupo(p) == Grupo.PRIMERO)
            .sorted(java.util.Comparator.comparingInt(PorHacerPanel::orden)).toList();
        if (!urgentes.isEmpty()) {
            Label nombre = new Label(Grupo.PRIMERO.titulo);
            nombre.getStyleClass().add("por-hacer-grupo");
            FlowPane filas = new FlowPane(10, 10);
            for (PorHacer p : urgentes) filas.getChildren().add(fila(p, primera, 330));
            cuerpo.getChildren().addAll(nombre, filas);
        }
        javafx.scene.layout.GridPane cols = new javafx.scene.layout.GridPane();
        cols.setHgap(14);
        int c = 0;
        for (Grupo g : Grupo.values()) {
            if (g == Grupo.PRIMERO) continue;
            List<PorHacer> deEste = lista.stream().filter(p -> grupo(p) == g)
                .sorted(java.util.Comparator.comparingInt(PorHacerPanel::orden)).toList();
            if (deEste.isEmpty()) continue;
            Label nombre = new Label(g.titulo);
            nombre.getStyleClass().add("por-hacer-grupo");
            VBox col = new VBox(6, nombre);
            if (g.pista != null) {
                Label pistaGrupo = new Label(g.pista);
                pistaGrupo.getStyleClass().add("muted-sm");
                pistaGrupo.setWrapText(true);
                pistaGrupo.setMinHeight(Region.USE_PREF_SIZE);
                col.getChildren().add(pistaGrupo);
            }
            conVerMas(col, g, deEste.stream().<javafx.scene.Node>map(p -> fila(p, primera, -1)).toList());
            cols.add(col, c++, 0);
            javafx.scene.layout.GridPane.setValignment(col, javafx.geometry.VPos.TOP);
        }
        for (int i = 0; i < c; i++) {
            javafx.scene.layout.ColumnConstraints cc = new javafx.scene.layout.ColumnConstraints();
            cc.setPercentWidth(100.0 / c);
            cc.setHgrow(Priority.ALWAYS);
            cols.getColumnConstraints().add(cc);
        }
        if (c > 0) cuerpo.getChildren().add(cols);
        return cuerpo;
    }

    /** How many tasks of a kind show before «Ver N más» (19-nov-2026: «Se arreglan en un momento»
     *  had 9 and pushed the inventory's numbers far down). One more than this shows whole. */
    static final int A_LA_VISTA = 4;
    private static final java.util.prefs.Preferences PREFS = java.util.prefs.Preferences.userNodeForPackage(DialogUtil.class);

    /** How many of {@code total} tasks show at first, unless the kind was left open. */
    static int aLaVista(int total, boolean abierta) {
        return abierta || total <= A_LA_VISTA + 1 ? total : A_LA_VISTA;
    }

    /** The first tasks of a kind and «Ver N más» / «Ver menos» for the rest, remembered per kind. */
    private static void conVerMas(javafx.scene.layout.Pane caja, Grupo g, List<javafx.scene.Node> tareas) {
        String clave = "inicio.porhacer.todas." + g.name();
        int n = aLaVista(tareas.size(), false);
        if (n == tareas.size()) { caja.getChildren().addAll(tareas); return; }
        List<javafx.scene.Node> resto = tareas.subList(n, tareas.size());
        caja.getChildren().addAll(tareas.subList(0, n));
        javafx.scene.control.Hyperlink mas = new javafx.scene.control.Hyperlink();
        mas.getStyleClass().add("por-hacer-mas");
        Runnable pintar = () -> {
            boolean abierta = PREFS.getBoolean(clave, false);
            caja.getChildren().removeAll(resto);
            if (abierta) caja.getChildren().addAll(caja.getChildren().indexOf(mas), resto);
            mas.setText(abierta ? "Ver menos" : "Ver " + resto.size() + " más");
        };
        caja.getChildren().add(mas);
        mas.setOnAction(e -> {
            PREFS.putBoolean(clave, !PREFS.getBoolean(clave, false));
            pintar.run();
        });
        pintar.run();
    }

    /** One task as a row: its number on the left, what it is and what to do on the right. */
    private static HBox fila(PorHacer p, boolean[] primera, double ancho) {
        Label numero = new Label(String.format("%,d", p.cantidad()));
        numero.getStyleClass().add("por-hacer-numero");
        numero.setMinWidth(Region.USE_PREF_SIZE);
        StackPane caja = new StackPane(numero);
        caja.getStyleClass().add("por-hacer-fila-numero");
        Label que = new Label(p.titulo());
        que.getStyleClass().add("por-hacer-titulo");
        que.setWrapText(true);
        que.setMinHeight(Region.USE_PREF_SIZE);
        Label ir = new Label(p.accion() + " →");
        ir.getStyleClass().add("por-hacer-accion");
        VBox texto = new VBox(2, que, ir);
        if (primera[0]) {
            Label empieza = new Label("Empieza aquí");
            empieza.getStyleClass().add("dash-section-badge");
            texto.getChildren().add(0, empieza);
            primera[0] = false;
        }
        HBox.setHgrow(texto, Priority.ALWAYS);
        HBox t = new HBox(12, caja, texto);
        t.setAlignment(Pos.CENTER_LEFT);
        t.getStyleClass().addAll("quick-action-card", "por-hacer-fila");
        if (ancho > 0) { t.setPrefWidth(ancho); t.setMaxWidth(ancho); } else t.setMaxWidth(Double.MAX_VALUE);
        t.setOnMouseClicked(e -> abrir(p));
        AccessibilityUtils.asButton(t, p.cantidad() + " " + p.titulo() + ": " + p.accion());
        return t;
    }

    /** The kinds of «Por hacer», in the order they are best done. */
    enum Grupo {
        PRIMERO("Atiende primero", null),
        RAPIDO("Se arreglan en un momento", "SIBIM te propone el cambio; tú lo revisas y lo confirmas"),
        CAPTURA("Por capturar", "Con la factura o el bien a la vista"),
        SEGUIMIENTO("Para seguir", "Avanzan con las áreas, los vehículos y las computadoras");

        final String titulo, pista;
        Grupo(String titulo, String pista) { this.titulo = titulo; this.pista = pista; }
    }

    static Grupo grupo(PorHacer p) {
        String a = p.abrir();
        // The late backup, an área waiting for an answer, bienes an área says aren't its own.
        if (a == null || "solicitudes".equals(a) || "observados".equals(a)) return Grupo.PRIMERO;
        if (a.startsWith("completar:") || "formatos".equals(a) || "grupos".equals(a)) return Grupo.CAPTURA;
        if (Set.of("lote", "juntar", "etiquetas", "vehiculos", "equipos").contains(a)) return Grupo.SEGUIMIENTO;
        return Grupo.RAPIDO;
    }

    /** Inside a kind: the folio fiscal first, because then «misma factura» copies more; the colors the
     *  names say right after, within the 4 in view (24-nov-2026: they depend on nothing else). */
    private static final List<String> ORDEN = List.of("solicitudes", "observados", "folio", "colores", "proveedores", "mismonombre", "factura",
        "tipo", "costos", "residual", "unir", "titulos", "series", "grupos", "completar:FOLIO_REVISAR", "formatos", "lote", "juntar", "etiquetas", "vehiculos", "equipos");

    static int orden(PorHacer p) {
        int i = p.abrir() == null ? -1 : ORDEN.indexOf(p.abrir());
        return i < 0 ? (p.abrir() == null ? -1 : ORDEN.size()) : i;
    }

    private static VBox tarjeta(PorHacer p) {
        Label numero = new Label(String.format("%,d", p.cantidad()));
        numero.getStyleClass().add("por-hacer-numero");
        Label que = new Label(p.titulo());
        que.getStyleClass().add("por-hacer-titulo");
        que.setWrapText(true);
        Label ir = new Label(p.accion() + " →");
        ir.getStyleClass().add("por-hacer-accion");
        VBox t = new VBox(2, numero, que, ir);
        t.getStyleClass().addAll("quick-action-card", "por-hacer-tarjeta");
        t.setOnMouseClicked(e -> abrir(p));
        AccessibilityUtils.asButton(t, p.cantidad() + " " + p.titulo() + ": " + p.accion());
        return t;
    }

    private static void abrir(PorHacer p) {
        if (p.abrir() != null) NavigationContext.setPendingAbrir(p.abrir());
        MainController mc = MainController.getInstance();
        if (mc != null) mc.navigateToView(p.vista());
    }
}
