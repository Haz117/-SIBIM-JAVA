package com.sibim.controller;

import com.sibim.config.Areas;
import com.sibim.controller.dialogs.RevisarBienesDialog;
import com.sibim.service.AvanceAreasService.Avance;
import com.sibim.session.SessionManager;
import com.sibim.util.AccessibilityUtils;
import com.sibim.util.AppColors;
import com.sibim.util.BarraAvance;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * The avance of the áreas in the Organigrama (5-nov-2026): three steps each —
 * 1 sign in, 2 review its bienes (and fill in what is missing), 3 confirm the
 * labels. A small 1·2·3 chip on every área, a summary card, the detail of one
 * área and a window with all of them.
 */
final class AvanceAreaView {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    static final String[] NOMBRES = {"Entrar a SIBIM", "Revisar sus bienes", "Confirmar etiquetas"};

    private AvanceAreaView() {}

    /** The 1·2·3 chip of an área, opening its detail on click. */
    static Node chip(Avance a, Runnable alCambiar) {
        HBox pasos = new HBox(3);
        pasos.setAlignment(Pos.CENTER_LEFT);
        for (int f = 1; f <= 3; f++) pasos.getChildren().add(circulo(a, f));
        Label texto = new Label(a.resumen());
        texto.getStyleClass().add("avance-texto");
        if (a.faseActual() == 4) texto.getStyleClass().add("avance-texto-hecho");
        HBox chip = new HBox(6, pasos, texto);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("avance-chip");
        chip.setMinWidth(Region.USE_PREF_SIZE);
        chip.setOnMouseClicked(e -> { e.consume(); mostrarArea(a, alCambiar); });
        Tooltip t = new Tooltip(detalle(a) + "\n\nClic para ver el avance de " + a.area());
        t.setShowDelay(javafx.util.Duration.millis(300));
        Tooltip.install(chip, t);
        AccessibilityUtils.asButton(chip, "Avance de " + a.area() + ": " + a.resumen());
        return chip;
    }

    private static Label circulo(Avance a, int fase) {
        // Without bienes there is nothing to review or label: a dash, not a tick.
        if (fase > 1 && a.bienes() == 0) {
            Label c = new Label("–");
            c.getStyleClass().addAll("avance-paso", "avance-paso-pendiente");
            return c;
        }
        boolean hecho = fase == 1 ? a.fase1() : fase == 2 ? a.fase2() : a.fase3();
        double avance = a.avance(fase);
        Label c = new Label(hecho ? "✓" : String.valueOf(fase));
        c.getStyleClass().addAll("avance-paso", hecho ? "avance-paso-hecho" : avance > 0 ? "avance-paso-curso" : "avance-paso-pendiente");
        return c;
    }

    /** The three steps in words, as the tooltip and the detail say them. */
    static String detalle(Avance a) {
        return "1. " + NOMBRES[0] + ": " + paso1(a) + "\n"
            + "2. " + NOMBRES[1] + ": " + paso2(a) + "\n"
            + "3. " + NOMBRES[2] + ": " + paso3(a);
    }

    private static String paso1(Avance a) {
        if (a.entro() != null) return "ya entró (último acceso " + a.entro().format(FECHA) + ")";
        if (a.sinCuenta()) return "todavía no tiene cuenta para entrar";
        if (Boolean.TRUE.equals(a.conCuenta())) return "ya tiene cuenta, pero todavía no entra";
        return "todavía no entra a SIBIM";
    }

    private static String paso2(Avance a) {
        if (a.bienes() == 0) return "no tiene bienes";
        return a.revisados() + " de " + a.bienes() + " revisados"
            + (a.observados() > 0 ? " (el área dice que " + a.observados() + (a.observados() == 1 ? " no es suyo)" : " no son suyos)") : "")
            + "; " + a.conDatos() + " con foto, ubicación y estado físico";
    }

    private static String paso3(Avance a) {
        if (a.bienes() == 0) return "no tiene bienes";
        return a.etiquetados() + " de " + a.bienes() + " con etiqueta";
    }

