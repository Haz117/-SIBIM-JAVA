package com.sibim.controller.dialogs;

import com.sibim.config.Areas;
import com.sibim.model.Producto;
import com.sibim.service.CalidadDatosService;
import com.sibim.service.CompletarPorFactura;
import com.sibim.service.CompletarPorFactura.Campo;
import com.sibim.service.CompletarPorFactura.Factura;
import com.sibim.service.EditarVarios;
import com.sibim.service.ProductoService;
import com.sibim.util.AppColors;
import com.sibim.util.CarpetaRecordada;
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
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Bienes › Más › «Completar por factura» (also from Completar datos and Inicio › Por hacer): each
 * purchase is one row, with its proveedor, fecha and folio fiscal typed once for all its bienes,
 * or read from its PDF or XML. See {@link CompletarPorFactura}.
 */
public final class CompletarPorFacturaDialog {

    private static final Logger log = LoggerFactory.getLogger(CompletarPorFacturaDialog.class);
    static final String TODAS = "Todas las áreas";
    private static final Map<String, Set<Campo>> FALTA = new LinkedHashMap<>();
    static {
        FALTA.put("Les falta proveedor, fecha o folio fiscal", EnumSet.allOf(Campo.class));
        FALTA.put("Les falta el proveedor", EnumSet.of(Campo.PROVEEDOR));
        FALTA.put("Les falta la fecha", EnumSet.of(Campo.FECHA));
        FALTA.put("Les falta el folio fiscal", EnumSet.of(Campo.FOLIO));
    }

    /** One row: its purchase, what is typed, and how its saving went. */
    static final class Fila {
        Factura factura;
        String proveedor = "", folio = "";
        LocalDate fecha;
        /** What its PDF or XML said: the total, or that it is another factura's. */
        String deSuArchivo;
        boolean completo, guardando;
        int guardados;
        String error;

        Fila(Factura f) {
            factura = f;
            var p = f.propuestas();
            if (p.containsKey(Campo.PROVEEDOR)) proveedor = String.valueOf(p.get(Campo.PROVEEDOR).valor());
            if (p.containsKey(Campo.FOLIO)) folio = String.valueOf(p.get(Campo.FOLIO).valor());
            if (p.containsKey(Campo.FECHA)) fecha = (LocalDate) p.get(Campo.FECHA).valor();
        }

        String texto(Campo c) {
            return switch (c) {
                case PROVEEDOR -> proveedor;
                case FOLIO -> folio;
                case FECHA -> fecha == null ? "" : fecha.toString();
            };
        }
    }

    private final ProductoService service = new ProductoService();
    private final Map<String, Producto> porId = new LinkedHashMap<>();
    private final Map<String, Producto> guardados = new LinkedHashMap<>();
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

    private CompletarPorFacturaDialog(List<Producto> bienes, Consumer<Collection<Producto>> alCerrar) {
        for (Producto p : bienes) if (p.getFechaBaja() == null) porId.put(p.getId(), p);
        this.alCerrar = alCerrar;
    }

    /** @param areaInicial the área to open on, or null for all
     *  @param alCerrar    on closing, with the bienes saved here (none when nothing was saved) */
    public static void show(List<Producto> bienes, String areaInicial, Consumer<Collection<Producto>> alCerrar) {
        new CompletarPorFacturaDialog(bienes, alCerrar).abrir(areaInicial);
    }

