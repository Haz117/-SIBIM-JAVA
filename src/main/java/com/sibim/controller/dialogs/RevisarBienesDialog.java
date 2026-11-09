package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.service.AvanceAreasService;
import com.sibim.service.ProductoService;
import com.sibim.session.SessionManager;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.MensajeError;
import com.sibim.util.NotificacionUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Phases 2 and 3 of an área's avance (Organigrama): the área goes through its
 * bienes saying which are theirs and which aren't (with why, for Patrimonio),
 * fills in what is missing, and confirms the ones that already have their label
 * stuck on. Patrimonio opens it too, to see an área's answers, answer for an área
 * that replied on paper, or clear an aviso.
 *
 * <p>9-nov-2026: made for people who don't use a computer every day. Each row
 * answers itself («Sí es de mi área» / «No es», «Ya tiene etiqueta», «Completar»),
 * several are chosen with check boxes instead of Ctrl/Shift, the bien shows its
 * marca, modelo, serie and ubicación to recognise it, every answer can be undone,
 * and steps 2 and 3 have the colors of «Avance de las áreas».
 */
public final class RevisarBienesDialog {

    /** What the list shows first. */
    public enum Vista {
        POR_REVISAR("Por revisar", p -> !p.isRevisadoPorArea()),
        REVISADOS("Sí son del área", p -> p.isRevisadoPorArea() && !p.isObservadoPorArea()),
        OBSERVADOS("No son del área", Producto::isObservadoPorArea),
        SIN_ETIQUETA("Sin etiqueta", p -> !p.isEtiquetado()),
        FALTAN_DATOS("Faltan datos", p -> !AvanceAreasService.conDatos(p)),
        TODOS("Todos", p -> true);

        final String texto;
        final Predicate<Producto> filtro;
        Vista(String texto, Predicate<Producto> filtro) { this.texto = texto; this.filtro = filtro; }
    }

    private final ProductoService service = new ProductoService();
    private final String titulo;
    /** The área whose bienes these are, or null for every área of the caller. */
    private final String area;
    private final Runnable alCerrar;
    private final ObservableList<Producto> todos = FXCollections.observableArrayList();
    private final FilteredList<Producto> visibles = new FilteredList<>(todos, p -> true);
    private final TableView<Producto> tabla = new TableView<>(visibles);
    private final ToggleGroup grupo = new ToggleGroup();
    private final Map<Vista, ToggleButton> chips = new EnumMap<>(Vista.class);
    private final TextField buscar = new TextField();
    private final Label resumen = new Label();
    private final com.sibim.util.BarraAvance barraRevision = new com.sibim.util.BarraAvance(0),
        barraEtiquetas = new com.sibim.util.BarraAvance(0);
    private final Label textoRevision = new Label(), textoEtiquetas = new Label();
    private final Label pctRevision = new Label(), pctEtiquetas = new Label();
    private final Button siNuestros = new Button("Sí son de mi área");
    private final Button noNuestros = new Button("No son de mi área…");
    private final Button etiquetados = new Button("Ya tienen etiqueta");
    private final Button quitarRevision = new Button("Quitar la revisión");
    private final Button desmarcar = new Button("Quitar las marcas");
    private final CheckBox todosMarcados = new CheckBox();
    private final Label pista = new Label("Marca varios con su casilla para responderlos juntos");
    /** The bienes ticked with their check box (by id): what the buttons below act on. */
    private final Set<String> marcados = new LinkedHashSet<>();
    private Dialog<ButtonType> dialog;
    private boolean cambio = false;

    private RevisarBienesDialog(String area, Runnable alCerrar) {
        this.area = area;
        this.titulo = area != null ? "Revisar los bienes de " + area : "Revisar los bienes de mis áreas";
        this.alCerrar = alCerrar;
    }

    /** @param area the área, or null for all of the caller's
     *  @param alCerrar run on closing when something changed */
    public static void show(String area, Vista inicial, Runnable alCerrar) {
        new RevisarBienesDialog(area, alCerrar).abrir(inicial);
    }

