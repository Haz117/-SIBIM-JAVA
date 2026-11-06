package com.sibim.controller;

import com.sibim.config.Areas;
import com.sibim.controller.dialogs.RevisarBienesDialog;
import com.sibim.service.AvanceAreasService.Avance;
import com.sibim.session.SessionManager;
import com.sibim.util.AccessibilityUtils;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * The avance of the áreas in the Organigrama (5-nov-2026): three steps each —
 * 1 sign in, 2 review its bienes (and fill in what is missing), 3 confirm the
 * labels. A small 1·2·3 chip on every área, a summary card, the detail of one
 * área and a table of all of them.
 */
final class AvanceAreaView {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    static final String[] NOMBRES = {"Iniciar sesión", "Revisar sus bienes", "Confirmar etiquetas"};

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

    /** The three phases in words, as the tooltip and the detail say them. */
    static String detalle(Avance a) {
        return "1. " + NOMBRES[0] + ": " + paso1(a) + "\n"
            + "2. " + NOMBRES[1] + ": " + paso2(a) + "\n"
            + "3. " + NOMBRES[2] + ": " + paso3(a);
    }

    private static String paso1(Avance a) {
        return a.entro() != null ? "ya entró (último acceso " + a.entro().format(FECHA) + ")" : "todavía no entra a SIBIM";
    }

    private static String paso2(Avance a) {
        if (a.bienes() == 0) return "no tiene bienes";
        return a.revisados() + " de " + a.bienes() + " revisados"
            + (a.observados() > 0 ? " (" + a.observados() + " dice que no son suyos)" : "")
            + "; " + a.conDatos() + " con foto, ubicación y estado físico";
    }

    private static String paso3(Avance a) {
        if (a.bienes() == 0) return "no tiene bienes";
        return a.etiquetados() + " de " + a.bienes() + " con etiqueta";
    }

    /** One área: its three phases, with the way to move each one forward. */
    static void mostrarArea(Avance a, Runnable alCambiar) {
        Dialog<ButtonType> dlg = new Dialog<>();
        DialogUtil.applyOwner(dlg);
        dlg.setTitle("Avance de " + a.area());
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        DialogUtil.applyStylesheet(dlg.getDialogPane());
        HBox header = DialogUtil.gradientHeader("mdi2s-stairs-up", "Avance de " + a.area(),
            a.faseActual() == 4 ? "Terminó las tres fases" : "Va en la fase " + a.faseActual() + " de 3",
            AppColors.PRIMARY, AppColors.PRIMARY_D);
        boolean puede = SessionManager.isAreaAccessible(a.area());
        VBox filas = new VBox(14,
            fila(a, 1, paso1(a), null, null),
            fila(a, 2, paso2(a), puede && a.bienes() > 0 ? "Revisar sus bienes" : null,
                () -> { dlg.close(); RevisarBienesDialog.show(a.area(),
                    a.observados() > 0 && SessionManager.isAdmin() ? RevisarBienesDialog.Vista.OBSERVADOS
                        : RevisarBienesDialog.Vista.POR_REVISAR, alCambiar); }),
            fila(a, 3, paso3(a), puede && a.bienes() > 0 ? "Confirmar etiquetas" : null,
                () -> { dlg.close(); RevisarBienesDialog.show(a.area(), RevisarBienesDialog.Vista.SIN_ETIQUETA, alCambiar); }));
        Label ayuda = new Label(a.entro() == null
            ? "Para la fase 1, el área entra a SIBIM con su cuenta (Configuración › Cuentas por área)."
            : "Las fases 2 y 3 las hace el área desde su Inicio («Pendientes de tu área»); Patrimonio puede ver y corregir aquí.");
        ayuda.getStyleClass().add("muted-sm");
        ayuda.setWrapText(true);
        VBox cuerpo = new VBox(16, filas, ayuda);
        cuerpo.setPadding(new Insets(16, 24, 12, 24));
        dlg.getDialogPane().setContent(new VBox(header, cuerpo));
        dlg.getDialogPane().setPrefWidth(560);
        dlg.showAndWait();
    }