    private void abrir(String areaInicial) {
        dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Completar por factura");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(1280, pantalla.getWidth() * 0.95), Math.min(760, pantalla.getHeight() * 0.88));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        HBox header = DialogUtil.gradientHeader("mdi2r-receipt", "Completar por factura",
            "Los bienes de una factura llevan su proveedor, su fecha y su folio fiscal: escríbelos una vez, o léelos de su PDF o XML",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        List<String> areas = new ArrayList<>(List.of(TODAS));
        Set<String> conFacturas = new HashSet<>();
        for (Factura f : CompletarPorFactura.facturas(new ArrayList<>(porId.values()))) conFacturas.addAll(f.areas());
        Areas.getAllAreaNames().stream().filter(conFacturas::contains).forEach(areas::add);
        conFacturas.stream().filter(a -> !areas.contains(a)).sorted().forEach(areas::add);
        area.getItems().setAll(areas);
        area.setValue(areaInicial != null && areas.contains(areaInicial) ? areaInicial : TODAS);
        area.setVisibleRowCount(16);
        area.setAccessibleText("Área");
        area.valueProperty().addListener((o, a, b) -> cargar());
        falta.getItems().setAll(FALTA.keySet());
        falta.setValue(falta.getItems().get(0));
        falta.setAccessibleText("Qué les falta");
        falta.valueProperty().addListener((o, a, b) -> cargar());
        buscar.setPromptText("Factura, folio o nombre de un bien…");
        buscar.getStyleClass().add("form-input");
        buscar.textProperty().addListener((o, a, b) -> refiltrar());
        HBox.setHgrow(buscar, Priority.ALWAYS);
        resumen.getStyleClass().add("muted-sm");
        resumen.setMinWidth(Region.USE_PREF_SIZE);
        HBox filtros = new HBox(10, area, falta, buscar, resumen);
        filtros.setAlignment(Pos.CENTER_LEFT);

        Label ayuda = new Label("Lo que escribes va solo a los bienes de la factura que no lo tienen; lo que ya viene escrito lo tienen "
            + "otros bienes de la misma factura. Enter guarda la factura y pasa a la siguiente; cada una se deshace desde su aviso.");
        ayuda.getStyleClass().add("muted-sm");
        ayuda.setWrapText(true);
        ayuda.setMinHeight(Region.USE_PREF_SIZE);
        ayuda.setGraphic(new FontIcon("mdi2k-keyboard-return"));

        lista.getStyleClass().add("cnt-lista");
        lista.setCellFactory(lv -> new CeldaFactura(lv));
        lista.setPlaceholder(new Label("No hay facturas a las que les falte eso"));
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

    private void cargar() {
        filas.clear();
        for (Factura f : CompletarPorFactura.facturas(new ArrayList<>(porId.values()), cuales(), campos())) filas.add(new Fila(f));
        refiltrar();
        if (!visibles.isEmpty()) lista.scrollTo(0);
    }

    private void refiltrar() {
        visibles.setAll(DialogUtil.coincidencias(filas,
            f -> f.factura.etiqueta() + " " + Objects.requireNonNullElse(f.factura.uuid(), "") + " " + f.factura.nombres(),
            buscar.getText()));
        long n = filas.stream().filter(f -> !f.completo).count();
        int b = filas.stream().filter(f -> !f.completo).mapToInt(f -> f.factura.incompletos()).sum();
        resumen.setText(FormatUtils.plural(n, "factura", "facturas") + " · " + FormatUtils.plural(b, "bien", "bienes") + " por completar");
    }

    private Scene escena() {
        return dialog.getDialogPane().getScene();
    }

    // ── Leer su PDF o XML ─────────────────────────────────────────────────────

    private void leerArchivo(Fila f) {
        FileChooser fc = new FileChooser();
        fc.setTitle("La factura " + f.factura.etiqueta() + " (PDF o XML)");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Factura electrónica (PDF o XML)", "*.pdf", "*.PDF", "*.xml", "*.XML"));
        File carpeta = CarpetaRecordada.FACTURAS.ultima();
        if (carpeta != null) fc.setInitialDirectory(carpeta);
        File archivo = fc.showOpenDialog(escena().getWindow());
        if (archivo == null) return;
        CarpetaRecordada.FACTURAS.recordar(archivo);
        f.deSuArchivo = "Leyendo " + archivo.getName() + "…";
        lista.refresh();
        DialogUtil.runAsync(() -> {
                if (archivo.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                    String[] receptor = com.sibim.service.FacturasDeCarpeta.receptor();
                    return com.sibim.util.FacturaPdf.leer(archivo.toPath(),
                        receptor[0].isBlank() ? List.of() : List.of(receptor[0]), List.of(receptor[1]));
                }
                return com.sibim.util.FacturaXml.leer(archivo.toPath());
            },
            leida -> {
                CompletarPorFactura.DeSuFactura d = CompletarPorFactura.deSuFactura(f.factura, leida);
                if (d.otraFactura() != null) {
                    f.deSuArchivo = d.otraFactura() + ": no se usó";
                } else {
                    // Only into what the row asks for: what the bienes have stays.
                    if (f.factura.faltan().containsKey(Campo.PROVEEDOR) && d.valores().containsKey(Campo.PROVEEDOR))
                        f.proveedor = (String) d.valores().get(Campo.PROVEEDOR);
                    if (f.factura.faltan().containsKey(Campo.FECHA) && d.valores().containsKey(Campo.FECHA))
                        f.fecha = (LocalDate) d.valores().get(Campo.FECHA);
                    if (f.factura.faltan().containsKey(Campo.FOLIO) && d.valores().containsKey(Campo.FOLIO))
                        f.folio = (String) d.valores().get(Campo.FOLIO);
                    f.deSuArchivo = "Leído de " + archivo.getName() + (d.total() != null ? " · " + d.total() : "")
                        + (d.valores().containsKey(Campo.PROVEEDOR) ? "" : " · el proveedor no se leyó: escríbelo");
                }
                pedirFoco = f;
                lista.refresh();
            },
            ex -> {
                f.deSuArchivo = ex instanceof com.sibim.util.FacturaXml.NoEsFactura ? ex.getMessage()
                    : "No se pudo leer: " + com.sibim.util.MensajeError.de(ex);
                lista.refresh();
            });
    }

    // ── Guardar una factura ───────────────────────────────────────────────────

    void guardar(Fila f) {
        if (f.guardando || f.completo) return;
        Map<Campo, Object> valores = new EnumMap<>(Campo.class);
        try {
            for (Campo c : f.factura.faltan().keySet()) {
                Object v = c == Campo.FECHA ? CompletarPorFactura.leerFecha(f.fecha) : CompletarPorFactura.leer(c, f.texto(c));
                if (v != null) valores.put(c, v);
            }
        } catch (IllegalArgumentException e) {
            f.error = e.getMessage();
            lista.refresh();
            return;
        }
        List<EditarVarios.Renglon> renglones = CompletarPorFactura.renglones(f.factura, valores);
        if (renglones.isEmpty()) return;
        f.guardando = true;
        f.error = null;
        lista.refresh();
        DialogUtil.runAsync(() -> EditarVarios.aplicar(service, renglones),
            r -> {
                f.guardando = false;
                r.guardados().forEach(p -> { porId.put(p.getId(), p); guardados.put(p.getId(), p); });
                f.guardados += r.guardados().size();
                if (!r.fallas().isEmpty()) f.error = FormatUtils.plural(r.fallas().size(), "no se guardó", "no se guardaron") + ": "
                    + String.join("; ", r.fallas().subList(0, Math.min(3, r.fallas().size())));
                reagrupar(f);
                lista.refresh();
                refiltrar();
                NotificacionUtil.exitoConAccion(escena(), "Factura " + f.factura.etiqueta() + ": "
                    + FormatUtils.plural(r.guardados().size(), "bien completado", "bienes completados"), "Deshacer", () -> deshacer(f, r));
            },
            e -> {
                f.guardando = false;
                f.error = e.getMessage() != null ? e.getMessage() : "No se pudo guardar";
                log.warn("No se pudo completar la factura {}: {}", f.factura.etiqueta(), e.getMessage());
                lista.refresh();
            });
    }

    /** The row as its bienes are now: what is still missing, or done. */
    private void reagrupar(Fila f) {
        Set<String> ids = new HashSet<>();
        f.factura.bienes().forEach(p -> ids.add(p.getId()));
        List<Producto> deEsa = f.factura.bienes().stream().map(p -> porId.getOrDefault(p.getId(), p)).toList();
        Optional<Factura> ahora = CompletarPorFactura.facturas(new ArrayList<>(porId.values()), cuales(), campos()).stream()
            .filter(x -> x.bienes().stream().anyMatch(p -> ids.contains(p.getId()))).findFirst();
        if (ahora.isEmpty()) {
            f.completo = true;
            f.factura = new Factura(f.factura.etiqueta(), f.factura.uuid(), deEsa, f.factura.enLaCompra(),
                Map.of(), Map.of(), f.factura.tienen());
            return;
        }
        f.completo = false;
        f.factura = ahora.get();
    }

    private void deshacer(Fila f, EditarVarios.Resultado r) {
        DialogUtil.runAsync(() -> {
                int n = EditarVarios.deshacer(service, r);
                List<Producto> ahora = new ArrayList<>();
                for (Producto p : r.guardados()) service.findById(p.getId()).ifPresent(ahora::add);
                return Map.entry(n, ahora);
            },
            res -> {
                res.getValue().forEach(p -> { porId.put(p.getId(), p); guardados.put(p.getId(), p); });
                f.guardados = Math.max(0, f.guardados - res.getKey());
                reagrupar(f);
                lista.refresh();
                refiltrar();
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

    private final class CeldaFactura extends ListCell<Fila> {
        private final Label etiqueta = new Label();
        private final Label nombres = new Label();
        private final Label detalle = new Label();
        private final TextField proveedor = new TextField();
        private final DatePicker fecha = new DatePicker();
        private final TextField folio = new TextField();
        private final Map<Campo, Label> notas = new EnumMap<>(Campo.class);
        private final Map<Campo, Control> controles = new EnumMap<>(Campo.class);
        private final Hyperlink leer = new Hyperlink("Leer su PDF o XML…");
        private final Label deSuArchivo = new Label();
        private final Button guardar = new Button("Guardar");
        private final Label estado = new Label();
        private final HBox fila;
        private boolean llenando = false;

        CeldaFactura(ListView<Fila> lv) {
            double k = com.sibim.util.EscalaTexto.factorActual();
            etiqueta.getStyleClass().add("trf-codigo");
            etiqueta.setMinWidth(Region.USE_PREF_SIZE);
            nombres.getStyleClass().add("org-child-label");
            nombres.setWrapText(true);
            nombres.setMaxHeight(42);
            nombres.setMinWidth(0);
            detalle.getStyleClass().add("org-table-sub");
            detalle.setWrapText(true);
            detalle.setMinWidth(0);
            deSuArchivo.getStyleClass().add("org-table-sub");
            deSuArchivo.setWrapText(true);
            deSuArchivo.setMinWidth(0);
            leer.setGraphic(new FontIcon("mdi2f-file-pdf-box"));
            leer.setOnAction(e -> { if (getItem() != null) leerArchivo(getItem()); });
            HBox lineaLeer = new HBox(6, leer, deSuArchivo);
            lineaLeer.setAlignment(Pos.CENTER_LEFT);
            VBox textos = new VBox(3, new HBox(8, etiqueta), nombres, detalle, lineaLeer);
            textos.setMinWidth(180);
            textos.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(textos, Priority.ALWAYS);

            proveedor.setPromptText("Proveedor");
            folio.setPromptText("Folio fiscal (UUID)");
            fecha.setConverter(FormatUtils.datePickerConverter());
            fecha.setPromptText("dd/mm/aaaa");
            controles.put(Campo.PROVEEDOR, proveedor);
            controles.put(Campo.FECHA, fecha);
            controles.put(Campo.FOLIO, folio);
            fila = new HBox(12, textos);
            for (Campo c : Campo.values()) {
                double ancho = Math.round((c == Campo.PROVEEDOR ? 220 : c == Campo.FECHA ? 140 : 280) * k);
                Control ctl = controles.get(c);
                ctl.getStyleClass().add("form-input");
                ctl.setPrefWidth(ancho);
                ctl.setMinWidth(ancho);
                ctl.setMaxWidth(ancho);
                Label titulo = new Label(c.etiqueta);
                titulo.getStyleClass().add("muted-sm");
                Label nota = new Label();
                nota.getStyleClass().add("org-table-sub");
                nota.setWrapText(true);
                nota.setMaxWidth(ancho);
                nota.setMinHeight(Region.USE_PREF_SIZE);
                notas.put(c, nota);
                VBox caja = new VBox(3, titulo, ctl, nota);
                caja.setMinWidth(ancho);
                caja.setPrefWidth(ancho);
                caja.setMaxWidth(ancho);
                fila.getChildren().add(caja);
            }
            guardar.setGraphic(new FontIcon("mdi2c-content-save-outline"));
            guardar.getStyleClass().add("btn-secondary");
            guardar.setMinWidth(Region.USE_PREF_SIZE);
            guardar.setOnAction(e -> { if (getItem() != null) { guardar(getItem()); if (getItem().error == null) siguiente(getItem()); } });
            double anchoAcciones = Math.round(140 * k);
            estado.setWrapText(true);
            estado.setMaxWidth(anchoAcciones);
            VBox acciones = new VBox(6, guardar, estado);
            acciones.setMinWidth(anchoAcciones);
            acciones.setPrefWidth(anchoAcciones);
            fila.getChildren().add(acciones);
            fila.setAlignment(Pos.TOP_LEFT);
            fila.getStyleClass().add("cnt-fila");
            fila.setStyle("-fx-cursor: default;");
            fila.maxWidthProperty().bind(lv.widthProperty().subtract(30));
            fila.setMinWidth(0);

            proveedor.textProperty().addListener((o, a, v) -> { if (!llenando && getItem() != null) { getItem().proveedor = v == null ? "" : v; limpiarError(); } });
            folio.textProperty().addListener((o, a, v) -> { if (!llenando && getItem() != null) { getItem().folio = v == null ? "" : v; limpiarError(); } });
            fecha.valueProperty().addListener((o, a, v) -> { if (!llenando && getItem() != null) { getItem().fecha = v; limpiarError(); } });
            for (Control c : List.of(proveedor, folio, fecha))
                c.setOnKeyPressed(e -> {
                    Fila f = getItem();
                    if (f == null || e.getCode() != KeyCode.ENTER) return;
                    // A date typed and not yet confirmed: take it before saving.
                    if (c == fecha) {
                        try { fecha.setValue(fecha.getConverter().fromString(fecha.getEditor().getText())); }
                        catch (RuntimeException ignored) { /* stays as it was */ }
                    }
                    guardar(f);
                    if (f.error == null) siguiente(f);
                    e.consume();
                });
        }

        private void limpiarError() {
            Fila f = getItem();
            if (f != null && f.error != null) { f.error = null; pintarEstado(f); }
        }

        @Override protected void updateItem(Fila f, boolean empty) {
            super.updateItem(f, empty);
            setText(null);
            if (empty || f == null) { setGraphic(null); return; }
            llenando = true;
            Factura fac = f.factura;
            etiqueta.setText("Factura " + fac.etiqueta());
            etiqueta.setTooltip(fac.uuid() != null ? new Tooltip("Folio fiscal " + fac.uuid()) : null);
            nombres.setText(fac.nombres());
            nombres.setTooltip(new Tooltip(fac.nombres()));
            Set<String> areas = fac.areas();
            detalle.setText(FormatUtils.plural(fac.bienes().size(), "bien", "bienes")
                + (fac.bienes().size() < fac.enLaCompra() ? " de " + fac.enLaCompra() + " en la factura" : "")
                + " · " + (areas.size() == 1 ? areas.iterator().next() : areas.size() + " áreas"));
            ver(deSuArchivo, f.deSuArchivo != null);
            deSuArchivo.setText(f.deSuArchivo == null ? "" : f.deSuArchivo);
            leer.setDisable(f.completo || f.guardando);

            boolean bloqueado = f.guardando || f.completo;
            proveedor.setText(f.proveedor);
            folio.setText(f.folio);
            fecha.setValue(f.fecha);
            for (Campo c : Campo.values()) {
                Integer n = fac.faltan().get(c);
                boolean pide = n != null && !f.completo;
                controles.get(c).setDisable(!pide || bloqueado);
                if (!pide) {
                    if (c == Campo.FECHA) fecha.setValue(null);
                    else ((TextField) controles.get(c)).setText("");
                }
                controles.get(c).setAccessibleText(c.etiqueta + " de la factura " + fac.etiqueta());
                CompletarPorFactura.Propuesta p = fac.propuestas().get(c);
                String nota;
                if (!pide) nota = f.completo && n != null ? "Listo" : fac.tienen().get(c);
                else if (p != null && Objects.equals(p.valor() instanceof LocalDate ? p.valor().toString() : String.valueOf(p.valor()), f.texto(c).trim()))
                    nota = "Falta en " + n + " · " + p.porQue();
                else {
                    String tienen = fac.tienen().get(c);
                    nota = "Falta en " + n + ("Ninguno tiene".equals(tienen) ? "" : " · ya tienen " + tienen);
                }
                notas.get(c).setText(nota);
            }
            guardar.setText(f.completo ? "Guardado" : "Guardar en " + fac.incompletos());
            guardar.setDisable(bloqueado);
            pintarEstado(f);
            fila.getStyleClass().removeAll("cnt-fila-vista", "cnt-fila-incidencia");
            if (f.error != null) fila.getStyleClass().add("cnt-fila-incidencia");
            else if (f.completo) fila.getStyleClass().add("cnt-fila-vista");
            llenando = false;
            setGraphic(fila);
            if (f == pedirFoco) {
                pedirFoco = null;
                Platform.runLater(() -> controles.values().stream().filter(t -> !t.isDisabled()).findFirst().ifPresent(Control::requestFocus));
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
