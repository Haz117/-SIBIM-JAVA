package com.sibim.controller;

import com.sibim.model.Producto;
import com.sibim.util.EmptyStateUtil;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** "Pendientes patrimoniales": bienes whose control record is incomplete —
 *  nobody signed for them (sin resguardante) or they carry no physical label
 *  (sin etiquetar). Stock-based alerts say nothing about an inventory where
 *  every bien is a single numbered item; these are what an audit asks for.
 *  Not counted in the red alert badge: they're paperwork to catch up on, not
 *  an emergency. */
class AlertasPatrimonialesSection {

    /** Not in any resguardo in force: what Patrimonio has to do (18-nov-2026: 256; the card
     *  said «2961» because every bien still waits for its área to confirm its label). */
    static final String SIN_RESGUARDO = "Sin resguardo";
    static final String SIN_RESGUARDANTE = "Sin resguardante";
    static final String SIN_ETIQUETAR = "Sin etiquetar";
    /** Above this many bienes the section opens grouped by área: a list of
     *  thousands of rows is not something anyone can work through. */
    private static final int UMBRAL_POR_AREA = 50;

    /** One row per área: how many of its bienes are missing what. */
    record PorArea(String area, long bienes, long sinResguardo, long sinResguardante, long sinEtiquetar) {}

    static List<PorArea> porArea(List<Pendiente> pendientes) {
        java.util.Map<String, long[]> m = new java.util.TreeMap<>();
        for (Pendiente x : pendientes) {
            String area = x.producto().getArea() != null && !x.producto().getArea().isBlank()
                ? x.producto().getArea() : "Sin área";
            long[] n = m.computeIfAbsent(area, k -> new long[4]);
            n[0]++;
            if (x.falta().contains(SIN_RESGUARDO)) n[1]++;
            if (x.falta().contains(SIN_RESGUARDANTE)) n[2]++;
            if (x.falta().contains(SIN_ETIQUETAR)) n[3]++;
        }
        List<PorArea> out = new ArrayList<>();
        m.forEach((area, n) -> out.add(new PorArea(area, n[0], n[1], n[2], n[3])));
        // What Patrimonio has to do first: the áreas with more bienes without resguardo.
        out.sort(java.util.Comparator.comparingLong(PorArea::sinResguardo).reversed()
            .thenComparing(java.util.Comparator.comparingLong(PorArea::bienes).reversed()));
        return out;
    }

    /** One row per bien, with what's missing. */
    record Pendiente(Producto producto, String falta) {}

    static List<Pendiente> pendientes(List<Producto> bienes) {
        return pendientes(bienes, null);
    }

    /** @param enResguardo the bienes in a resguardo in force; null when they could not be read
     *                     (then nobody is said to be «sin resguardo») */
    static List<Pendiente> pendientes(List<Producto> bienes, java.util.Set<String> enResguardo) {
        List<Pendiente> out = new ArrayList<>();
        for (Producto p : bienes) {
            if (p.getFechaBaja() != null) continue;
            List<String> falta = new ArrayList<>(3);
            if (enResguardo != null && !enResguardo.contains(p.getId())) falta.add(SIN_RESGUARDO);
            if (p.getResguardante() == null || p.getResguardante().isBlank()) falta.add(SIN_RESGUARDANTE);
            if (!p.isEtiquetado()) falta.add(SIN_ETIQUETAR);
            if (!falta.isEmpty()) out.add(new Pendiente(p, String.join(" · ", falta)));
        }
        return out;
    }

    static long contar(List<Pendiente> pendientes, String falta) {
        return pendientes.stream().filter(x -> x.falta().contains(falta)).count();
    }

    /** What Patrimonio has to do: sin resguardo or sin resguardante (the labels are confirmed
     *  by the áreas). */
    static long dePatrimonio(List<Pendiente> pendientes) {
        return pendientes.stream().filter(x -> x.falta().contains(SIN_RESGUARDO) || x.falta().contains(SIN_RESGUARDANTE)).count();
    }

    private final Supplier<javafx.scene.Node> okPlaceholder;
    private final TableView<Pendiente> tabla;
    private final TableView<PorArea> tablaAreas;
    private final ToggleGroup vistas = new ToggleGroup();
    private final ToggleButton porAreaBtn = new ToggleButton("Por área");
    private final ToggleButton porBienBtn = new ToggleButton("Por bien");
    private final HBox barraVistas;
    private final StackPane contenido;
    private boolean vistaElegida;

    AlertasPatrimonialesSection(Supplier<javafx.scene.Node> okPlaceholder,
                                Consumer<Producto> abrirBien, Consumer<String> abrirArea) {
        this.okPlaceholder = okPlaceholder;
        this.tabla = buildTable(abrirBien);
        this.tablaAreas = buildTablaAreas(abrirArea);

        // Two ways to read the same pendientes: grouped by área (what to chase,
        // and whom) or one row per bien.
        for (ToggleButton b : List.of(porAreaBtn, porBienBtn)) {
            b.setToggleGroup(vistas);
            b.getStyleClass().add("filter-chip");
            b.setOnAction(e -> vistaElegida = true);
        }
        Label ayuda = new Label();
        ayuda.getStyleClass().add("muted-sm");
        barraVistas = new HBox(8, porAreaBtn, porBienBtn, ayuda);
        barraVistas.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        // From saying how many to fixing them, without looking for where.
        if (com.sibim.session.Permisos.editaFichaCompleta())
            barraVistas.getChildren().addAll(
                resolver("Etiquetas por área", "mdi2t-tag-multiple-outline", "productos", "etiquetas"),
                resolver("Resguardos en lote", "mdi2c-clipboard-multiple-outline", "resguardos", "lote"));
        contenido = new StackPane(tablaAreas, tabla);
        vistas.selectedToggleProperty().addListener((obs, antes, ahora) -> {
            if (ahora == null) { vistas.selectToggle(antes); return; }   // one stays selected
            boolean areas = ahora == porAreaBtn;
            tablaAreas.setVisible(areas); tablaAreas.setManaged(areas);
            tabla.setVisible(!areas);     tabla.setManaged(!areas);
            ayuda.setText(areas ? "Doble clic en un área para ver sus bienes en Bienes"
                                : "Doble clic en un bien para ver su detalle");
        });
        vistas.selectToggle(porBienBtn);
    }

