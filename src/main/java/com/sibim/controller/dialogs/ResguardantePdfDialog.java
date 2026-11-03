package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.model.Resguardo;
import com.sibim.model.ResguardoItem;
import com.sibim.service.ResguardoService.Resguardante;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reportes › Resguardos por resguardante, laid out like "Transferir arrastrando":
 * the people on the left with their search, what each one can print on the right
 * as cards, what comes out at the bottom. The first match follows the search, so
 * a few letters and Enter are enough.
 */
public final class ResguardantePdfDialog {

    /** Run once the dialog closes, with what was ticked. */
    @FunctionalInterface
    public interface AlGenerar {
        void generar(Resguardante persona, List<Resguardo> elegidos, boolean conSinResguardo);
    }

    /** Códigos shown per card; a long resguardo says how many more it has. */
    private static final int MAX_CODIGOS = 6;

    private final List<Resguardante> personas;
    private final TextField buscar = new TextField();
    private final ListView<Resguardante> lista = new ListView<>();
    private final Label cuantas = new Label();
    private final VBox tarjetas = new VBox(8);
    private final StackPane vacio = new StackPane();
    private final ScrollPane scroll = new ScrollPane(tarjetas);
    private final VBox persona = new VBox(2);
    private final Hyperlink todos = new Hyperlink("Todos");
    private final Hyperlink ninguno = new Hyperlink("Ninguno");
    private final HBox encabezadoPersona;
    private final Label resumen = new Label();
    /** Ticked resguardos; the box of the bienes without one goes apart. */
    private final List<CheckBox> casillas = new ArrayList<>();
    private CheckBox sinResguardo;
    private Button generar;
    private java.util.function.Consumer<Resguardante> alRegistrar;
    private java.util.function.Consumer<Resguardante> alVerBienes;
    private String inicial;
    /** Who "Ver sus bienes" was pressed for: it runs once this window closes. */
    private Resguardante aVerBienes;
    private Dialog<ButtonType> dialog;
    /** Who "Registrar como resguardo" was pressed for: it runs once this window closes. */
    private Resguardante aRegistrar;

    private ResguardantePdfDialog(List<Resguardante> personas) {
        this.personas = personas;
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        FontIcon icono = new FontIcon("mdi2a-account-outline");
        icono.getStyleClass().add("trf-tarjeta-icono");
        StackPane fondo = new StackPane(icono);
        fondo.getStyleClass().add("trf-tarjeta-icono-fondo");
        fondo.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        encabezadoPersona = new HBox(10, fondo, persona, hueco, todos, new Label("·"), ninguno);
        encabezadoPersona.setAlignment(Pos.CENTER_LEFT);
    }

    /** @param alRegistrar turn the bienes without a resguardo into a registered one:
     *                     null for whoever can't create resguardos */
    public static void show(List<Resguardante> personas, String inicial, AlGenerar alGenerar,
                            java.util.function.Consumer<Resguardante> alRegistrar,
                            java.util.function.Consumer<Resguardante> alVerBienes) {
        ResguardantePdfDialog d = new ResguardantePdfDialog(personas);
        d.alRegistrar = alRegistrar;
        d.alVerBienes = alVerBienes;
        d.inicial = inicial;
        d.abrir(alGenerar);
    }