    private void abrir(Vista inicial) {
        dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle(titulo);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(1240, pantalla.getWidth() * 0.95), Math.min(760, pantalla.getHeight() * 0.92));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        HBox header = DialogUtil.gradientHeader("mdi2c-clipboard-check-multiple-outline", titulo,
            "Di si cada bien sí es de tu área, completa lo que le falte y confirma los que ya tienen su etiqueta",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        // ── Avance: steps 2 and 3, with the colors of «Avance de las áreas» ──
        HBox avance = new HBox(14,
            paso("2", "Revisar tus bienes", "avance-revisar", barraRevision, textoRevision, pctRevision),
            paso("3", "Confirmar etiquetas", "avance-etiquetar", barraEtiquetas, textoEtiquetas, pctEtiquetas));

        Label ayuda = new Label("Responde con los botones de cada renglón. Para responder varios a la vez, márcalos con su casilla "
            + "y usa los botones de abajo. «Completar» le pone foto, ubicación y estado físico.");
        ayuda.getStyleClass().add("muted-sm");
        ayuda.setWrapText(true);
        ayuda.setMinHeight(Region.USE_PREF_SIZE);

        // ── Filtros ──
        HBox filtros = new HBox(5);
        filtros.setAlignment(Pos.CENTER_LEFT);
        for (Vista v : Vista.values()) {
            ToggleButton chip = new ToggleButton(v.texto);
            chip.getStyleClass().add("filter-chip");
            if (v == Vista.OBSERVADOS) chip.getStyleClass().add("filter-chip-amber");
            if (v == Vista.REVISADOS) chip.getStyleClass().add("filter-chip-teal");
            chip.setToggleGroup(grupo);
            chip.setOnAction(e -> filtrar());
            chips.put(v, chip);
            filtros.getChildren().add(chip);
        }
        grupo.selectedToggleProperty().addListener((o, antes, ahora) -> { if (ahora == null && antes != null) antes.setSelected(true); });
        chips.get(inicial != null ? inicial : Vista.POR_REVISAR).setSelected(true);
        buscar.setPromptText("Buscar por código, nombre, marca, serie o ubicación…");
        buscar.getStyleClass().add("search-field");
        buscar.setAccessibleText("Buscar bien");
        HBox.setHgrow(buscar, Priority.ALWAYS);
        buscar.textProperty().addListener((o, a, b) -> filtrar());
        HBox barraFiltros = new HBox(10, filtros, buscar);
        barraFiltros.setAlignment(Pos.CENTER_LEFT);

        // ── Tabla ──
        tabla.getStyleClass().addAll("data-table", "revisar-tabla");
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setFixedCellSize(56);
        todosMarcados.setAccessibleText("Marcar todos los de la lista");
        todosMarcados.setTooltip(new Tooltip("Marca o quita la marca a todos los bienes de la lista"));
        todosMarcados.setOnAction(e -> {
            if (todosMarcados.isSelected()) visibles.forEach(p -> marcados.add(p.getId()));
            else visibles.forEach(p -> marcados.remove(p.getId()));
            actualizarMarcas();
        });
        TableColumn<Producto, Producto> colMarca = columna(null, 44, this::celdaCasilla);
        colMarca.setGraphic(todosMarcados);
        colMarca.setResizable(false);
        colMarca.setMaxWidth(44);
        tabla.getColumns().add(colMarca);
        tabla.getColumns().add(columna("Código", 104, p -> texto(p.getCodigo(), "revisar-codigo")));
        tabla.getColumns().add(columna("Bien", 260, RevisarBienesDialog::celdaBien));
        if (area == null) tabla.getColumns().add(columna("Área", 160, p -> texto(p.getArea(), null)));
        tabla.getColumns().add(columna("Resguardante", 150, p -> texto(p.getResguardante(), null)));
        tabla.getColumns().add(columna("¿Es de tu área?", 230, this::celdaRevision));
        tabla.getColumns().add(columna("Etiqueta", 140, this::celdaEtiqueta));
        tabla.getColumns().add(columna("Le falta", 180, this::celdaFalta));
        Label vacia = new Label("No hay bienes en esta lista");
        vacia.getStyleClass().add("muted");
        tabla.setPlaceholder(vacia);
        VBox.setVgrow(tabla, Priority.ALWAYS);
        tabla.setRowFactory(tv -> {
            TableRow<Producto> row = new TableRow<>() {
                @Override protected void updateItem(Producto p, boolean empty) {
                    super.updateItem(p, empty);
                    getStyleClass().remove("revisar-fila-marcada");
                    if (!empty && p != null && marcados.contains(p.getId())) getStyleClass().add("revisar-fila-marcada");
                }
            };
            row.setOnMouseClicked(e -> { if (e.getClickCount() == 2 && !row.isEmpty()) completar(row.getItem()); });
            return row;
        });
        // Space ticks the row with the focus, Enter completes its data.
        tabla.setOnKeyPressed(e -> {
            Producto p = tabla.getSelectionModel().getSelectedItem();
            if (p == null) return;
            if (e.getCode() == KeyCode.SPACE) { alternar(p); e.consume(); }
            else if (e.getCode() == KeyCode.ENTER) { completar(p); e.consume(); }
        });

        // ── Acciones sobre los marcados ──
        siNuestros.setGraphic(icono("mdi2c-check"));
        siNuestros.getStyleClass().add("btn-primary");
        siNuestros.setTooltip(new Tooltip("Los bienes marcados sí están en tu área"));
        siNuestros.setOnAction(e -> responder(marcadosVisibles(), null));
        noNuestros.setGraphic(new FontIcon("mdi2c-close"));
        noNuestros.getStyleClass().add("btn-secondary");
        noNuestros.setTooltip(new Tooltip("No están en tu área o no los encuentras: Patrimonio lo revisa"));
        noNuestros.setOnAction(e -> { List<Producto> sel = marcadosVisibles(); pedirNota(sel.size()).ifPresent(n -> responder(sel, n)); });
        etiquetados.setGraphic(new FontIcon("mdi2t-tag-outline"));
        etiquetados.getStyleClass().add("btn-secondary");
        etiquetados.setTooltip(new Tooltip("Los marcados ya tienen su etiqueta pegada"));
        etiquetados.setOnAction(e -> etiquetas(marcadosVisibles()));
        quitarRevision.setGraphic(new FontIcon("mdi2u-undo-variant"));
        quitarRevision.getStyleClass().add("btn-ghost");
        quitarRevision.setTooltip(new Tooltip("Borra la respuesta del área para que lo revise de nuevo (por ejemplo, ya que Patrimonio atendió el aviso)"));
        quitarRevision.setOnAction(e -> quitar(marcadosVisibles().stream().filter(Producto::isRevisadoPorArea).toList()));
        boolean admin = SessionManager.isAdmin();
        quitarRevision.setVisible(admin);
        quitarRevision.setManaged(admin);
        desmarcar.getStyleClass().add("btn-ghost");
        desmarcar.setOnAction(e -> { marcados.clear(); actualizarMarcas(); });
        for (Button b : List.of(siNuestros, noNuestros, etiquetados, quitarRevision, desmarcar)) b.setMinWidth(Region.USE_PREF_SIZE);
        botones();

        resumen.getStyleClass().add("table-count-label");
        resumen.setMinWidth(Region.USE_PREF_SIZE);
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        pista.getStyleClass().add("muted-sm");
        HBox acciones = new HBox(8, resumen, desmarcar, pista, hueco, quitarRevision, etiquetados, noNuestros, siNuestros);
        acciones.setAlignment(Pos.CENTER_LEFT);
        acciones.getStyleClass().add("revisar-acciones");

        VBox cuerpo = new VBox(10, avance, ayuda, barraFiltros, tabla, acciones);
        cuerpo.setPadding(new Insets(0, 22, 14, 22));
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        dialog.getDialogPane().setContent(new VBox(12, header, cuerpo));
        dialog.setOnHidden(e -> { if (cambio && alCerrar != null) alCerrar.run(); });
        cargar();
        Platform.runLater(tabla::requestFocus);
        dialog.showAndWait();
    }