    private static javafx.scene.control.Button resolver(String texto, String icono, String vista, String abrir) {
        javafx.scene.control.Button b = new javafx.scene.control.Button(texto, new org.kordamp.ikonli.javafx.FontIcon(icono));
        b.getStyleClass().add("btn-secondary");
        b.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        b.setOnAction(e -> {
            com.sibim.session.NavigationContext.setPendingAbrir(abrir);
            MainController mc = MainController.getInstance();
            if (mc != null) mc.navigateToView(vista);
        });
        return b;
    }

    /** The two tables, one on top of the other; the chosen view is the visible one. */
    javafx.scene.Node contenido() { return contenido; }

    /** "Por área / Por bien" chips, for the panel's toolbar. */
    javafx.scene.Node barraVistas() { return barraVistas; }

    List<TableView<?>> tablas() { return List.of(tabla, tablaAreas); }

    /** @param q the search text in lower case, blank when not searching
     *  @return how many pendientes match */
    int mostrar(List<Pendiente> todos, String q) {
        List<Pendiente> visibles = q.isBlank() ? todos : todos.stream()
            .filter(x -> contiene(x.producto().getNombre(), q) || contiene(x.producto().getCodigo(), q)
                      || contiene(x.producto().getArea(), q))
            .toList();
        tabla.setPlaceholder(q.isBlank() || todos.isEmpty() ? okPlaceholder.get() : EmptyStateUtil.buildSearch(q));
        tabla.getItems().setAll(visibles);
        tablaAreas.getItems().setAll(porArea(visibles));
        // Until the user picks a view, a long list opens grouped by área.
        if (!vistaElegida) vistas.selectToggle(todos.size() > UMBRAL_POR_AREA ? porAreaBtn : porBienBtn);
        return visibles.size();
    }

    private static boolean contiene(String texto, String q) {
        return texto != null && texto.toLowerCase().contains(q);
    }

    private static TableView<PorArea> buildTablaAreas(Consumer<String> abrirArea) {
        TableView<PorArea> t = new TableView<>();
        t.getStyleClass().add("data-table");
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        t.setId("tablePatrimonialesPorArea");
        TableColumn<PorArea, String> cArea = new TableColumn<>("Área");
        cArea.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().area()));
        cArea.setPrefWidth(340);
        TableColumn<PorArea, Number> cBienes = new TableColumn<>("Bienes con pendientes");
        cBienes.setCellValueFactory(c -> new javafx.beans.property.SimpleLongProperty(c.getValue().bienes()));
        TableColumn<PorArea, Number> cSinRes = new TableColumn<>("Sin resguardo");
        cSinRes.setCellValueFactory(c -> new javafx.beans.property.SimpleLongProperty(c.getValue().sinResguardo()));
        TableColumn<PorArea, Number> cResg = new TableColumn<>("Sin resguardante");
        cResg.setCellValueFactory(c -> new javafx.beans.property.SimpleLongProperty(c.getValue().sinResguardante()));
        TableColumn<PorArea, Number> cEtiq = new TableColumn<>("Sin etiquetar");
        cEtiq.setCellValueFactory(c -> new javafx.beans.property.SimpleLongProperty(c.getValue().sinEtiquetar()));
        t.getColumns().addAll(java.util.List.of(cArea, cBienes, cSinRes, cResg, cEtiq));
        t.setOnMouseClicked(e -> {
            PorArea sel = t.getSelectionModel().getSelectedItem();
            if (e.getClickCount() == 2 && sel != null) abrirArea.accept(sel.area());
        });
        t.setOnKeyPressed(e -> {
            PorArea sel = t.getSelectionModel().getSelectedItem();
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER && sel != null) { abrirArea.accept(sel.area()); e.consume(); }
        });
        return t;
    }

    private static TableView<Pendiente> buildTable(Consumer<Producto> abrirBien) {
        TableView<Pendiente> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setId("tablePatrimoniales");

        TableColumn<Pendiente, String> colNombre = new TableColumn<>("Bien");
        colNombre.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().producto().getNombre()));
        colNombre.setPrefWidth(240);
        TableColumn<Pendiente, String> colCodigo = new TableColumn<>("Código");
        colCodigo.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().producto().getCodigo()));
        colCodigo.setPrefWidth(110);
        TableColumn<Pendiente, String> colArea = new TableColumn<>("Área");
        colArea.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().producto().getArea()));
        colArea.setPrefWidth(200);
        TableColumn<Pendiente, String> colFalta = new TableColumn<>("Qué falta");
        colFalta.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().falta()));
        colFalta.setPrefWidth(200);
        table.getColumns().addAll(java.util.List.of(colNombre, colCodigo, colArea, colFalta));

        table.setOnMouseClicked(e -> {
            Pendiente sel = table.getSelectionModel().getSelectedItem();
            if (e.getClickCount() == 2 && sel != null) abrirBien.accept(sel.producto());
        });
        table.setOnKeyPressed(e -> {
            Pendiente sel = table.getSelectionModel().getSelectedItem();
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER && sel != null) { abrirBien.accept(sel.producto()); e.consume(); }
        });
        return table;
    }
}