    private static Node fila(Avance a, int fase, String estado, String accion, Runnable alAccion) {
        Label n = circulo(a, fase);
        Label titulo = new Label("Fase " + fase + " · " + NOMBRES[fase - 1]);
        titulo.getStyleClass().add("avance-fila-titulo");
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        Label pct = new Label(Math.round(a.avance(fase) * 100) + " %");
        pct.getStyleClass().add("avance-texto");
        HBox arriba = new HBox(8, n, titulo, hueco, pct);
        arriba.setAlignment(Pos.CENTER_LEFT);
        com.sibim.util.BarraAvance barra = new com.sibim.util.BarraAvance(a.avance(fase));
        Label texto = new Label(estado);
        texto.getStyleClass().add("muted-sm");
        texto.setWrapText(true);
        VBox caja = new VBox(5, arriba, barra, texto);
        if (accion != null) {
            Button b = new Button(accion);
            b.getStyleClass().addAll("btn-secondary", "btn-sm");
            b.setOnAction(e -> alAccion.run());
            caja.getChildren().add(b);
        }
        return caja;
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
        HBox fases = new HBox(18,
            resumenFase(1, entraron + " de " + mias.size() + " áreas ya entraron", (double) entraron / mias.size()),
            resumenFase(2, revisados + " de " + miles(bienes) + " bienes revisados", bienes == 0 ? 0 : (double) revisados / bienes),
            resumenFase(3, etiquetados + " de " + miles(bienes) + " bienes con etiqueta", bienes == 0 ? 0 : (double) etiquetados / bienes));
        card.getChildren().addAll(encabezado, fases);
    }

    private static String miles(int n) {
        return String.format(Locale.forLanguageTag("es-MX"), "%,d", n);
    }

    private static Node resumenFase(int fase, String texto, double avance) {
        Label n = new Label(String.valueOf(fase));
        n.getStyleClass().addAll("avance-paso", avance >= 1 ? "avance-paso-hecho" : avance > 0 ? "avance-paso-curso" : "avance-paso-pendiente");
        Label t = new Label(NOMBRES[fase - 1]);
        t.getStyleClass().add("avance-fila-titulo");
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        Label pct = new Label(Math.round(avance * 100) + " %");
        pct.getStyleClass().add("avance-texto");
        HBox arriba = new HBox(6, n, t, hueco, pct);
        arriba.setAlignment(Pos.CENTER_LEFT);
        com.sibim.util.BarraAvance barra = new com.sibim.util.BarraAvance(avance);
        Label detalle = new Label(texto);
        detalle.getStyleClass().add("muted-sm");
        VBox caja = new VBox(4, arriba, barra, detalle);
        HBox.setHgrow(caja, Priority.ALWAYS);
        caja.setMaxWidth(Double.MAX_VALUE);
        caja.setMinWidth(160);
        return caja;
    }

    /** The filters of the table, by the step each área is on. */
    private enum Filtro {
        TODAS("Todas", null), ENTRAR("Falta entrar", null), REVISAR("Revisando bienes", "filter-chip-amber"),
        ETIQUETAR("Poniendo etiquetas", "filter-chip-purple"), TERMINARON("Terminaron", "filter-chip-teal");

        final String texto, estilo;
        Filtro(String texto, String estilo) { this.texto = texto; this.estilo = estilo; }

        boolean acepta(Avance a) {
            return this == TODAS || ordinal() == a.faseActual();
        }
    }

