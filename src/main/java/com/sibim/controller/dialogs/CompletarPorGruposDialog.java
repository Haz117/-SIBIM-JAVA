package com.sibim.controller.dialogs;

import com.sibim.config.Areas;
import com.sibim.model.Producto;
import com.sibim.service.CalidadDatosService;
import com.sibim.service.CompletarPorGrupos;
import com.sibim.service.CompletarPorGrupos.Campo;
import com.sibim.service.CompletarPorGrupos.Grupo;
import com.sibim.service.EditarVarios;
import com.sibim.service.ProductoService;
import com.sibim.util.AppColors;
import com.sibim.util.CategoriaCorta;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Bienes › Más › «Completar por grupos» (also from Completar datos and Inicio › Por hacer): each
 * name with two or more bienes is one row, with its vida útil, color and tipo typed once for all
 * of them. See {@link CompletarPorGrupos} for why and what each row brings written.
 */
public final class CompletarPorGruposDialog {

    private static final Logger log = LoggerFactory.getLogger(CompletarPorGruposDialog.class);
    static final String TODAS = "Todas las áreas";
    /** What the rows must lack: the three, or one of them. */
    private static final Map<String, Set<Campo>> FALTA = new LinkedHashMap<>();
    static {
        FALTA.put("Les falta vida útil, color o tipo", EnumSet.allOf(Campo.class));
        FALTA.put("Les falta vida útil", EnumSet.of(Campo.VIDA_UTIL));
        FALTA.put("Les falta color", EnumSet.of(Campo.COLOR));
        FALTA.put("Les falta tipo de bien", EnumSet.of(Campo.TIPO));
    }

    /** One row: its group, what is typed in each field, and how its saving went. */
    static final class Fila {
        Grupo grupo;
        final Map<Campo, String> texto = new EnumMap<>(Campo.class);
        /** Every bien of the row has now what was asked of it. */
        boolean completo;
        boolean guardando;
        int guardados;
        String error;

        Fila(Grupo g) {
            grupo = g;
            g.propuestas().forEach((c, p) -> texto.put(c, p.texto()));
        }
    }

    private final ProductoService service = new ProductoService();
    /** The inventory, as each bien is saved here. */
    private final Map<String, Producto> porId = new LinkedHashMap<>();
    private final Map<String, Producto> guardados = new LinkedHashMap<>();
    private final List<String> tipos;
    private final Consumer<Collection<Producto>> alCerrar;
    private Dialog<ButtonType> dialog;

    private final ComboBox<String> area = new ComboBox<>();
    private final ComboBox<String> falta = new ComboBox<>();
    private final TextField buscar = new TextField();
    private final Label resumen = new Label();
    private final List<Fila> filas = new ArrayList<>();
    private final ObservableList<Fila> visibles = FXCollections.observableArrayList();
    private final ListView<Fila> lista = new ListView<>(visibles);
    private Fila pedirFoco;

    private CompletarPorGruposDialog(List<Producto> bienes, Consumer<Collection<Producto>> alCerrar) {
        for (Producto p : bienes) if (p.getFechaBaja() == null) porId.put(p.getId(), p);
        this.tipos = CalidadDatosService.tiposParaElegir(bienes);
        this.alCerrar = alCerrar;
    }

    /**
     * @param areaInicial the área to open on, or null for all
     * @param alCerrar    on closing, with the bienes saved here (none when nothing was saved)
     */
    public static void show(List<Producto> bienes, String areaInicial, Consumer<Collection<Producto>> alCerrar) {
        new CompletarPorGruposDialog(bienes, alCerrar).abrir(areaInicial);
    }