    /** One step on top: its badge and name in its color, a bar, and how far it got. */
    private static HBox paso(String numero, String nombre, String estilo, com.sibim.util.BarraAvance barra, Label texto, Label pct) {
        Label n = new Label(numero);
        n.getStyleClass().addAll("avance-insignia", "avance-insignia-chica");
        Label t = new Label(nombre);
        t.getStyleClass().add("avance-fila-titulo");
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        pct.getStyleClass().add("avance-texto");
        HBox fila = new HBox(7, n, t, hueco, pct);
        fila.setAlignment(Pos.CENTER_LEFT);
        texto.getStyleClass().add("muted-sm");
        VBox caja = new VBox(5, fila, barra, texto);
        HBox.setHgrow(caja, Priority.ALWAYS);
        caja.setMaxWidth(Double.MAX_VALUE);
        HBox tarjeta = new HBox(caja);
        HBox.setHgrow(caja, Priority.ALWAYS);
        tarjeta.getStyleClass().addAll("revisar-paso", estilo);
        HBox.setHgrow(tarjeta, Priority.ALWAYS);
        tarjeta.setMaxWidth(Double.MAX_VALUE);
        return tarjeta;
    }

    private void cargar() {
        DialogUtil.runAsync(service::getAll,
            lista -> {
                todos.setAll(lista.stream().filter(p -> p.getFechaBaja() == null)
                    .filter(p -> area == null || area.equals(p.getArea()))
                    .sorted(Comparator.comparing(p -> Objects.requireNonNullElse(p.getCodigo(), ""),
                        com.sibim.service.ReporteService.POR_CODIGO))
                    .toList());
                Set<String> ids = new HashSet<>();
                todos.forEach(p -> ids.add(p.getId()));
                marcados.retainAll(ids);
                filtrar();
            },
            e -> NotificacionUtil.error(escena(), "No se pudieron cargar los bienes: " + MensajeError.de(e)));
    }