    /** Every área (of the caller) in a table, in organigrama order: chips by the
     *  step each one is on, a search, and the three steps of each one. */
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
        dlg.getDialogPane().setPrefSize(Math.min(1120, pantalla.getWidth() * 0.94), Math.min(720, pantalla.getHeight() * 0.9));
        dlg.setResizable(true);
        HBox header = DialogUtil.gradientHeader("mdi2s-stairs-up", "Avance de las áreas",
            FormatUtils.plural(filas.size(), "área", "áreas")
                + " · cada una: 1 entrar a SIBIM, 2 revisar sus bienes, 3 confirmar etiquetas",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        javafx.collections.transformation.FilteredList<Avance> visibles =
            new javafx.collections.transformation.FilteredList<>(FXCollections.observableArrayList(filas));
        javafx.collections.transformation.SortedList<Avance> ordenadas = new javafx.collections.transformation.SortedList<>(visibles);
        TableView<Avance> tabla = new TableView<>(ordenadas);
        ordenadas.comparatorProperty().bind(tabla.comparatorProperty());
        tabla.getStyleClass().addAll("data-table", "avance-tabla");
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        tabla.setFixedCellSize(50);
        tabla.getColumns().add(columna("Área", 270, Comparator.comparing(Avance::area), AvanceAreaView::celdaArea));
        tabla.getColumns().add(columna("Va en", 230,
            Comparator.comparingInt(Avance::faseActual).thenComparingDouble(a -> a.avance(Math.min(3, a.faseActual()))),
            AvanceAreaView::celdaPasos));
        tabla.getColumns().add(columna("Entró", 110,
            Comparator.comparing(Avance::entro, Comparator.nullsFirst(Comparator.naturalOrder())), AvanceAreaView::celdaEntro));
        tabla.getColumns().add(columna("Bienes revisados", 230, Comparator.comparingDouble(a -> a.avance(2)), a -> celdaConteo(a, 2)));
        tabla.getColumns().add(columna("Con etiqueta", 190, Comparator.comparingDouble(a -> a.avance(3)), a -> celdaConteo(a, 3)));
        Label vacio = new Label("Ningún área en este paso");
        vacio.getStyleClass().add("muted");
        tabla.setPlaceholder(vacio);
        tabla.setRowFactory(tv -> {
            TableRow<Avance> row = new TableRow<>();
            row.setOnMouseClicked(e -> { if (e.getClickCount() == 2 && !row.isEmpty()) mostrarArea(row.getItem(), alCambiar); });
            return row;
        });
        tabla.setOnKeyPressed(e -> {
            Avance sel = tabla.getSelectionModel().getSelectedItem();
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER && sel != null) { e.consume(); mostrarArea(sel, alCambiar); }
        });
        VBox.setVgrow(tabla, Priority.ALWAYS);

        // Chips with how many áreas are on each step, and a search by name.
        ToggleGroup grupo = new ToggleGroup();
        HBox chips = new HBox(5);
        chips.setAlignment(Pos.CENTER_LEFT);
        TextField buscar = new TextField();
        buscar.setPromptText("Buscar área…");
        buscar.getStyleClass().add("search-field");
        buscar.setPrefWidth(260);
        Runnable filtrar = () -> {
            Filtro f = grupo.getSelectedToggle() == null ? Filtro.TODAS : (Filtro) grupo.getSelectedToggle().getUserData();
            String texto = plano(buscar.getText());
            visibles.setPredicate(a -> f.acepta(a) && (texto.isEmpty() || plano(a.area()).contains(texto)));
        };
        for (Filtro f : Filtro.values()) {
            long n = filas.stream().filter(f::acepta).count();
            ToggleButton chip = new ToggleButton(f.texto + " (" + n + ")");
            chip.getStyleClass().add("filter-chip");
            if (f.estilo != null) chip.getStyleClass().add(f.estilo);
            chip.setUserData(f);
            chip.setToggleGroup(grupo);
            chip.setSelected(f == Filtro.TODAS);
            chip.setOnAction(e -> filtrar.run());
            chips.getChildren().add(chip);
        }
        // A chip pressed again stays on: one of them is always the view.
        grupo.selectedToggleProperty().addListener((o, antes, ahora) -> { if (ahora == null && antes != null) antes.setSelected(true); });
        buscar.textProperty().addListener((o, a, b) -> filtrar.run());
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox filtros = new HBox(10, chips, hueco, buscar);
        filtros.setAlignment(Pos.CENTER_LEFT);