    private void abrir(String areaInicial) {
        dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Completar por grupos");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(1240, pantalla.getWidth() * 0.94), Math.min(760, pantalla.getHeight() * 0.88));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        HBox header = DialogUtil.gradientHeader("mdi2f-format-list-checks", "Completar por grupos",
            "Los bienes con el mismo nombre llevan la misma vida útil, el mismo color y el mismo tipo: escríbelos una vez por grupo",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        List<String> areas = new ArrayList<>(List.of(TODAS));
        Set<String> conGrupos = new HashSet<>();
        for (Grupo g : CompletarPorGrupos.grupos(new ArrayList<>(porId.values()))) conGrupos.addAll(g.areas());
        Areas.getAllAreaNames().stream().filter(conGrupos::contains).forEach(areas::add);
        conGrupos.stream().filter(a -> !areas.contains(a)).sorted().forEach(areas::add);
        area.getItems().setAll(areas);
        area.setValue(areaInicial != null && areas.contains(areaInicial) ? areaInicial : TODAS);
        area.setVisibleRowCount(16);
        area.setAccessibleText("Área");
        area.valueProperty().addListener((o, a, b) -> cargar());
        falta.getItems().setAll(FALTA.keySet());
        falta.setValue(falta.getItems().get(0));
        falta.setAccessibleText("Qué les falta");
        falta.valueProperty().addListener((o, a, b) -> cargar());
        buscar.setId("buscarGrupo");
        buscar.setPromptText("Buscar por nombre…");
        buscar.getStyleClass().add("form-input");
        buscar.textProperty().addListener((o, a, b) -> refiltrar());
        HBox.setHgrow(buscar, Priority.ALWAYS);
        resumen.getStyleClass().add("muted-sm");
        resumen.setMinWidth(Region.USE_PREF_SIZE);
        HBox filtros = new HBox(10, area, falta, buscar, resumen);
        filtros.setAlignment(Pos.CENTER_LEFT);

        Label ayuda = new Label("Lo que escribes va solo a los bienes del grupo que no lo tienen; lo que ya viene escrito es lo que "
            + "SIBIM sabe (abajo dice de dónde). Enter guarda el grupo y pasa al siguiente; cada guardado se deshace desde su aviso.");
        ayuda.getStyleClass().add("muted-sm");
        ayuda.setWrapText(true);
        ayuda.setMinHeight(Region.USE_PREF_SIZE);
        ayuda.setGraphic(new FontIcon("mdi2k-keyboard-return"));

        lista.getStyleClass().add("cnt-lista");
        lista.setCellFactory(lv -> new CeldaGrupo(lv));
        lista.setPlaceholder(new Label("No hay grupos a los que les falte eso"));
        VBox.setVgrow(lista, Priority.ALWAYS);

        VBox cuerpo = new VBox(10, filtros, ayuda, lista);
        cuerpo.setPadding(new Insets(0, 22, 14, 22));
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        dialog.getDialogPane().setContent(new VBox(12, header, cuerpo));
        dialog.setOnHidden(e -> { if (alCerrar != null) alCerrar.accept(guardados.values()); });

        cargar();
        Platform.runLater(() -> { if (!visibles.isEmpty()) { pedirFoco = visibles.get(0); lista.refresh(); } });
        dialog.showAndWait();
    }

    private Predicate<Producto> cuales() {
        String a = area.getValue();
        return a == null || TODAS.equals(a) ? p -> true : p -> a.equals(areaDe(p));
    }

    private Set<Campo> campos() {
        return FALTA.getOrDefault(falta.getValue(), EnumSet.allOf(Campo.class));
    }

    static String areaDe(Producto p) {
        return p.getArea() != null && !p.getArea().isBlank() ? p.getArea() : "Sin área";
    }

    /** Another área or another «les falta» is another list: what was typed belonged to the last one. */
    private void cargar() {
        filas.clear();
        for (Grupo g : CompletarPorGrupos.grupos(new ArrayList<>(porId.values()), cuales(), campos())) filas.add(new Fila(g));
        refiltrar();
        if (!visibles.isEmpty()) lista.scrollTo(0);
    }

    private void refiltrar() {
        visibles.setAll(DialogUtil.coincidencias(filas, f -> f.grupo.nombre(), buscar.getText()));
        actualizarResumen();
    }

    private void actualizarResumen() {
        long grupos = filas.stream().filter(f -> !f.completo).count();
        int bienes = filas.stream().filter(f -> !f.completo).mapToInt(f -> f.grupo.incompletos()).sum();
        resumen.setText(FormatUtils.plural(grupos, "grupo", "grupos") + " · " + FormatUtils.plural(bienes, "bien", "bienes") + " por completar");
    }

