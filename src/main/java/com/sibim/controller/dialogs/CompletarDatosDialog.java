package com.sibim.controller.dialogs;

import com.sibim.config.Areas;
import com.sibim.config.VidaUtilConac;
import com.sibim.controller.FiltroArea;
import com.sibim.model.Producto;
import com.sibim.service.CalidadDatosService;
import com.sibim.service.CalidadDatosService.Dato;
import com.sibim.service.ProductoService;
import com.sibim.util.AppColors;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Bienes › Completar datos: what each bien is missing, área by área, typed right on
 *  the list, without opening a form per bien. A value and Enter save it and go on to the
 *  next. On 30-oct-2026, 1,145 of 3,062 bienes had no price and 2,951 no vida útil,
 *  which leaves the totals and the depreciation short. */
public final class CompletarDatosDialog {

    private static final Logger log = LoggerFactory.getLogger(CompletarDatosDialog.class);
    private static final String TODAS = "Todas las áreas";

    /** One row, and what happened when it was saved. */
    private static final class Fila {
        Producto producto;
        String texto = "";
        LocalDate fecha;
        boolean guardado;
        boolean guardando;
        String error;
        Fila(Producto p) { producto = p; }
    }

    private final ProductoService service = new ProductoService();
    private final CalidadDatosService calidad = new CalidadDatosService();
    private final List<Fila> filas = new ArrayList<>();
    private final Runnable alCambiar;
    private Dialog<ButtonType> dialog;
    private boolean cambio = false;

    private final ListView<String> areas = new ListView<>();
    private final Map<String, Integer> porArea = new HashMap<>();
    private final Map<Dato, ToggleButton> pestanas = new EnumMap<>(Dato.class);
    /** The chips by what they are for, each row with its name. */
    private static final Map<String, List<Dato>> GRUPOS_DE_DATOS = new LinkedHashMap<>();
    static {
        GRUPOS_DE_DATOS.put("Para los totales", List.of(Dato.PRECIO, Dato.FECHA, Dato.VIDA_UTIL, Dato.FOTO));
        GRUPOS_DE_DATOS.put("De la factura", List.of(Dato.PROVEEDOR, Dato.FOLIO, Dato.FOLIO_REVISAR, Dato.FACTURA));
        GRUPOS_DE_DATOS.put("Del bien", List.of(Dato.COLOR, Dato.TIPO));
    }
    private final Map<String, HBox> filasDeDatos = new LinkedHashMap<>();
    private final TextField buscar = new TextField();
    private final ObservableList<Fila> visibles = FXCollections.observableArrayList();
    private final ListView<Fila> lista = new ListView<>(visibles);
    private final Label tituloArea = new Label();
    private final Label ayuda = new Label();
    private final HBox barraConac = new HBox(10);
    /** The bar's button: «Revisar y aplicar» (CONAC), «Pasarlos a N° Factura» (folios). */
    private final Button revisarConac = new Button("Revisar y aplicar");
    private final Label textoConac = new Label();
    private final HBox barraTodos = new HBox(10);
    private final Label textoTodos = new Label();
    private final TextField aniosTodos = new TextField();
    private final VBox vacio = new VBox(8);
    private final VBox panel = new VBox(8);
    private Fila pedirFoco;
    private Dato inicial = Dato.PRECIO;

    private CompletarDatosDialog(List<Producto> bienes, Runnable alCambiar) {
        bienes.stream().filter(p -> p.getFechaBaja() == null)
            .sorted(Comparator.comparing(p -> Objects.requireNonNullElse(p.getCodigo(), ""), com.sibim.service.ReporteService.POR_CODIGO))
            .forEach(p -> filas.add(new Fila(p)));
        this.alCambiar = alCambiar;
    }

    /** @param alCambiar run on closing when something was saved */
    public static void show(List<Producto> bienes, Runnable alCambiar) {
        show(bienes, Dato.PRECIO, alCambiar);
    }

    /** @param inicial the dato it opens on: the one somebody came from Inicio to fill */
    public static void show(List<Producto> bienes, Dato inicial, Runnable alCambiar) {
        CompletarDatosDialog d = new CompletarDatosDialog(bienes, alCambiar);
        d.inicial = inicial;
        d.abrir();
    }

    private void abrir() {
        dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Completar datos");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(1240, pantalla.getWidth() * 0.94), Math.min(760, pantalla.getHeight() * 0.88));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        HBox header = DialogUtil.gradientHeader("mdi2c-clipboard-edit-outline", "Completar datos",
            "Elige un área y captura lo que le falta a cada bien; Enter guarda y pasa al siguiente",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        HBox cuerpo = new HBox(16, panelAreas(), panelDerecho());
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        VBox debajo = new VBox(cuerpo);
        debajo.setPadding(new Insets(0, 22, 14, 22));
        VBox.setVgrow(debajo, Priority.ALWAYS);
        dialog.getDialogPane().setContent(new VBox(12, header, debajo));

        dialog.setOnHidden(e -> { if (cambio && alCambiar != null) alCambiar.run(); });
        pestanas.get(inicial).setSelected(true);
        cargarAreas();
        mostrarArea();
        Platform.runLater(areas::requestFocus);
        dialog.showAndWait();
    }

    // ── Paso 1: el área ───────────────────────────────────────────────────────