    /** One área: its three steps one under the other, each with its bar, where it is and
     *  the way to move it forward; the button of the step it is on stands out. */
    static void mostrarArea(Avance a, Runnable alCambiar) {
        Dialog<ButtonType> dlg = new Dialog<>();
        DialogUtil.applyOwner(dlg);
        dlg.setTitle("Avance de " + a.area());
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        DialogUtil.applyStylesheet(dlg.getDialogPane());
        HBox header = DialogUtil.gradientHeader("mdi2s-stairs-up", a.area(),
            (a.faseActual() == 4 ? "Terminó los tres pasos" : "Va en el paso " + a.faseActual() + " de 3")
                + " · " + (a.bienes() == 0 ? "sin bienes" : cuantos(a.bienes(), "bien", "bienes")),
            AppColors.PRIMARY, AppColors.PRIMARY_D);
        boolean puede = SessionManager.isAreaAccessible(a.area());
        MainController main = MainController.getInstance();
        boolean crearCuenta = a.sinCuenta() && SessionManager.isAdmin() && main != null;
        String observados = a.observados() > 0
            ? "El área dice que " + (a.observados() == 1 ? "1 no es suyo" : a.observados() + " no son suyos") : null;
        VBox pasos = new VBox(
            pasoDetalle(a, 1, mayuscula(paso1(a)), null, crearCuenta ? "Crear su cuenta" : null,
                () -> { dlg.close(); main.navigateToView("configuracion"); }),
            pasoDetalle(a, 2, a.bienes() == 0 ? "No tiene bienes que revisar"
                    : a.revisados() + " de " + a.bienes() + " revisados · " + a.conDatos() + " con foto, ubicación y estado físico",
                observados, puede && a.bienes() > 0
                    ? (observados != null && SessionManager.isAdmin() ? "Ver los que no son suyos" : "Revisar sus bienes") : null,
                () -> { dlg.close(); RevisarBienesDialog.show(a.area(),
                    a.observados() > 0 && SessionManager.isAdmin() ? RevisarBienesDialog.Vista.OBSERVADOS
                        : RevisarBienesDialog.Vista.POR_REVISAR, alCambiar); }),
            pasoDetalle(a, 3, a.bienes() == 0 ? "No tiene bienes que etiquetar" : mayuscula(paso3(a)), null,
                puede && a.bienes() > 0 ? "Confirmar etiquetas" : null,
                () -> { dlg.close(); RevisarBienesDialog.show(a.area(), RevisarBienesDialog.Vista.SIN_ETIQUETA, alCambiar); }));
        pasos.getStyleClass().add("avance-detalle");
        Label ayuda = new Label(a.entro() == null
            ? "Cada área entra a SIBIM con su propia cuenta (Configuración › Usuarios › «Cuentas por área»)."
            : "Los pasos 2 y 3 los hace el área desde su Inicio («Tu avance»); Patrimonio puede verlos y corregirlos aquí.");
        ayuda.getStyleClass().add("muted-sm");
        ayuda.setWrapText(true);
        ayuda.setMinHeight(Region.USE_PREF_SIZE);
        VBox cuerpo = new VBox(14, pasos, ayuda);
        cuerpo.setPadding(new Insets(18, 24, 12, 24));
        dlg.getDialogPane().setContent(new VBox(header, cuerpo));
        dlg.getDialogPane().setPrefWidth(580);
        dlg.showAndWait();
    }

    /** One step of the detail: a badge joined to the next one by a line, then its name,
     *  its bar, where it is, and its button (the primary one when it is the current step). */
    private static Node pasoDetalle(Avance a, int fase, String estado, String nota, String accion, Runnable alAccion) {
        boolean nada = fase > 1 && a.bienes() == 0;
        boolean hecho = !nada && (fase == 1 ? a.fase1() : fase == 2 ? a.fase2() : a.fase3());
        boolean actual = a.faseActual() == fase;
        Label insignia = new Label(hecho ? "✓" : nada ? "–" : String.valueOf(fase));
        insignia.getStyleClass().add("avance-insignia");
        Region linea = new Region();
        linea.getStyleClass().add("avance-detalle-linea");
        if (hecho) linea.getStyleClass().add("avance-detalle-linea-hecha");
        VBox.setVgrow(linea, Priority.ALWAYS);
        VBox izquierda = new VBox(4, insignia);
        izquierda.setAlignment(Pos.TOP_CENTER);
        izquierda.setMinWidth(30);
        if (fase < 3) izquierda.getChildren().add(linea);

        Label titulo = new Label(NOMBRES[fase - 1]);
        titulo.getStyleClass().add("avance-detalle-titulo");
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        Label pct = new Label(nada ? "—" : hecho ? "Hecho" : Math.round(a.avance(fase) * 100) + " %");
        pct.getStyleClass().add(hecho ? "avance-detalle-hecho" : "avance-texto");
        HBox arriba = new HBox(8, titulo, hueco, pct);
        arriba.setAlignment(Pos.CENTER_LEFT);
        BarraAvance barra = new BarraAvance(nada ? 0 : a.avance(fase));
        barra.getStyleClass().add("avance-segmento-" + fase);
        Label texto = new Label(estado);
        texto.getStyleClass().add("avance-detalle-estado");
        texto.setWrapText(true);
        texto.setMinHeight(Region.USE_PREF_SIZE);
        VBox derecha = new VBox(6, arriba, barra, texto);
        if (nota != null) {
            Label n = new Label(nota);
            n.getStyleClass().add("avance-observados");
            derecha.getChildren().add(n);
        }
        if (accion != null) {
            Button b = new Button(accion);
            b.getStyleClass().addAll(actual ? "btn-primary" : "btn-secondary", "btn-sm");
            b.setOnAction(e -> alAccion.run());
            VBox.setMargin(b, new Insets(2, 0, 0, 0));
            derecha.getChildren().add(b);
        }
        derecha.setPadding(new Insets(4, 0, fase < 3 ? 18 : 0, 0));
        HBox.setHgrow(derecha, Priority.ALWAYS);
        HBox h = new HBox(14, izquierda, derecha);
        Filtro estilo = hecho ? Filtro.TERMINARON : actual ? Filtro.values()[fase] : null;
        h.getStyleClass().add(estilo == null ? "avance-pendiente" : "avance-" + estilo.clave);
        if (hecho) h.getStyleClass().add("avance-paso-terminado");
        return h;
    }