    // ── Guardar un grupo ──────────────────────────────────────────────────────

    private Scene escena() {
        return dialog.getDialogPane().getScene();
    }

    void guardar(Fila f) {
        if (f.guardando || f.completo) return;
        Map<Campo, Object> valores = new EnumMap<>(Campo.class);
        try {
            for (Campo c : f.grupo.faltan().keySet()) {
                Object v = CompletarPorGrupos.leer(c, f.texto.get(c), tipos);
                if (v != null) valores.put(c, v);
            }
        } catch (IllegalArgumentException e) {
            f.error = e.getMessage();
            lista.refresh();
            return;
        }
        List<EditarVarios.Renglon> renglones = CompletarPorGrupos.renglones(f.grupo, valores);
        if (renglones.isEmpty()) return;
        f.guardando = true;
        f.error = null;
        lista.refresh();
        String que = describir(valores);
        DialogUtil.runAsync(() -> EditarVarios.aplicar(service, renglones),
            r -> {
                f.guardando = false;
                r.guardados().forEach(p -> { porId.put(p.getId(), p); guardados.put(p.getId(), p); });
                f.guardados += r.guardados().size();
                if (!r.fallas().isEmpty()) f.error = FormatUtils.plural(r.fallas().size(), "no se guardó", "no se guardaron") + ": "
                    + String.join("; ", r.fallas().subList(0, Math.min(3, r.fallas().size())));
                reagrupar(f);
                lista.refresh();
                actualizarResumen();
                NotificacionUtil.exitoConAccion(escena(), f.grupo.nombre() + ": " + que + " en "
                    + FormatUtils.plural(r.guardados().size(), "bien", "bienes"), "Deshacer", () -> deshacer(f, r));
            },
            e -> {
                f.guardando = false;
                f.error = e.getMessage() != null ? e.getMessage() : "No se pudo guardar";
                log.warn("No se pudo completar el grupo {}: {}", f.grupo.nombre(), e.getMessage());
                lista.refresh();
            });
    }

    /** «vida útil 10 años, color NEGRO, tipo MUEBLE». */
    private static String describir(Map<Campo, Object> valores) {
        List<String> partes = new ArrayList<>();
        valores.forEach((c, v) -> partes.add((c == Campo.TIPO ? "tipo" : c.etiqueta.toLowerCase(Locale.ROOT)) + " " + c.comoSeGuarda.texto(v)));
        return String.join(", ", partes);
    }

    /** The row as its bienes are now: what is still missing, or done. */
    private void reagrupar(Fila f) {
        String clave = CalidadDatosService.claveTexto(f.grupo.nombre());
        List<Producto> mismos = porId.values().stream().filter(p -> clave.equals(CalidadDatosService.claveTexto(p.getNombre()))).toList();
        List<Grupo> ahora = CompletarPorGrupos.grupos(mismos, cuales(), campos());
        if (ahora.isEmpty()) {
            f.completo = true;
            f.texto.clear();
            return;
        }
        f.completo = false;
        Grupo g = ahora.get(0);
        f.texto.keySet().removeIf(c -> !g.faltan().containsKey(c));
        f.grupo = g;
    }

    private void deshacer(Fila f, EditarVarios.Resultado r) {
        DialogUtil.runAsync(() -> {
                int n = EditarVarios.deshacer(service, r);
                // As saved now (each one's change time), to be saved again from here.
                List<Producto> ahora = new ArrayList<>();
                for (Producto p : r.guardados()) service.findById(p.getId()).ifPresent(ahora::add);
                return Map.entry(n, ahora);
            },
            res -> {
                res.getValue().forEach(p -> { porId.put(p.getId(), p); guardados.put(p.getId(), p); });
                f.guardados = Math.max(0, f.guardados - res.getKey());
                reagrupar(f);
                f.grupo.propuestas().forEach((c, p) -> f.texto.putIfAbsent(c, p.texto()));
                lista.refresh();
                actualizarResumen();
                NotificacionUtil.info(escena(), FormatUtils.plural(res.getKey(), "bien quedó", "bienes quedaron") + " como estaba");
            },
            ex -> NotificacionUtil.error(escena(), "No se pudo deshacer: " + com.sibim.util.MensajeError.de(ex)));
    }