    private void abrir(AlGenerar alGenerar) {
        dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Resguardos por resguardante");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        // Also on a 1366×768 laptop: never bigger than the screen.
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(980, pantalla.getWidth() * 0.94),
            Math.min(620, pantalla.getHeight() * 0.88));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        HBox header = DialogUtil.gradientHeader("mdi2b-badge-account-outline", "Resguardos por resguardante",
            "Busca a la persona y genera el PDF de sus resguardos, sin entrar a Resguardos",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        generar = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        generar.setText("Generar PDF");
        generar.getStyleClass().add("dialog-ok-btn");

        HBox cuerpo = new HBox(16, panelPersonas(), panelImprimir());
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        // The margin goes on an inner box: the dialog stylesheet sets the content's
        // own padding to 0 (the header runs edge to edge), and it won over setPadding.
        VBox debajo = new VBox(cuerpo);
        debajo.setPadding(new Insets(0, 22, 14, 22));
        VBox.setVgrow(debajo, Priority.ALWAYS);
        dialog.getDialogPane().setContent(new VBox(12, header, debajo));

        filtrar();
        mostrar(null);   // nobody picked yet: the selection never "changes" to that
        if (inicial != null) {
            String clave = com.sibim.service.ResguardoService.clavePersona(inicial);
            personas.stream().filter(p -> com.sibim.service.ResguardoService.clavePersona(p.nombre()).equals(clave)).findFirst()
                .ifPresent(p -> {
                    lista.getSelectionModel().select(p);
                    // Once it is laid out, with some room above: it came out cut at the bottom.
                    Platform.runLater(() -> lista.scrollTo(Math.max(0, lista.getItems().indexOf(p) - 2)));
                });
        }
        Platform.runLater(buscar::requestFocus);
        dialog.showAndWait().filter(bt -> bt == ButtonType.OK).ifPresent(bt ->
            alGenerar.generar(lista.getSelectionModel().getSelectedItem(), elegidos(),
                sinResguardo != null && sinResguardo.isSelected()));
        if (aRegistrar != null) alRegistrar.accept(aRegistrar);
        if (aVerBienes != null) alVerBienes.accept(aVerBienes);
    }

    // ── Paso 1: la persona ─────────────────────────────────────────────────────

