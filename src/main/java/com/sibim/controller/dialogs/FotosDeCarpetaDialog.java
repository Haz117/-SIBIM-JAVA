package com.sibim.controller.dialogs;

import com.sibim.config.Areas;
import com.sibim.model.Producto;
import com.sibim.service.FotosDeCarpeta;
import com.sibim.service.FotosDeCarpeta.Foto;
import com.sibim.service.ProductoService;
import com.sibim.service.ReporteFotosPorTomarService;
import com.sibim.util.AppColors;
import com.sibim.util.CarpetaDeFotos;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

/**
 * Bienes › Más › «Fotos de una carpeta» (also from Completar datos › Sin foto): the bienes of an
 * área on the left, in the order of the visit; the photos of a folder on the right, in the order
 * they were taken, with the one in view large in the middle to read its label. A click (or Space)
 * marks a photo for the bien chosen, as many as it needs (the 4 of a patrulla); Enter saves them,
 * as its principal, and goes to the next bien with the next photo not used already marked. See
 * {@link FotosDeCarpeta} for why.
 */
public final class FotosDeCarpetaDialog {

    private static final Logger log = LoggerFactory.getLogger(FotosDeCarpetaDialog.class);
    static final String TODAS = "Todas las áreas";
    private static final double MINI_W = 120, MINI_H = 90;

    /** A bien of the list and how its photos went here. */
    static final class Fila {
        Producto bien;
        int nuevas;
        boolean guardando;
        String error;
        Fila(Producto p) { bien = p; }
    }

    private final ProductoService service = new ProductoService();
    /** The inventory in force, as each bien is saved here (another área or «solo sin foto» is
     *  read from it again, and a bien just photographed must not come back as without photo). */
    private final Map<String, Producto> inventario = new LinkedHashMap<>();
    /** The photos put here on each bien, to say it again when the list is read again. */
    private final Map<String, Integer> nuevasPorBien = new HashMap<>();
    private final Runnable alCambiar;
    private boolean cambio;
    private Dialog<ButtonType> dialog;

    private File carpeta;
    private List<Foto> fotos = List.of();
    private int sinLeer;
    /** Each photo given to a bien here, and its número. */
    private final Map<File, String> usadaEn = new HashMap<>();
    /** The photos marked for the bien chosen, in the order they were clicked. */
    private final List<File> marcadas = new ArrayList<>();
    /** The photo in view; -1 when there are none. */
    private int cursor = -1;
    /** Where the next suggestion starts: after the last photo saved. */
    private int ultimaUsada = -1;
    private final Map<File, Image> miniaturas = new HashMap<>();
    private final Map<File, StackPane> nodos = new HashMap<>();

    private final ComboBox<String> area = new ComboBox<>();
    private final CheckBox soloSinFoto = new CheckBox("Solo los que no tienen foto");
    private final TextField buscar = new TextField();
    private final List<Fila> filas = new ArrayList<>();
    private final ObservableList<Fila> visibles = FXCollections.observableArrayList();
    private final ListView<Fila> lista = new ListView<>(visibles);
    private final Label ruta = new Label();
    private final ComboBox<FotosDeCarpeta.Orden> orden = new ComboBox<>();
    private final Label resumenFotos = new Label();
    private final ImageView vista = new ImageView();
    private final Label vistaNombre = new Label();
    /** Takes what is left of the height: the photo fits in it and never pushes «Guardar» out of a
     *  1366x768 screen (its own size would, as the box follows the photo and the photo the box). */
    private final StackPane vistaCaja = new StackPane() {
        @Override protected double computePrefHeight(double ancho) { return 160; }
        @Override protected double computeMinHeight(double ancho) { return 120; }
        @Override protected double computePrefWidth(double alto) { return 300; }
        @Override protected double computeMinWidth(double alto) { return 200; }
    };
    private final TilePane mosaico = new TilePane(8, 8);
    private final VBox sinCarpeta = new VBox(10);
    private final ScrollPane mosaicoScroll = new ScrollPane(mosaico);
    private final Label paraQuien = new Label();
    private final CheckBox reemplazar = new CheckBox("Quitar las fotos que ya tiene");
    private final Button guardar = new Button("Guardar y siguiente");