    private static String mayuscula(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** The summary card: how many áreas entered, and the bienes reviewed and labelled. */
    static void llenarTarjeta(VBox card, Map<String, Avance> avance, Runnable verTodas) {
        card.getChildren().clear();
        List<Avance> mias = avance.values().stream().filter(a -> SessionManager.isAreaAccessible(a.area())).toList();
        if (mias.isEmpty()) { card.setVisible(false); card.setManaged(false); return; }
        card.setVisible(true);
        card.setManaged(true);
        FontIcon icono = new FontIcon("mdi2s-stairs-up");
        icono.getStyleClass().add("card-icon-indigo");
        Label titulo = new Label("Avance de las áreas");
        titulo.getStyleClass().add("card-title");
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        Button ver = new Button("Ver por área");
        ver.getStyleClass().addAll("btn-secondary", "btn-sm");
        ver.setGraphic(new FontIcon("mdi2t-table-large"));
        ver.setOnAction(e -> verTodas.run());
        HBox encabezado = new HBox(8, icono, titulo, hueco, ver);
        encabezado.setAlignment(Pos.CENTER_LEFT);

        long entraron = mias.stream().filter(Avance::fase1).count();
        int bienes = mias.stream().mapToInt(Avance::bienes).sum();
        int revisados = mias.stream().mapToInt(Avance::revisados).sum();
        int etiquetados = mias.stream().mapToInt(Avance::etiquetados).sum();
        long sinCuenta = mias.stream().filter(Avance::sinCuenta).count();
        HBox fases = new HBox(18,
            resumenFase(1, entraron + " de " + mias.size() + " áreas ya entraron"
                + (sinCuenta > 0 ? " · " + sinCuenta + " sin cuenta" : ""), (double) entraron / mias.size()),
            resumenFase(2, revisados + " de " + miles(bienes) + " bienes revisados", bienes == 0 ? 0 : (double) revisados / bienes),
            resumenFase(3, etiquetados + " de " + miles(bienes) + " bienes con etiqueta", bienes == 0 ? 0 : (double) etiquetados / bienes));
        card.getChildren().addAll(encabezado, fases);
    }

    private static String miles(int n) {
        return String.format(Locale.forLanguageTag("es-MX"), "%,d", n);
    }

    /** "2,422 bienes", "1 bien". */
    private static String cuantos(int n, String uno, String varios) {
        return miles(n) + " " + (n == 1 ? uno : varios);
    }

    private static Node resumenFase(int fase, String texto, double avance) {
        Label n = new Label(avance >= 1 ? "✓" : String.valueOf(fase));
        n.getStyleClass().addAll("avance-insignia", "avance-insignia-chica");
        Label t = new Label(NOMBRES[fase - 1]);
        t.getStyleClass().add("avance-fila-titulo");
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        Label pct = new Label(Math.round(avance * 100) + " %");
        pct.getStyleClass().add("avance-texto");
        HBox arriba = new HBox(6, n, t, hueco, pct);
        arriba.setAlignment(Pos.CENTER_LEFT);
        BarraAvance barra = new BarraAvance(avance);
        Label detalle = new Label(texto);
        detalle.getStyleClass().add("muted-sm");
        VBox caja = new VBox(4, arriba, barra, detalle);
        // The colors of each step, as in «Avance de las áreas».
        caja.getStyleClass().add(avance >= 1 ? "avance-terminaron" : "avance-" + Filtro.values()[fase].clave);
        HBox.setHgrow(caja, Priority.ALWAYS);
        caja.setMaxWidth(Double.MAX_VALUE);
        caja.setMinWidth(160);
        return caja;
    }

    // ── «Avance de las áreas»: every área at once (redesigned 9-nov-2026) ─────────────

    /** The step each área is on: the cards at the top filter by it. */
    enum Filtro {
        TODAS("Todas las áreas", "todas"), ENTRAR("Falta entrar", "entrar"), REVISAR("Revisando sus bienes", "revisar"),
        ETIQUETAR("Poniendo etiquetas", "etiquetar"), TERMINARON("Terminaron", "terminaron");

        final String texto, clave;
        Filtro(String texto, String clave) { this.texto = texto; this.clave = clave; }

        boolean acepta(Avance a) {
            return this == TODAS || ordinal() == a.faseActual();
        }

        static Filtro de(Avance a) { return values()[a.faseActual()]; }
    }

    /** How the list is ordered; by organigrama it also goes in groups by dependencia. */
    enum Orden {
        ORGANIGRAMA("Como el organigrama"), ATRASADAS("Las más atrasadas primero"),
        AVANZADAS("Las más avanzadas primero"), BIENES("Las de más bienes primero"), NOMBRE("Por nombre (A-Z)");

        final String texto;
        Orden(String texto) { this.texto = texto; }
        @Override public String toString() { return texto; }

        /** null for ORGANIGRAMA: the order they come in. */
        Comparator<Avance> comparador() {
            Comparator<Avance> atraso = Comparator.comparingInt(Avance::faseActual)
                .thenComparingDouble(a -> a.avance(Math.min(3, a.faseActual())));
            return switch (this) {
                case ORGANIGRAMA -> null;
                case ATRASADAS -> atraso.thenComparing(Comparator.comparingInt(Avance::bienes).reversed());
                case AVANZADAS -> atraso.reversed().thenComparing(Comparator.comparingInt(Avance::bienes).reversed());
                case BIENES -> Comparator.comparingInt(Avance::bienes).reversed().thenComparing(Avance::area);
                case NOMBRE -> Comparator.comparing(a -> plano(a.area()));
            };
        }
    }

    static final String AUTONOMOS = Areas.AUTONOMOS;

    static String dependenciaDe(String area) { return Areas.dependenciaDe(area); }

    /** The áreas in groups by dependencia (Areas#porDependencia). */
    static LinkedHashMap<String, List<Avance>> agrupar(List<Avance> filas) {
        return Areas.porDependencia(filas, Avance::area);
    }

    private static final double ANCHO_AVANCE = 330, ANCHO_ACCESO = 120, ANCHO_MAXIMO = 1320;

    /** Every área (of the caller): five cards that sum up and filter by step, a search,
     *  an order, and the list grouped by dependencia with each área's three steps in one
     *  bar. One click (or Enter) on an área opens its detail. */
    static void mostrarTodas(Map<String, Avance> avance, Runnable alCambiar) {
        List<Avance> filas = new ArrayList<>();
        for (String area : Areas.getAllAreaNames()) {
            Avance a = avance.get(area);
            if (a != null && SessionManager.isAreaAccessible(area)) filas.add(a);
        }
        Dialog<ButtonType> dlg = new Dialog<>();
        DialogUtil.applyOwner(dlg);
        dlg.setTitle("Avance de las áreas");
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        DialogUtil.applyStylesheet(dlg.getDialogPane());
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dlg.getDialogPane().setPrefSize(Math.min(1180, pantalla.getWidth() * 0.94), Math.min(780, pantalla.getHeight() * 0.9));
        dlg.setResizable(true);
        HBox header = DialogUtil.gradientHeader("mdi2s-stairs-up", "Avance de las áreas",
            FormatUtils.plural(filas.size(), "área", "áreas")
                + " · cada una da tres pasos: entrar a SIBIM, revisar sus bienes y confirmar sus etiquetas",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        // The cards: how many áreas are on each step, and how far all of them have got.
        int total = filas.size();
        long entraron = filas.stream().filter(Avance::fase1).count();
        int bienes = filas.stream().mapToInt(Avance::bienes).sum();
        int revisados = filas.stream().mapToInt(Avance::revisados).sum();
        int etiquetados = filas.stream().mapToInt(Avance::etiquetados).sum();
        Map<Filtro, Long> enPaso = new EnumMap<>(Filtro.class);
        for (Filtro f : Filtro.values()) enPaso.put(f, filas.stream().filter(f::acepta).count());
        ToggleGroup grupo = new ToggleGroup();
        HBox tarjetas = new HBox(10,
            tarjeta(Filtro.TODAS, grupo, null, enPaso.get(Filtro.TODAS),
                cuantos(bienes, "bien", "bienes") + " en total", -1),
            tarjeta(Filtro.ENTRAR, grupo, "1", enPaso.get(Filtro.ENTRAR),
                entraron + " de " + total + " ya entraron", total == 0 ? 0 : (double) entraron / total),
            tarjeta(Filtro.REVISAR, grupo, "2", enPaso.get(Filtro.REVISAR),
                miles(revisados) + " de " + miles(bienes) + " bienes revisados", bienes == 0 ? 0 : (double) revisados / bienes),
            tarjeta(Filtro.ETIQUETAR, grupo, "3", enPaso.get(Filtro.ETIQUETAR),
                miles(etiquetados) + " de " + miles(bienes) + " con etiqueta", bienes == 0 ? 0 : (double) etiquetados / bienes),
            tarjeta(Filtro.TERMINARON, grupo, "✓", enPaso.get(Filtro.TERMINARON),
                "de " + FormatUtils.plural(total, "área", "áreas"), total == 0 ? 0 : (double) enPaso.get(Filtro.TERMINARON) / total));
        grupo.selectToggle(grupo.getToggles().get(0));
        // A card pressed again stays on: one of them is always the view.
        grupo.selectedToggleProperty().addListener((o, antes, ahora) -> { if (ahora == null && antes != null) antes.setSelected(true); });

        TextField buscar = new TextField();
        buscar.setPromptText("Buscar área…");
        buscar.getStyleClass().add("search-field");
        buscar.setPrefWidth(280);
        buscar.setAccessibleText("Buscar área por nombre");
        ComboBox<Orden> orden = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(Orden.values()));
        orden.setValue(Orden.ORGANIGRAMA);
        orden.setAccessibleText("Ordenar las áreas");
        Label ordenLbl = new Label("Ordenar:");
        ordenLbl.getStyleClass().add("muted-sm");
        ordenLbl.setLabelFor(orden);
        Label cuantas = new Label();
        cuantas.getStyleClass().add("muted-sm");
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox barra = new HBox(10, buscar, cuantas, hueco, ordenLbl, orden);
        barra.setAlignment(Pos.CENTER_LEFT);

        // The list: a header with the columns, then the áreas (in groups by dependencia).
        VBox lista = new VBox();
        lista.getStyleClass().add("avance-lista");
        ScrollPane scroll = new ScrollPane(lista);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("avance-lista-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox marco = new VBox(cabeceraLista(), scroll);
        marco.getStyleClass().add("avance-marco");
        VBox.setVgrow(marco, Priority.ALWAYS);

        Map<String, Node> renglones = new HashMap<>();
        Runnable pintar = () -> {
            Filtro f = (Filtro) grupo.getSelectedToggle().getUserData();
            String texto = plano(buscar.getText());
            List<Avance> visibles = filas.stream()
                .filter(f::acepta)
                .filter(a -> texto.isEmpty() || plano(a.area()).contains(texto)
                    || plano(dependenciaDe(a.area())).contains(texto))
                .toList();
            lista.getChildren().clear();
            Comparator<Avance> c = orden.getValue().comparador();
            if (c == null) {
                agrupar(visibles).forEach((g, deEse) -> {
                    lista.getChildren().add(cabeceraGrupo(g, deEse));
                    deEse.forEach(a -> lista.getChildren().add(renglones.computeIfAbsent(a.area(),
                        k -> renglon(a, alCambiar, scroll))));
                });
            } else {
                visibles.stream().sorted(c).forEach(a -> lista.getChildren().add(renglones.computeIfAbsent(a.area(),
                    k -> renglon(a, alCambiar, scroll))));
            }
            if (visibles.isEmpty()) {
                Label vacio = new Label(texto.isEmpty() ? "Ningún área va en este paso" : "Ningún área coincide con «" + buscar.getText().trim() + "»");
                vacio.getStyleClass().add("muted");
                VBox caja = new VBox(vacio);
                caja.setAlignment(Pos.CENTER);
                caja.setPadding(new Insets(40));
                lista.getChildren().add(caja);
            }
            cuantas.setText(visibles.size() == total ? "" : "Mostrando " + visibles.size() + " de " + total);
            scroll.setVvalue(0);
        };
        grupo.selectedToggleProperty().addListener((o, a, b) -> { if (b != null) pintar.run(); });
        buscar.textProperty().addListener((o, a, b) -> pintar.run());
        orden.valueProperty().addListener((o, a, b) -> pintar.run());
        pintar.run();

        Label ayuda = new Label("Un clic (o Enter) en un área abre su detalle, desde donde se revisan sus bienes. "
            + "Las áreas sin bienes solo necesitan entrar.");
        ayuda.getStyleClass().add("muted-sm");
        ayuda.setWrapText(true);
        VBox cuerpo = new VBox(12, tarjetas);
        Node aviso = aviso(filas, dlg);
        if (aviso != null) cuerpo.getChildren().add(aviso);
        cuerpo.getChildren().addAll(barra, marco, ayuda);
        // Maximized on a wide screen the rows stretched until the name and its steps
        // were a screen apart: the content keeps a readable width, centered.
        cuerpo.setMaxWidth(ANCHO_MAXIMO);
        StackPane centro = new StackPane(cuerpo);
        StackPane.setAlignment(cuerpo, Pos.TOP_CENTER);
        centro.setPadding(new Insets(0, 22, 14, 22));
        VBox.setVgrow(centro, Priority.ALWAYS);
        dlg.getDialogPane().setContent(new VBox(12, header, centro));
        dlg.showAndWait();
    }

    /** One card: the step, how many áreas are on it, and how far all of them have got. */
    private static ToggleButton tarjeta(Filtro f, ToggleGroup grupo, String numero, long areas, String detalle, double avance) {
        Label titulo = new Label(f.texto);
        titulo.getStyleClass().add("avance-tarjeta-titulo");
        HBox arriba = new HBox(7);
        arriba.setAlignment(Pos.CENTER_LEFT);
        if (numero != null) {
            Label n = new Label(numero);
            n.getStyleClass().addAll("avance-insignia", "avance-insignia-chica");
            arriba.getChildren().add(n);
        } else {
            FontIcon i = new FontIcon("mdi2f-format-list-bulleted");
            i.getStyleClass().add("avance-tarjeta-icono");
            arriba.getChildren().add(i);
        }
        arriba.getChildren().add(titulo);
        Label cifra = new Label(String.valueOf(areas));
        cifra.getStyleClass().add("avance-tarjeta-cifra");
        Label unidad = new Label(areas == 1 ? "área" : "áreas");
        unidad.getStyleClass().add("avance-tarjeta-unidad");
        HBox numeros = new HBox(5, cifra, unidad);
        numeros.setAlignment(Pos.BASELINE_LEFT);
        Label det = new Label(detalle);
        det.getStyleClass().add("avance-tarjeta-detalle");
        VBox caja = new VBox(4, arriba, numeros, det);
        if (avance >= 0) {
            BarraAvance b = new BarraAvance(avance);
            VBox.setMargin(b, new Insets(4, 0, 0, 0));
            caja.getChildren().add(b);
        }
        ToggleButton t = new ToggleButton();
        t.setGraphic(caja);
        t.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        t.getStyleClass().addAll("avance-tarjeta", "avance-" + f.clave);
        t.setUserData(f);
        t.setToggleGroup(grupo);
        t.setMaxWidth(Double.MAX_VALUE);
        t.setMinWidth(150);
        caja.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(t, Priority.ALWAYS);
        t.setAccessibleText(f.texto + ": " + areas + (areas == 1 ? " área" : " áreas") + ". " + detalle);
        t.setTooltip(new Tooltip(f == Filtro.TODAS ? "Ver todas las áreas" : "Ver solo las áreas en «" + f.texto + "»"));
        return t;
    }

    /** When no área has entered, or some have no account: say it once, at the top, with
     *  the way to start — each área enters with its own account. */
    private static Node aviso(List<Avance> filas, Dialog<?> dlg) {
        if (filas.isEmpty()) return null;
        long sinCuenta = filas.stream().filter(Avance::sinCuenta).count();
        boolean nadieEntro = filas.stream().noneMatch(Avance::fase1);
        if (!nadieEntro && sinCuenta == 0) return null;
        String titulo, texto;
        if (sinCuenta == filas.size()) {
            titulo = "Ninguna área tiene cuenta todavía";
            texto = "Por eso ninguna ha entrado. Créalas todas de una vez: sale un PDF con el usuario y la contraseña "
                + "que le entregas a cada área.";
        } else if (nadieEntro) {
            titulo = "Ninguna área ha entrado a SIBIM todavía";
            texto = sinCuenta > 0
                ? FormatUtils.plural(sinCuenta, "área no tiene", "áreas no tienen") + " cuenta todavía; las demás ya pueden entrar con la suya."
                : "Todas tienen cuenta: falta que cada una entre con la suya. Cuando entren, aquí verás cómo revisan sus bienes.";
        } else {
            titulo = FormatUtils.plural(sinCuenta, "área todavía no tiene", "áreas todavía no tienen") + " cuenta";
            texto = "Sin cuenta no pueden entrar ni revisar sus bienes. Las que faltan se crean sin tocar las que ya existen.";
        }
        FontIcon icono = new FontIcon("mdi2a-account-key-outline");
        icono.setIconSize(22);
        icono.getStyleClass().add("avance-aviso-icono");
        Label t = new Label(titulo);
        t.getStyleClass().add("avance-fila-titulo");
        Label d = new Label(texto);
        d.getStyleClass().add("muted-sm");
        d.setWrapText(true);
        d.setMinHeight(Region.USE_PREF_SIZE);
        VBox textos = new VBox(2, t, d);
        HBox.setHgrow(textos, Priority.ALWAYS);
        HBox caja = new HBox(12, icono, textos);
        caja.setAlignment(Pos.CENTER_LEFT);
        caja.getStyleClass().addAll("cnt-aviso", "avance-aviso");
        MainController main = MainController.getInstance();
        if (SessionManager.isAdmin() && main != null && sinCuenta > 0) {
            Button cuentas = new Button(sinCuenta == filas.size() ? "Crear las cuentas" : "Crear las que faltan");
            cuentas.getStyleClass().addAll("btn-primary", "btn-sm");
            FontIcon mas = new FontIcon("mdi2a-account-multiple-plus-outline");
            mas.getStyleClass().add("avance-aviso-boton-icono");
            cuentas.setGraphic(mas);
            cuentas.setMinWidth(Region.USE_PREF_SIZE);
            cuentas.setTooltip(new Tooltip("Abre Configuración › Usuarios, donde está el botón «Cuentas por área»"));
            cuentas.setOnAction(e -> { dlg.close(); main.navigateToView("configuracion"); });
            caja.getChildren().add(cuentas);
        }
        return caja;
    }

    private static HBox cabeceraLista() {
        Label area = new Label("Área");
        HBox.setHgrow(area, Priority.ALWAYS);
        area.setMaxWidth(Double.MAX_VALUE);
        HBox pasos = new HBox(4);
        for (int f = 1; f <= 3; f++) {
            Label l = new Label(f + " " + (f == 1 ? "Entrar" : f == 2 ? "Revisar bienes" : "Etiquetas"));
            l.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(l, Priority.ALWAYS);
            pasos.getChildren().add(l);
        }
        fijo(pasos, ANCHO_AVANCE);
        Label acceso = new Label("Último acceso");
        fijo(acceso, ANCHO_ACCESO);
        Region flecha = new Region();
        fijo(flecha, 18);
        HBox h = new HBox(16, area, pasos, acceso, flecha);
        h.setAlignment(Pos.CENTER_LEFT);
        h.getStyleClass().add("avance-lista-cabeza");
        // The badge column, so "Área" starts where the names do.
        h.setPadding(new Insets(9, 14, 9, 14 + 30 + 12));
        return h;
    }

    private static HBox cabeceraGrupo(String nombre, List<Avance> areas) {
        Label n = new Label(nombre);
        n.getStyleClass().add("avance-grupo-nombre");
        int bienes = areas.stream().mapToInt(Avance::bienes).sum();
        Label det = new Label(FormatUtils.plural(areas.size(), "área", "áreas") + " · " + cuantos(bienes, "bien", "bienes"));
        det.getStyleClass().add("avance-grupo-detalle");
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        long entraron = areas.stream().filter(Avance::fase1).count();
        long terminaron = areas.stream().filter(a -> a.faseActual() == 4).count();
        Label resumen = new Label(terminaron > 0 ? terminaron + " de " + areas.size() + " terminaron"
            : entraron + " de " + areas.size() + " entraron");
        resumen.getStyleClass().add("avance-grupo-detalle");
        HBox h = new HBox(8, n, det, hueco, resumen);
        h.setAlignment(Pos.CENTER_LEFT);
        h.getStyleClass().add("avance-grupo");
        return h;
    }

    /** One área: where it is (a badge), its name, its three steps in one bar with where it
     *  is in words, when it last entered, and a chevron — the whole row opens its detail. */
    private static HBox renglon(Avance a, Runnable alCambiar, ScrollPane scroll) {
        Filtro f = Filtro.de(a);
        Label insignia = new Label(a.faseActual() == 4 ? "✓" : String.valueOf(a.faseActual()));
        insignia.getStyleClass().add("avance-insignia");

        Label nombre = new Label(a.area());
        nombre.getStyleClass().add("avance-area-nombre");
        Label sub = new Label(a.bienes() == 0 ? "Sin bienes" : cuantos(a.bienes(), "bien", "bienes"));
        sub.getStyleClass().add("muted-sm");
        VBox textos = new VBox(1, nombre, sub);
        textos.setAlignment(Pos.CENTER_LEFT);
        textos.setMinWidth(160);
        HBox.setHgrow(textos, Priority.ALWAYS);
        textos.setMaxWidth(Double.MAX_VALUE);

        HBox segmentos = new HBox(4);
        for (int fase = 1; fase <= 3; fase++) {
            BarraAvance s = new BarraAvance(fase > 1 && a.bienes() == 0 ? 0 : a.avance(fase));
            s.getStyleClass().add("avance-segmento-" + fase);
            if (fase > 1 && a.bienes() == 0) s.getStyleClass().add("avance-segmento-nada");
            HBox.setHgrow(s, Priority.ALWAYS);
            segmentos.getChildren().add(s);
        }
        Label estado = new Label(estadoLargo(a));
        estado.getStyleClass().add("avance-estado");
        HBox abajo = new HBox(8, estado);
        abajo.setAlignment(Pos.CENTER_LEFT);
        if (a.observados() > 0) {
            Label obs = new Label(a.observados() + " no son suyos");
            obs.getStyleClass().add("avance-observados");
            obs.setMinWidth(Region.USE_PREF_SIZE);
            abajo.getChildren().add(obs);
        }
        VBox progreso = new VBox(6, segmentos, abajo);
        progreso.setAlignment(Pos.CENTER_LEFT);
        fijo(progreso, ANCHO_AVANCE);

        Label acceso = new Label(a.entro() == null ? "Nunca" : haceCuanto(a.entro()));
        acceso.getStyleClass().add(a.entro() == null ? "avance-cuenta-cero" : "avance-cuenta");
        if (a.entro() != null) Tooltip.install(acceso, new Tooltip("Último acceso: " + FormatUtils.formatDateTime(a.entro())));
        fijo(acceso, ANCHO_ACCESO);

        FontIcon flecha = new FontIcon("mdi2c-chevron-right");
        flecha.getStyleClass().add("avance-flecha");
        StackPane flechaCaja = new StackPane(flecha);
        fijo(flechaCaja, 18);

        HBox h = new HBox(16, insignia, textos, progreso, acceso, flechaCaja);
        HBox.setMargin(textos, new Insets(0, 0, 0, -4));
        h.setAlignment(Pos.CENTER_LEFT);
        h.getStyleClass().addAll("avance-renglon", "avance-" + f.clave);
        if (a.sinCuenta()) h.getStyleClass().add("avance-sin-cuenta");
        h.setOnMouseClicked(e -> { if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) mostrarArea(a, alCambiar); });
        Tooltip t = new Tooltip(detalle(a) + "\n\nClic para ver su detalle");
        t.setShowDelay(javafx.util.Duration.millis(500));
        Tooltip.install(h, t);
        AccessibilityUtils.asButton(h, a.area() + ": " + estadoLargo(a) + ". " + sub.getText());
        // Up and down move between áreas, as in a table; the list follows the focus.
        h.setOnKeyPressed(e -> {
            if (e.getCode() != KeyCode.DOWN && e.getCode() != KeyCode.UP) return;
            List<Node> hijos = ((Pane) h.getParent()).getChildren();
            int i = hijos.indexOf(h), paso = e.getCode() == KeyCode.DOWN ? 1 : -1;
            for (int j = i + paso; j >= 0 && j < hijos.size(); j += paso) {
                if (hijos.get(j).getStyleClass().contains("avance-renglon")) { hijos.get(j).requestFocus(); break; }
            }
            e.consume();
        });
        h.focusedProperty().addListener((o, antes, ahora) -> { if (ahora) asegurarVisible(scroll, h); });
        return h;
    }

    /** Where the área is, in words: "Sin cuenta todavía", "Revisando sus bienes · 12 de 75". */
    static String estadoLargo(Avance a) {
        return switch (a.faseActual()) {
            case 1 -> a.sinCuenta() ? "Sin cuenta todavía"
                : Boolean.TRUE.equals(a.conCuenta()) ? "Tiene cuenta · aún no entra" : "Falta que entre";
            case 2 -> a.revisados() == 0 ? "Entró · falta revisar sus bienes"
                : "Revisando sus bienes · " + a.revisados() + " de " + a.bienes();
            case 3 -> a.etiquetados() == 0 ? "Bienes revisados · faltan las etiquetas"
                : "Poniendo etiquetas · " + a.etiquetados() + " de " + a.bienes();
            default -> a.bienes() == 0 ? "Entró · no tiene bienes" : "Terminó los tres pasos";
        };
    }

    private static void fijo(Region r, double ancho) {
        r.setMinWidth(ancho);
        r.setPrefWidth(ancho);
        r.setMaxWidth(ancho);
    }

    /** Scrolls just enough for {@code n} to be seen whole. */
    private static void asegurarVisible(ScrollPane scroll, Node n) {
        Node contenido = scroll.getContent();
        double alto = contenido.getBoundsInLocal().getHeight(), visible = scroll.getViewportBounds().getHeight();
        if (alto <= visible) return;
        Bounds b = contenido.sceneToLocal(n.localToScene(n.getBoundsInLocal()));
        double arriba = scroll.getVvalue() * (alto - visible);
        if (b.getMinY() < arriba) scroll.setVvalue(b.getMinY() / (alto - visible));
        else if (b.getMaxY() > arriba + visible) scroll.setVvalue((b.getMaxY() - visible) / (alto - visible));
    }

    /** "Hoy", "Ayer", "Hace 5 días", or the date when it was more than a month ago. */
    static String haceCuanto(java.time.LocalDateTime cuando) {
        long dias = java.time.temporal.ChronoUnit.DAYS.between(cuando.toLocalDate(), java.time.LocalDate.now());
        if (dias <= 0) return "Hoy";
        if (dias == 1) return "Ayer";
        if (dias < 31) return "Hace " + dias + " días";
        return cuando.format(FECHA);
    }

    private static String plano(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
    }
}
