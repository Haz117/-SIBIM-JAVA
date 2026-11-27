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
 *
 * <p>Also 9-nov-2026: Patrimonio attends the avisos «No es del área» right here.
 * In that list each one says what the área wrote and has «Pasar a otra área»
 * (Transferir, with that bien ticked and the aviso as motivo), «Es suyo» (back to
 * the área's «Por revisar», with a message the área reads next to the bien) and
 * «Ver» (its ficha: who had it, its photos and movements). Before, the only thing
 * here was «Cambiar», and moving it meant closing and looking for it again.
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
    private final Button quitarRevision = new Button("Que lo revisen otra vez");
    private final Button pasar = new Button("Pasar a otra área");
    private final Button desmarcar = new Button("Quitar las marcas");
    private final CheckBox todosMarcados = new CheckBox();
    private final Label pista = new Label("Marca varios con su casilla para responderlos juntos");
    private final Label ayuda = new Label();
    /** For the área: what Patrimonio said of the bienes it sent back, whole (a row only fits the start). */
    private final VBox mensajes = new VBox(3);
    /** The bienes ticked with their check box (by id): what the buttons below act on. */
    private final Set<String> marcados = new LinkedHashSet<>();
    private final boolean admin = SessionManager.isAdmin();
    /** Every bien, also of other áreas: what Transferir needs to number them in the área that receives them. */
    private List<Producto> inventario = List.of();
    private Vista vista = Vista.POR_REVISAR;
    private TableColumn<Producto, Producto> colCodigo, colBien, colArea, colResguardante, colRevision, colEtiqueta, colFalta, colAtender;
    /** Which set of columns is showing: the área's answers, or Patrimonio's «Qué hacer» (null before the first). */
    private Boolean columnasAtender;
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
            admin ? "Lo que contestó cada área de sus bienes; en «No son del área» atiendes sus avisos"
                  : "Di si cada bien sí es de tu área, completa lo que le falte y confirma los que ya tienen su etiqueta",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        // ── Avance: steps 2 and 3, with the colors of «Avance de las áreas» ──
        HBox avance = new HBox(14,
            paso("2", "Revisar tus bienes", "avance-revisar", barraRevision, textoRevision, pctRevision),
            paso("3", "Confirmar etiquetas", "avance-etiquetar", barraEtiquetas, textoEtiquetas, pctEtiquetas));

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
        colCodigo = columna("Código", 104, p -> texto(p.getCodigo(), "revisar-codigo"));
        colBien = columna("Bien", 260, RevisarBienesDialog::celdaBien);
        colArea = columna("Área", 160, p -> texto(p.getArea(), null));
        colResguardante = columna("Resguardante", 150, p -> texto(p.getResguardante(), null));
        tabla.getColumns().addAll(List.of(colCodigo, colBien));
        if (area == null) tabla.getColumns().add(colArea);
        tabla.getColumns().add(colResguardante);
        colRevision = columna(admin ? "¿Es del área?" : "¿Es de tu área?", 230, this::celdaRevision);
        colEtiqueta = columna("Etiqueta", 140, this::celdaEtiqueta);
        colFalta = columna("Le falta", 180, this::celdaFalta);
        // Patrimonio, in «No son del área»: what to do with each aviso, instead of the label and the data.
        colAtender = columna("Qué hacer", 285, this::celdaAtender);
        colAtender.setMinWidth(280);
        colAtender.setVisible(false);
        tabla.getColumns().addAll(List.of(colRevision, colEtiqueta, colFalta, colAtender));
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
        // Space ticks the row with the focus, Enter completes its data; S, N and E answer it as
        // its buttons do, and the list stays on the next one (18-nov-2026: the per-row buttons
        // were out of Tab's reach, and an área has from 4 to 316 bienes to answer).
        tabla.setOnKeyPressed(e -> {
            Producto p = tabla.getSelectionModel().getSelectedItem();
            if (p == null) return;
            boolean solo = !e.isControlDown() && !e.isAltDown() && !e.isShortcutDown() && !e.isMetaDown();
            if (e.getCode() == KeyCode.SPACE) { alternar(p); e.consume(); }
            else if (e.getCode() == KeyCode.ENTER) { completar(p); e.consume(); }
            else if (solo && !atendiendo() && e.getCode() == KeyCode.S && !p.isRevisadoPorArea()) {
                seguirEn = tabla.getSelectionModel().getSelectedIndex();
                responder(List.of(p), null);
                e.consume();
            } else if (solo && !atendiendo() && e.getCode() == KeyCode.N && !p.isRevisadoPorArea()) {
                e.consume();
                int en = tabla.getSelectionModel().getSelectedIndex();
                pedirNota(1).ifPresent(n -> { seguirEn = en; responder(List.of(p), n); });
            } else if (solo && !atendiendo() && e.getCode() == KeyCode.E && p.isRevisadoPorArea() && !p.isEtiquetado()) {
                seguirEn = tabla.getSelectionModel().getSelectedIndex();
                etiquetas(List.of(p));
                e.consume();
            }
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
        quitarRevision.setGraphic(new FontIcon("mdi2k-keyboard-return"));
        quitarRevision.getStyleClass().add("btn-secondary");
        quitarRevision.setTooltip(new Tooltip("Regresa los marcados a «Por revisar» de su área, con lo que le quieras decir"));
        quitarRevision.setOnAction(e -> regresar(marcadosVisibles()));
        pasar.setGraphic(icono("mdi2s-swap-horizontal"));
        pasar.getStyleClass().add("btn-primary");
        pasar.setTooltip(new Tooltip("Están en otra área: se transfieren con su número y su resguardo nuevos, y esa área los revisa"));
        pasar.setOnAction(e -> pasarAOtraArea(marcadosVisibles()));
        desmarcar.getStyleClass().add("btn-ghost");
        desmarcar.setOnAction(e -> { marcados.clear(); actualizarMarcas(); });
        for (Button b : List.of(siNuestros, noNuestros, etiquetados, quitarRevision, pasar, desmarcar)) b.setMinWidth(Region.USE_PREF_SIZE);
        botones();

        resumen.getStyleClass().add("table-count-label");
        resumen.setMinWidth(Region.USE_PREF_SIZE);
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        pista.getStyleClass().add("muted-sm");
        HBox acciones = new HBox(8, resumen, desmarcar, pista, hueco, quitarRevision, etiquetados, noNuestros, siNuestros, pasar);
        acciones.setAlignment(Pos.CENTER_LEFT);
        acciones.getStyleClass().add("revisar-acciones");

        mensajes.getStyleClass().add("revisar-mensajes");
        mensajes.setVisible(false);
        mensajes.setManaged(false);
        VBox cuerpo = new VBox(10, avance, ayuda, mensajes, barraFiltros, tabla, acciones);
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
                inventario = lista;
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
        vista = v;
        boolean atender = atendiendo();
        columnas(atender);
        colRevision.setText(atender ? "Lo que dijo el área" : admin ? "¿Es del área?" : "¿Es de tu área?");
        pintarMensajes();
        ayuda.setText(atender
            ? "Estos bienes, según su área, no son suyos o no los encuentran. Si está en otra área, «Pasar a otra área» lo "
              + "transfiere con su número y su resguardo nuevos, y esa área lo revisa. Si sí es suyo, «Es suyo» se lo regresa "
              + "a «Por revisar» con lo que le quieras decir. «Ver» muestra quién lo tenía y sus movimientos."
            : "Responde con los botones de cada renglón. Para responder varios a la vez, márcalos con su casilla "
              + "y usa los botones de abajo. «Completar» le pone foto, ubicación y estado físico. "
              + "Con el teclado: ↑↓ para moverte, S «Sí es», N «No es», E «Ya la tiene», Espacio marca y Enter completa.");
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
        // Answered from the keyboard: on to the one that took its place.
        if (seguirEn >= 0) {
            int i = Math.min(seguirEn, tabla.getItems().size() - 1);
            seguirEn = -1;
            if (i >= 0) {
                tabla.getSelectionModel().clearAndSelect(i);
                tabla.getFocusModel().focus(i);
                tabla.scrollTo(Math.max(0, i - 2));
                tabla.requestFocus();
            }
        }
    }

    /** The row to go on from after an answer given with S, N or E (-1: none). */
    private int seguirEn = -1;

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

    /** The columns of each set, with their widths. Shown or hidden on a table already laid
     *  out, the constrained policy split the room unevenly («Có…», «Bien» cut to a word):
     *  the widths are set with the policy off and it goes back on, giving the rest to the last. */
    private void columnas(boolean atender) {
        if (columnasAtender != null && columnasAtender == atender) return;
        columnasAtender = atender;
        tabla.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        colAtender.setVisible(atender);
        colEtiqueta.setVisible(!atender);
        colFalta.setVisible(!atender);
        // What the área wrote is what Patrimonio reads to decide: more room for it.
        // With the «Área» column (all áreas at once) the others make room for it.
        double[] anchos = atender
            ? new double[]{96, 220, 150, 140, 250, 285}
            : area == null ? new double[]{100, 200, 150, 120, 230, 130, 200}
                           // An área's own list (27-nov-2026): «Bien» had 260 px («ARCHIVERO METALICO
                           // CON 4 GAVET...») and what was left went to «Le falta», mostly «Nada».
                           : new double[]{96, 340, 160, 150, 210, 130, 150};
        List<TableColumn<Producto, Producto>> cols = atender
            ? List.of(colCodigo, colBien, colArea, colResguardante, colRevision, colAtender)
            : List.of(colCodigo, colBien, colArea, colResguardante, colRevision, colEtiqueta, colFalta);
        for (int i = 0; i < cols.size(); i++) cols.get(i).setPrefWidth(anchos[i]);
        Platform.runLater(() -> tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN));
    }

    /** Patrimonio looking at the avisos «No es del área». */
    private boolean atendiendo() {
        return admin && vista == Vista.OBSERVADOS;
    }

    /** The área: Patrimonio's messages on the bienes it sent back, whole, above the list. */
    private void pintarMensajes() {
        List<Producto> con = admin ? List.of() : todos.stream().filter(p -> p.getMensajePatrimonio() != null).toList();
        mensajes.getChildren().clear();
        mensajes.setVisible(!con.isEmpty());
        mensajes.setManaged(!con.isEmpty());
        if (con.isEmpty()) return;
        Label titulo = new Label(con.size() == 1 ? "Patrimonio te regresó 1 bien para que lo revises otra vez"
            : "Patrimonio te regresó " + con.size() + " bienes para que los revises otra vez",
            new FontIcon("mdi2m-message-text-outline"));
        titulo.getStyleClass().add("revisar-mensajes-titulo");
        mensajes.getChildren().add(titulo);
        for (Producto p : con.subList(0, Math.min(3, con.size()))) {
            Label l = new Label(p.getCodigo() + " · " + p.getNombre() + ": «" + p.getMensajePatrimonio() + "»");
            l.setWrapText(true);
            l.getStyleClass().add("revisar-mensajes-texto");
            mensajes.getChildren().add(l);
        }
        if (con.size() > 3) {
            Label mas = new Label("… y " + (con.size() - 3) + " más: están en «Por revisar», cada uno con su mensaje");
            mas.getStyleClass().add("revisar-mensajes-texto");
            mensajes.getChildren().add(mas);
        }
    }

    private void botones() {
        List<Producto> sel = marcadosVisibles();
        boolean hay = !sel.isEmpty();
        boolean atender = atendiendo();
        // Attending avisos: move them or send them back; the área's own answers make no sense there.
        for (Button b : List.of(siNuestros, noNuestros, etiquetados)) { b.setVisible(!atender); b.setManaged(!atender); }
        pasar.setVisible(atender);
        pasar.setManaged(atender);
        quitarRevision.setVisible(admin);
        quitarRevision.setManaged(admin);
        siNuestros.setDisable(!hay);
        noNuestros.setDisable(!hay);
        etiquetados.setDisable(sel.stream().allMatch(Producto::isEtiquetado));
        quitarRevision.setDisable(sel.stream().noneMatch(Producto::isRevisadoPorArea));
        pasar.setDisable(!hay);
        desmarcar.setVisible(!marcados.isEmpty());
        desmarcar.setManaged(!marcados.isEmpty());
        pista.setVisible(marcados.isEmpty());
        pista.setManaged(marcados.isEmpty());
        pista.setText(atender ? "Marca varios con su casilla para atenderlos juntos" : "Marca varios con su casilla para responderlos juntos");
        String n = sel.size() > 1 ? " (" + sel.size() + ")" : "";
        String de = admin ? "del área" : "de mi área";
        siNuestros.setText("Sí son " + de + n);
        noNuestros.setText("No son " + de + n + "…");
        etiquetados.setText("Ya tienen etiqueta" + n);
        quitarRevision.setText((atender ? "Son suyos: que los revise otra vez" : "Que los revisen otra vez") + n);
        pasar.setText("Pasar a otra área" + n);
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
            String mensaje = p.getMensajePatrimonio();
            if (mensaje == null) return h;
            // Sent back by Patrimonio: why, so the área doesn't answer "no es" again blindly.
            Label m = new Label("Patrimonio: «" + mensaje + "»", new FontIcon("mdi2m-message-text-outline"));
            m.getStyleClass().add("revisar-mensaje");
            m.setTooltip(new Tooltip("Patrimonio te lo regresó para que lo revises otra vez:\n" + mensaje));
            m.setMaxWidth(Double.MAX_VALUE);
            VBox v = new VBox(3, h, m);
            v.setAlignment(Pos.CENTER_LEFT);
            return v;
        }
        Label estado = new Label(p.isObservadoPorArea() ? "No es del área" : "Sí es del área",
            new FontIcon(p.isObservadoPorArea() ? "mdi2a-alert-circle-outline" : "mdi2c-check-circle"));
        estado.getStyleClass().addAll("revisar-estado", p.isObservadoPorArea() ? "revisar-estado-no" : "revisar-estado-si");
        HBox arriba = new HBox(6, estado);
        arriba.setAlignment(Pos.CENTER_LEFT);
        // Attending avisos, «Qué hacer» has the way back; elsewhere, «Cambiar» takes the answer back.
        if (!atendiendo()) {
            Hyperlink cambiar = new Hyperlink("Cambiar");
            cambiar.getStyleClass().add("revisar-cambiar");
            cambiar.setTooltip(new Tooltip("Borra esta respuesta para volver a responder"));
            cambiar.setOnAction(e -> quitar(List.of(p)));
            arriba.getChildren().add(cambiar);
        }
        VBox v = new VBox(0, arriba);
        v.setAlignment(Pos.CENTER_LEFT);
        if (p.isObservadoPorArea() && p.getRevisionAreaNota() != null) {
            Label nota = new Label("«" + p.getRevisionAreaNota() + "»");
            nota.getStyleClass().add(atendiendo() ? "revisar-nota" : "revisar-bien-detalle");
            if (atendiendo()) {
                // Two lines of it fit in the row; the whole note is in the tooltip.
                nota.setWrapText(true);
                nota.setMaxHeight(30);
            }
            Tooltip.install(nota, new Tooltip(p.getRevisionAreaNota()
                + (p.getRevisadoAreaPor() != null ? "\n— " + p.getRevisadoAreaPor() : "")
                + (p.getRevisadoAreaEn() != null ? ", " + FormatUtils.formatDateTime(p.getRevisadoAreaEn()) : "")));
            v.getChildren().add(nota);
        }
        return v;
    }

    /** Patrimonio, on an aviso «No es del área»: move it, send it back, or look at its ficha. */
    private Node celdaAtender(Producto p) {
        if (!p.isObservadoPorArea()) return new Region();
        Button aOtra = boton("Pasar a otra área", "mdi2s-swap-horizontal", "revisar-pasar",
            "Está en otra área: se transfiere con su número y su resguardo nuevos, y esa área lo revisa");
        aOtra.setOnAction(e -> pasarAOtraArea(List.of(p)));
        Button suyo = boton("Es suyo", "mdi2k-keyboard-return", "revisar-es-suyo",
            "Regresarlo a «Por revisar» de " + Objects.requireNonNullElse(p.getArea(), "su área") + ", con lo que le quieras decir");
        suyo.setOnAction(e -> regresar(List.of(p)));
        Button ver = boton("Ver", "mdi2e-eye-outline", "revisar-ver", "Su ficha: quién lo tenía, sus fotos y sus movimientos");
        ver.setOnAction(e -> ProductoDetailDialog.show(p, escena(), new com.sibim.service.MovimientoService(),
            org.slf4j.LoggerFactory.getLogger(RevisarBienesDialog.class)));
        HBox h = new HBox(6, aOtra, suyo, ver);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
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
            // As it was, with Patrimonio's message if it had one.
            () -> service.restaurarRevision(copia), copia);
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
        List<Producto> copia = List.copyOf(bienes);
        hacer(() -> service.quitarRevision(copia),
            copia.size() == 1 ? copia.get(0).getCodigo() + ": vuelve a «Por revisar»"
                : "Se quitó la revisión de " + FormatUtils.plural(copia.size(), "bien", "bienes"),
            () -> service.restaurarRevision(copia), copia);
    }

    /** Patrimonio: back to the área's «Por revisar», with what it wants to tell the área. */
    private void regresar(List<Producto> bienes) {
        List<Producto> copia = bienes.stream().filter(Producto::isRevisadoPorArea).toList();
        if (copia.isEmpty()) return;
        String area = unaArea(copia);
        pedirMensaje(copia, area).ifPresent(m -> hacer(() -> service.quitarRevision(copia, m),
            (copia.size() == 1 ? copia.get(0).getCodigo() + " regresó" : FormatUtils.plural(copia.size(), "bien regresó", "bienes regresaron"))
                + " a «Por revisar»" + (area != null ? " de " + area : "") + (m.isBlank() ? "" : ", con tu mensaje"),
            () -> service.restaurarRevision(copia), copia));
    }

    /** Patrimonio: Transferir, with these bienes ticked and the área's aviso as the motivo.
     *  Whatever is moved leaves this list on its own: in another área it is reviewed again. */
    private void pasarAOtraArea(List<Producto> bienes) {
        if (bienes.isEmpty()) return;
        String area = unaArea(bienes);
        String nota = bienes.size() == 1 ? bienes.get(0).getRevisionAreaNota() : null;
        String motivo = (area != null ? "Aviso de " + area : "Aviso de las áreas") + ": "
            + (nota != null && !nota.isBlank() ? nota.trim() : bienes.size() == 1 ? "no es suyo" : "no son suyos");
        TransferenciaArrastreDialog.show(inventario.isEmpty() ? new ArrayList<>(todos) : inventario,
            bienes.stream().map(Producto::getId).toList(), motivo, () -> cambio = true);
        cargar();
    }

    /** The área of all of them, or null when they are from several. */
    private static String unaArea(List<Producto> bienes) {
        Set<String> areas = new HashSet<>();
        bienes.forEach(p -> areas.add(p.getArea()));
        return areas.size() == 1 ? areas.iterator().next() : null;
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

    /** What Patrimonio tells the área when sending bienes back; empty to say nothing,
     *  nothing when it was cancelled. */
    private Optional<String> pedirMensaje(List<Producto> bienes, String area) {
        TextInputDialog d = new TextInputDialog();
        DialogUtil.applyOwner(d);
        DialogUtil.applyStylesheet(d.getDialogPane());
        d.setTitle("Que lo revise otra vez");
        Producto uno = bienes.size() == 1 ? bienes.get(0) : null;
        String dijo = uno != null && uno.isObservadoPorArea() ? "\nEl área dijo: «" + uno.getRevisionAreaNota() + "»" : "";
        d.setHeaderText((uno != null ? uno.getCodigo() + " · " + uno.getNombre() + " vuelve"
                : FormatUtils.plural(bienes.size(), "bien vuelve", "bienes vuelven"))
            + " a «Por revisar»" + (area != null ? " de " + area : "") + dijo);
        d.setContentText("¿Qué le dices al área? (opcional)");
        d.getEditor().setPromptText("Ej. sí es suyo, está en la bodega del 2º piso; búscalo por su etiqueta");
        d.getEditor().setPrefColumnCount(38);
        d.getEditor().setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().length() <= 300 ? c : null));
        Button ok = (Button) d.getDialogPane().lookupButton(ButtonType.OK);
        ok.setText(bienes.size() == 1 ? "Regresárselo" : "Regresárselos");
        return d.showAndWait().map(String::trim);
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
