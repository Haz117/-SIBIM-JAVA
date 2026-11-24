package com.sibim.controller;

import com.sibim.util.AnimationUtils;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * «Repisa» (Priority+) for the side menu: as many options as the real height of the sidebar
 * holds, and the rest in «Más», in their order, instead of a scroll bar that hid the last ones
 * (24-nov-2026: at 1366×768 the 16 options and 5 sections didn't fit, and Configuración and
 * Auditoría were only reached by scrolling).
 *
 * <ul>
 *   <li>Measured, not guessed: the menu's own preferred height against the space the sidebar
 *       gives it, every time that space or the menu changes (window, folding a section, the icon
 *       rail, the size of the text, the active page). No fixed breakpoints.</li>
 *   <li>The options leave from the bottom up; a section whose options all left takes its title
 *       with it, and a folded section that doesn't fit goes whole. The page in use, and its
 *       section's title, always stay.</li>
 *   <li>«Más» shows only while something is in it, with how many; its menu lists them by
 *       section, with their icon, counter and shortcut. Enter, Space or ↓ open it, Escape or a
 *       click outside close it.</li>
 *   <li>In the icon rail, less padding first ({@code sidebar-baja}) and «Más» only if even
 *       then they don't fit.</li>
 *   <li>Nothing is rebuilt: the FXML buttons are only shown or hidden; what a role may not see
 *       stays hidden and never reaches «Más».</li>
 * </ul>
 */
final class RepisaLateral {

    static final String CLASE_BAJA = "sidebar-baja";

    private final VBox sidebar;
    private final ScrollPane navScroll;
    private final VBox navList;
    private final List<VBox> secciones;
    private final Function<Button, Optional<NavItem>> itemDe;
    private final Supplier<Button> activo;
    private final BooleanSupplier riel;
    private final Runnable alCambiar;

    private final Button mas = new Button("Más");
    private final Label cuenta = new Label();
    private final StackPane fila;
    private final ContextMenu menu = new ContextMenu();
    /** The rows and whole sections hidden here (never what a role may not see: that stays as it is). */
    private final Set<Node> ocultos = new LinkedHashSet<>();
    private final PauseTransition espera = new PauseTransition(Duration.millis(60));
    private boolean pendiente, calculando;