    private void filtrar() {
        Vista v = chips.entrySet().stream().filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey)
            .findFirst().orElse(Vista.TODOS);
        List<Producto> base = todos.stream().filter(v.filtro).toList();
        Set<Producto> coinciden = new HashSet<>(DialogUtil.coincidencias(base,
            p -> String.join(" ", Objects.requireNonNullElse(p.getCodigo(), ""), Objects.requireNonNullElse(p.getNombre(), ""),
                Objects.requireNonNullElse(p.getResguardante(), ""), Objects.requireNonNullElse(p.getUbicacion(), ""),
                Objects.requireNonNullElse(p.getMarca(), ""), Objects.requireNonNullElse(p.getModelo(), ""),
                Objects.requireNonNullElse(p.getNumeroSerie(), "")),
            buscar.getText()));
        visibles.setPredicate(p -> v.filtro.test(p) && coinciden.contains(p));
        for (Vista x : Vista.values()) {
            long n = todos.stream().filter(x.filtro).count();
            chips.get(x).setText(x.texto + " (" + n + ")");
        }
        int total = todos.size();
        long revisados = todos.stream().filter(Producto::isRevisadoPorArea).count();
        long observados = todos.stream().filter(Producto::isObservadoPorArea).count();
        long conDatos = todos.stream().filter(AvanceAreasService::conDatos).count();
        long etiq = todos.stream().filter(Producto::isEtiquetado).count();
        barraRevision.setAvance(total == 0 ? 0 : (double) revisados / total);
        pctRevision.setText(total == 0 ? "—" : Math.round(100.0 * revisados / total) + " %");
        textoRevision.setText(revisados + " de " + total + " respondidos"
            + (observados > 0 ? " (" + observados + (observados == 1 ? " no es del área)" : " no son del área)") : "")
            + " · " + conDatos + " con foto, ubicación y estado físico");
        barraEtiquetas.setAvance(total == 0 ? 0 : (double) etiq / total);
        pctEtiquetas.setText(total == 0 ? "—" : Math.round(100.0 * etiq / total) + " %");
        textoEtiquetas.setText(etiq + " de " + total + " con su etiqueta pegada");
        actualizarMarcas();
    }

    /** The counts, the buttons, the "all" box and the rows after the marks changed. */
    private void actualizarMarcas() {
        long enLista = visibles.stream().filter(p -> marcados.contains(p.getId())).count();
        todosMarcados.setSelected(!visibles.isEmpty() && enLista == visibles.size());
        todosMarcados.setIndeterminate(enLista > 0 && enLista < visibles.size());
        resumen.setText(FormatUtils.plural(visibles.size(), "bien", "bienes")
            + (marcados.isEmpty() ? "" : " · " + FormatUtils.plural(marcados.size(), "marcado", "marcados")));
        botones();
        tabla.refresh();
    }

    private void botones() {
        List<Producto> sel = marcadosVisibles();
        boolean hay = !sel.isEmpty();
        siNuestros.setDisable(!hay);
        noNuestros.setDisable(!hay);
        etiquetados.setDisable(sel.stream().allMatch(Producto::isEtiquetado));
        quitarRevision.setDisable(sel.stream().noneMatch(Producto::isRevisadoPorArea));
        desmarcar.setVisible(!marcados.isEmpty());
        desmarcar.setManaged(!marcados.isEmpty());
        pista.setVisible(marcados.isEmpty());
        pista.setManaged(marcados.isEmpty());
        String n = sel.size() > 1 ? " (" + sel.size() + ")" : "";
        siNuestros.setText("Sí son de mi área" + n);
        noNuestros.setText("No son de mi área" + n + "…");
        etiquetados.setText("Ya tienen etiqueta" + n);
    }

    /** The ticked bienes that are in the list now (a filter can hide some). */
    private List<Producto> marcadosVisibles() {
        return visibles.stream().filter(p -> marcados.contains(p.getId())).toList();
    }

    private void alternar(Producto p) {
        if (!marcados.remove(p.getId())) marcados.add(p.getId());
        actualizarMarcas();
    }

    // ── Cells ──────────────────────────────────────────────────────────────────

    private Node celdaCasilla(Producto p) {
        CheckBox c = new CheckBox();
        c.setSelected(marcados.contains(p.getId()));
        c.setFocusTraversable(false);
        c.setAccessibleText("Marcar " + p.getCodigo());
        c.setOnAction(e -> {
            if (c.isSelected()) marcados.add(p.getId()); else marcados.remove(p.getId());
            actualizarMarcas();
        });
        return c;
    }

    /** The name, and under it what tells it apart: marca, modelo, serie and where it is. */
    static Node celdaBien(Producto p) {
        Label nombre = new Label(Objects.requireNonNullElse(p.getNombre(), ""));
        nombre.getStyleClass().add("revisar-bien-nombre");
        String detalle = detalle(p);
        VBox v = new VBox(1, nombre);
        if (!detalle.isEmpty()) {
            Label d = new Label(detalle);
            d.getStyleClass().add("revisar-bien-detalle");
            v.getChildren().add(d);
        }
        v.setAlignment(Pos.CENTER_LEFT);
        Tooltip.install(v, new Tooltip(nombre.getText() + (detalle.isEmpty() ? "" : "\n" + detalle)));
        return v;
    }

    /** "HP · ProDesk 400 · Serie MXL123 · Oficina 2" (only what it has). */
    static String detalle(Producto p) {
        List<String> partes = new ArrayList<>();
        String marca = com.sibim.util.ProductoUtils.dato(p.getMarca()), modelo = com.sibim.util.ProductoUtils.dato(p.getModelo()),
            serie = com.sibim.util.ProductoUtils.dato(p.getNumeroSerie());
        if (marca != null) partes.add(marca);
        if (modelo != null) partes.add(modelo);
        if (serie != null) partes.add("Serie " + serie);
        if (p.getUbicacion() != null && !p.getUbicacion().isBlank()) partes.add(p.getUbicacion().trim());
        return String.join(" · ", partes);
    }

    private Node celdaRevision(Producto p) {
        if (!p.isRevisadoPorArea()) {
            Button si = boton("Sí es", "mdi2c-check", "revisar-si", "Este bien sí está en tu área");
            si.setOnAction(e -> responder(List.of(p), null));
            Button no = boton("No es", "mdi2c-close", "revisar-no", "No está en tu área o no lo encuentras: Patrimonio lo revisa");
            no.setOnAction(e -> pedirNota(1).ifPresent(n -> responder(List.of(p), n)));
            HBox h = new HBox(6, si, no);
            h.setAlignment(Pos.CENTER_LEFT);
            return h;
        }
        Label estado = new Label(p.isObservadoPorArea() ? "No es del área" : "Sí es del área",
            new FontIcon(p.isObservadoPorArea() ? "mdi2a-alert-circle-outline" : "mdi2c-check-circle"));
        estado.getStyleClass().addAll("revisar-estado", p.isObservadoPorArea() ? "revisar-estado-no" : "revisar-estado-si");
        Hyperlink cambiar = new Hyperlink("Cambiar");
        cambiar.getStyleClass().add("revisar-cambiar");
        cambiar.setTooltip(new Tooltip("Borra esta respuesta para volver a responder"));
        cambiar.setOnAction(e -> quitar(List.of(p)));
        HBox arriba = new HBox(6, estado, cambiar);
        arriba.setAlignment(Pos.CENTER_LEFT);
        VBox v = new VBox(0, arriba);
        v.setAlignment(Pos.CENTER_LEFT);
        if (p.isObservadoPorArea() && p.getRevisionAreaNota() != null) {
            Label nota = new Label("«" + p.getRevisionAreaNota() + "»");
            nota.getStyleClass().add("revisar-bien-detalle");
            Tooltip.install(nota, new Tooltip(p.getRevisionAreaNota()));
            v.getChildren().add(nota);
        }
        return v;
    }

    private Node celdaEtiqueta(Producto p) {
        if (p.isEtiquetado()) {
            Label l = new Label("Tiene", new FontIcon("mdi2t-tag-outline"));
            l.getStyleClass().addAll("revisar-estado", "revisar-estado-si");
            return l;
        }
        Button b = boton("Ya la tiene", "mdi2t-tag-outline", "revisar-etiqueta", "La etiqueta de este bien ya está pegada");
        b.setOnAction(e -> etiquetas(List.of(p)));
        return b;
    }

    private Node celdaFalta(Producto p) {
        String falta = falta(p);
        if (falta.isEmpty()) {
            Label l = new Label("Nada", new FontIcon("mdi2c-check-circle"));
            l.getStyleClass().addAll("revisar-estado", "revisar-estado-si");
            return l;
        }
        Label l = new Label(falta);
        l.getStyleClass().add("revisar-bien-detalle");
        Button b = boton("Completar", "mdi2p-pencil-outline", "revisar-completar", "Ponerle foto, ubicación y estado físico");
        b.setOnAction(e -> completar(p));
        VBox v = new VBox(2, l, b);
        v.setAlignment(Pos.CENTER_LEFT);
        return v;
    }

    private static Button boton(String texto, String icono, String estilo, String ayuda) {
        Button b = new Button(texto, icono(icono));
        b.getStyleClass().addAll("revisar-boton", estilo);
        b.setTooltip(new Tooltip(ayuda));
        b.setFocusTraversable(false);
        b.setMinWidth(Region.USE_PREF_SIZE);
        return b;
    }

    private static FontIcon icono(String literal) {
        FontIcon i = new FontIcon(literal);
        i.getStyleClass().add("revisar-boton-icono");
        return i;
    }

    private static Label texto(String s, String estilo) {
        Label l = new Label(s == null || s.isBlank() ? "—" : s);
        if (estilo != null) l.getStyleClass().add(estilo);
        if (s != null && s.length() > 24) l.setTooltip(new Tooltip(s));
        return l;
    }

    // ── Actions: each one says what it did and can be undone ─────────────────

    private void responder(List<Producto> bienes, String nota) {
        if (bienes.isEmpty()) return;
        List<Producto> copia = List.copyOf(bienes);
        hacer(() -> service.revisarComoArea(copia, nota),
            nota == null ? (copia.size() == 1 ? copia.get(0).getCodigo() + ": sí es de tu área"
                    : FormatUtils.plural(copia.size(), "bien marcado como de tu área", "bienes marcados como de tu área"))
                : "Avisado a Patrimonio: " + FormatUtils.plural(copia.size(), "bien que no es del área", "bienes que no son del área"),
            () -> service.quitarRevision(copia), copia);
    }

    private void etiquetas(List<Producto> bienes) {
        List<Producto> sin = bienes.stream().filter(p -> !p.isEtiquetado()).toList();
        if (sin.isEmpty()) return;
        hacer(() -> service.confirmarEtiquetas(sin, true),
            sin.size() == 1 ? sin.get(0).getCodigo() + ": ya tiene su etiqueta" : FormatUtils.plural(sin.size(), "bien con etiqueta", "bienes con etiqueta"),
            () -> service.confirmarEtiquetas(sin, false), sin);
    }

    private void quitar(List<Producto> bienes) {
        if (bienes.isEmpty()) return;
        hacer(() -> service.quitarRevision(bienes),
            bienes.size() == 1 ? bienes.get(0).getCodigo() + ": vuelve a «Por revisar»"
                : "Se quitó la revisión de " + FormatUtils.plural(bienes.size(), "bien", "bienes"),
            null, bienes);
    }

    private void completar(Producto p) {
        // Same path as "Actualizar datos" everywhere else: it uploads the photos too.
        com.sibim.controller.GuardarBien.actualizarDatos(escena(), service, p,
            org.slf4j.LoggerFactory.getLogger(RevisarBienesDialog.class), guardado -> { cambio = true; cargar(); });
    }

    /** Runs {@code accion}, takes the marks off what it acted on and offers Deshacer
     *  (also Ctrl+Z) when there is a way back. */
    private void hacer(DialogUtil.CheckedRunnable accion, String exito, DialogUtil.CheckedRunnable deshacer, List<Producto> sobre) {
        Scene escena = escena();
        DialogUtil.runAsync(accion,
            () -> {
                cambio = true;
                sobre.forEach(p -> marcados.remove(p.getId()));
                cargar();
                if (deshacer == null) { NotificacionUtil.exito(escena, exito); return; }
                NotificacionUtil.exitoConAccion(escena, exito, "Deshacer", () -> DialogUtil.runAsync(deshacer,
                    () -> { cargar(); NotificacionUtil.info(escena, "Se deshizo"); },
                    e -> NotificacionUtil.error(escena, "No se pudo deshacer: " + MensajeError.de(e))));
            },
            e -> NotificacionUtil.error(escena, "No se pudo guardar: " + MensajeError.de(e)));
    }

    private Optional<String> pedirNota(int cuantos) {
        TextInputDialog d = new TextInputDialog();
        DialogUtil.applyOwner(d);
        DialogUtil.applyStylesheet(d.getDialogPane());
        d.setTitle("No es de mi área");
        d.setHeaderText(cuantos == 1 ? "Un bien que no es de tu área o no encuentras"
            : cuantos + " bienes que no son de tu área o no encuentras");
        d.setContentText("¿Por qué? (Patrimonio lo revisa)");
        d.getEditor().setPromptText("Ej. lo tiene Obras Públicas; no está en la oficina; se dio de baja en 2023");
        d.getEditor().setPrefColumnCount(36);
        Button ok = (Button) d.getDialogPane().lookupButton(ButtonType.OK);
        ok.setText("Avisar a Patrimonio");
        ok.disableProperty().bind(d.getEditor().textProperty().isEmpty());
        return d.showAndWait().map(String::trim).filter(s -> !s.isEmpty());
    }

    private Scene escena() {
        return dialog != null ? dialog.getDialogPane().getScene() : null;
    }

    /** "foto, ubicación, estado físico", or empty when it has all three. */
    static String falta(Producto p) {
        List<String> f = new ArrayList<>();
        if (p.getFotoUrl() == null || p.getFotoUrl().isBlank()) f.add("foto");
        if (p.getUbicacion() == null || p.getUbicacion().isBlank()) f.add("ubicación");
        if (p.getEstadoFisico() == null || p.getEstadoFisico().isBlank()) f.add("estado físico");
        return String.join(", ", f);
    }

    private static TableColumn<Producto, Producto> columna(String titulo, double ancho, Function<Producto, Node> celda) {
        TableColumn<Producto, Producto> c = new TableColumn<>(titulo);
        c.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue()));
        c.setPrefWidth(ancho);
        c.setSortable(false);
        c.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Producto p, boolean empty) {
                super.updateItem(p, empty);
                setText(null);
                setGraphic(empty || p == null ? null : celda.apply(p));
            }
        });
        return c;
    }
}