    private VBox panelAreas() {
        areas.getStyleClass().add("trf-areas");
        areas.setCellFactory(lv -> new CeldaArea(lv));
        areas.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> mostrarArea());
        VBox.setVgrow(areas, Priority.ALWAYS);
        VBox p = new VBox(8, TransferenciaArrastreDialog.paso(1, "Elige el área"), areas);
        p.getStyleClass().add("trf-panel");
        p.setPrefWidth(330);
        p.setMinWidth(270);
        return p;
    }

    private Dato datoElegido() {
        return pestanas.entrySet().stream().filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey).findFirst().orElse(Dato.PRECIO);
    }

    private static String areaDe(Producto p) {
        return p.getArea() != null && !p.getArea().isBlank() ? p.getArea() : "Sin área";
    }

    /** The áreas in the organigrama's order, with what is missing of the chosen dato. */
    private void cargarAreas() {
        String elegida = areas.getSelectionModel().getSelectedItem();
        Dato dato = datoElegido();
        porArea.clear();
        for (Fila f : filas) if (CalidadDatosService.falta(f.producto, dato)) {
            porArea.merge(areaDe(f.producto), 1, Integer::sum);
            porArea.merge(TODAS, 1, Integer::sum);
        }
        Set<String> presentes = filas.stream().map(f -> areaDe(f.producto)).collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> orden = new ArrayList<>(List.of(TODAS));
        Areas.getAllAreaNames().stream().filter(presentes::contains).forEach(orden::add);
        presentes.stream().filter(a -> !orden.contains(a)).sorted().forEach(orden::add);
        // Only if it changed: replacing the items drops the selection, which restarts the area.
        if (!areas.getItems().equals(orden)) {
            areas.getItems().setAll(orden);
            // Opens on «Todas las áreas» (9-nov-2026): the right half was empty until a click.
            areas.getSelectionModel().select(elegida != null ? elegida : TODAS);
        }
        areas.refresh();
    }

    // ── Paso 2: lo que falta ──────────────────────────────────────────────────

    private VBox panelDerecho() {
        FontIcon icono = new FontIcon("mdi2c-clipboard-edit-outline");
        icono.getStyleClass().add("empty-icon-lg");
        Label titulo = new Label("Elige un área");
        titulo.getStyleClass().add("empty-state-msg");
        Label pista = new Label("El número junto a cada área es lo que le falta del dato elegido");
        pista.getStyleClass().add("empty-state-hint");
        vacio.getChildren().setAll(icono, titulo, pista);
        vacio.setAlignment(Pos.CENTER);
        VBox.setVgrow(vacio, Priority.ALWAYS);

        tituloArea.getStyleClass().add("trf-paso-titulo");
        ToggleGroup grupo = new ToggleGroup();
        // Three rows with their name, and only what has something to capture (12-nov-2026:
        // ten chips in two rows, several at 0, and no telling which mattered for what).
        VBox chips = new VBox(4);
        for (var g : GRUPOS_DE_DATOS.entrySet()) {
            Label nombre = new Label(g.getKey());
            nombre.getStyleClass().add("muted-sm");
            nombre.setMinWidth(112);
            nombre.setPrefWidth(112);
            FlowPane fila = new FlowPane(6, 6);
            fila.setAlignment(Pos.CENTER_LEFT);
            for (Dato d : g.getValue()) {
                ToggleButton t = new ToggleButton(d.etiqueta);
                t.setToggleGroup(grupo);
                t.getStyleClass().add("filter-chip");
                t.setMinWidth(Region.USE_PREF_SIZE);
                pestanas.put(d, t);
                fila.getChildren().add(t);
            }
            HBox.setHgrow(fila, Priority.ALWAYS);
            HBox renglon = new HBox(8, nombre, fila);
            renglon.setAlignment(Pos.CENTER_LEFT);
            filasDeDatos.put(g.getKey(), renglon);
            chips.getChildren().add(renglon);
        }
        grupo.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) { a.setSelected(true); return; }
            cargarAreas();
            mostrarArea();
        });

        buscar.setId("buscarBien");
        buscar.setPromptText("Buscar por código, nombre o resguardante…");
        buscar.getStyleClass().add("form-input");
        buscar.textProperty().addListener((o, a, b) -> refiltrar());

        // Most of the vida útil comes from the bien's CONAC account: no need to type it.
        textoConac.getStyleClass().add("muted-sm");
        textoConac.setWrapText(true);
        textoConac.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(textoConac, Priority.ALWAYS);
        Button revisar = revisarConac;
        revisar.setGraphic(new FontIcon("mdi2c-calendar-check-outline"));
        revisar.getStyleClass().add("btn-secondary");
        revisar.setMinWidth(Region.USE_PREF_SIZE);
        revisar.setOnAction(e -> aplicarConac());
        barraConac.getChildren().setAll(textoConac, revisar);
        barraConac.setAlignment(Pos.CENTER_LEFT);
        barraConac.getStyleClass().add("cnt-aviso");

        // What the CONAC leaves to the municipio (esposas, chalecos, cascos…) is the same
        // for many bienes: typed once for the list as it is filtered.
        textoTodos.getStyleClass().add("muted-sm");
        textoTodos.setWrapText(true);
        textoTodos.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(textoTodos, Priority.ALWAYS);
        aniosTodos.setId("aniosTodos");
        aniosTodos.setPromptText("Años");
        aniosTodos.getStyleClass().add("form-input");
        aniosTodos.setPrefWidth(70);
        aniosTodos.setMinWidth(70);
        Button todos = new Button("Poner a todos");
        todos.setGraphic(new FontIcon("mdi2c-check-all"));
        todos.getStyleClass().add("btn-secondary");
        todos.setMinWidth(Region.USE_PREF_SIZE);
        todos.setOnAction(e -> ponerATodos());
        aniosTodos.setOnAction(e -> ponerATodos());
        barraTodos.getChildren().setAll(textoTodos, aniosTodos, todos);
        barraTodos.setAlignment(Pos.CENTER_LEFT);
        barraTodos.getStyleClass().add("cnt-aviso");

        lista.getStyleClass().add("cnt-lista");
        lista.setCellFactory(lv -> new CeldaBien(lv));
        lista.setPlaceholder(new Label("A esta área no le falta este dato"));
        VBox.setVgrow(lista, Priority.ALWAYS);
        ayuda.getStyleClass().add("muted-sm");
        ayuda.setWrapText(true);
        // What to do, above the list where the eyes are, not under it.
        ayuda.setGraphic(new FontIcon("mdi2k-keyboard-return"));
        ayuda.setMinHeight(Region.USE_PREF_SIZE);

        panel.getChildren().setAll(tituloArea, chips, barraConac, buscar, barraTodos, ayuda, lista);
        VBox.setVgrow(panel, Priority.ALWAYS);

        HBox paso2 = TransferenciaArrastreDialog.paso(2, "Captura lo que falta");
        paso2.setPadding(Insets.EMPTY);
        VBox p = new VBox(8, paso2, vacio, panel);
        HBox.setHgrow(p, Priority.ALWAYS);
        return p;
    }

    private void mostrarArea() {
        String area = areas.getSelectionModel().getSelectedItem();
        ver(vacio, area == null);
        ver(panel, area != null);
        if (area == null) return;
        tituloArea.setText(area);
        Dato dato = datoElegido();
        // Another área or dato is another list: what was typed or marked belonged to the last one.
        for (Fila f : filas) { f.texto = ""; f.fecha = null; f.guardado = false; f.error = null; }
        // A folio to correct starts with what is written, to fix the character that is off.
        if (dato == Dato.FOLIO_REVISAR)
            for (Fila f : filas) f.texto = Objects.requireNonNullElse(f.producto.getFolioFiscal(), "").trim();
        ayuda.setText(switch (dato) {
            case PRECIO -> "Costo de adquisición por pieza, según factura. Enter guarda y pasa al siguiente.";
            case FECHA -> "Fecha de adquisición. Enter guarda y pasa al siguiente.";
            case VIDA_UTIL -> "Años de vida útil, para calcular la depreciación. Enter guarda y pasa al siguiente.";
            case FOTO -> "Elige una foto del bien; se sube con el mismo tamaño que en su ficha.";
            case PROVEEDOR -> "Quien vendió el bien, como dice la factura. Enter guarda y pasa al siguiente; "
                + "para los demás de su factura usa Más › «Copiar datos de la misma factura».";
            case FOLIO -> "El folio fiscal (UUID) de la factura electrónica. Enter guarda y pasa al siguiente.";
            case FOLIO_REVISAR -> "Folios fiscales escritos con otra forma: corrígelos con la factura a la vista (32 caracteres, "
                + "como A1B2C3D4-0000-4000-8000-000000000001). Enter guarda y pasa al siguiente.";
            case FACTURA -> "El número de la factura; si no hay, escribe SIN DOCUMENTO. Enter guarda y pasa al siguiente.";
            case COLOR -> "El color del bien. Enter guarda y pasa al siguiente.";
            case TIPO -> "MUEBLE para todo lo que no es vehículo; para un vehículo, su tipo (CAMIONETA, MOTOCICLETA…). "
                + "Enter guarda y pasa al siguiente.";
        });
        refiltrar();
        actualizarConteos();
        if (!visibles.isEmpty()) lista.scrollTo(0);
    }

    private List<Fila> delArea() {
        String area = areas.getSelectionModel().getSelectedItem();
        if (area == null) return List.of();
        return TODAS.equals(area) ? filas : filas.stream().filter(f -> area.equals(areaDe(f.producto))).toList();
    }

    /** What was just saved stays on the list, marked, until the área or the dato changes:
     *  rows vanishing under the cursor made it easy to type on the wrong bien. */
    private void refiltrar() {
        Dato dato = datoElegido();
        visibles.setAll(DialogUtil.coincidencias(delArea().stream()
                .filter(f -> f.guardado || CalidadDatosService.falta(f.producto, dato)).toList(),
            f -> Objects.requireNonNullElse(f.producto.getCodigo(), "") + " " + Objects.requireNonNullElse(f.producto.getNombre(), "")
                + " " + Objects.requireNonNullElse(f.producto.getResguardante(), ""),
            buscar.getText()));
        actualizarTodos();
    }

    /** The bienes of the list as it is filtered that still lack the chosen dato. */
    private List<Fila> sinDatoEnLista() {
        Dato dato = datoElegido();
        return visibles.stream().filter(f -> !f.guardado && !f.guardando && CalidadDatosService.falta(f.producto, dato)).toList();
    }

    private void actualizarTodos() {
        Dato dato = datoElegido();
        List<Fila> sin = sinDatoEnLista();
        boolean mostrar = (dato == Dato.VIDA_UTIL || (dato.esTexto() && dato != Dato.FOLIO_REVISAR)) && sin.size() > 1;
        ver(barraTodos, mostrar);
        if (!mostrar) return;
        String filtro = buscar.getText().isBlank() ? "; busca, por ejemplo, «chaleco» para tomar solo esos." : ".";
        if (dato == Dato.VIDA_UTIL) {
            boolean sinConac = sin.stream().anyMatch(f -> VidaUtilConac.anios(f.producto.getClaveArmonizada()) == null);
            textoTodos.setText("La misma vida útil a los " + FormatUtils.plural(sin.size(), "bien", "bienes") + " de la lista" + filtro
                + (sinConac ? " El CONAC no la fija para equipo de defensa y seguridad: la decide el municipio." : ""));
            aniosTodos.setPromptText("Años");
            aniosTodos.setPrefWidth(70);
            aniosTodos.setMinWidth(70);
        } else {
            String que = dato.etiqueta.replaceFirst("^Sin ", "");
            textoTodos.setText(elMismo(dato) + que + " a los " + FormatUtils.plural(sin.size(), "bien", "bienes") + " de la lista" + filtro);
            aniosTodos.setPromptText(que.substring(0, 1).toUpperCase() + que.substring(1));
            aniosTodos.setPrefWidth(220);
            aniosTodos.setMinWidth(160);
        }
    }

    private void ponerATodos() {
        if (datoElegido().esTexto()) { ponerTextoATodos(); return; }
        Integer anios = leerVidaUtil(aniosTodos.getText());
        if (anios == null) {
            NotificacionUtil.error(dialog.getDialogPane().getScene(), "Escribe los años, de 1 a 100");
            aniosTodos.requestFocus();
            return;
        }
        List<Fila> sin = sinDatoEnLista();
        if (sin.isEmpty()) return;
        List<Producto> bienes = sin.stream().map(f -> f.producto).toList();
        Map<String, Long> porNombre = bienes.stream().collect(Collectors.groupingBy(
            p -> Objects.requireNonNullElse(p.getNombre(), "Sin nombre"), Collectors.counting()));
        String muestra = porNombre.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(6)
            .map(e -> "• " + e.getKey() + (e.getValue() > 1 ? " (" + e.getValue() + ")" : ""))
            .collect(Collectors.joining("\n")) + (porNombre.size() > 6 ? "\n…" : "");
        long otraConac = bienes.stream().map(p -> VidaUtilConac.anios(p.getClaveArmonizada()))
            .filter(c -> c != null && !c.equals(anios)).count();
        String area = areas.getSelectionModel().getSelectedItem();
        if (!ConfirmacionUtil.confirmar("Misma vida útil",
                anios + " años a " + FormatUtils.plural(bienes.size(), "bien", "bienes") + " de " + area
                    + (buscar.getText().isBlank() ? "" : " que coinciden con «" + buscar.getText().trim() + "»") + ":\n\n" + muestra
                    + "\n\nCambia la depreciación y el valor en libros de esos bienes."
                    + (otraConac > 0 ? "\n" + (otraConac == 1 ? "1 tiene" : otraConac + " tienen")
                        + " otra según el CONAC: «Revisar y aplicar» les pone esa." : "")
                    + "\n\n¿Aplicar?", "Poner a todos")) return;
        DialogUtil.runAsyncWithProgress(dialog.getDialogPane().getScene(), "Aplicando vida útil…",
            () -> calidad.asignarVidaUtil(anios, bienes),
            n -> {
                for (Fila f : sin) { f.producto.setVidaUtilAnios(anios); f.guardado = true; }
                cambio = true;
                aniosTodos.clear();
                NotificacionUtil.exito(dialog.getDialogPane().getScene(), FormatUtils.plural(n, "bien actualizado", "bienes actualizados"));
                cargarAreas();
                refiltrar();
                actualizarConteos();
                lista.refresh();
            },
            e -> NotificacionUtil.error(dialog.getDialogPane().getScene(), "No se pudo aplicar: " + com.sibim.util.MensajeError.de(e)));
    }

    private void ponerTextoATodos() {
        Dato dato = datoElegido();
        String valor = CalidadDatosService.normalizarTexto(dato, aniosTodos.getText());
        String que = dato.etiqueta.replaceFirst("^Sin ", "");
        if (valor == null) {
            NotificacionUtil.error(dialog.getDialogPane().getScene(), (dato == Dato.FACTURA ? "Escribe la " : "Escribe el ") + que);
            aniosTodos.requestFocus();
            return;
        }
        List<Fila> sin = sinDatoEnLista();
        if (sin.isEmpty()) return;
        aplicarTexto(dato, valor, sin, elMismo(dato) + que, "Poner a todos");
    }

    /** «La misma factura», «El mismo proveedor». */
    private static String elMismo(Dato dato) {
        return dato == Dato.FACTURA ? "La misma " : "El mismo ";
    }

    /** One text dato on several rows, after showing which bienes take it. */
    private void aplicarTexto(Dato dato, String valor, List<Fila> sin, String titulo, String boton) {
        List<Producto> bienes = sin.stream().map(f -> f.producto).toList();
        Map<String, Long> porNombre = bienes.stream().collect(Collectors.groupingBy(
            p -> Objects.requireNonNullElse(p.getNombre(), "Sin nombre"), Collectors.counting()));
        String muestra = porNombre.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(6)
            .map(e -> "• " + e.getKey() + (e.getValue() > 1 ? " (" + e.getValue() + ")" : ""))
            .collect(Collectors.joining("\n")) + (porNombre.size() > 6 ? "\n…" : "");
        String area = areas.getSelectionModel().getSelectedItem();
        String que = dato.etiqueta.replaceFirst("^Sin ", "");
        if (!ConfirmacionUtil.confirmar(titulo,
                "«" + valor + "» como " + que + " a " + FormatUtils.plural(bienes.size(), "bien", "bienes") + " de " + area
                    + (buscar.getText().isBlank() ? "" : " que coinciden con «" + buscar.getText().trim() + "»") + ":\n\n" + muestra
                    + "\n\nSolo a los que no lo tienen. ¿Aplicar?", boton)) return;
        javafx.scene.Scene scene = dialog.getDialogPane().getScene();
        DialogUtil.runAsyncWithProgress(scene, "Guardando…",
            () -> calidad.asignarTextoIds(dato, valor, bienes),
            ids -> {
                Set<String> tomaron = new HashSet<>(ids);
                List<Fila> hechas = sin.stream().filter(f -> tomaron.contains(f.producto.getId())).toList();
                for (Fila f : hechas) { CalidadDatosService.poner(f.producto, dato, valor); f.guardado = true; }
                cambio = true;
                aniosTodos.clear();
                cargarAreas();
                refiltrar();
                actualizarConteos();
                lista.refresh();
                // Many bienes at once: one click puts them back (13-nov-2026, «MUEBLE» to 2,000 at a time).
                NotificacionUtil.exitoConAccion(scene, FormatUtils.plural(ids.size(), "bien actualizado", "bienes actualizados"),
                    "Deshacer", () -> DialogUtil.runAsync(() -> calidad.quitarTexto(dato, valor, ids),
                        n -> {
                            for (Fila f : hechas) { CalidadDatosService.poner(f.producto, dato, null); f.guardado = false; }
                            cargarAreas();
                            refiltrar();
                            actualizarConteos();
                            lista.refresh();
                            NotificacionUtil.info(scene, FormatUtils.plural(n, "bien quedó", "bienes quedaron") + " como estaba");
                        },
                        ex -> NotificacionUtil.error(scene, "No se pudo deshacer: " + com.sibim.util.MensajeError.de(ex))));
            },
            e -> NotificacionUtil.error(scene, "No se pudo aplicar: " + com.sibim.util.MensajeError.de(e)));
    }

    /** The rows of what is chosen without color whose name or description says it, with that color. */
    private Map<Fila, String> conColorEnNombre() {
        Map<Fila, String> out = new LinkedHashMap<>();
        for (Fila f : delArea()) {
            if (f.guardado || f.guardando || !CalidadDatosService.falta(f.producto, Dato.COLOR)) continue;
            String c = com.sibim.util.ColorEnNombre.de(f.producto.getNombre(), f.producto.getDescripcion());
            if (c != null) out.put(f, c);
        }
        return out;
    }

    /** «Tomar el color del nombre»: each one with its color, reviewed before saving; undone from the notice. */
    private void tomarColores() {
        Map<Fila, String> propuestos = conColorEnNombre();
        if (propuestos.isEmpty()) return;
        Map<Producto, String> porBien = new LinkedHashMap<>();
        propuestos.forEach((f, c) -> porBien.put(f.producto, c));
        javafx.scene.Scene scene = dialog.getDialogPane().getScene();
        ColoresDelNombreDialog.show(porBien).ifPresent(elegidos -> DialogUtil.runAsyncWithProgress(scene, "Guardando los colores…",
            () -> calidad.asignarTextos(Dato.COLOR, elegidos),
            hecho -> {
                Set<String> ids = new HashSet<>();
                hecho.forEach((color, deEse) -> ids.addAll(deEse));
                for (Fila f : propuestos.keySet())
                    if (ids.contains(f.producto.getId())) {
                        f.producto.setColor(CalidadDatosService.normalizarTexto(Dato.COLOR, elegidos.get(f.producto)));
                        f.guardado = true;
                    }
                cambio = true;
                cargarAreas();
                refiltrar();
                actualizarConteos();
                lista.refresh();
                NotificacionUtil.exitoConAccion(scene, "Color tomado del nombre en " + FormatUtils.plural(ids.size(), "bien", "bienes"),
                    "Deshacer", () -> DialogUtil.runAsync(() -> calidad.quitarTextos(Dato.COLOR, hecho),
                        n -> {
                            for (Fila f : propuestos.keySet())
                                if (ids.contains(f.producto.getId())) { f.producto.setColor(null); f.guardado = false; }
                            cargarAreas();
                            refiltrar();
                            actualizarConteos();
                            lista.refresh();
                            NotificacionUtil.info(scene, FormatUtils.plural(n, "bien quedó", "bienes quedaron") + " sin color otra vez");
                        },
                        ex -> NotificacionUtil.error(scene, "No se pudo deshacer: " + com.sibim.util.MensajeError.de(ex))));
            },
            ex -> NotificacionUtil.error(scene, "No se guardaron los colores: " + com.sibim.util.MensajeError.de(ex))));
    }

    /** The rows of what is chosen that can take «MUEBLE» (all but vehicles), by their CONAC account. */
    private List<Fila> conTipoSugerido() {
        return delArea().stream().filter(f -> !f.guardado && !f.guardando
            && CalidadDatosService.falta(f.producto, Dato.TIPO) && CalidadDatosService.tipoSugerido(f.producto) != null).toList();
    }

    private void actualizarConteos() {
        List<Fila> base = delArea();
        for (Dato d : Dato.values()) {
            long n = base.stream().filter(f -> CalidadDatosService.falta(f.producto, d)).count();
            ToggleButton t = pestanas.get(d);
            t.setText(d.etiqueta + " (" + String.format("%,d", n) + ")");
            // A chip at 0 has nothing to capture: out of the way, unless it is the one chosen.
            ver(t, n > 0 || t.isSelected());
        }
        for (var g : GRUPOS_DE_DATOS.entrySet())
            ver(filasDeDatos.get(g.getKey()), g.getValue().stream().anyMatch(d -> pestanas.get(d).isVisible()));
        List<Producto> sinVida = base.stream().map(f -> f.producto).toList();
        revisarConac.setText("Revisar y aplicar");
        revisarConac.setGraphic(new FontIcon("mdi2c-calendar-check-outline"));
        if (datoElegido() == Dato.FOLIO_REVISAR) {
            // The factura's own number typed in «Folio fiscal» (12 bienes on 12-nov-2026).
            int n = numerosEnFolio().size();
            ver(barraConac, n > 0);
            if (n > 0) {
                textoConac.setText(FormatUtils.plural(n, "bien tiene", "bienes tienen") + " en «Folio fiscal» un número corto y «N° Factura» "
                    + "vacía: es su número de factura.");
                revisarConac.setText("Pasarlos a N° Factura");
                revisarConac.setGraphic(new FontIcon("mdi2f-file-swap-outline"));
            }
            actualizarTodos();
            return;
        }
        if (datoElegido() == Dato.TIPO) {
            int n = conTipoSugerido().size();
            ver(barraConac, n > 0);
            if (n > 0) textoConac.setText(FormatUtils.plural(n, "bien de lo elegido no es", "bienes de lo elegido no son")
                + " vehículo según su clave CONAC: llevan «MUEBLE», como los demás.");
            actualizarTodos();
            return;
        }
        if (datoElegido() == Dato.COLOR) {
            // 13-nov-2026: 820 bienes without color had it in their name («… COLOR NEGRO»).
            int n = conColorEnNombre().size();
            ver(barraConac, n > 0);
            if (n > 0) {
                textoConac.setText(FormatUtils.plural(n, "bien de lo elegido trae", "bienes de lo elegido traen")
                    + " el color escrito en su nombre o descripción.");
                revisarConac.setText("Tomar el color del nombre");
                revisarConac.setGraphic(new FontIcon("mdi2p-palette-outline"));
            }
            actualizarTodos();
            return;
        }
        Map<Integer, List<Producto>> propuesta = CalidadDatosService.propuestaVidaUtil(sinVida);
        int n = propuesta.values().stream().mapToInt(List::size).sum();
        boolean mostrar = datoElegido() == Dato.VIDA_UTIL && n > 0;
        ver(barraConac, mostrar);
        if (mostrar) textoConac.setText("Según su clave CONAC, se puede proponer la vida útil de "
            + FormatUtils.plural(n, "bien", "bienes") + " de lo elegido.");
        actualizarTodos();
    }

    /** The rows of what is chosen whose «Folio fiscal» holds the factura's number. */
    private List<Fila> numerosEnFolio() {
        return delArea().stream().filter(f -> !f.guardado && !f.guardando && CalidadDatosService.esNumeroDeFactura(f.producto)).toList();
    }

    /** «Pasarlos a N° Factura», after showing which; undone from the notice. */
    private void pasarNumerosAFactura() {
        List<Fila> filas = numerosEnFolio();
        if (filas.isEmpty()) return;
        List<Producto> bienes = filas.stream().map(f -> f.producto).toList();
        String muestra = bienes.stream().limit(8)
            .map(p -> "• " + p.getCodigo() + ": «" + p.getFolioFiscal().trim() + "» → N° Factura «"
                + CalidadDatosService.numeroDeFactura(p.getFolioFiscal()) + "»")
            .collect(Collectors.joining("\n")) + (bienes.size() > 8 ? "\n…" : "");
        if (!ConfirmacionUtil.confirmar("Es su número de factura",
                FormatUtils.plural(bienes.size(), "bien tiene", "bienes tienen") + " en «Folio fiscal» un número corto y «N° Factura» vacía:\n\n"
                    + muestra + "\n\nPasan a «N° Factura» y «Folio fiscal» queda vacío para el UUID, si lo tienes. ¿Pasarlos?", "Pasarlos")) return;
        javafx.scene.Scene scene = dialog.getDialogPane().getScene();
        DialogUtil.runAsyncWithProgress(scene, "Pasando los números…",
            () -> calidad.pasarNumeroAFactura(bienes),
            hecho -> {
                for (var c : hecho)
                    for (Fila f : filas)
                        if (f.producto.getId().equals(c.id())) {
                            f.producto.setNumeroFactura(c.numero());
                            f.producto.setFolioFiscal(null);
                            f.guardado = true;
                        }
                cambio = true;
                cargarAreas();
                refiltrar();
                actualizarConteos();
                lista.refresh();
                NotificacionUtil.exitoConAccion(scene, FormatUtils.plural(hecho.size(), "número pasó", "números pasaron") + " a «N° Factura»",
                    "Deshacer", () -> DialogUtil.runAsync(() -> calidad.regresarNumeroAFolio(hecho),
                        m -> {
                            for (var c : hecho)
                                for (Fila f : filas)
                                    if (f.producto.getId().equals(c.id())) {
                                        f.producto.setFolioFiscal(c.folio());
                                        f.producto.setNumeroFactura(null);
                                        f.guardado = false;
                                    }
                            cargarAreas();
                            refiltrar();
                            actualizarConteos();
                            lista.refresh();
                            NotificacionUtil.info(scene, FormatUtils.plural(m, "número regresó", "números regresaron") + " a «Folio fiscal»");
                        },
                        ex -> NotificacionUtil.error(scene, "No se pudo deshacer: " + com.sibim.util.MensajeError.de(ex))));
            },
            ex -> NotificacionUtil.error(scene, "No se pasaron: " + com.sibim.util.MensajeError.de(ex)));
    }

    private void aplicarConac() {
        if (datoElegido() == Dato.FOLIO_REVISAR) { pasarNumerosAFactura(); return; }
        if (datoElegido() == Dato.COLOR) { tomarColores(); return; }
        if (datoElegido() == Dato.TIPO) {
            List<Fila> sugeridos = conTipoSugerido();
            if (!sugeridos.isEmpty()) aplicarTexto(Dato.TIPO, "MUEBLE", sugeridos, "Tipo de bien según CONAC", "Aplicar");
            return;
        }
        List<Producto> base = delArea().stream().map(f -> f.producto).toList();
        Map<Integer, List<Producto>> propuesta = CalidadDatosService.propuestaVidaUtil(base);
        if (propuesta.isEmpty()) return;
        String detalle = propuesta.entrySet().stream()
            .map(e -> e.getKey() + " años: " + FormatUtils.plural(e.getValue().size(), "bien", "bienes"))
            .collect(Collectors.joining("\n"));
        int total = propuesta.values().stream().mapToInt(List::size).sum();
        long quedan = base.stream().filter(p -> p.getVidaUtilAnios() == null).count() - total;
        if (!ConfirmacionUtil.confirmar("Vida útil según CONAC",
                "Se asigna a los bienes que no tienen:\n\n" + detalle
                    + "\n\nCambia la depreciación y el valor en libros de esos bienes."
                    + (quedan > 0 ? "\nQuedan " + FormatUtils.plural(quedan, "bien", "bienes") + " sin valor en CONAC (por ejemplo, equipo de defensa): los decide el municipio; varios iguales se capturan juntos con «Poner a todos»." : "")
                    + "\n\n¿Aplicar?", "Asignar")) return;
        DialogUtil.runAsyncWithProgress(dialog.getDialogPane().getScene(), "Aplicando vida útil…",
            () -> calidad.aplicarVidaUtil(propuesta),
            n -> {
                propuesta.forEach((anios, bienes) -> bienes.forEach(p -> p.setVidaUtilAnios(anios)));
                cambio = true;
                NotificacionUtil.exito(dialog.getDialogPane().getScene(), FormatUtils.plural(n, "bien actualizado", "bienes actualizados"));
                cargarAreas();
                refiltrar();
                actualizarConteos();
            },
            e -> NotificacionUtil.error(dialog.getDialogPane().getScene(), "No se pudo aplicar: " + com.sibim.util.MensajeError.de(e)));
    }

    // ── Guardar una fila ──────────────────────────────────────────────────────

    /** What is typed for a price: "$1,250.50" as well as "1250.5". @return null if it is no amount */
    static BigDecimal leerPrecio(String texto) {
        if (texto == null) return null;
        String limpio = texto.replace("$", "").replace(",", "").replace(" ", "");
        if (limpio.isEmpty()) return null;
        try {
            BigDecimal v = new BigDecimal(limpio);
            return v.signum() > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** @return the years, or null unless it is a whole number from 1 to 100 */
    static Integer leerVidaUtil(String texto) {
        if (texto == null) return null;
        try {
            int v = Integer.parseInt(texto.trim());
            return v >= 1 && v <= 100 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void guardarTexto(Fila f, Dato dato) {
        if (f.guardado || f.guardando || f.texto.isBlank()) return;
        if (dato == Dato.PRECIO) {
            BigDecimal precio = leerPrecio(f.texto);
            if (precio == null) { fallar(f, "Escribe un costo mayor a cero"); return; }
            if (!ProductoDialogFactory.precioConfirmado(precio, f.producto)) return;
            guardar(f, p -> p.setPrecioCompra(precio));
        } else if (dato == Dato.VIDA_UTIL) {
            Integer anios = leerVidaUtil(f.texto);
            if (anios == null) { fallar(f, "Escribe los años, de 1 a 100"); return; }
            guardar(f, p -> p.setVidaUtilAnios(anios));
        } else if (dato.esTexto()) {
            String valor = CalidadDatosService.normalizarTexto(dato, f.texto);
            if (valor == null) return;
            if (valor.length() > (dato == Dato.FOLIO ? 60 : 200)) { fallar(f, "Es demasiado largo"); return; }
            // A folio fiscal with another shape is not saved: it would not match its factura.
            String problema = dato == Dato.FOLIO || dato == Dato.FOLIO_REVISAR ? CalidadDatosService.problemaFolio(valor) : null;
            if (problema != null) { fallar(f, "El folio fiscal " + problema); return; }
            guardar(f, p -> CalidadDatosService.poner(p, dato, valor));
        }
    }

    private void guardarFecha(Fila f) {
        if (f.guardado || f.guardando || f.fecha == null) return;
        if (f.fecha.isAfter(LocalDate.now())) { fallar(f, "La fecha de adquisición no puede ser futura"); return; }
        LocalDate fecha = f.fecha;
        guardar(f, p -> p.setFechaAdquisicion(fecha));
    }

    private void fallar(Fila f, String motivo) {
        f.error = motivo;
        lista.refresh();
    }

    private void guardar(Fila f, java.util.function.Consumer<Producto> cambiar) {
        Producto p = f.producto;
        Producto antes = p.copia();
        BigDecimal precioAntes = p.getPrecioCompra();
        LocalDate fechaAntes = p.getFechaAdquisicion();
        Integer vidaAntes = p.getVidaUtilAnios();
        cambiar.accept(p);
        f.guardando = true;
        f.error = null;
        lista.refresh();
        DialogUtil.runAsync(() -> service.save(p),
            guardado -> {
                f.producto = guardado;
                f.guardando = false;
                f.guardado = true;
                cambio = true;
                cargarAreas();
                actualizarConteos();
                lista.refresh();
            },
            e -> {
                // What failed isn't what the bien has: the list and the counts go on saying so.
                p.setPrecioCompra(precioAntes);
                p.setFechaAdquisicion(fechaAntes);
                p.setVidaUtilAnios(vidaAntes);
                for (Dato d : Dato.values()) if (d.esTexto()) CalidadDatosService.poner(p, d, switch (d) {
                    case PROVEEDOR -> antes.getProveedor();
                    case FOLIO, FOLIO_REVISAR -> antes.getFolioFiscal();
                    case FACTURA -> antes.getNumeroFactura();
                    case COLOR -> antes.getColor();
                    default -> antes.getTipoBien();
                });
                f.guardando = false;
                f.error = e.getMessage() != null ? e.getMessage() : "No se pudo guardar";
                log.warn("No se pudo completar el dato de {}: {}", p.getCodigo(), e.getMessage());
                lista.refresh();
            });
    }

    private void siguiente(Fila f) {
        int i = visibles.indexOf(f);
        if (i < 0 || i + 1 >= visibles.size()) return;
        pedirFoco = visibles.get(i + 1);
        lista.scrollTo(Math.max(0, i - 2));
        lista.refresh();
    }

    private void agregarFoto(Fila f) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Foto de " + f.producto.getCodigo());
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imágenes (*.jpg, *.png)", "*.jpg", "*.jpeg", "*.png"));
        File archivo = fc.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
        if (archivo == null) return;
        Producto p = f.producto;
        f.guardando = true;
        f.error = null;
        lista.refresh();
        DialogUtil.runAsync(() -> {
                p.setFotosUrls(new ArrayList<>(List.of(archivo.getAbsolutePath())));
                ProductoDialogFactory.procesarArchivos(p, null, new HashMap<>(), log);
                Producto guardado = service.save(p);
                service.saveFotos(guardado.getId(), p.getFotosUrls());
                com.sibim.service.FichaPublica.actualizarLuego(guardado.getId());
                return guardado;
            },
            guardado -> {
                f.producto = guardado;
                f.guardando = false;
                f.guardado = true;
                cambio = true;
                cargarAreas();
                actualizarConteos();
                lista.refresh();
            },
            e -> {
                f.guardando = false;
                f.error = e.getMessage() != null ? e.getMessage() : "No se pudo subir la foto";
                lista.refresh();
            });
    }

    private static void ver(Node n, boolean visible) {
        n.setVisible(visible);
        n.setManaged(visible);
    }

    // ── Celdas ────────────────────────────────────────────────────────────────

    private final class CeldaArea extends ListCell<String> {
        private final FontIcon icono = new FontIcon();
        private final StackPane fondo = new StackPane(icono);
        private final Label nombre = new Label();
        private final Label cuenta = new Label();
        private final HBox caja;

        CeldaArea(ListView<String> lv) {
            icono.getStyleClass().add("trf-tarjeta-icono");
            fondo.getStyleClass().addAll("trf-tarjeta-icono-fondo", "trf-area-icono");
            fondo.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            nombre.getStyleClass().add("org-child-label");
            nombre.setTextOverrun(OverrunStyle.ELLIPSIS);
            nombre.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(nombre, Priority.ALWAYS);
            cuenta.getStyleClass().add("org-area-count");
            cuenta.setMinWidth(Region.USE_PREF_SIZE);
            caja = new HBox(10, fondo, nombre, cuenta);
            caja.setAlignment(Pos.CENTER_LEFT);
            caja.maxWidthProperty().bind(lv.widthProperty().subtract(30));
            caja.setMinWidth(0);
        }

        @Override protected void updateItem(String area, boolean empty) {
            super.updateItem(area, empty);
            setText(null);
            if (empty || area == null) { setGraphic(null); return; }
            icono.setIconLiteral(TODAS.equals(area) ? "mdi2p-package-variant" : FiltroArea.icono(area).getIconLiteral());
            nombre.setText(area);
            nombre.setTooltip(new Tooltip(area));
            cuenta.setText(String.valueOf(porArea.getOrDefault(area, 0)));
            setGraphic(caja);
        }
    }

    private final class CeldaBien extends ListCell<Fila> {
        private final Label codigo = new Label();
        private final Label nombre = new Label();
        private final Label detalle = new Label();
        private final TextField campo = new TextField();
        private final DatePicker fecha = new DatePicker();
        private final Button foto = new Button("Elegir foto");
        private final Label estado = new Label();
        private final HBox fila;
        private boolean llenando = false;

        CeldaBien(ListView<Fila> lv) {
            codigo.getStyleClass().add("trf-codigo");
            codigo.setMinWidth(Region.USE_PREF_SIZE);
            nombre.getStyleClass().add("org-child-label");
            nombre.setTextOverrun(OverrunStyle.ELLIPSIS);
            nombre.setMinWidth(0);
            HBox linea = new HBox(8, codigo, nombre);
            linea.setAlignment(Pos.CENTER_LEFT);
            detalle.getStyleClass().add("org-table-sub");
            detalle.setTextOverrun(OverrunStyle.ELLIPSIS);
            detalle.setMinWidth(0);
            VBox textos = new VBox(3, linea, detalle);
            textos.setMinWidth(0);
            textos.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(textos, Priority.ALWAYS);

            campo.getStyleClass().add("form-input");
            campo.setPrefWidth(150);
            campo.setMinWidth(150);
            fecha.setConverter(FormatUtils.datePickerConverter());
            fecha.setPromptText("dd/mm/aaaa");
            fecha.setPrefWidth(150);
            fecha.setMinWidth(150);
            foto.setGraphic(new FontIcon("mdi2c-camera-outline"));
            foto.getStyleClass().add("btn-secondary");
            foto.setMinWidth(Region.USE_PREF_SIZE);
            estado.setMinWidth(Region.USE_PREF_SIZE);

            fila = new HBox(10, textos, campo, fecha, foto, estado);
            fila.setAlignment(Pos.CENTER_LEFT);
            fila.getStyleClass().add("cnt-fila");
            fila.setStyle("-fx-cursor: default;");
            fila.maxWidthProperty().bind(lv.widthProperty().subtract(30));
            fila.setMinWidth(0);

            campo.textProperty().addListener((o, a, v) -> { if (!llenando && getItem() != null) getItem().texto = v == null ? "" : v; });
            campo.setOnKeyPressed(e -> {
                Fila f = getItem();
                if (f == null || e.getCode() != KeyCode.ENTER) return;
                guardarTexto(f, datoElegido());
                siguiente(f);
                e.consume();
            });
            campo.focusedProperty().addListener((o, a, si) -> {
                Fila f = getItem();
                if (!si && f != null) guardarTexto(f, datoElegido());
            });
            fecha.setOnAction(e -> {
                Fila f = getItem();
                if (llenando || f == null) return;
                f.fecha = fecha.getValue();
                guardarFecha(f);
                siguiente(f);
            });
            foto.setOnAction(e -> { if (getItem() != null) agregarFoto(getItem()); });
        }

        @Override protected void updateItem(Fila f, boolean empty) {
            super.updateItem(f, empty);
            setText(null);
            if (empty || f == null) { setGraphic(null); return; }
            llenando = true;
            Producto p = f.producto;
            Dato dato = datoElegido();
            codigo.setText(p.getCodigo());
            nombre.setText(p.getNombre());
            nombre.setTooltip(new Tooltip(p.getNombre()));
            List<String> partes = new ArrayList<>();
            partes.add(p.getResguardante() != null && !p.getResguardante().isBlank() ? p.getResguardante() : "Sin resguardante");
            if (p.getStockActual() > 1) partes.add(p.getStockActual() + " piezas");
            if (p.getNumeroSerie() != null && !p.getNumeroSerie().isBlank()) partes.add("Serie " + p.getNumeroSerie());
            // What the factura says is what these are copied from.
            if (dato == Dato.FOLIO_REVISAR) {
                String problema = CalidadDatosService.problemaFolio(p.getFolioFiscal());
                if (problema != null) partes.add("Revisa: " + problema);
            }
            if (dato.esTexto() && dato != Dato.FACTURA && p.getNumeroFactura() != null && !p.getNumeroFactura().isBlank())
                partes.add("Factura " + p.getNumeroFactura().trim());
            if (TODAS.equals(tituloArea.getText())) partes.add(areaDe(p));
            detalle.setText(String.join(" · ", partes));
            detalle.setTooltip(new Tooltip(detalle.getText()));

            ver(campo, dato == Dato.PRECIO || dato == Dato.VIDA_UTIL || dato.esTexto());
            ver(fecha, dato == Dato.FECHA);
            ver(foto, dato == Dato.FOTO);
            campo.setText(f.texto);
            campo.setAccessibleText(dato.etiqueta.replaceFirst("^Sin ", "") + " de " + p.getCodigo());
            fecha.setValue(f.fecha);
            double ancho = dato == Dato.FOLIO || dato == Dato.FOLIO_REVISAR || dato == Dato.PROVEEDOR ? 300 : dato.esTexto() ? 200 : 150;
            campo.setPrefWidth(ancho);
            campo.setMinWidth(ancho);
            if (dato == Dato.PRECIO) campo.setPromptText(p.getStockActual() > 1 ? "Costo por pieza" : "Costo");
            if (dato == Dato.VIDA_UTIL) {
                Integer conac = VidaUtilConac.anios(p.getClaveArmonizada());
                campo.setPromptText(conac != null ? "CONAC: " + conac + " años" : "Años");
            }
            switch (dato) {
                case PROVEEDOR -> campo.setPromptText("Proveedor");
                case FOLIO, FOLIO_REVISAR -> campo.setPromptText("Folio fiscal (UUID)");
                case FACTURA -> campo.setPromptText("No. de factura");
                case COLOR -> campo.setPromptText("Color");
                case TIPO -> {
                    String sugerido = CalidadDatosService.tipoSugerido(p);
                    campo.setPromptText(sugerido != null ? "Ej. " + sugerido : "Ej. CAMIONETA");
                }
                default -> { }
            }
            boolean bloqueado = f.guardado || f.guardando;
            campo.setDisable(bloqueado);
            fecha.setDisable(bloqueado);
            foto.setDisable(bloqueado);

            estado.getStyleClass().setAll("cell-badge", f.error != null ? "cell-badge-danger"
                : f.guardado ? "cell-badge-success" : "cell-badge-blue");
            estado.setText(f.error != null ? "No se guardó" : f.guardado ? "Guardado" : f.guardando ? "Guardando…" : "");
            estado.setTooltip(f.error != null ? new Tooltip(f.error) : null);
            ver(estado, f.error != null || f.guardado || f.guardando);
            fila.getStyleClass().removeAll("cnt-fila-vista", "cnt-fila-incidencia");
            if (f.error != null) fila.getStyleClass().add("cnt-fila-incidencia");
            else if (f.guardado) fila.getStyleClass().add("cnt-fila-vista");
            llenando = false;
            setGraphic(fila);
            if (f == pedirFoco) {
                pedirFoco = null;
                Platform.runLater(() -> (dato == Dato.FECHA ? fecha : campo).requestFocus());
            }
        }
    }
}