    private void siguiente(Fila f) {
        int i = visibles.indexOf(f);
        for (int j = i + 1; j < visibles.size(); j++) {
            if (visibles.get(j).completo) continue;
            pedirFoco = visibles.get(j);
            lista.scrollTo(Math.max(0, j - 1));
            break;
        }
        lista.refresh();
    }

    private static void ver(Node n, boolean visible) {
        n.setVisible(visible);
        n.setManaged(visible);
    }

    // ── La celda ──────────────────────────────────────────────────────────────

    /** One field of the row: its name, what is typed and where it comes from. */
    private static final class Casilla {
        final Label titulo = new Label();
        final TextField campo = new TextField();
        final Label nota = new Label();
        final VBox caja;

        Casilla(Campo c, double medida) {
            // With «Texto: Grande» or «Extra» the field grows with the letter, as the tables' columns do.
            double ancho = Math.round(medida * com.sibim.util.EscalaTexto.factorActual());
            titulo.setText(c.etiqueta);
            titulo.getStyleClass().add("muted-sm");
            campo.getStyleClass().add("form-input");
            campo.setPrefWidth(ancho);
            campo.setMinWidth(ancho);
            campo.setMaxWidth(ancho);
            campo.setPromptText(switch (c) {
                case VIDA_UTIL -> "Años";
                case COLOR -> "Color";
                case TIPO -> "Ej. MUEBLE";
            });
            nota.getStyleClass().add("org-table-sub");
            nota.setWrapText(true);
            nota.setMaxWidth(ancho);
            nota.setMinHeight(Region.USE_PREF_SIZE);
            caja = new VBox(3, titulo, campo, nota);
            caja.setMinWidth(ancho);
            caja.setPrefWidth(ancho);
            caja.setMaxWidth(ancho);
        }
    }

    private final class CeldaGrupo extends ListCell<Fila> {
        private final Label nombre = new Label();
        private final Label detalle = new Label();
        private final Map<Campo, Casilla> casillas = new EnumMap<>(Campo.class);
        private final Button guardar = new Button("Guardar");
        private final Label estado = new Label();
        private final HBox fila;
        private boolean llenando = false;

        CeldaGrupo(ListView<Fila> lv) {
            nombre.getStyleClass().add("org-child-label");
            nombre.setTextOverrun(OverrunStyle.ELLIPSIS);
            nombre.setWrapText(true);
            nombre.setMaxHeight(42);
            nombre.setMinWidth(0);
            detalle.getStyleClass().add("org-table-sub");
            detalle.setWrapText(true);
            detalle.setMinWidth(0);
            VBox textos = new VBox(3, nombre, detalle);
            textos.setMinWidth(160);
            textos.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(textos, Priority.ALWAYS);

            casillas.put(Campo.VIDA_UTIL, new Casilla(Campo.VIDA_UTIL, 110));
            casillas.put(Campo.COLOR, new Casilla(Campo.COLOR, 190));
            casillas.put(Campo.TIPO, new Casilla(Campo.TIPO, 170));

            guardar.setGraphic(new FontIcon("mdi2c-content-save-outline"));
            guardar.getStyleClass().add("btn-secondary");
            guardar.setMinWidth(Region.USE_PREF_SIZE);
            guardar.setOnAction(e -> { if (getItem() != null) { guardar(getItem()); siguiente(getItem()); } });
            estado.setMinWidth(Region.USE_PREF_SIZE);
            estado.setWrapText(true);
            double ancho = Math.round(150 * com.sibim.util.EscalaTexto.factorActual());
            estado.setMaxWidth(ancho);
            VBox acciones = new VBox(6, guardar, estado);
            acciones.setAlignment(Pos.CENTER_LEFT);
            acciones.setMinWidth(ancho);
            acciones.setPrefWidth(ancho);

            fila = new HBox(14, textos);
            casillas.values().forEach(c -> fila.getChildren().add(c.caja));
            fila.getChildren().add(acciones);
            fila.setAlignment(Pos.TOP_LEFT);
            fila.getStyleClass().add("cnt-fila");
            fila.setStyle("-fx-cursor: default;");
            fila.maxWidthProperty().bind(lv.widthProperty().subtract(30));
            fila.setMinWidth(0);

            casillas.forEach((c, cas) -> {
                cas.campo.textProperty().addListener((o, a, v) -> {
                    Fila f = getItem();
                    if (llenando || f == null) return;
                    f.texto.put(c, v == null ? "" : v);
                    if (f.error != null) { f.error = null; pintarEstado(f); }
                });
                cas.campo.setOnKeyPressed(e -> {
                    Fila f = getItem();
                    if (f == null || e.getCode() != KeyCode.ENTER) return;
                    guardar(f);
                    if (f.error == null) siguiente(f);
                    e.consume();
                });
            });
        }