    /**
     * @param secciones the sections' roots, top to bottom (header HBox + items VBox, as in main.fxml)
     * @param itemDe    what a menu button opens (its label, icon, shortcut and action)
     * @param activo    the button of the page in use
     * @param riel      whether the sidebar is folded to icons
     * @param alCambiar after options came or went (the active-tab marker moves with them)
     */
    RepisaLateral(VBox sidebar, ScrollPane navScroll, List<VBox> secciones, Function<Button, Optional<NavItem>> itemDe,
                  Supplier<Button> activo, BooleanSupplier riel, Runnable alCambiar) {
        this.sidebar = sidebar;
        this.navScroll = navScroll;
        this.navList = (VBox) navScroll.getContent();
        this.secciones = List.copyOf(secciones);
        this.itemDe = itemDe;
        this.activo = activo;
        this.riel = riel;
        this.alCambiar = alCambiar;

        mas.getStyleClass().addAll("nav-btn", "nav-mas");
        mas.setMaxWidth(Double.MAX_VALUE);
        mas.setContentDisplay(ContentDisplay.LEFT);
        mas.setGraphicTextGap(10);
        FontIcon icono = new FontIcon("mdi2d-dots-horizontal");
        icono.getStyleClass().add("nav-icon");
        mas.setGraphic(icono);
        mas.setAccessibleRole(AccessibleRole.MENU_BUTTON);
        mas.setOnAction(e -> abrir());
        mas.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DOWN || e.getCode() == KeyCode.RIGHT) {
                abrir();
                e.consume();
            }
        });
        cuenta.getStyleClass().addAll("alert-nav-badge", "nav-mas-cuenta");
        cuenta.setMouseTransparent(true);
        StackPane.setAlignment(cuenta, Pos.CENTER_RIGHT);
        StackPane.setMargin(cuenta, new Insets(0, 10, 0, 0));
        fila = new StackPane(mas, cuenta);
        fila.setAlignment(Pos.CENTER_RIGHT);
        fila.getStyleClass().add("nav-mas-fila");
        ver(fila, false);
        // Right after the last option that fits, as one more of them: what is left of the
        // height stays below it, as below the last section when everything fits.
        navList.getChildren().add(fila);

        menu.getStyleClass().add("nav-mas-menu");
        menu.setOnShown(e -> animarMenu());
        menu.setOnHidden(e -> mas.getStyleClass().remove("nav-mas-abierto"));

        espera.setOnFinished(e -> recalcular());
        // The space: the window, the status strips, the header and the account row.
        navScroll.viewportBoundsProperty().addListener((o, a, b) -> {
            if (a == null || Math.abs(a.getHeight() - b.getHeight()) > 0.5 || Math.abs(a.getWidth() - b.getWidth()) > 0.5) programar();
        });
        // The menu: a section folded or unfolded (after its animation), the rail, the text size.
        // Its own changes come back here too, and find nothing to do.
        navList.layoutBoundsProperty().addListener((o, a, b) -> {
            if (!calculando) espera.playFromStart();
        });
    }

    /** The button «Más» (its sidebar row also holds the counter). */
    Button boton() { return mas; }

    /** Whether the option of {@code boton} is in «Más» now. */
    boolean estaEnMas(Button boton) {
        if (boton == null) return false;
        for (Node p = boton; p != null && p != navList; p = p.getParent())
            if (ocultos.contains(p)) return true;
        return false;
    }

    /** As soon as the FX thread is free, once however many times it is asked. */
    void programar() {
        if (pendiente) return;
        pendiente = true;
        Platform.runLater(this::recalcular);
    }

    // ── Medir y repartir ──────────────────────────────────────────────────────

    void recalcular() {
        pendiente = false;
        if (calculando || sidebar.getScene() == null) return;
        double visible = navScroll.getViewportBounds().getHeight();
        if (visible <= 0) return;
        calculando = true;
        try {
            Set<Node> antes = new HashSet<>(ocultos);
            int cuantosAntes = enMas().size();
            boolean filaAntes = fila.isManaged();
            mostrarTodo();
            ver(fila, false);

            boolean enRiel = riel.getAsBoolean();
            sidebar.getStyleClass().remove(CLASE_BAJA);
            if (enRiel && altoNatural() > visible + 0.5) sidebar.getStyleClass().add(CLASE_BAJA);

            // «Más» measured in its place: what leaves is what it takes too.
            if (altoNatural() > visible + 0.5) {
                ver(fila, true);
                repartir(visible, enRiel);
            }

            List<Entrada> enMas = enMas();
            boolean conMas = !enMas.isEmpty();
            ver(fila, conMas);
            // While it fits, no scroll bar: a section unfolding (170 ms) would flash one. Only when
            // not even the page in use and «Más» fit (a tiny window) is it allowed back.
            navScroll.setVbarPolicy(altoNatural() <= visible + 0.5 ? ScrollPane.ScrollBarPolicy.NEVER : ScrollPane.ScrollBarPolicy.AS_NEEDED);
            cuenta.setText(String.valueOf(enMas.size()));
            boolean conAviso = enMas.stream().anyMatch(x -> x.contador() != null);
            cuenta.getStyleClass().remove("nav-mas-alerta");
            if (conAviso) cuenta.getStyleClass().add("nav-mas-alerta");
            mas.setAccessibleText("Más opciones del menú: " + enMas.size());
            mas.setTooltip(conMas ? new Tooltip("También: " + String.join(", ", enMas.stream().map(x -> x.item().label()).toList())) : null);
            if (!conMas && menu.isShowing()) menu.hide();

            // Gently: «Más» fades in, its number pulses when it changes, an option that returns fades in.
            if (conMas && !filaAntes) aparecer(fila);
            else if (conMas && cuantosAntes != enMas.size()) latido(cuenta);
            for (Node n : antes) if (!ocultos.contains(n)) aparecer(n);
            if (!antes.equals(ocultos)) alCambiar.run();
        } finally {
            calculando = false;
        }
    }

    /** The menu as it would be laid out now (CSS applied: the rail's padding depends on a class). */
    private double altoNatural() {
        navList.applyCss();
        return navList.prefHeight(-1);
    }

    /** From the bottom up until it fits in {@code presupuesto}, never the page in use nor its title. */
    private void repartir(double presupuesto, boolean enRiel) {
        Button act = activo.get();
        VBox secActiva = act == null ? null : seccionDe(act);
        Node filaActiva = act == null ? null : filaDe(act);
        List<Node[]> orden = new ArrayList<>();          // {node, section root}, top to bottom
        for (VBox s : secciones) {
            if (!s.isVisible() || !s.isManaged()) continue;
            Node cab = cabecera(s);
            if (!enRiel && cab.isManaged()) orden.add(new Node[]{cab, s});
            Pane items = items(s);
            if (items.isManaged() && items.isVisible())
                for (Node f : items.getChildren()) if (f.isManaged() && f.isVisible() && muestraBoton(f)) orden.add(new Node[]{f, s});
        }
        for (int i = orden.size() - 1; i >= 0 && altoNatural() > presupuesto + 0.5; i--) {
            Node n = orden.get(i)[0];
            VBox s = (VBox) orden.get(i)[1];
            if (n == filaActiva || (n == cabecera(s) && s == secActiva)) continue;
            if (n == cabecera(s)) {
                // A folded section (or one already empty): the whole of it.
                ocultar(s);
                continue;
            }
            ocultar(n);
            if (s != secActiva && items(s).getChildren().stream().noneMatch(f -> f.isManaged() && muestraBoton(f)))
                ocultar(s);                          // no title (nor rail divider) over nothing
        }
    }

    /** Only rows and section roots: a section's title is the rail's to show or hide. */
    private void ocultar(Node n) {
        if (!ocultos.add(n)) return;
        n.setVisible(false);
        n.setManaged(false);
    }

    private void mostrarTodo() {
        for (Node n : ocultos) {
            n.setVisible(true);
            n.setManaged(true);
            n.setOpacity(1);
        }
        ocultos.clear();
    }

    // ── «Más» ─────────────────────────────────────────────────────────────────

    /** An option in «Más»: what it opens and its counter (alerts, overdue loans) if it shows one. */
    record Entrada(NavItem item, String contador) {}

    /** The options hidden here that the role may open, top to bottom. */
    List<Entrada> enMas() {
        List<Entrada> out = new ArrayList<>();
        for (VBox s : secciones) {
            boolean todaLaSeccion = ocultos.contains(s);
            if (!todaLaSeccion && (!s.isVisible() || !s.isManaged())) continue;      // the role may not see any of it
            for (Node f : items(s).getChildren()) {
                if (!(todaLaSeccion || ocultos.contains(f)) || !permitido(f)) continue;
                itemDe.apply(botonDe(f)).ifPresent(it -> out.add(new Entrada(it, contadorDe(f))));
            }
        }
        return out;
    }

    /** A row the role may open. A button row hidden here was on screen before; one in a whole
     *  section hidden here keeps the role's own visibility; a counter row's button is never touched. */
    private boolean permitido(Node fila) {
        Button b = botonDe(fila);
        if (b == null) return false;
        return b == fila ? ocultos.contains(fila) || b.isVisible() : b.isVisible();
    }

    private void abrir() {
        List<Entrada> entradas = enMas();
        if (entradas.isEmpty()) return;
        menu.getItems().clear();
        NavSection seccion = null;
        Button act = activo.get();
        for (Entrada e : entradas) {
            if (e.item().section() != seccion) {
                seccion = e.item().section();
                MenuItem titulo = new MenuItem(seccion.title);
                titulo.setDisable(true);
                titulo.getStyleClass().add("menu-seccion");
                menu.getItems().add(titulo);
            }
            menu.getItems().add(opcion(e, e.item().button() == act));
        }
        mas.getStyleClass().remove("nav-mas-abierto");
        mas.getStyleClass().add("nav-mas-abierto");
        menu.show(mas, Side.RIGHT, 6, 0);
    }

    private CustomMenuItem opcion(Entrada e, boolean activa) {
        FontIcon icono = new FontIcon(Objects.requireNonNullElse(e.item().iconLiteral(), "mdi2c-circle-small"));
        icono.getStyleClass().add("nav-mas-menu-icono");
        Label texto = new Label(e.item().button().getText());
        texto.getStyleClass().add("nav-mas-menu-texto");
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox caja = new HBox(10, icono, texto, hueco);
        caja.setAlignment(Pos.CENTER_LEFT);
        caja.setPrefWidth(260);                     // the shortcuts in one column, at the right
        if (e.contador() != null) {
            Label c = new Label(e.contador());
            c.getStyleClass().add("nav-mas-menu-contador");
            caja.getChildren().add(c);
        }
        if (!e.item().shortcutText().isEmpty()) {
            Label atajo = new Label(e.item().shortcutText());
            atajo.getStyleClass().add("nav-mas-menu-atajo");
            caja.getChildren().add(atajo);
        }
        CustomMenuItem mi = new CustomMenuItem(caja, true);
        mi.getStyleClass().add("nav-mas-menu-opcion");
        if (activa) mi.getStyleClass().add("nav-mas-menu-activa");
        mi.setOnAction(ev -> e.item().run());
        return mi;
    }

    private void animarMenu() {
        if (!AnimationUtils.isEnabled() || menu.getSkin() == null) return;
        Node n = menu.getSkin().getNode();
        n.setOpacity(0);
        FadeTransition f = new FadeTransition(Duration.millis(170), n);
        f.setToValue(1);
        TranslateTransition t = new TranslateTransition(Duration.millis(170), n);
        t.setFromX(-6);
        t.setToX(0);
        t.setInterpolator(Interpolator.EASE_OUT);
        new ParallelTransition(f, t).play();
    }

    private static void aparecer(Node n) {
        if (!AnimationUtils.isEnabled()) return;
        n.setOpacity(0);
        FadeTransition f = new FadeTransition(Duration.millis(180), n);
        f.setToValue(1);
        f.setInterpolator(Interpolator.EASE_OUT);
        f.play();
    }

    private static void latido(Node n) {
        if (!AnimationUtils.isEnabled()) return;
        ScaleTransition s = new ScaleTransition(Duration.millis(100), n);
        s.setToX(1.18);
        s.setToY(1.18);
        s.setAutoReverse(true);
        s.setCycleCount(2);
        s.play();
    }

    // ── La forma del menú (main.fxml) ─────────────────────────────────────────

    private static Node cabecera(VBox seccion) { return seccion.getChildren().get(0); }

    private static Pane items(VBox seccion) { return (Pane) seccion.getChildren().get(1); }

    /** The row's button: the row itself, or the one in its StackPane (with a counter). */
    private static Button botonDe(Node fila) {
        if (fila instanceof Button b) return b;
        if (fila instanceof Pane p)
            for (Node n : p.getChildren()) if (n instanceof Button b) return b;
        return null;
    }

    /** A row whose button the role may see (the áreas don't see Patrimonio's pages). */
    private static boolean muestraBoton(Node fila) {
        Button b = botonDe(fila);
        return b != null && (b == fila || b.isVisible() && b.isManaged());
    }

    private static String contadorDe(Node fila) {
        if (!(fila instanceof Pane p)) return null;
        for (Node n : p.getChildren())
            if (n instanceof Label l && l.isVisible() && l.getText() != null && !l.getText().isBlank() && !"0".equals(l.getText()))
                return l.getText();
        return null;
    }

    private Node filaDe(Button b) {
        for (Node p = b; p != null; p = p.getParent())
            if (p.getParent() instanceof Pane items && secciones.stream().anyMatch(s -> items(s) == items)) return p;
        return b;
    }

    private VBox seccionDe(Button b) {
        for (Node p = b; p != null; p = p.getParent())
            if (p instanceof VBox v && secciones.contains(v)) return v;
        return null;
    }

    private static void ver(Node n, boolean visible) {
        n.setVisible(visible);
        n.setManaged(visible);
    }
}