        Label ayuda = new Label("Doble clic (o Enter) en un área para ver su detalle y revisar sus bienes. "
            + "Las áreas sin bienes solo necesitan entrar.");
        ayuda.getStyleClass().add("muted-sm");
        VBox cuerpo = new VBox(12);
        Node aviso = avisoSinEntrar(filas, dlg);
        if (aviso != null) cuerpo.getChildren().add(aviso);
        cuerpo.getChildren().addAll(filtros, tabla, ayuda);
        cuerpo.setPadding(new Insets(0, 22, 14, 22));
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        dlg.getDialogPane().setContent(new VBox(12, header, cuerpo));
        dlg.showAndWait();
    }

    /** When no área has entered yet every row says the same: say why once, at
     *  the top, with the way to start (each área needs its account). */
    private static Node avisoSinEntrar(List<Avance> filas, Dialog<?> dlg) {
        if (filas.isEmpty() || filas.stream().anyMatch(Avance::fase1)) return null;
        FontIcon icono = new FontIcon("mdi2i-information-outline");
        icono.setIconSize(20);
        icono.setIconColor(javafx.scene.paint.Color.web("#8A5A12"));
        Label titulo = new Label("Ninguna área ha entrado a SIBIM todavía");
        titulo.getStyleClass().add("avance-fila-titulo");
        Label texto = new Label("Por eso todas van en el paso 1. Cada área empieza al entrar con su propia cuenta; "
            + "cuando entre, aquí verás cómo revisa sus bienes y confirma sus etiquetas.");
        texto.getStyleClass().add("muted-sm");
        texto.setWrapText(true);
        VBox textos = new VBox(2, titulo, texto);
        HBox.setHgrow(textos, Priority.ALWAYS);
        HBox caja = new HBox(12, icono, textos);
        caja.setAlignment(Pos.CENTER_LEFT);
        caja.getStyleClass().add("cnt-aviso");
        MainController main = MainController.getInstance();
        if (SessionManager.isAdmin() && main != null) {
            Button cuentas = new Button("Crear las cuentas por área");
            cuentas.getStyleClass().addAll("btn-secondary", "btn-sm");
            cuentas.setGraphic(new FontIcon("mdi2a-account-multiple-plus-outline"));
            cuentas.setMinWidth(Region.USE_PREF_SIZE);
            Tooltip.install(cuentas, new Tooltip("Abre Configuración: ahí está el botón «Cuentas por área»"));
            cuentas.setOnAction(e -> { dlg.close(); main.navigateToView("configuracion"); });
            caja.getChildren().add(cuentas);
        }
        return caja;
    }

    private static TableColumn<Avance, Avance> columna(String titulo, double ancho, Comparator<Avance> orden,
                                                       java.util.function.Function<Avance, Node> celda) {
        TableColumn<Avance, Avance> c = new TableColumn<>(titulo);
        c.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue()));
        c.setComparator(orden);
        c.setPrefWidth(ancho);
        c.setMinWidth(Math.min(ancho, 110));
        c.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Avance a, boolean empty) {
                super.updateItem(a, empty);
                setText(null);
                setGraphic(empty || a == null ? null : celda.apply(a));
            }
        });
        return c;
    }

    private static Node celdaArea(Avance a) {
        Label nombre = new Label(a.area());
        nombre.getStyleClass().add("avance-area-nombre");
        nombre.setTooltip(new Tooltip(a.area()));
        Label bienes = new Label(a.bienes() == 0 ? "Sin bienes" : FormatUtils.plural(a.bienes(), "bien", "bienes"));
        bienes.getStyleClass().add("muted-sm");
        VBox v = new VBox(1, nombre, bienes);
        v.setAlignment(Pos.CENTER_LEFT);
        return v;
    }

    /** The three steps joined by a line, and where the área is in words. */
    private static Node celdaPasos(Avance a) {
        HBox pasos = new HBox();
        pasos.setAlignment(Pos.CENTER_LEFT);
        for (int f = 1; f <= 3; f++) {
            if (f > 1) {
                Region linea = new Region();
                linea.getStyleClass().add("avance-linea");
                if (f <= a.faseActual()) linea.getStyleClass().add("avance-linea-hecha");
                pasos.getChildren().add(linea);
            }
            Label c = circulo(a, f);
            c.getStyleClass().add("avance-paso-grande");
            pasos.getChildren().add(c);
        }
        Label estado = new Label(estado(a));
        estado.getStyleClass().add("avance-estado");
        estado.setMinWidth(Region.USE_PREF_SIZE);
        int f = a.faseActual();
        if (f == 4) estado.getStyleClass().add("avance-estado-hecho");
        else if (f > 1) estado.getStyleClass().add("avance-estado-curso");
        HBox h = new HBox(10, pasos, estado);
        h.setAlignment(Pos.CENTER_LEFT);
        Tooltip t = new Tooltip(detalle(a));
        t.setShowDelay(javafx.util.Duration.millis(300));
        Tooltip.install(h, t);
        return h;
    }

    /** "Falta que entre", "Por revisar", "Revisando · 35 %", "Terminó". */
    static String estado(Avance a) {
        return switch (a.faseActual()) {
            case 1 -> "Falta que entre";
            case 2 -> a.revisados() == 0 ? "Por revisar" : "Revisando · " + Math.round(a.avance(2) * 100) + " %";
            case 3 -> a.etiquetados() == 0 ? "Por etiquetar" : "Etiquetando · " + Math.round(a.avance(3) * 100) + " %";
            default -> a.bienes() == 0 ? "Entró · sin bienes" : "Terminó";
        };
    }

    private static Node celdaEntro(Avance a) {
        Label l = new Label(a.entro() == null ? "Nunca" : haceCuanto(a.entro()));
        l.getStyleClass().add(a.entro() == null ? "avance-cuenta-cero" : "avance-cuenta");
        if (a.entro() != null) Tooltip.install(l, new Tooltip("Último acceso: " + FormatUtils.formatDateTime(a.entro())));
        return l;
    }

    /** "Hoy", "Ayer", "Hace 5 días", or the date when it was more than a month ago. */
    static String haceCuanto(java.time.LocalDateTime cuando) {
        long dias = java.time.temporal.ChronoUnit.DAYS.between(cuando.toLocalDate(), java.time.LocalDate.now());
        if (dias <= 0) return "Hoy";
        if (dias == 1) return "Ayer";
        if (dias < 31) return "Hace " + dias + " días";
        return cuando.format(FECHA);
    }

    /** Reviewed (2) or labelled (3): a short bar with "12 de 75", and how many
     *  the área says are not theirs. */
    private static Node celdaConteo(Avance a, int fase) {
        if (a.bienes() == 0) {
            Label l = new Label("—");
            l.getStyleClass().add("avance-cuenta-cero");
            return l;
        }
        int hechos = fase == 2 ? a.revisados() : a.etiquetados();
        com.sibim.util.BarraAvance barra = new com.sibim.util.BarraAvance(a.avance(fase));
        barra.setPrefWidth(90);
        barra.setMaxWidth(90);
        barra.setMinWidth(50);
        Label n = new Label(hechos + " de " + a.bienes());
        n.getStyleClass().add(hechos == 0 ? "avance-cuenta-cero" : hechos >= a.bienes() ? "avance-estado-hecho" : "avance-cuenta");
        n.setMinWidth(Region.USE_PREF_SIZE);
        HBox h = new HBox(8, barra, n);
        h.setAlignment(Pos.CENTER_LEFT);
        if (fase == 2 && a.observados() > 0) {
            Label obs = new Label(a.observados() + " no son suyos");
            obs.getStyleClass().add("avance-observados");
            obs.setMinWidth(Region.USE_PREF_SIZE);
            h.getChildren().add(obs);
        }
        return h;
    }

    private static String plano(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
    }
}