        @Override protected void updateItem(Fila f, boolean empty) {
            super.updateItem(f, empty);
            setText(null);
            if (empty || f == null) { setGraphic(null); return; }
            llenando = true;
            Grupo g = f.grupo;
            nombre.setText(g.nombre());
            nombre.setTooltip(new Tooltip(g.nombre()));
            List<String> partes = new ArrayList<>();
            partes.add(CompletarPorGrupos.cuantos(g));
            Set<String> areas = g.areas();
            partes.add(areas.size() == 1 ? areas.iterator().next() : areas.size() + " áreas");
            String categoria = g.bienes().get(0).getCategoriaNombre();
            if (categoria != null && !categoria.isBlank()) partes.add(CategoriaCorta.de(categoria));
            detalle.setText(String.join(" · ", partes));
            detalle.setTooltip(new Tooltip(String.join(" · ", partes) + (areas.size() > 1 ? "\n" + String.join(", ", areas) : "")));

            boolean bloqueado = f.guardando || f.completo;
            casillas.forEach((c, cas) -> {
                Integer n = g.faltan().get(c);
                boolean pide = n != null && !f.completo;
                cas.campo.setText(pide ? f.texto.getOrDefault(c, "") : "");
                cas.campo.setDisable(!pide || bloqueado);
                cas.campo.setAccessibleText(c.etiqueta + " de " + g.nombre());
                CompletarPorGrupos.Propuesta p = g.propuestas().get(c);
                String nota;
                if (!pide) nota = f.completo && n != null ? "Listo" : g.tienen().get(c);
                else if (p != null && p.texto().equals(f.texto.getOrDefault(c, "").trim())) nota = "Falta en " + n + " · " + p.porQue();
                else {
                    String tienen = g.tienen().get(c);
                    nota = "Falta en " + n + ("Ninguno tiene".equals(tienen) ? "" : " · ya tienen " + tienen);
                }
                cas.nota.setText(nota);
            });
            guardar.setText(f.completo ? "Guardado" : "Guardar en " + g.incompletos());
            guardar.setDisable(bloqueado);
            pintarEstado(f);
            fila.getStyleClass().removeAll("cnt-fila-vista", "cnt-fila-incidencia");
            if (f.error != null) fila.getStyleClass().add("cnt-fila-incidencia");
            else if (f.completo) fila.getStyleClass().add("cnt-fila-vista");
            llenando = false;
            setGraphic(fila);
            if (f == pedirFoco) {
                pedirFoco = null;
                Platform.runLater(() -> casillas.values().stream().map(c -> c.campo).filter(t -> !t.isDisabled())
                    .findFirst().ifPresent(TextField::requestFocus));
            }
        }

        private void pintarEstado(Fila f) {
            estado.getStyleClass().setAll("cell-badge", f.error != null ? "cell-badge-danger"
                : f.guardando ? "cell-badge-blue" : "cell-badge-success");
            estado.setText(f.error != null ? f.error : f.guardando ? "Guardando…"
                : f.guardados > 0 ? "Guardado en " + FormatUtils.plural(f.guardados, "bien", "bienes") : "");
            estado.setTooltip(f.error != null ? new Tooltip(f.error) : null);
            ver(estado, f.error != null || f.guardando || f.guardados > 0);
        }
    }
}