    private VBox panelPersonas() {
        buscar.setId("buscarPersona");
        buscar.setPromptText("Nombre o área de la persona…");
        buscar.getStyleClass().add("form-input");
        buscar.textProperty().addListener((o, a, b) -> filtrar());
        // The arrows move the list from the search, which keeps the focus to go on typing.
        buscar.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.DOWN) { mover(1); e.consume(); }
            else if (e.getCode() == KeyCode.UP) { mover(-1); e.consume(); }
        });

        lista.getStyleClass().add("trf-areas");
        lista.setCellFactory(lv -> new CeldaPersona(lv));
        lista.setPlaceholder(new Label("Nadie coincide con lo escrito"));
        lista.getSelectionModel().selectedItemProperty().addListener((o, a, p) -> mostrar(p));
        VBox.setVgrow(lista, Priority.ALWAYS);
        cuantas.getStyleClass().add("table-count-label");

        VBox panel = new VBox(8, TransferenciaArrastreDialog.paso(1, "Busca a la persona"), buscar, lista, cuantas);
        panel.getStyleClass().add("trf-panel");
        panel.setPrefWidth(370);
        panel.setMinWidth(300);
        return panel;
    }

    private void filtrar() {
        String q = buscar.getText() == null ? "" : buscar.getText().trim();
        List<Resguardante> hallados = DialogUtil.coincidencias(personas, Resguardante::texto, q);
        lista.getItems().setAll(hallados);
        if (q.isEmpty()) lista.getSelectionModel().clearSelection();
        else if (!hallados.isEmpty()) lista.getSelectionModel().select(0);
        if (!hallados.isEmpty()) lista.scrollTo(0);
        cuantas.setText(q.isEmpty()
            ? FormatUtils.plural(personas.size(), "persona", "personas") + " · escribe para buscar"
            : FormatUtils.plural(hallados.size(), "coincidencia", "coincidencias") + " · Enter genera el PDF");
    }

    private void mover(int paso) {
        int total = lista.getItems().size();
        if (total == 0) return;
        int i = Math.max(0, Math.min(total - 1, lista.getSelectionModel().getSelectedIndex() + paso));
        lista.getSelectionModel().select(i);
        lista.scrollTo(Math.max(0, i - 3));
    }

    // ── Paso 2: qué imprimir ───────────────────────────────────────────────────

    private VBox panelImprimir() {
        tarjetas.setPadding(new Insets(2, 4, 4, 2));
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("dialog-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        FontIcon icono = new FontIcon("mdi2a-account-search-outline");
        icono.getStyleClass().add("empty-icon-lg");
        Label titulo = new Label("Elige a una persona");
        titulo.getStyleClass().add("empty-state-msg");
        Label pista = new Label("Aquí aparecen sus resguardos y los bienes que tiene a su nombre");
        pista.getStyleClass().add("empty-state-hint");
        VBox textoVacio = new VBox(8, icono, titulo, pista);
        textoVacio.setAlignment(Pos.CENTER);
        vacio.getChildren().add(textoVacio);
        VBox.setVgrow(vacio, Priority.ALWAYS);

        todos.setOnAction(e -> marcar(true));
        ninguno.setOnAction(e -> marcar(false));

        resumen.getStyleClass().add("table-count-label");
        resumen.setWrapText(true);
        HBox paso3 = TransferenciaArrastreDialog.paso(3, "Se imprime");
        paso3.setPadding(Insets.EMPTY);
        paso3.setMinWidth(Region.USE_PREF_SIZE);
        HBox pie = new HBox(10, paso3, resumen);
        pie.setAlignment(Pos.CENTER_LEFT);

        VBox panel = new VBox(8, TransferenciaArrastreDialog.paso(2, "Elige qué imprimir"), encabezadoPersona, vacio, scroll, new Separator(), pie);
        ((HBox) panel.getChildren().get(0)).setPadding(Insets.EMPTY);
        HBox.setHgrow(panel, Priority.ALWAYS);
        return panel;
    }

    private void mostrar(Resguardante p) {
        casillas.clear();
        sinResguardo = null;
        tarjetas.getChildren().clear();
        ver(vacio, p == null);
        ver(scroll, p != null);
        ver(encabezadoPersona, p != null);
        persona.getChildren().clear();
        if (p != null) {
            Label nombre = new Label(p.nombre());
            nombre.getStyleClass().add("org-area-name");
            nombre.setWrapText(true);
            Label datos = new Label(lugar(p));
            datos.getStyleClass().add("org-table-sub");
            datos.setWrapText(true);
            persona.getChildren().addAll(nombre, datos);
            if (alVerBienes != null) {
                Hyperlink verBienes = new Hyperlink("Ver sus bienes");
                verBienes.setPadding(Insets.EMPTY);
                verBienes.setOnAction(e -> {
                    aVerBienes = p;
                    dialog.setResult(ButtonType.CANCEL);
                    dialog.close();
                });
                persona.getChildren().add(verBienes);
            }

            // What is in force first: it is what gets signed. Newest first within each.
            p.resguardos().stream().sorted(Comparator.comparing(r -> !r.isActivo())).forEach(r -> {
                CheckBox cb = new CheckBox();
                cb.setUserData(r);
                // A cancelled one can still be marked, but nobody prints it by accident.
                cb.setSelected(r.isActivo());
                casillas.add(cb);
                tarjetas.getChildren().add(tarjeta(cb, r.getNumero(),
                    FormatUtils.plural(r.getItems().size(), "bien", "bienes")
                        + (r.getCreadoEn() != null ? " · " + FormatUtils.formatDate(r.getCreadoEn().toLocalDate()) : "")
                        + (r.getResguardanteArea() != null ? " · " + r.getResguardanteArea() : ""),
                    r.isActivo() ? "Vigente" : "Cancelado", r.isActivo() ? "cell-badge-success" : "cell-badge-blue",
                    r.getItems().stream().map(ResguardoItem::getProductoCodigo).toList()));
            });
            if (!p.sinResguardo().isEmpty()) {
                sinResguardo = new CheckBox();
                // Ticked when it is all the person has; next to registered ones it is an extra.
                sinResguardo.setSelected(p.activos() == 0);
                Node t = tarjeta(sinResguardo, "Sin resguardo registrado",
                    FormatUtils.plural(p.sinResguardo().size(), "bien", "bienes")
                        + " a su nombre · salen del inventario, con folio RSP",
                    "Del inventario", "cell-badge-warning",
                    p.sinResguardo().stream().map(Producto::getCodigo).toList());
                if (alRegistrar != null) ((VBox) ((HBox) t).getChildren().get(1)).getChildren().add(botonRegistrar(p));
                Tooltip.install(t, new Tooltip("Bienes que el inventario tiene a su nombre y no están en ningún resguardo vigente. "
                    + "Salen como en Bienes › Resguardo PDF, con folio RSP."));
                tarjetas.getChildren().add(t);
            }
            boolean varias = casillas.size() + (sinResguardo != null ? 1 : 0) > 1;
            ver(todos, varias);
            ver(ninguno, varias);
            ((Node) encabezadoPersona.getChildren().get(4)).setVisible(varias);
            scroll.setVvalue(0);
        }
        actualizar();
    }

    /** The way out of "PENDIENTE": these bienes become a resguardo in force, with its folio. */
    private Node botonRegistrar(Resguardante p) {
        Button b = new Button("Registrar como resguardo");
        b.setGraphic(new FontIcon("mdi2c-clipboard-account-outline"));
        b.getStyleClass().add("btn-secondary");
        b.setTooltip(new Tooltip("Abre Nuevo Resguardo con esta persona y estos bienes ya marcados; queda vigente, con folio RSG"));
        b.setOnAction(e -> {
            aRegistrar = p;
            dialog.setResult(ButtonType.CANCEL);
            dialog.close();
        });
        return b;
    }

    /** "Secretaría de Obras Públicas · 1 vigente · 5 bienes". */
    private static String lugar(Resguardante p) {
        List<String> areas = p.areas();
        return (areas.size() == 1 ? areas.get(0) + " · " : areas.size() > 1 ? areas.size() + " áreas · " : "")
            + FormatUtils.plural((int) p.activos(), "resguardo vigente", "resguardos vigentes")
            + " · " + FormatUtils.plural(bienes(p), "bien", "bienes");
    }

    /** What the person holds today: a cancelled resguardo no longer counts. */
    static int bienes(Resguardante p) {
        return p.resguardos().stream().filter(Resguardo::isActivo).mapToInt(r -> r.getItems().size()).sum()
            + p.sinResguardo().size();
    }

    /** One thing that can be printed: a click anywhere on it ticks it. */
    private Node tarjeta(CheckBox cb, String titulo, String detalle, String insignia, String claseInsignia, List<String> codigos) {
        cb.setAccessibleText(titulo);
        cb.setMinWidth(Region.USE_PREF_SIZE);
        Label folio = new Label(titulo);
        folio.getStyleClass().add("rsg-tarjeta-titulo");
        Label badge = new Label(insignia);
        badge.getStyleClass().addAll("cell-badge", claseInsignia);
        badge.setMinWidth(Region.USE_PREF_SIZE);
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox arriba = new HBox(8, folio, hueco, badge);
        arriba.setAlignment(Pos.CENTER_LEFT);
        Label sub = new Label(detalle);
        sub.getStyleClass().add("org-table-sub");
        sub.setWrapText(true);
        FlowPane chips = new FlowPane(5, 5);
        codigos.stream().filter(c -> c != null && !c.isBlank()).limit(MAX_CODIGOS).forEach(c -> {
            Label chip = new Label(c);
            chip.getStyleClass().add("trf-codigo");
            chips.getChildren().add(chip);
        });
        if (codigos.size() > MAX_CODIGOS) {
            Label mas = new Label("+" + (codigos.size() - MAX_CODIGOS) + " más");
            mas.getStyleClass().add("org-table-sub");
            chips.getChildren().add(mas);
        }
        VBox textos = new VBox(4, arriba, sub, chips);
        HBox.setHgrow(textos, Priority.ALWAYS);
        HBox t = new HBox(12, cb, textos);
        t.getStyleClass().add("rsg-tarjeta");
        // The box toggles itself; the rest of the card does it here.
        t.setOnMouseClicked(e -> { if (!dentroDe(e.getTarget(), cb) && !enUnBoton(e.getTarget())) cb.setSelected(!cb.isSelected()); });
        Runnable pintar = () -> {
            t.getStyleClass().remove("rsg-tarjeta-sel");
            if (cb.isSelected()) t.getStyleClass().add("rsg-tarjeta-sel");
        };
        pintar.run();
        cb.selectedProperty().addListener((o, a, si) -> { pintar.run(); actualizar(); });
        return t;
    }

    private void marcar(boolean si) {
        casillas.forEach(cb -> cb.setSelected(si));
        if (sinResguardo != null) sinResguardo.setSelected(si);
    }

    private List<Resguardo> elegidos() {
        return casillas.stream().filter(CheckBox::isSelected).map(cb -> (Resguardo) cb.getUserData()).toList();
    }

    private void actualizar() {
        Resguardante p = lista.getSelectionModel().getSelectedItem();
        List<Resguardo> elegidos = elegidos();
        boolean sueltos = sinResguardo != null && sinResguardo.isSelected();
        int documentos = elegidos.size() + (sueltos ? 1 : 0);
        int total = elegidos.stream().mapToInt(r -> r.getItems().size()).sum() + (sueltos ? p.sinResguardo().size() : 0);
        resumen.getStyleClass().remove("trf-seleccion-activa");
        if (documentos > 0) resumen.getStyleClass().add("trf-seleccion-activa");
        resumen.setText(p == null ? "Nada todavía"
            : documentos == 0 ? "Nada: marca al menos uno"
            : FormatUtils.plural(documentos, "resguardo", "resguardos") + " · " + FormatUtils.plural(total, "bien", "bienes")
                + (documentos > 1 ? " · salen juntos en un solo PDF" : ""));
        generar.setDisable(documentos == 0);
    }

    private static boolean enUnBoton(Object objetivo) {
        for (Node n = objetivo instanceof Node o ? o : null; n != null; n = n.getParent())
            if (n instanceof ButtonBase) return true;
        return false;
    }

    private static boolean dentroDe(Object objetivo, Node nodo) {
        for (Node n = objetivo instanceof Node o ? o : null; n != null; n = n.getParent())
            if (n == nodo) return true;
        return false;
    }

    private static void ver(Node n, boolean visible) {
        n.setVisible(visible);
        n.setManaged(visible);
    }

    /** A person in the list: the name, the área, and how much they hold. */
    static final class CeldaPersona extends ListCell<Resguardante> {
        private final FontIcon icono = new FontIcon("mdi2a-account-outline");
        private final StackPane fondo = new StackPane(icono);
        private final Label nombre = new Label();
        private final Label area = new Label();
        private final Label cuenta = new Label();
        private final Tooltip completo = new Tooltip();
        private final HBox caja;

        CeldaPersona(ListView<Resguardante> lv) {
            icono.getStyleClass().add("trf-tarjeta-icono");
            fondo.getStyleClass().addAll("trf-tarjeta-icono-fondo", "trf-area-icono");
            fondo.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            nombre.getStyleClass().add("org-child-label");
            nombre.setTextOverrun(OverrunStyle.ELLIPSIS);
            area.getStyleClass().add("org-table-sub");
            area.setTextOverrun(OverrunStyle.ELLIPSIS);
            VBox textos = new VBox(1, nombre, area);
            textos.setMinWidth(0);
            textos.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(textos, Priority.ALWAYS);
            cuenta.getStyleClass().add("org-area-count");
            cuenta.setMinWidth(Region.USE_PREF_SIZE);
            caja = new HBox(10, fondo, textos, cuenta);
            caja.setAlignment(Pos.CENTER_LEFT);
            caja.maxWidthProperty().bind(lv.widthProperty().subtract(30));
            caja.setMinWidth(0);
        }

        @Override protected void updateItem(Resguardante p, boolean empty) {
            super.updateItem(p, empty);
            setText(null);
            if (empty || p == null) { setGraphic(null); setTooltip(null); return; }
            nombre.setText(p.nombre());
            List<String> areas = p.areas();
            area.setText(areas.size() == 1 ? areas.get(0) : areas.isEmpty() ? "Sin área" : areas.size() + " áreas");
            cuenta.setText(FormatUtils.plural(bienes(p), "bien", "bienes"));
            completo.setText(p.texto());
            setTooltip(completo);
            setGraphic(caja);
        }
    }
}