    private FotosDeCarpetaDialog(List<Producto> bienes, Runnable alCambiar) {
        bienes.stream().filter(p -> p.getFechaBaja() == null).forEach(p -> inventario.put(p.getId(), p));
        this.alCambiar = alCambiar;
    }

    /** @param areaInicial the área to open on, or null for all */
    public static void show(List<Producto> bienes, String areaInicial, Runnable alCambiar) {
        new FotosDeCarpetaDialog(bienes, alCambiar).abrir(areaInicial, CarpetaDeFotos.ultima());
    }

    /** @param carpetaInicial the folder to open with (the tests', not the one remembered on this PC) */
    static void show(List<Producto> bienes, String areaInicial, File carpetaInicial, Runnable alCambiar) {
        new FotosDeCarpetaDialog(bienes, alCambiar).abrir(areaInicial, carpetaInicial);
    }

    private void abrir(String areaInicial, File carpetaInicial) {
        dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Fotos de una carpeta");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(1280, pantalla.getWidth() * 0.95), Math.min(780, pantalla.getHeight() * 0.9));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        HBox header = DialogUtil.gradientHeader("mdi2c-camera-burst", "Fotos de una carpeta",
            "Elige el bien, marca su foto (o varias) y Enter: se guarda como su foto principal y pasa al siguiente, con la siguiente foto ya marcada",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        HBox cuerpo = new HBox(14, panelBienes(), panelVista(), panelFotos());
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        VBox debajo = new VBox(cuerpo);
        debajo.setPadding(new Insets(0, 22, 14, 22));
        VBox.setVgrow(debajo, Priority.ALWAYS);
        dialog.getDialogPane().setContent(new VBox(12, header, debajo));
        dialog.getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, this::tecla);
        dialog.setOnHidden(e -> { if (cambio && alCambiar != null) alCambiar.run(); });

