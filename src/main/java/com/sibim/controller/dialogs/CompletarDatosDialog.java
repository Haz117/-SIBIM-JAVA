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
    private final TextField buscar = new TextField();
    private final ObservableList<Fila> visibles = FXCollections.observableArrayList();
    private final ListView<Fila> lista = new ListView<>(visibles);
    private final Label tituloArea = new Label();
    private final Label ayuda = new Label();
    private final HBox barraConac = new HBox(10);
    private final Label textoConac = new Label();
    private final VBox vacio = new VBox(8);
    private final VBox panel = new VBox(8);
    private Fila pedirFoco;

    private CompletarDatosDialog(List<Producto> bienes, Runnable alCambiar) {
        bienes.stream().filter(p -> p.getFechaBaja() == null)
            .sorted(Comparator.comparing(p -> Objects.requireNonNullElse(p.getCodigo(), ""), com.sibim.service.ReporteService.POR_CODIGO))
            .forEach(p -> filas.add(new Fila(p)));
        this.alCambiar = alCambiar;
    }

    /** @param alCambiar run on closing when something was saved */
    public static void show(List<Producto> bienes, Runnable alCambiar) {
        new CompletarDatosDialog(bienes, alCambiar).abrir();
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
        pestanas.get(Dato.PRECIO).setSelected(true);
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
            if (elegida != null) areas.getSelectionModel().select(elegida);
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
        HBox chips = new HBox(6);
        chips.setAlignment(Pos.CENTER_LEFT);
        for (Dato d : Dato.values()) {
            ToggleButton t = new ToggleButton(d.etiqueta);
            t.setToggleGroup(grupo);
            t.getStyleClass().add("filter-chip");
            t.setMinWidth(Region.USE_PREF_SIZE);
            pestanas.put(d, t);
            chips.getChildren().add(t);
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
        Button revisar = new Button("Revisar y aplicar");
        revisar.setGraphic(new FontIcon("mdi2c-calendar-check-outline"));
        revisar.getStyleClass().add("btn-secondary");
        revisar.setMinWidth(Region.USE_PREF_SIZE);
        revisar.setOnAction(e -> aplicarConac());
        barraConac.getChildren().setAll(textoConac, revisar);
        barraConac.setAlignment(Pos.CENTER_LEFT);
        barraConac.getStyleClass().add("cnt-aviso");

        lista.getStyleClass().add("cnt-lista");
        lista.setCellFactory(lv -> new CeldaBien(lv));
        lista.setPlaceholder(new Label("A esta área no le falta este dato"));
        VBox.setVgrow(lista, Priority.ALWAYS);
        ayuda.getStyleClass().add("muted-sm");
        ayuda.setWrapText(true);

        panel.getChildren().setAll(tituloArea, chips, barraConac, buscar, lista, ayuda);
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
        ayuda.setText(switch (dato) {
            case PRECIO -> "Costo de adquisición por pieza, según factura. Enter guarda y pasa al siguiente.";
            case FECHA -> "Fecha de adquisición. Enter guarda y pasa al siguiente.";
            case VIDA_UTIL -> "Años de vida útil, para calcular la depreciación. Enter guarda y pasa al siguiente.";
            case FOTO -> "Elige una foto del bien; se sube con el mismo tamaño que en su ficha.";
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
    }

    private void actualizarConteos() {
        List<Fila> base = delArea();
        for (Dato d : Dato.values()) {
            long n = base.stream().filter(f -> CalidadDatosService.falta(f.producto, d)).count();
            pestanas.get(d).setText(d.etiqueta + " (" + n + ")");
        }
        List<Producto> sinVida = base.stream().map(f -> f.producto).toList();
        Map<Integer, List<Producto>> propuesta = CalidadDatosService.propuestaVidaUtil(sinVida);
        int n = propuesta.values().stream().mapToInt(List::size).sum();
        boolean mostrar = datoElegido() == Dato.VIDA_UTIL && n > 0;
        ver(barraConac, mostrar);
        if (mostrar) textoConac.setText("Según su clave CONAC, se puede proponer la vida útil de "
            + FormatUtils.plural(n, "bien", "bienes") + " de lo elegido.");
    }

    private void aplicarConac() {
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
                    + (quedan > 0 ? "\nQuedan " + FormatUtils.plural(quedan, "bien", "bienes") + " sin valor en CONAC (por ejemplo, equipo de defensa): esos se capturan a mano." : "")
                    + "\n\n¿Aplicar?")) return;
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
            e -> NotificacionUtil.error(dialog.getDialogPane().getScene(), "No se pudo aplicar: " + e.getMessage()));
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
            if (TODAS.equals(tituloArea.getText())) partes.add(areaDe(p));
            detalle.setText(String.join(" · ", partes));
            detalle.setTooltip(new Tooltip(detalle.getText()));

            ver(campo, dato == Dato.PRECIO || dato == Dato.VIDA_UTIL);
            ver(fecha, dato == Dato.FECHA);
            ver(foto, dato == Dato.FOTO);
            campo.setText(f.texto);
            fecha.setValue(f.fecha);
            if (dato == Dato.PRECIO) campo.setPromptText(p.getStockActual() > 1 ? "Costo por pieza" : "Costo");
            if (dato == Dato.VIDA_UTIL) {
                Integer conac = VidaUtilConac.anios(p.getClaveArmonizada());
                campo.setPromptText(conac != null ? "CONAC: " + conac + " años" : "Años");
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