        List<String> areas = new ArrayList<>(List.of(TODAS));
        Set<String> presentes = new HashSet<>();
        inventario.values().forEach(p -> presentes.add(areaDe(p)));
        Areas.getAllAreaNames().stream().filter(presentes::contains).forEach(areas::add);
        presentes.stream().filter(a -> !areas.contains(a)).sorted().forEach(areas::add);
        area.getItems().setAll(areas);
        area.setValue(areaInicial != null && areas.contains(areaInicial) ? areaInicial : TODAS);
        cargarBienes();
        if (carpetaInicial != null && carpetaInicial.isDirectory()) leerCarpeta(carpetaInicial);
        else mostrarFotos();
        Platform.runLater(lista::requestFocus);
        dialog.showAndWait();
    }

    static String areaDe(Producto p) {
        return p.getArea() != null && !p.getArea().isBlank() ? p.getArea() : "Sin área";
    }

    // ── Izquierda: los bienes ─────────────────────────────────────────────────

    private VBox panelBienes() {
        area.setMaxWidth(Double.MAX_VALUE);
        area.setVisibleRowCount(16);
        area.setAccessibleText("Área");
        area.valueProperty().addListener((o, a, b) -> cargarBienes());
        soloSinFoto.setSelected(true);
        soloSinFoto.selectedProperty().addListener((o, a, b) -> cargarBienes());
        buscar.setPromptText("Número o nombre…");
        buscar.getStyleClass().add("form-input");
        buscar.textProperty().addListener((o, a, b) -> refiltrar());
        Hyperlink imprimir = new Hyperlink("Lista para tomarlas (PDF)");
        imprimir.setGraphic(new FontIcon("mdi2p-printer"));
        imprimir.setTooltip(new Tooltip("Los bienes sin foto del área elegida, por ubicación y número, con una casilla: en ese orden salen aquí"));
        imprimir.setOnAction(e -> imprimirLista());

        lista.getStyleClass().add("cnt-lista");
        lista.setCellFactory(lv -> new CeldaBien(lv));
        lista.setPlaceholder(new Label("No hay bienes sin foto aquí"));
        lista.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> alElegirBien());
        VBox.setVgrow(lista, Priority.ALWAYS);
        VBox p = new VBox(8, TransferenciaArrastreDialog.paso(1, "El bien"), area, soloSinFoto, buscar, imprimir, lista);
        p.getStyleClass().add("trf-panel");
        p.setPrefWidth(310);
        p.setMinWidth(260);
        return p;
    }

    private void cargarBienes() {
        String a = area.getValue();
        filas.clear();
        List<String> organigrama = new ArrayList<>(Areas.getAllAreaNames());
        inventario.values().stream()
            .filter(p -> a == null || TODAS.equals(a) || a.equals(areaDe(p)))
            .filter(p -> !soloSinFoto.isSelected() || FotosDeCarpeta.sinFoto(p))
            .sorted(Comparator.comparingInt((Producto p) -> {
                    int i = organigrama.indexOf(areaDe(p));
                    return i < 0 ? Integer.MAX_VALUE : i;
                }).thenComparing(FotosDeCarpetaDialog::areaDe).thenComparing(FotosDeCarpeta.EN_ORDEN_DE_VISITA))
            .forEach(p -> {
                Fila f = new Fila(p);
                f.nuevas = nuevasPorBien.getOrDefault(p.getId(), 0);
                filas.add(f);
            });
        refiltrar();
        if (!visibles.isEmpty()) lista.getSelectionModel().select(0);
    }

    private void refiltrar() {
        Fila antes = lista.getSelectionModel().getSelectedItem();
        visibles.setAll(DialogUtil.coincidencias(filas,
            f -> Objects.requireNonNullElse(f.bien.getCodigo(), "") + " " + Objects.requireNonNullElse(f.bien.getNombre(), "")
                + " " + Objects.requireNonNullElse(com.sibim.util.ProductoUtils.inventarioAnterior(f.bien), ""),
            buscar.getText()));
        if (antes != null && visibles.contains(antes)) lista.getSelectionModel().select(antes);
    }

    private Fila elegida() {
        return lista.getSelectionModel().getSelectedItem();
    }

    /** Another bien: what was marked was for the last one; the next photo not used comes marked. */
    private void alElegirBien() {
        marcadas.clear();
        Fila f = elegida();
        if (f != null && f.nuevas == 0 && !f.guardando && !fotos.isEmpty()) {
            int i = FotosDeCarpeta.siguienteLibre(fotos, usadaEn.keySet(), ultimaUsada);
            if (i >= 0) {
                marcadas.add(fotos.get(i).archivo());
                cursor = i;
            }
        }
        if (f != null) lista.scrollTo(Math.max(0, visibles.indexOf(f) - 3));
        actualizarVista();
        pintarMosaico();
    }

    // ── En medio: la foto grande y guardar ────────────────────────────────────

    private VBox panelVista() {
        vista.setPreserveRatio(true);
        vista.setSmooth(true);
        vistaCaja.getChildren().setAll(vista);
        vistaCaja.getStyleClass().add("fdc-vista");
        VBox.setVgrow(vistaCaja, Priority.ALWAYS);
        vista.fitWidthProperty().bind(vistaCaja.widthProperty().subtract(16));
        vista.fitHeightProperty().bind(vistaCaja.heightProperty().subtract(16));
        vistaNombre.getStyleClass().add("muted-sm");
        vistaNombre.setWrapText(true);

        paraQuien.getStyleClass().add("trf-paso-titulo");
        paraQuien.setWrapText(true);
        paraQuien.setMinHeight(Region.USE_PREF_SIZE);
        reemplazar.setTooltip(new Tooltip("Sin marcar, las que ya tiene quedan después de las nuevas"));
        guardar.setGraphic(new FontIcon("mdi2c-content-save-outline"));
        guardar.getStyleClass().add("btn-primary");
        guardar.setMinWidth(Region.USE_PREF_SIZE);
        guardar.setOnAction(e -> guardarYSiguiente());
        Label teclas = new Label("← → otra foto · Espacio marca o quita · Enter guarda y sigue · ↑ ↓ otro bien");
        teclas.getStyleClass().add("muted-sm");
        teclas.setWrapText(true);
        teclas.setGraphic(new FontIcon("mdi2k-keyboard-outline"));
        HBox acciones = new HBox(10, guardar, reemplazar);
        acciones.setAlignment(Pos.CENTER_LEFT);

        VBox p = new VBox(8, TransferenciaArrastreDialog.paso(2, "Su foto"), vistaCaja, vistaNombre, paraQuien, acciones, teclas);
        HBox.setHgrow(p, Priority.ALWAYS);
        p.setMinWidth(300);
        return p;
    }

    private void actualizarVista() {
        Foto f = cursor >= 0 && cursor < fotos.size() ? fotos.get(cursor) : null;
        vista.setImage(f == null ? null : new Image(f.archivo().toURI().toString(), 900, 900, true, true, true));
        String usada = f == null ? null : usadaEn.get(f.archivo());
        vistaNombre.setText(f == null ? "" : (cursor + 1) + " de " + fotos.size() + " · " + f.nombre()
            + (usada != null ? " · ya es de " + usada : marcadas.contains(f.archivo()) ? " · marcada" : ""));
        Fila b = elegida();
        boolean tiene = b != null && !FotosDeCarpeta.sinFoto(b.bien) && b.nuevas == 0;
        reemplazar.setVisible(tiene);
        reemplazar.setManaged(tiene);
        if (b == null) {
            paraQuien.setText("Elige un bien de la lista");
            guardar.setDisable(true);
            return;
        }
        String quien = b.bien.getCodigo() + " · " + b.bien.getNombre();
        paraQuien.setText(b.nuevas > 0 ? quien + ": ya tiene " + FormatUtils.plural(b.nuevas, "foto nueva", "fotos nuevas")
            : marcadas.isEmpty() ? quien + ": ninguna foto marcada (Enter pasa al siguiente)"
            : quien + ": " + FormatUtils.plural(marcadas.size(), "foto marcada", "fotos marcadas"));
        guardar.setText(marcadas.isEmpty() ? "Siguiente" : "Guardar y siguiente");
        guardar.setDisable(b.guardando);
    }

    // ── Derecha: las fotos de la carpeta ──────────────────────────────────────

    private VBox panelFotos() {
        ruta.getStyleClass().add("muted-sm");
        ruta.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
        ruta.setMaxWidth(Double.MAX_VALUE);
        Button cambiar = new Button("Elegir carpeta…");
        cambiar.setGraphic(new FontIcon("mdi2f-folder-image"));
        cambiar.getStyleClass().add("btn-secondary");
        cambiar.setMinWidth(Region.USE_PREF_SIZE);
        cambiar.setOnAction(e -> elegirCarpeta());
        orden.getItems().setAll(FotosDeCarpeta.Orden.values());
        orden.setValue(FotosDeCarpeta.Orden.POR_NOMBRE);
        orden.setAccessibleText("Orden de las fotos");
        orden.valueProperty().addListener((o, a, b) -> { if (carpeta != null) leerCarpeta(carpeta); });
        resumenFotos.getStyleClass().add("muted-sm");
        resumenFotos.setWrapText(true);
        HBox barra = new HBox(8, cambiar, orden);
        barra.setAlignment(Pos.CENTER_LEFT);

        mosaico.setPrefColumns(3);
        mosaico.setPrefTileWidth(MINI_W + 10);
        mosaico.setPadding(new Insets(4));
        mosaicoScroll.setFitToWidth(true);
        mosaicoScroll.getStyleClass().add("area-elegir-lista");
        VBox.setVgrow(mosaicoScroll, Priority.ALWAYS);

        FontIcon icono = new FontIcon("mdi2f-folder-image");
        icono.getStyleClass().add("empty-icon-lg");
        Label titulo = new Label("Elige la carpeta con las fotos");
        titulo.getStyleClass().add("empty-state-msg");
        Label pista = new Label("La carpeta donde copiaste las fotos del teléfono (JPG o PNG)");
        pista.getStyleClass().add("empty-state-hint");
        pista.setWrapText(true);
        Button elegir = new Button("Elegir carpeta…");
        elegir.getStyleClass().add("btn-primary");
        elegir.setOnAction(e -> elegirCarpeta());
        sinCarpeta.getChildren().setAll(icono, titulo, pista, elegir);
        sinCarpeta.setAlignment(Pos.CENTER);
        VBox.setVgrow(sinCarpeta, Priority.ALWAYS);

        VBox p = new VBox(8, TransferenciaArrastreDialog.paso(3, "Las fotos"), barra, ruta, resumenFotos, sinCarpeta, mosaicoScroll);
        p.getStyleClass().add("trf-panel");
        // Three across: the tiles, their gaps and padding, the scroll bar and the panel's own padding.
        double ancho = 3 * (MINI_W + 10) + 2 * 8 + 8 + 18 + 34;
        p.setPrefWidth(ancho);
        p.setMinWidth(ancho);
        return p;
    }

    private void elegirCarpeta() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Carpeta con las fotos");
        File ultima = carpeta != null ? carpeta : CarpetaDeFotos.ultima();
        if (ultima != null && ultima.isDirectory()) dc.setInitialDirectory(ultima);
        File elegida = dc.showDialog(dialog.getDialogPane().getScene().getWindow());
        if (elegida == null) return;
        CarpetaDeFotos.recordar(elegida);
        leerCarpeta(elegida);
    }

    private void leerCarpeta(File c) {
        carpeta = c;
        FotosDeCarpeta.Contenido cont = FotosDeCarpeta.leer(c, orden.getValue());
        fotos = cont.fotos();
        sinLeer = cont.sinLeer();
        ultimaUsada = -1;
        for (int i = 0; i < fotos.size(); i++) if (usadaEn.containsKey(fotos.get(i).archivo())) ultimaUsada = i;
        ruta.setText(c.getAbsolutePath());
        ruta.setTooltip(new Tooltip(c.getAbsolutePath()));
        nodos.clear();
        mosaico.getChildren().clear();
        for (int i = 0; i < fotos.size(); i++) mosaico.getChildren().add(miniatura(i));
        cursor = fotos.isEmpty() ? -1 : 0;
        alElegirBien();
        mostrarFotos();
    }

    private void mostrarFotos() {
        boolean hay = carpeta != null;
        ver(sinCarpeta, !hay);
        ver(mosaicoScroll, hay);
        long usadas = fotos.stream().filter(f -> usadaEn.containsKey(f.archivo())).count();
        resumenFotos.setText(!hay ? "" : fotos.isEmpty() ? "Esta carpeta no tiene fotos JPG ni PNG"
            + (sinLeer > 0 ? " (tiene " + sinLeer + " HEIC: pásalas a JPG desde el teléfono)" : "")
            : FormatUtils.plural(fotos.size(), "foto", "fotos") + (usadas > 0 ? " · " + usadas + " ya puestas" : "")
            + (sinLeer > 0 ? " · " + sinLeer + " HEIC no se pueden leer: pásalas a JPG" : ""));
    }

    private StackPane miniatura(int i) {
        Foto f = fotos.get(i);
        ImageView iv = new ImageView(miniaturas.computeIfAbsent(f.archivo(),
            a -> new Image(a.toURI().toString(), MINI_W * 2, MINI_H * 2, true, true, true)));
        iv.setFitWidth(MINI_W);
        iv.setFitHeight(MINI_H);
        iv.setPreserveRatio(true);
        Label num = new Label();
        num.getStyleClass().add("fdc-num");
        Label deQuien = new Label();
        deQuien.getStyleClass().add("fdc-usada-en");
        StackPane n = new StackPane(iv, num, deQuien);
        StackPane.setAlignment(num, Pos.TOP_LEFT);
        StackPane.setAlignment(deQuien, Pos.BOTTOM_CENTER);
        n.setPrefSize(MINI_W + 6, MINI_H + 6);
        n.getStyleClass().add("fdc-mini");
        n.setOnMouseClicked(e -> { cursor = i; alternar(i); });
        n.setAccessibleRole(AccessibleRole.BUTTON);
        n.setAccessibleText("Foto " + (i + 1) + ", " + f.nombre());
        Tooltip.install(n, new Tooltip(f.nombre()));
        nodos.put(f.archivo(), n);
        pintar(i);
        return n;
    }

    private void pintarMosaico() {
        for (int i = 0; i < fotos.size(); i++) pintar(i);
        mostrarFotos();
        if (cursor >= 0 && cursor < fotos.size()) asomar(nodos.get(fotos.get(cursor).archivo()));
    }

    private void pintar(int i) {
        File a = fotos.get(i).archivo();
        StackPane n = nodos.get(a);
        if (n == null) return;
        int orden = marcadas.indexOf(a);
        String usada = usadaEn.get(a);
        n.getStyleClass().removeAll("fdc-mini-marcada", "fdc-mini-vista");
        if (orden >= 0) n.getStyleClass().add("fdc-mini-marcada");
        else if (i == cursor) n.getStyleClass().add("fdc-mini-vista");
        n.getChildren().get(0).setOpacity(usada != null && orden < 0 ? 0.4 : 1);
        Label num = (Label) n.getChildren().get(1);
        num.setText(orden >= 0 ? String.valueOf(orden + 1) : "");
        ver(num, orden >= 0);
        Label deQuien = (Label) n.getChildren().get(2);
        deQuien.setText(usada == null ? "" : usada);
        ver(deQuien, usada != null);
    }

    /** Scrolls the mosaic so {@code n} shows. */
    private void asomar(Node n) {
        if (n == null || mosaico.getHeight() <= 0) return;
        double alto = mosaico.getHeight() - mosaicoScroll.getViewportBounds().getHeight();
        if (alto <= 0) return;
        double y = n.getBoundsInParent().getMinY(), h = n.getBoundsInParent().getHeight();
        double desde = mosaicoScroll.getVvalue() * alto, hasta = desde + mosaicoScroll.getViewportBounds().getHeight();
        if (y < desde) mosaicoScroll.setVvalue(y / alto);
        else if (y + h > hasta) mosaicoScroll.setVvalue(Math.min(1, (y + h - mosaicoScroll.getViewportBounds().getHeight()) / alto));
    }

    /** Marks or unmarks photo {@code i} for the bien chosen; one of another bien is only looked at. */
    private void alternar(int i) {
        File a = fotos.get(i).archivo();
        Fila f = elegida();
        if (usadaEn.containsKey(a) && !marcadas.contains(a)) {
            NotificacionUtil.info(escena(), "Esa foto ya es de " + usadaEn.get(a) + " (se deshace desde su aviso)");
        } else if (f != null && f.nuevas == 0 && !f.guardando) {
            if (!marcadas.remove(a)) marcadas.add(a);
        }
        actualizarVista();
        pintarMosaico();
    }

    private void tecla(KeyEvent e) {
        // Typing, choosing in a list, or a button or box of its own: theirs.
        if (e.getTarget() instanceof TextInputControl || e.getTarget() instanceof ComboBoxBase<?>) return;
        if (e.getTarget() instanceof ButtonBase && (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE)) return;
        switch (e.getCode()) {
            case RIGHT, LEFT -> {
                if (fotos.isEmpty()) return;
                cursor = Math.max(0, Math.min(fotos.size() - 1, cursor + (e.getCode() == KeyCode.RIGHT ? 1 : -1)));
                actualizarVista();
                pintarMosaico();
            }
            case SPACE -> { if (cursor >= 0) alternar(cursor); }
            case ENTER -> guardarYSiguiente();
            case UP, DOWN -> {
                int i = lista.getSelectionModel().getSelectedIndex() + (e.getCode() == KeyCode.DOWN ? 1 : -1);
                if (i >= 0 && i < visibles.size()) lista.getSelectionModel().select(i);
            }
            default -> { return; }
        }
        e.consume();
    }

    // ── Guardar ───────────────────────────────────────────────────────────────

    private Scene escena() {
        return dialog.getDialogPane().getScene();
    }

    private void guardarYSiguiente() {
        Fila f = elegida();
        if (f == null) return;
        if (!marcadas.isEmpty() && f.nuevas == 0 && !f.guardando) guardar(f, new ArrayList<>(marcadas), reemplazar.isVisible() && reemplazar.isSelected());
        int i = visibles.indexOf(f);
        if (i + 1 < visibles.size()) lista.getSelectionModel().select(i + 1);
        else { marcadas.clear(); actualizarVista(); pintarMosaico(); }
    }

    /** What was saved, to undo it: the bien as saved and the photos it had. */
    private record Hecho(Producto bien, List<String> tenia, int subidas) {}

    void guardar(Fila f, List<File> archivos, boolean quitarLasQueTenia) {
        f.guardando = true;
        f.error = null;
        for (File a : archivos) usadaEn.put(a, f.bien.getCodigo());
        for (File a : archivos) {
            int i = indiceDe(a);
            if (i > ultimaUsada) ultimaUsada = i;
        }
        lista.refresh();
        Producto p = f.bien.copia();
        DialogUtil.runAsync(() -> {
                List<String> tenia = new ArrayList<>(service.getFotosByProductoId(p.getId()));
                if (tenia.isEmpty() && p.getFotoUrl() != null && !p.getFotoUrl().isBlank()) tenia.add(p.getFotoUrl());
                List<String> nuevas = archivos.stream().map(File::getAbsolutePath).toList();
                p.setFotosUrls(new ArrayList<>(FotosDeCarpeta.conLasNuevas(nuevas, tenia, quitarLasQueTenia)));
                ProductoDialogFactory.procesarArchivos(p, p.getFacturaUrl(), new HashMap<>(), log);
                int subidas = (int) p.getFotosUrls().stream().filter(u -> !tenia.contains(u)).count();
                if (subidas == 0) throw new IllegalStateException("No se pudo subir ninguna foto");
                Producto guardado = service.save(p);
                service.saveFotos(guardado.getId(), p.getFotosUrls());
                com.sibim.service.FichaPublica.actualizarLuego(guardado.getId());
                return new Hecho(guardado, tenia, subidas);
            },
            h -> {
                f.guardando = false;
                f.bien = h.bien();
                f.nuevas = h.subidas();
                inventario.put(h.bien().getId(), h.bien());
                nuevasPorBien.put(h.bien().getId(), h.subidas());
                if (h.subidas() < archivos.size())
                    f.error = FormatUtils.plural(archivos.size() - h.subidas(), "foto no se pudo subir", "fotos no se pudieron subir");
                cambio = true;
                lista.refresh();
                if (f == elegida()) actualizarVista();
                pintarMosaico();
                NotificacionUtil.exitoConAccion(escena(), f.bien.getCodigo() + ": " + FormatUtils.plural(h.subidas(), "foto puesta", "fotos puestas"),
                    "Deshacer", () -> deshacer(f, h, archivos));
            },
            e -> {
                f.guardando = false;
                f.error = e.getMessage() != null ? e.getMessage() : "No se pudo guardar";
                archivos.forEach(usadaEn::remove);
                ultimaUsada = -1;
                for (int i = 0; i < fotos.size(); i++) if (usadaEn.containsKey(fotos.get(i).archivo())) ultimaUsada = i;
                log.warn("No se pudieron poner las fotos de {}: {}", f.bien.getCodigo(), e.getMessage());
                lista.refresh();
                pintarMosaico();
            });
    }

    private int indiceDe(File a) {
        for (int i = 0; i < fotos.size(); i++) if (fotos.get(i).archivo().equals(a)) return i;
        return -1;
    }

    /** The bien gets back the photos it had; the photos of the folder are free again. */
    private void deshacer(Fila f, Hecho h, List<File> archivos) {
        String id = h.bien().getId();
        DialogUtil.runAsync(() -> {
                Producto q = service.findById(id).orElseThrow(() -> new IllegalStateException("El bien ya no existe"));
                q.setFotosUrls(new ArrayList<>(h.tenia()));
                q.setFotoUrl(h.tenia().isEmpty() ? null : h.tenia().get(0));
                Producto g = service.save(q);
                service.saveFotos(id, h.tenia());
                com.sibim.service.FichaPublica.actualizarLuego(id);
                return g;
            },
            g -> {
                f.bien = g;
                f.nuevas = 0;
                f.error = null;
                inventario.put(g.getId(), g);
                nuevasPorBien.remove(g.getId());
                archivos.forEach(usadaEn::remove);
                lista.refresh();
                if (f == elegida()) alElegirBien(); else pintarMosaico();
                NotificacionUtil.info(escena(), g.getCodigo() + " quedó con las fotos que tenía");
            },
            ex -> NotificacionUtil.error(escena(), "No se pudo deshacer: " + com.sibim.util.MensajeError.de(ex)));
    }

    private void imprimirLista() {
        String a = area.getValue();
        List<Producto> deEsas = inventario.values().stream().filter(p -> a == null || TODAS.equals(a) || a.equals(areaDe(p))).toList();
        DialogUtil.runAsyncWithProgress(escena(), "Generando la lista…",
            () -> new ReporteFotosPorTomarService().exportPdf(deEsas),
            file -> {
                if (file == null) NotificacionUtil.info(escena(), "Todos los bienes de " + (TODAS.equals(a) ? "las áreas" : a) + " tienen foto");
                else DialogUtil.showExportResultDialog(escena(), file);
            },
            e -> NotificacionUtil.error(escena(), "No se pudo generar la lista: " + com.sibim.util.MensajeError.de(e)));
    }

    private static void ver(Node n, boolean visible) {
        n.setVisible(visible);
        n.setManaged(visible);
    }

    // ── La celda de un bien ───────────────────────────────────────────────────

    private final class CeldaBien extends ListCell<Fila> {
        private final Label codigo = new Label();
        private final Label nombre = new Label();
        private final Label detalle = new Label();
        private final Label estado = new Label();
        private final VBox caja;

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
            estado.setMinWidth(Region.USE_PREF_SIZE);
            caja = new VBox(3, linea, detalle, estado);
            caja.getStyleClass().add("cnt-fila");
            caja.setStyle("-fx-cursor: hand;");
            caja.maxWidthProperty().bind(lv.widthProperty().subtract(30));
            caja.setMinWidth(0);
        }

        @Override protected void updateItem(Fila f, boolean empty) {
            super.updateItem(f, empty);
            setText(null);
            if (empty || f == null) { setGraphic(null); return; }
            Producto p = f.bien;
            codigo.setText(p.getCodigo());
            nombre.setText(p.getNombre());
            nombre.setTooltip(new Tooltip(p.getNombre()));
            List<String> partes = new ArrayList<>();
            String anterior = com.sibim.util.ProductoUtils.inventarioAnterior(p);
            if (anterior != null) partes.add(anterior);
            partes.add(p.getUbicacion() != null && !p.getUbicacion().isBlank() ? p.getUbicacion() : "Sin ubicación");
            if (TODAS.equals(area.getValue())) partes.add(areaDe(p));
            detalle.setText(String.join(" · ", partes));
            detalle.setTooltip(new Tooltip(detalle.getText()));
            estado.getStyleClass().setAll("cell-badge", f.error != null ? "cell-badge-danger"
                : f.guardando ? "cell-badge-blue" : f.nuevas > 0 ? "cell-badge-success" : "cell-badge-muted");
            estado.setText(f.error != null ? f.error : f.guardando ? "Guardando…"
                : f.nuevas > 0 ? FormatUtils.plural(f.nuevas, "foto nueva", "fotos nuevas")
                : FotosDeCarpeta.sinFoto(p) ? "Sin foto" : "Ya tiene foto");
            estado.setTooltip(f.error != null ? new Tooltip(f.error) : null);
            caja.getStyleClass().removeAll("cnt-fila-vista", "cnt-fila-incidencia");
            if (f.error != null) caja.getStyleClass().add("cnt-fila-incidencia");
            else if (f.nuevas > 0) caja.getStyleClass().add("cnt-fila-vista");
            setGraphic(caja);
        }
    }
}
