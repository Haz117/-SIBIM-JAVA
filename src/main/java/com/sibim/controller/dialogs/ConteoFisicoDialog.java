package com.sibim.controller.dialogs;

import com.sibim.config.Areas;
import com.sibim.controller.FiltroArea;
import com.sibim.model.ConteoFisico;
import com.sibim.model.ConteoItem;
import com.sibim.model.Producto;
import com.sibim.model.Usuario;
import com.sibim.repository.ConteoRepository;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.common.HybridBinarizer;
import com.sibim.service.MovimientoService;
import com.sibim.service.ReporteConteoService;
import com.sibim.session.SessionManager;
import com.sibim.util.AppColors;
import com.sibim.util.AppExecutor;
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
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.stage.FileChooser;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.prefs.Preferences;

/** A physical inventory count (toma de inventario), laid out like "Transferir
 *  arrastrando": on the left what to count, by área or category, with how far each
 *  one has got; on the right its bienes. A click on a row says the bien is there;
 *  what doesn't match is noted on it. On finalizing, the conteo of what is chosen is
 *  saved and its differences reconciled through audited AJUSTE movements; whatever
 *  was captured elsewhere stays in the draft.
 *
 *  The rows are data drawn by a ListView, which only builds the ones on screen: one
 *  set of controls per bien took 456 MB for 700 bienes (the installed app has 512),
 *  and the whole inventory ran out of memory. */
public final class ConteoFisicoDialog {

    private static final Logger log = LoggerFactory.getLogger(ConteoFisicoDialog.class);

    enum EstadoConteo {
        ENCONTRADO("Encontrado"),
        MAL_ESTADO("Mal estado"),
        EN_OTRA_AREA("En otra área"),
        FALTANTE("Faltante");

        private final String label;
        EstadoConteo(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    /** The estado a row is saved with: what was captured, or {@link #SIN_REVISAR}
     *  for a bien nobody marked as seen. */
    static String estadoGuardado(boolean visto, String estado) {
        return visto ? estado : SIN_REVISAR;
    }

    /** A row that carries a difference or an estado was looked at, whether or
     *  not "Visto" is ticked: its ajuste is applied on finalizing, so it can't
     *  go into the record as "sin revisar". (Also what makes a draft saved by
     *  the previous version, which had no "Visto", resume as it was left.) */
    static boolean vista(boolean marcada, int contado, int sistema, EstadoConteo estado) {
        return marcada || contado != sistema || (estado != null && estado != EstadoConteo.ENCONTRADO);
    }

    /** A bien that is missing is not there: its count follows, and comes back if
     *  "Faltante" was a slip.
     *  @return the count after changing the estado */
    static int contadoAlCambiarEstado(EstadoConteo antes, EstadoConteo ahora, int contado, int sistema) {
        if (ahora == EstadoConteo.FALTANTE) return 0;
        if (antes == EstadoConteo.FALTANTE && contado == 0) return sistema;
        return contado;
    }

    /** One bien of the conteo and what was captured for it. */
    private static final class Fila {
        final Producto producto;
        int contado;
        EstadoConteo estado = EstadoConteo.ENCONTRADO;
        String nota = "";
        boolean visto;
        /** The note field and the count are only on screen when they are needed. */
        boolean verNota;
        boolean verCantidad;

        Fila(Producto producto) {
            this.producto = producto;
            this.contado = producto.getStockActual();
        }
        int sistema() { return producto.getStockActual(); }
        boolean vista() { return ConteoFisicoDialog.vista(visto, contado, sistema(), estado); }
        boolean incidencia() { return contado != sistema() || estado != EstadoConteo.ENCONTRADO; }
        boolean capturada() { return vista() || !nota.isBlank(); }
        /** Almost every bien is one piece: only a lot, or a count that changed, shows a number. */
        boolean conCantidad() { return sistema() != 1 || contado != sistema() || verCantidad; }
        String area() { return producto.getArea() != null && !producto.getArea().isBlank() ? producto.getArea() : SIN_AREA; }
        String categoria() {
            return producto.getCategoriaNombre() != null && !producto.getCategoriaNombre().isBlank()
                ? producto.getCategoriaNombre() : SIN_CATEGORIA;
        }
    }

    private static final String SIN_AREA = "Sin área", SIN_CATEGORIA = "Sin categoría", TODO = "Todo el inventario";

    /** What a row is saved as when nobody looked at it: the record of a
     *  conteo must not say "encontrado" about a bien that was never checked. */
    static final String SIN_REVISAR = "SIN_REVISAR";

    record DraftValues(int contado, EstadoConteo estado, String nota, boolean verificado) {}
    private record Captured(Producto producto, int contado, String estadoConteo, String nota) {}

    @FunctionalInterface
    private interface Exportacion { File generar() throws Exception; }

    private final MovimientoService movimientoService;
    private final Runnable onReconciled;
    private final List<Fila> filas = new ArrayList<>();
    private Dialog<ButtonType> dialog;

    // Paso 1: qué contar
    private final ToggleButton porArea = new ToggleButton("Áreas");
    private final ToggleButton porCategoria = new ToggleButton("Categorías");
    private final TextField buscarGrupo = new TextField();
    private final ListView<String> grupos = new ListView<>();
    /** Vistos and total of each área or category, read by the cells of the list. */
    private final Map<String, int[]> avanceGrupos = new HashMap<>();

    // Paso 2: los bienes de lo elegido
    private List<Fila> alcance = List.of();
    private final Label tituloAlcance = new Label();
    private final TextField buscar = new TextField();
    private final ToggleButton fTodos = new ToggleButton(), fPendientes = new ToggleButton(),
                               fVistos = new ToggleButton(), fIncidencias = new ToggleButton();
    private final ProgressBar progreso = new ProgressBar(0);
    private final Label progresoTexto = new Label();
    private final ObservableList<Fila> visibles = FXCollections.observableArrayList();
    private final ListView<Fila> lista = new ListView<>(visibles);
    private final VBox vacio = new VBox(8);
    private final VBox panelBienes = new VBox(8);
    private final Button btnScan = new Button("Escanear etiqueta");
    private final Button btnFinalizar = new Button("Finalizar conteo");
    private final MenuButton btnExportar = new MenuButton("Exportar");
    /** Nothing to save once it is done or was saved by hand. */
    private boolean guardado = false;

    private ConteoFisicoDialog(MovimientoService movimientoService, Runnable onReconciled) {
        this.movimientoService = movimientoService;
        this.onReconciled = onReconciled;
    }

    public static void show(List<Producto> productos, MovimientoService movimientoService, Runnable onReconciled) {
        new ConteoFisicoDialog(movimientoService, onReconciled).abrir(productos);
    }

    private static void exportar(Dialog<?> dialog, String formato, Exportacion exportacion) {
        Scene scene = dialog.getDialogPane().getScene();
        AppExecutor.submit(() -> {
            try {
                File f = exportacion.generar();
                Platform.runLater(() -> {
                    if (scene != null) DialogUtil.showExportResultDialog(scene, f);
                });
            } catch (Exception ex) {
                log.error("Error al exportar el conteo físico a {}", formato, ex);
                Platform.runLater(() -> {
                    if (scene != null) NotificacionUtil.error(scene, "No se pudo generar el " + formato);
                });
            }
        });
    }

    /** True when {@code target} is {@code control} or inside it. */
    private static boolean dentroDe(Object target, Node... controles) {
        for (Node n = target instanceof Node t ? t : null; n != null; n = n.getParent())
            for (Node c : controles) if (n == c) return true;
        return false;
    }

    /** Where drafts used to live. A registry value holds 8 192 characters at
     *  most, so "Guardar borrador" failed for any conteo of more than about a
     *  hundred bienes; still read once, so a draft saved there is not lost. */
    private static final Preferences DRAFT_PREFS = Preferences.userRoot().node("sibim/conteo/draft");

    private static String draftKey() {
        Usuario u = SessionManager.getCurrentUser();
        return u != null ? u.getId() : "anon";
    }

    /** The draft of the signed-in account: first line the date, second the rows. */
    static java.nio.file.Path archivoBorrador() {
        return java.nio.file.Path.of(System.getProperty("user.home"), ".sibim",
            "conteo-borrador-" + draftKey().replaceAll("[^A-Za-z0-9_-]", "_") + ".txt");
    }

    static String serializar(String id, int contado, String estado, String nota, boolean verificado) {
        String notaEnc = nota != null && !nota.isBlank()
            ? Base64.getEncoder().encodeToString(nota.getBytes(StandardCharsets.UTF_8)) : "";
        return id + '=' + contado + '|' + estado + '|' + notaEnc + '|' + (verificado ? '1' : '0');
    }

    /** id of the bien → its serialized row, in the order they were saved. */
    static Map<String, String> filasCrudas(String raw) {
        Map<String, String> filas = new java.util.LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return filas;
        for (String item : raw.split(";")) {
            int eq = item.indexOf('=');
            if (eq > 0) filas.put(item.substring(0, eq), item);
        }
        return filas;
    }

    /** The draft keeps what was captured for each bien, whatever conteo it came from:
     *  what this window has replaces what it had for those bienes (a bien unmarked here
     *  leaves it), and the rows of bienes not in this window stay where they were. */
    private void saveDraft() throws java.io.IOException {
        String[] previo = leerBorrador();
        Map<String, String> guardadas = filasCrudas(previo != null ? previo[1] : "");
        for (Fila f : filas) {
            String id = f.producto.getId();
            if (f.capturada()) guardadas.put(id, serializar(id, f.contado, f.estado.name(), f.nota, f.vista()));
            else guardadas.remove(id);
        }
        if (guardadas.isEmpty()) {
            java.nio.file.Files.deleteIfExists(archivoBorrador());
            DRAFT_PREFS.remove(draftKey());
            DRAFT_PREFS.remove(draftKey() + ".fecha");
        } else {
            escribirBorrador(LocalDate.now().toString(), guardadas);
        }
    }

    private static void escribirBorrador(String fecha, Map<String, String> filas) throws java.io.IOException {
        java.nio.file.Path f = archivoBorrador();
        java.nio.file.Files.createDirectories(f.getParent());
        java.nio.file.Files.writeString(f, fecha + '\n' + String.join(";", filas.values()), StandardCharsets.UTF_8);
    }

    /** Takes these bienes out of the draft — their conteo was finalized or
     *  started over. What was captured for any other bien stays. */
    private static void clearDraft(java.util.Collection<String> ids) {
        try {
            String[] previo = leerBorrador();
            Map<String, String> filas = filasCrudas(previo != null ? previo[1] : "");
            filas.keySet().removeAll(ids);
            if (filas.isEmpty()) java.nio.file.Files.deleteIfExists(archivoBorrador());
            else escribirBorrador(previo[0], filas);
        } catch (java.io.IOException e) {
            log.warn("No se pudo actualizar el borrador del conteo: {}", e.getMessage());
        }
        DRAFT_PREFS.remove(draftKey());
        DRAFT_PREFS.remove(draftKey() + ".fecha");
    }

    /** {fecha, filas} of the saved draft, or null when there is none. */
    private static String[] leerBorrador() {
        try {
            java.nio.file.Path f = archivoBorrador();
            if (java.nio.file.Files.exists(f)) {
                String[] partes = java.nio.file.Files.readString(f, StandardCharsets.UTF_8).split("\n", 2);
                if (partes.length == 2 && !partes[1].isBlank()) return new String[]{partes[0].trim(), partes[1].trim()};
            }
        } catch (java.io.IOException e) {
            log.warn("No se pudo leer el borrador del conteo: {}", e.getMessage());
        }
        String viejo = DRAFT_PREFS.get(draftKey(), "");
        return viejo.isBlank() ? null : new String[]{DRAFT_PREFS.get(draftKey() + ".fecha", ""), viejo};
    }

    static Map<String, DraftValues> parsearBorrador(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyMap();
        Map<String, DraftValues> map = new HashMap<>();
        for (String item : raw.split(";")) {
            int eq = item.indexOf('=');
            if (eq <= 0) continue;
            String id = item.substring(0, eq);
            String rest = item.substring(eq + 1);
            String[] parts = rest.split("\\|", 4);
            if (parts.length == 0) continue;
            try {
                int count = Integer.parseInt(parts[0]);
                EstadoConteo estado = parts.length > 1 ? parseEstado(parts[1]) : EstadoConteo.ENCONTRADO;
                String nota = "";
                if (parts.length > 2 && !parts[2].isBlank()) {
                    try { nota = new String(Base64.getDecoder().decode(parts[2]), StandardCharsets.UTF_8); }
                    catch (Exception ignored) {
                        log.debug("Could not decode Base64 nota for conteo draft entry", ignored);
                    }
                }
                map.put(id, new DraftValues(count, estado, nota, parts.length > 3 && "1".equals(parts[3].trim())));
            } catch (NumberFormatException ignored) {
                log.debug("Malformed conteo draft entry '{}', skipping", id, ignored);
            }
        }
        return map;
    }

    private static EstadoConteo parseEstado(String s) {
        try { return EstadoConteo.valueOf(s); }
        catch (Exception e) { return EstadoConteo.ENCONTRADO; }
    }

    // ── La ventana ────────────────────────────────────────────────────────────

    private void abrir(List<Producto> productos) {
        for (Producto p : productos) filas.add(new Fila(p));
        retomarBorrador();

        dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Conteo físico");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        // Also on a 1366×768 laptop: never bigger than the screen.
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(1240, pantalla.getWidth() * 0.94),
            Math.min(760, pantalla.getHeight() * 0.88));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2c-clipboard-list-outline", "Conteo físico",
            "Elige qué contar y marca cada bien que tengas a la vista — " + FormatUtils.formatDate(LocalDate.now()),
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        HBox cuerpo = new HBox(16, panelGrupos(), panelDerecho());
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        // The margin goes on an inner box: the dialog stylesheet sets the content's
        // own padding to 0 (the header runs edge to edge), and it won over setPadding.
        VBox debajo = new VBox(cuerpo);
        debajo.setPadding(new Insets(0, 22, 14, 22));
        VBox.setVgrow(debajo, Priority.ALWAYS);
        dialog.getDialogPane().setContent(new VBox(12, header, debajo));

        cargarGrupos();
        mostrarAlcance();
        // Started from Bienes filtered to one área, that área is what gets counted.
        List<String> areas = filas.stream().map(Fila::area).distinct().toList();
        if (areas.size() == 1) grupos.getSelectionModel().select(areas.get(0));

        // Closing never loses what was captured: it stays in the draft and is offered
        // on opening again. (It asked "¿salir de todos modos?", and only on the window's X.)
        dialog.setOnHidden(ev -> {
            if (guardado || filas.stream().noneMatch(Fila::capturada)) return;
            try {
                saveDraft();
                Scene principal = escenaPrincipal();
                if (principal != null) NotificacionUtil.info(principal,
                    "Tu avance del conteo quedó guardado en esta computadora");
            } catch (java.io.IOException ex) {
                log.error("No se pudo guardar el borrador del conteo al cerrar", ex);
                Scene principal = escenaPrincipal();
                if (principal != null) NotificacionUtil.error(principal, "No se pudo guardar el avance del conteo: " + ex.getMessage());
            }
        });
        // A handheld scanner works right here, without opening "Escanear": each
        // label read marks its bien as seen.
        dialog.setOnShown(ev -> com.sibim.util.BarcodeScanner.attach(dialog.getDialogPane().getScene(), leido -> {
            String code = com.sibim.util.QrUtils.codigoLeido(leido);
            if (code.isBlank()) return;
            String aviso = marcarVisto(code);
            if (aviso != null) NotificacionUtil.advertencia(dialog.getDialogPane().getScene(), aviso);
        }));
        Platform.runLater(grupos::requestFocus);
        dialog.showAndWait();
    }

    private static Scene escenaPrincipal() {
        return com.sibim.MainApp.getPrimaryStage() != null ? com.sibim.MainApp.getPrimaryStage().getScene() : null;
    }

    /** Only what the draft has for the bienes of this window: the rows of another
     *  conteo are neither offered nor thrown away. */
    private void retomarBorrador() {
        String[] borrador = leerBorrador();
        if (borrador == null) return;
        Map<String, DraftValues> guardadas = new HashMap<>(parsearBorrador(borrador[1]));
        Set<String> ids = filas.stream().map(f -> f.producto.getId()).collect(Collectors.toSet());
        guardadas.keySet().retainAll(ids);
        if (guardadas.isEmpty()) return;
        boolean retomar = ConfirmacionUtil.confirmar("Reanudar borrador",
            "Hay un borrador guardado el " + borrador[0] + " con " + FormatUtils.plural(guardadas.size(), "bien", "bienes") + " de este conteo.\n"
            + "¿Deseas retomarlo? (Cancelar = empezar desde cero)");
        if (!retomar) { clearDraft(ids); return; }
        for (Fila f : filas) {
            DraftValues dv = guardadas.get(f.producto.getId());
            if (dv == null) continue;
            f.contado = dv.contado();
            f.estado = dv.estado();
            f.nota = dv.nota() != null ? dv.nota() : "";
            f.visto = vista(dv.verificado(), dv.contado(), f.sistema(), dv.estado());
            f.verNota = !f.nota.isBlank();
        }
    }

    // ── Paso 1: qué contar ────────────────────────────────────────────────────

    private VBox panelGrupos() {
        ToggleGroup tipo = new ToggleGroup();
        for (ToggleButton t : List.of(porArea, porCategoria)) {
            t.setToggleGroup(tipo);
            t.getStyleClass().add("filter-chip");
        }
        porArea.setSelected(true);
        tipo.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) { a.setSelected(true); return; }
            cargarGrupos();
        });
        HBox tipos = new HBox(6, porArea, porCategoria);
        tipos.setAlignment(Pos.CENTER_LEFT);

        buscarGrupo.setPromptText("Buscar…");
        buscarGrupo.getStyleClass().add("form-input");
        buscarGrupo.textProperty().addListener((o, a, b) -> cargarGrupos());

        grupos.getStyleClass().add("trf-areas");
        grupos.setCellFactory(lv -> new CeldaGrupo(lv));
        grupos.setPlaceholder(new Label("Nada coincide con lo escrito"));
        VBox.setVgrow(grupos, Priority.ALWAYS);
        grupos.getSelectionModel().selectedItemProperty().addListener((o, a, g) -> { if (g != null) elegir(g); });

        VBox panel = new VBox(8, paso(1, "Elige qué contar"), tipos, buscarGrupo, grupos);
        panel.getStyleClass().add("trf-panel");
        panel.setPrefWidth(340);
        panel.setMinWidth(280);
        return panel;
    }

    /** The áreas in the organigrama's order, then any other; or the categories. */
    private void cargarGrupos() {
        String elegido = grupos.getSelectionModel().getSelectedItem();
        List<String> nombres;
        if (porArea.isSelected()) {
            Set<String> presentes = filas.stream().map(Fila::area).collect(Collectors.toCollection(LinkedHashSet::new));
            List<String> orden = new ArrayList<>(Areas.getAllAreaNames().stream().filter(presentes::contains).toList());
            presentes.stream().filter(a -> !orden.contains(a)).sorted().forEach(orden::add);
            nombres = orden;
        } else {
            nombres = filas.stream().map(Fila::categoria).distinct().sorted().toList();
        }
        List<String> todos = new ArrayList<>();
        if (filas.stream().map(porArea.isSelected() ? Fila::area : Fila::categoria).distinct().count() > 1) todos.add(TODO);
        todos.addAll(nombres);
        grupos.getItems().setAll(DialogUtil.coincidencias(todos, s -> s, buscarGrupo.getText()));
        actualizarGrupos();
        if (elegido != null && grupos.getItems().contains(elegido)) grupos.getSelectionModel().select(elegido);
    }

    private List<Fila> filasDe(String grupo) {
        if (TODO.equals(grupo)) return filas;
        return filas.stream().filter(f -> grupo.equals(porArea.isSelected() ? f.area() : f.categoria())).toList();
    }

    private void actualizarGrupos() {
        avanceGrupos.clear();
        for (Fila f : filas) {
            sumar(TODO, f);
            sumar(porArea.isSelected() ? f.area() : f.categoria(), f);
        }
        grupos.refresh();
    }

    private void sumar(String grupo, Fila f) {
        int[] avance = avanceGrupos.computeIfAbsent(grupo, k -> new int[2]);
        if (f.vista()) avance[0]++;
        avance[1]++;
    }

    private void elegir(String grupo) {
        alcance = filasDe(grupo);
        tituloAlcance.setText(grupo);
        buscar.clear();
        fTodos.setSelected(true);
        mostrarAlcance();
        refiltrar();
        actualizarAvance();
        if (!visibles.isEmpty()) lista.scrollTo(0);
    }

    private void mostrarAlcance() {
        boolean hay = !alcance.isEmpty();
        ver(vacio, !hay);
        ver(panelBienes, hay);
        btnScan.setDisable(!hay);
        btnExportar.setDisable(!hay);
        actualizarAvance();
    }

    // ── Paso 2: los bienes ────────────────────────────────────────────────────

    private VBox panelDerecho() {
        FontIcon iconoVacio = new FontIcon("mdi2c-clipboard-check-outline");
        iconoVacio.getStyleClass().add("empty-icon-lg");
        Label tituloVacio = new Label("Elige qué vas a contar");
        tituloVacio.getStyleClass().add("empty-state-msg");
        Label pistaVacio = new Label("Un área a la vez es lo más manejable; tu avance se guarda aunque cierres la ventana");
        pistaVacio.getStyleClass().add("empty-state-hint");
        vacio.getChildren().setAll(iconoVacio, tituloVacio, pistaVacio);
        vacio.setAlignment(Pos.CENTER);
        VBox.setVgrow(vacio, Priority.ALWAYS);

        tituloAlcance.getStyleClass().add("trf-paso-titulo");
        tituloAlcance.setMinWidth(0);

        buscar.setPromptText("Buscar por nombre, código, número anterior, serie o resguardante…");
        buscar.getStyleClass().add("form-input");
        buscar.textProperty().addListener((o, a, b) -> refiltrar());
        HBox.setHgrow(buscar, Priority.ALWAYS);
        btnScan.setGraphic(new FontIcon("mdi2q-qrcode-scan"));
        btnScan.getStyleClass().add("btn-secondary");
        btnScan.setMinWidth(Region.USE_PREF_SIZE);
        btnScan.setTooltip(new Tooltip("Con una pistola lectora ni hace falta abrirlo: basta escanear cada etiqueta"));
        btnScan.setOnAction(e -> showScanDialog());
        Button btnHistorial = new Button("Historial");
        btnHistorial.setGraphic(new FontIcon("mdi2h-history"));
        btnHistorial.getStyleClass().add("btn-ghost");
        btnHistorial.setMinWidth(Region.USE_PREF_SIZE);
        btnHistorial.setOnAction(e -> {
            if (SessionManager.isAdmin()) HistorialConteosDialog.show(dialog.getDialogPane().getScene());
            else NotificacionUtil.info(dialog.getDialogPane().getScene(), "Solo el administrador puede ver el historial de conteos");
        });
        HBox barra = new HBox(10, buscar, btnScan);
        barra.setAlignment(Pos.CENTER_LEFT);

        // Which rows to show, as tabs with their count.
        ToggleGroup vista = new ToggleGroup();
        for (ToggleButton t : List.of(fTodos, fPendientes, fVistos, fIncidencias)) {
            t.setToggleGroup(vista);
            t.getStyleClass().add("filter-chip");
            t.setMinWidth(Region.USE_PREF_SIZE);
        }
        fTodos.setSelected(true);
        // A tab can't be "unselected" into showing nothing.
        vista.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) { a.setSelected(true); return; }
            refiltrar();
        });
        fPendientes.setTooltip(new Tooltip("Los bienes que todavía nadie marcó como vistos"));
        fIncidencias.setTooltip(new Tooltip("Cantidad distinta, mal estado, en otra área o faltante"));
        progreso.setPrefWidth(200);
        progresoTexto.getStyleClass().add("muted-sm");
        progresoTexto.setMinWidth(Region.USE_PREF_SIZE);
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox pestanas = new HBox(6, fTodos, fPendientes, fVistos, fIncidencias, hueco, progreso, progresoTexto);
        pestanas.setAlignment(Pos.CENTER_LEFT);

        lista.getStyleClass().add("cnt-lista");
        lista.setCellFactory(lv -> new CeldaBien(lv));
        lista.setPlaceholder(new Label("Ningún bien coincide"));
        VBox.setVgrow(lista, Priority.ALWAYS);
        // Space says "here it is" and goes on to the next, without touching the mouse.
        lista.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() != KeyCode.SPACE || !(e.getTarget() instanceof ListView<?>)) return;
            Fila f = lista.getSelectionModel().getSelectedItem();
            if (f == null) return;
            alternarVisto(f);
            lista.getSelectionModel().selectNext();
            e.consume();
        });

        panelBienes.getChildren().setAll(tituloAlcance, barra, pestanas, lista);
        VBox.setVgrow(panelBienes, Priority.ALWAYS);

        // Up here so the past conteos are at hand before choosing what to count.
        Region huecoPaso = new Region();
        HBox.setHgrow(huecoPaso, Priority.ALWAYS);
        HBox paso2 = paso(2, "Marca lo que tengas a la vista");
        paso2.setPadding(Insets.EMPTY);
        paso2.getChildren().addAll(huecoPaso, btnHistorial);
        VBox panel = new VBox(8, paso2, vacio, panelBienes, new Separator(), pie());
        HBox.setHgrow(panel, Priority.ALWAYS);
        return panel;
    }

    private HBox pie() {
        Label nota = new Label("Un clic en el bien lo marca como visto. Tu avance se guarda al cerrar.");
        nota.getStyleClass().add("muted-sm");
        nota.setWrapText(true);
        nota.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nota, Priority.ALWAYS);

        ReporteConteoService reporteService = new ReporteConteoService();
        String tituloConteo = "Conteo del " + FormatUtils.formatDate(LocalDate.now());
        Usuario userForPdf = SessionManager.getCurrentUser();
        String userNameForPdf = userForPdf != null ? userForPdf.getNombre() : "Sistema";
        MenuItem miPdf = new MenuItem("PDF");
        miPdf.setGraphic(new FontIcon("mdi2f-file-pdf-box"));
        MenuItem miExcel = new MenuItem("Excel");
        miExcel.setGraphic(new FontIcon("mdi2f-file-excel"));
        btnExportar.setGraphic(new FontIcon("mdi2d-download-outline"));
        btnExportar.getItems().setAll(miPdf, miExcel);
        btnExportar.getStyleClass().add("btn-secondary");
        btnExportar.setMinWidth(Region.USE_PREF_SIZE);
        btnExportar.setTooltip(new Tooltip("Exporta lo elegido como está ahora, sin finalizar el conteo"));
        miPdf.setOnAction(ev -> { List<ConteoItem> c = capturaActual(); exportar(dialog, "PDF",
            () -> reporteService.exportConteoPdf(tituloConteo, userNameForPdf, c)); });
        miExcel.setOnAction(ev -> { List<ConteoItem> c = capturaActual(); exportar(dialog, "Excel",
            () -> reporteService.exportConteoExcel(tituloConteo, userNameForPdf, c)); });

        btnFinalizar.setGraphic(new FontIcon("mdi2c-check-circle-outline"));
        btnFinalizar.getStyleClass().add("btn-primary");
        btnFinalizar.setMinWidth(Region.USE_PREF_SIZE);
        btnFinalizar.setOnAction(e -> finalizar());

        HBox pie = new HBox(10, nota, btnExportar, btnFinalizar);
        pie.setAlignment(Pos.CENTER_LEFT);
        return pie;
    }

    /** "①  Elige qué contar": the same steps as the transfer board. */
    private static HBox paso(int numero, String texto) {
        return TransferenciaArrastreDialog.paso(numero, texto);
    }

    private static void ver(Node n, boolean visible) {
        n.setVisible(visible);
        n.setManaged(visible);
    }

    private void refiltrar() {
        String q = buscar.getText() == null ? "" : buscar.getText().trim().toLowerCase();
        visibles.setAll(alcance.stream().filter(f -> coincide(f, q)
            && (!fIncidencias.isSelected() || f.incidencia())
            && (!fPendientes.isSelected() || !f.vista())
            && (!fVistos.isSelected() || f.vista())).toList());
    }

    private static boolean coincide(Fila f, String q) {
        if (q.isBlank()) return true;
        Producto p = f.producto;
        return contiene(p.getNombre(), q) || contiene(p.getCodigo(), q)
            || contiene(p.getNumeroSerie(), q) || contiene(p.getResguardante(), q)
            || com.sibim.util.ProductoUtils.anteriorContiene(p, q);
    }

    private static boolean contiene(String texto, String q) {
        return texto != null && texto.toLowerCase().contains(q);
    }

    private void actualizarAvance() {
        int total = alcance.size();
        int n = (int) alcance.stream().filter(Fila::vista).count();
        int inc = (int) alcance.stream().filter(Fila::incidencia).count();
        progresoTexto.setText(n + " de " + total + " vistos" + (n < total ? " · faltan " + (total - n) : " · completo"));
        progreso.setProgress(total > 0 ? (double) n / total : 0);
        fTodos.setText("Todos (" + total + ")");
        fPendientes.setText("Pendientes (" + (total - n) + ")");
        fVistos.setText("Vistos (" + n + ")");
        fIncidencias.setText("Con incidencia (" + inc + ")");
        // A record of a conteo where nobody looked at anything says nothing.
        btnFinalizar.setDisable(n == 0);
    }

    /** What was captured changed on a row: the counts, and the tab it belongs in. */
    private void alCambiar(boolean cambiaDePestana) {
        actualizarAvance();
        actualizarGrupos();
        // After the event that changed it: the list is not replaced under its own cell.
        if (cambiaDePestana && !fTodos.isSelected()) Platform.runLater(this::refiltrar);
    }

    /** A row with a difference or an estado stays marked: its ajuste is applied on
     *  finalizing. Put the count and the estado back first to unmark it. */
    private void alternarVisto(Fila f) {
        if (f.incidencia()) return;
        f.visto = !f.visto;
        lista.refresh();
        alCambiar(true);
    }

    private void cambiarEstado(Fila f, EstadoConteo estado) {
        f.contado = contadoAlCambiarEstado(f.estado, estado, f.contado, f.sistema());
        f.estado = estado;
        if (estado != EstadoConteo.ENCONTRADO) f.visto = true;
        lista.refresh();
        alCambiar(true);
    }

    private List<ConteoItem> capturaActual() {
        return alcance.stream().map(f -> {
            ConteoItem ci = new ConteoItem();
            ci.setProductoId(f.producto.getId());
            ci.setProductoNombre(f.producto.getNombre());
            ci.setProductoCodigo(f.producto.getCodigo());
            ci.setArea(f.producto.getArea());
            ci.setStockSistema(f.sistema());
            ci.setStockContado(f.contado);
            ci.setEstadoConteo(estadoGuardado(f.vista(), f.estado.name()));
            ci.setNota(f.nota);
            return ci;
        }).toList();
    }

    // ── Finalizar ─────────────────────────────────────────────────────────────

    private void finalizar() {
        btnFinalizar.setDisable(true);
        if (!showResumen(alcance, tituloAlcance.getText())) {
            btnFinalizar.setDisable(false);
            return;
        }
        btnFinalizar.setText("Guardando...");
        btnFinalizar.setGraphic(new FontIcon("mdi2l-loading"));
        lista.setDisable(true);
        grupos.setDisable(true);

        // Snapshot on FX thread — the rows can't change under the background thread
        List<Captured> snapshot = new ArrayList<>();
        for (Fila f : alcance)
            snapshot.add(new Captured(f.producto, f.contado, estadoGuardado(f.vista(), f.estado.name()), f.nota));
        String motivo = "Conteo físico del " + FormatUtils.formatDate(LocalDate.now());
        Usuario currentUser = SessionManager.getCurrentUser();

        AppExecutor.submit(() -> {
            int ok = 0;
            List<String> fallidos = new ArrayList<>();
            List<ConteoItem> items = new ArrayList<>();

            for (Captured r : snapshot) {
                boolean esDiscrepancia = r.contado() != r.producto().getStockActual();
                boolean ajustado = false;
                if (esDiscrepancia) {
                    try {
                        movimientoService.registrarAjusteVerificado(r.producto().getId(),
                            r.contado(), motivo, "Conteo físico", r.producto().getStockActual());
                        ajustado = true;
                        ok++;
                    } catch (Exception ex) {
                        fallidos.add(r.producto().getNombre());
                        log.error("No se pudo aplicar el ajuste de conteo físico para '{}' (id={})",
                            r.producto().getNombre(), r.producto().getId(), ex);
                    }
                }
                ConteoItem item = new ConteoItem();
                item.setProductoId(r.producto().getId());
                item.setProductoNombre(r.producto().getNombre());
                item.setProductoCodigo(r.producto().getCodigo());
                item.setArea(r.producto().getArea());
                item.setStockSistema(r.producto().getStockActual());
                item.setStockContado(r.contado());
                item.setAjustado(ajustado);
                item.setEstadoConteo(r.estadoConteo());
                item.setNota(r.nota().isBlank() ? null : r.nota());
                items.add(item);
            }

            long totalDiscrepancias = items.stream()
                .filter(i -> i.getStockContado() != i.getStockSistema()).count();
            long sinRevisar = items.stream().filter(i -> SIN_REVISAR.equals(i.getEstadoConteo())).count();

            ConteoFisico conteo = new ConteoFisico();
            if (currentUser != null) {
                conteo.setUsuarioId(currentUser.getId());
                conteo.setUsuarioNombre(currentUser.getNombre());
            } else {
                conteo.setUsuarioNombre("Sistema");
            }
            conteo.setTotalContados(items.size());
            conteo.setTotalDiscrepancias((int) totalDiscrepancias);
            conteo.setItems(items);

            boolean saved = true;
            try {
                new ConteoRepository().guardar(conteo);
            } catch (Exception ex) {
                saved = false;
                log.error("No se pudo guardar el registro del conteo físico", ex);
            }

            int okFinal = ok;
            List<String> fallidosFinal = List.copyOf(fallidos);
            boolean savedFinal = saved;
            Platform.runLater(() -> {
                String base;
                if (totalDiscrepancias == 0) {
                    base = "Conteo guardado — sin diferencias en " + (items.size() - sinRevisar)
                        + (items.size() - sinRevisar == 1 ? " bien revisado" : " bienes revisados");
                } else if (fallidosFinal.isEmpty()) {
                    base = FormatUtils.plural(okFinal, "ajuste aplicado", "ajustes aplicados") + " correctamente";
                } else {
                    String nombres = fallidosFinal.stream().limit(3)
                        .collect(Collectors.joining(", "))
                        + (fallidosFinal.size() > 3 ? "…" : "");
                    base = FormatUtils.plural(okFinal, "ajuste aplicado", "ajustes aplicados") + ", " + fallidosFinal.size()
                        + " fallaron: " + nombres;
                }
                // A conteo closed halfway is not a clean inventory: say how many are left.
                if (sinRevisar > 0) base += " — " + sinRevisar + " quedaron sin revisar";
                if (!savedFinal) base += " (no se pudo guardar el registro del conteo)";
                Scene scene = dialog.getDialogPane().getScene();
                if (scene != null) {
                    if (!fallidosFinal.isEmpty() || !savedFinal || sinRevisar > 0) NotificacionUtil.advertencia(scene, base);
                    else if (totalDiscrepancias == 0) NotificacionUtil.exitoConteo(scene, items.size());
                    else NotificacionUtil.exito(scene, base);
                }
                // What is done leaves the draft; what was captured in other áreas stays in it.
                Set<String> hechos = alcance.stream().map(f -> f.producto.getId()).collect(Collectors.toSet());
                filas.removeIf(f -> hechos.contains(f.producto.getId()));
                clearDraft(hechos);
                try {
                    if (filas.stream().anyMatch(Fila::capturada)) saveDraft();
                } catch (java.io.IOException ex) {
                    log.error("No se pudo guardar lo capturado en otras áreas", ex);
                }
                guardado = true;
                if (onReconciled != null) onReconciled.run();
                dialog.close();
            });
        });
    }

    private static boolean showResumen(List<Fila> filas, String alcance) {
        long concordantes = filas.stream().filter(f -> f.vista() && !f.incidencia()).count();
        long conDiff    = filas.stream().filter(f -> f.contado != f.sistema()).count();
        long malEstado  = filas.stream().filter(f -> f.estado == EstadoConteo.MAL_ESTADO).count();
        long otraArea   = filas.stream().filter(f -> f.estado == EstadoConteo.EN_OTRA_AREA).count();
        long faltante   = filas.stream().filter(f -> f.estado == EstadoConteo.FALTANTE).count();
        long sinRevisar = filas.stream().filter(f -> !f.vista()).count();

        ButtonType btnConfirmar = new ButtonType("Confirmar y finalizar", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnVolver    = new ButtonType("Volver",                ButtonBar.ButtonData.CANCEL_CLOSE);

        Dialog<ButtonType> dlg = new Dialog<>();
        DialogUtil.applyOwner(dlg);
        dlg.getDialogPane().getButtonTypes().addAll(btnConfirmar, btnVolver);
        dlg.getDialogPane().setPrefWidth(440);
        DialogUtil.applyStylesheet(dlg.getDialogPane());

        HBox hdr = DialogUtil.gradientHeader("mdi2c-clipboard-check-outline", "Resumen del conteo",
            alcance, AppColors.PRIMARY, AppColors.PRIMARY_D);

        VBox stats = new VBox(8);
        stats.setPadding(new Insets(16, 22, 20, 22));
        stats.getChildren().addAll(
            summaryRow("Vistos y sin novedad", concordantes, "text-ok"),
            summaryRow("Con diferencias de cantidad", conDiff,    conDiff    > 0 ? "text-warn" : "text-ok"),
            summaryRow("Mal estado",               malEstado,  malEstado  > 0 ? "text-warn" : "muted"),
            summaryRow("En otra área",             otraArea,   otraArea   > 0 ? "text-warn" : "muted"),
            summaryRow("Faltante",                 faltante,   faltante   > 0 ? "text-warn" : "muted")
        );
        if (sinRevisar > 0) {
            stats.getChildren().add(summaryRow("Sin revisar", sinRevisar, "text-warn"));
            Label aviso = new Label((sinRevisar == 1 ? "El bien que no marcaste como visto quedará en el "
                : "Los " + sinRevisar + " bienes que no marcaste como vistos quedarán en el ")
                + "registro como \"sin revisar\", no como encontrados. Si cierras la ventana puedes seguir después.");
            aviso.getStyleClass().add("muted-sm");
            aviso.setWrapText(true);
            stats.getChildren().add(aviso);
        }
        if (conDiff > 0) {
            Label note = new Label((conDiff == 1 ? "Se registrará 1 ajuste" : "Se registrarán " + conDiff + " ajustes") + " de cantidad al confirmar.");
            note.getStyleClass().add("muted-sm");
            note.setWrapText(true);
            stats.getChildren().add(note);
        }

        dlg.getDialogPane().setContent(new VBox(hdr, stats));
        dlg.getDialogPane().lookupButton(btnConfirmar).getStyleClass().add("dialog-ok-btn");

        return dlg.showAndWait().filter(b -> b == btnConfirmar).isPresent();
    }

    private static HBox summaryRow(String label, long count, String countStyle) {
        HBox row = new HBox();
        row.setPadding(new Insets(2, 0, 2, 0));
        Label lbl = new Label(label);
        lbl.getStyleClass().add("dlg-detail-value");
        HBox.setHgrow(lbl, Priority.ALWAYS);
        lbl.setMaxWidth(Double.MAX_VALUE);
        Label val = new Label(String.valueOf(count));
        val.getStyleClass().addAll("dlg-detail-value", countStyle);
        row.getChildren().addAll(lbl, val);
        return row;
    }

    // ── Celdas ────────────────────────────────────────────────────────────────

    /** An área or category: its name and how far its count has got. */
    private final class CeldaGrupo extends ListCell<String> {
        private final FontIcon icono = new FontIcon();
        private final StackPane fondo = new StackPane(icono);
        private final Label nombre = new Label();
        private final Label cuenta = new Label();
        private final ProgressBar barra = new ProgressBar(0);
        private final HBox caja;

        CeldaGrupo(ListView<String> lv) {
            icono.getStyleClass().add("trf-tarjeta-icono");
            fondo.getStyleClass().addAll("trf-tarjeta-icono-fondo", "trf-area-icono");
            fondo.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            nombre.getStyleClass().add("org-child-label");
            nombre.setTextOverrun(OverrunStyle.ELLIPSIS);
            nombre.setMaxWidth(Double.MAX_VALUE);
            barra.setMaxWidth(Double.MAX_VALUE);
            barra.getStyleClass().add("cnt-barra");
            VBox textos = new VBox(3, nombre, barra);
            textos.setMinWidth(0);
            textos.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(textos, Priority.ALWAYS);
            cuenta.getStyleClass().add("org-area-count");
            cuenta.setMinWidth(Region.USE_PREF_SIZE);
            caja = new HBox(10, fondo, textos, cuenta);
            caja.setAlignment(Pos.CENTER_LEFT);
            caja.maxWidthProperty().bind(lv.widthProperty().subtract(30));
            caja.setMinWidth(0);
        }

        @Override protected void updateItem(String grupo, boolean empty) {
            super.updateItem(grupo, empty);
            setText(null);
            if (empty || grupo == null) { setGraphic(null); return; }
            icono.setIconLiteral(TODO.equals(grupo) ? "mdi2p-package-variant"
                : porArea.isSelected() ? FiltroArea.icono(grupo).getIconLiteral() : "mdi2t-tag-outline");
            nombre.setText(grupo);
            nombre.setTooltip(new Tooltip(grupo));
            int[] avance = avanceGrupos.getOrDefault(grupo, new int[2]);
            cuenta.setText(avance[0] > 0 ? avance[0] + " de " + avance[1] : String.valueOf(avance[1]));
            barra.setProgress(avance[1] > 0 ? (double) avance[0] / avance[1] : 0);
            ver(barra, avance[0] > 0);
            setGraphic(caja);
        }
    }

    /** A bien: a click says it is there; everything else only appears when it applies. */
    private final class CeldaBien extends ListCell<Fila> {
        private final CheckBox visto = new CheckBox();
        private final Label codigo = new Label();
        private final Label nombre = new Label();
        private final Label detalle = new Label();
        private final Label cantidadTexto = new Label();
        private final Spinner<Integer> contado = new Spinner<>(0, Integer.MAX_VALUE, 0);
        private final Label diferencia = new Label();
        private final HBox cantidad = new HBox(6, cantidadTexto, contado, diferencia);
        private final Label estado = new Label();
        private final MenuButton incidencia = new MenuButton("Incidencia");
        private final MenuItem miSinIncidencia = new MenuItem("Sin incidencia");
        private final MenuItem miCantidad = new MenuItem("Cambiar la cantidad");
        private final MenuItem miNota = new MenuItem("Agregar una nota");
        private final TextField nota = new TextField();
        private final HBox fila;
        private final VBox caja;
        /** Filling the controls for a row is not the user changing them. */
        private boolean llenando = false;

        CeldaBien(ListView<Fila> lv) {
            visto.setAccessibleText("Visto");
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

            cantidadTexto.getStyleClass().add("org-table-sub");
            cantidadTexto.setMinWidth(Region.USE_PREF_SIZE);
            contado.setEditable(true);
            contado.setPrefWidth(92);
            contado.setMinWidth(92);
            DialogUtil.commitOnFocusLoss(contado);
            diferencia.getStyleClass().add("cnt-diferencia");
            diferencia.setMinWidth(Region.USE_PREF_SIZE);
            cantidad.setAlignment(Pos.CENTER_LEFT);
            cantidad.setMinWidth(Region.USE_PREF_SIZE);

            estado.setMinWidth(Region.USE_PREF_SIZE);
            incidencia.setGraphic(new FontIcon("mdi2a-alert-circle-outline"));
            incidencia.getStyleClass().add("btn-ghost");
            incidencia.setMinWidth(Region.USE_PREF_SIZE);
            MenuItem miMal = new MenuItem(EstadoConteo.MAL_ESTADO.toString());
            MenuItem miOtra = new MenuItem(EstadoConteo.EN_OTRA_AREA.toString());
            MenuItem miFalta = new MenuItem(EstadoConteo.FALTANTE.toString());
            miMal.setOnAction(e -> cambiarEstado(getItem(), EstadoConteo.MAL_ESTADO));
            miOtra.setOnAction(e -> cambiarEstado(getItem(), EstadoConteo.EN_OTRA_AREA));
            miFalta.setOnAction(e -> cambiarEstado(getItem(), EstadoConteo.FALTANTE));
            miSinIncidencia.setOnAction(e -> {
                Fila f = getItem();
                f.contado = f.sistema();
                cambiarEstado(f, EstadoConteo.ENCONTRADO);
            });
            miCantidad.setOnAction(e -> {
                getItem().verCantidad = true;
                lista.refresh();
            });
            miNota.setOnAction(e -> {
                getItem().verNota = true;
                lista.refresh();
                Platform.runLater(nota::requestFocus);
            });
            incidencia.getItems().addAll(miMal, miOtra, miFalta, miSinIncidencia, new SeparatorMenuItem(), miCantidad, miNota);

            fila = new HBox(10, visto, textos, cantidad, estado, incidencia);
            fila.setAlignment(Pos.CENTER_LEFT);
            fila.getStyleClass().add("cnt-fila");
            nota.setPromptText("Observación…");
            nota.getStyleClass().add("form-input");
            VBox.setMargin(nota, new Insets(0, 12, 8, 42));
            caja = new VBox(fila, nota);
            caja.maxWidthProperty().bind(lv.widthProperty().subtract(30));
            caja.setMinWidth(0);

            visto.setOnAction(e -> {
                Fila f = getItem();
                if (f == null) return;
                alternarVisto(f);
            });
            // A click anywhere on the row marks it, or unmarks it: the box is a 16-pixel target.
            fila.setOnMouseClicked(e -> {
                Fila f = getItem();
                if (f == null || dentroDe(e.getTarget(), visto, contado, incidencia)) return;
                alternarVisto(f);
            });
            contado.valueProperty().addListener((o, a, v) -> {
                Fila f = getItem();
                if (llenando || f == null || v == null) return;
                f.contado = v;
                f.visto = true;
                pintar(f);
                alCambiar(true);
            });
            nota.textProperty().addListener((o, a, v) -> {
                Fila f = getItem();
                if (llenando || f == null) return;
                f.nota = v == null ? "" : v;
                alCambiar(false);
            });
        }

        @Override protected void updateItem(Fila f, boolean empty) {
            super.updateItem(f, empty);
            setText(null);
            if (empty || f == null) { setGraphic(null); return; }
            llenando = true;
            Producto p = f.producto;
            codigo.setText(p.getCodigo());
            nombre.setText(p.getNombre());
            nombre.setTooltip(new Tooltip(p.getNombre()));
            List<String> partes = new ArrayList<>();
            partes.add(p.getResguardante() != null && !p.getResguardante().isBlank() ? p.getResguardante() : "Sin resguardante");
            if (p.getNumeroSerie() != null && !p.getNumeroSerie().isBlank()) partes.add("Serie " + p.getNumeroSerie());
            if (TODO.equals(tituloAlcance.getText()) || !porArea.isSelected()) partes.add(f.area());
            detalle.setText(String.join(" · ", partes));
            detalle.setTooltip(new Tooltip(detalle.getText()));
            contado.getValueFactory().setValue(f.contado);
            nota.setText(f.nota);
            pintar(f);
            llenando = false;
            setGraphic(caja);
        }

        private void pintar(Fila f) {
            boolean v = f.vista();
            visto.setSelected(v);
            // Marked because of a difference or an estado: it can't be unticked, see alternarVisto.
            visto.setDisable(f.incidencia());
            cantidadTexto.setText("Hay " + f.sistema() + " · contados");
            ver(cantidad, f.conCantidad());
            int dif = f.contado - f.sistema();
            diferencia.setText(dif == 0 ? "" : dif > 0 ? "+" + dif : String.valueOf(dif));
            ver(diferencia, dif != 0);
            boolean verNota = f.verNota || !f.nota.isBlank();
            ver(nota, verNota);
            miCantidad.setVisible(!f.conCantidad());
            miSinIncidencia.setVisible(f.incidencia());
            miNota.setVisible(!verNota);

            estado.getStyleClass().setAll("cell-badge", !v ? "cell-badge-blue"
                : f.estado == EstadoConteo.FALTANTE ? "cell-badge-danger"
                : f.incidencia() ? "cell-badge-warning" : "cell-badge-success");
            estado.setText(!v ? "Pendiente" : f.estado != EstadoConteo.ENCONTRADO ? f.estado.toString()
                : dif != 0 ? "Diferencia" : "Visto");
            fila.getStyleClass().removeAll("cnt-fila-vista", "cnt-fila-incidencia");
            if (f.incidencia()) fila.getStyleClass().add("cnt-fila-incidencia");
            else if (v) fila.getStyleClass().add("cnt-fila-vista");
        }
    }

    // ── Escanear ──────────────────────────────────────────────────────────────

    /** The row of the bien with this código or, failing that, with this old inventory
     *  number — the label still stuck on the bienes. Old numbers repeat (34 on 3-nov-2026):
     *  then the first of what is being counted not seen yet, as there are that many labels. */
    private Fila filaDeCodigo(String code) {
        Fila exacta = filas.stream().filter(f -> code.equalsIgnoreCase(f.producto.getCodigo())).findFirst().orElse(null);
        if (exacta != null) return exacta;
        List<Fila> anteriores = filas.stream()
            .filter(f -> com.sibim.util.ProductoUtils.esInventarioAnterior(f.producto, code)).toList();
        return anteriores.stream().filter(f -> alcance.contains(f) && !f.visto).findFirst()
            .or(() -> anteriores.stream().filter(alcance::contains).findFirst())
            .orElse(anteriores.isEmpty() ? null : anteriores.get(0));
    }

    /** The row {@link #marcarVisto} marked last. */
    private Fila ultimaMarcada;

    /** Marks the bien with this código (or old number) as seen and brings its row into
     *  view. With nothing chosen yet, the first label read chooses its área.
     *  @return null when it was marked, else why it wasn't */
    private String marcarVisto(String code) {
        Fila f = filaDeCodigo(code);
        if (f == null) return "\"" + code + "\" no está en este conteo: no es un código ni un número anterior registrado, o ya se finalizó. Anótalo aparte.";
        if (alcance.isEmpty()) {
            porArea.setSelected(true);
            grupos.getSelectionModel().select(f.area());
        }
        if (!alcance.contains(f))
            return "\"" + code + "\" es de " + f.area() + ", no de lo que estás contando: márcalo en " + tituloAlcance.getText() + " como «En otra área» si no le corresponde estar ahí.";
        f.visto = true;
        ultimaMarcada = f;
        if (!visibles.contains(f)) { buscar.clear(); fTodos.setSelected(true); refiltrar(); }
        lista.refresh();
        alCambiar(true);
        lista.getSelectionModel().select(f);
        lista.scrollTo(Math.max(0, visibles.indexOf(f) - 3));
        return null;
    }

    private static com.google.zxing.LuminanceSource bufferedImageToLuminance(java.awt.image.BufferedImage image) {
        int width = image.getWidth(), height = image.getHeight();
        byte[] luminances = new byte[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                luminances[y * width + x] = (byte) ((r * 299 + g * 587 + b * 114 + 500) / 1000);
            }
        }
        return new com.google.zxing.LuminanceSource(width, height) {
            @Override public byte[] getRow(int y, byte[] row) {
                if (row == null || row.length < width) row = new byte[width];
                System.arraycopy(luminances, y * width, row, 0, width);
                return row;
            }
            @Override public byte[] getMatrix() { return luminances; }
        };
    }

    private void showScanDialog() {
        Dialog<ButtonType> scanDlg = new Dialog<>();
        DialogUtil.applyOwner(scanDlg);
        scanDlg.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);
        scanDlg.getDialogPane().setPrefWidth(420);
        DialogUtil.applyStylesheet(scanDlg.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2q-qrcode-scan", "Escanear código",
            "Escribe o pega el código, o carga una imagen con QR/código de barras",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        TextField codeField = new TextField();
        codeField.setPromptText("Código del bien o su número anterior");
        codeField.setMaxWidth(Double.MAX_VALUE);

        Button btnCargar = new Button("Cargar imagen…");
        btnCargar.setGraphic(new org.kordamp.ikonli.javafx.FontIcon("mdi2f-file-image-outline"));
        btnCargar.getStyleClass().add("btn-secondary");
        btnCargar.setOnAction(ev -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Seleccionar imagen con QR o código de barras");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Imágenes (*.png, *.jpg, *.bmp)", "*.png", "*.jpg", "*.jpeg", "*.bmp"));
            File file = fc.showOpenDialog(scanDlg.getDialogPane().getScene().getWindow());
            if (file == null) return;
            // Decoding a phone-camera-sized photo (ImageIO.read + pixel-by-pixel luminance
            // conversion + ZXing) is slow enough to freeze the dialog if run on the FX thread.
            DialogUtil.runAsync(
                () -> {
                    BufferedImage img = ImageIO.read(file);
                    LuminanceSource source = bufferedImageToLuminance(img);
                    BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
                    return new MultiFormatReader().decode(bitmap).getText();
                },
                texto -> codeField.setText(com.sibim.util.QrUtils.codigoLeido(texto)),
                ex -> NotificacionUtil.advertencia(
                    scanDlg.getDialogPane().getScene(), "No se pudo decodificar la imagen")
            );
        });

        Button btnBuscar = new Button("Marcar como visto");
        btnBuscar.getStyleClass().add("btn-primary");
        btnBuscar.setDefaultButton(true);
        // Stays open and marks each bien as seen: counting is scanning one label
        // after another. It used to close after every code and only scroll to
        // the row, so each bien took reopening this window and a click besides.
        Label resultado = new Label("Escanea la primera etiqueta.");
        resultado.getStyleClass().add("muted-sm");
        resultado.setWrapText(true);
        btnBuscar.setOnAction(ev -> {
            String code = com.sibim.util.QrUtils.codigoLeido(codeField.getText());
            if (code.isBlank()) return;
            resultado.getStyleClass().removeAll("text-ok", "text-warn");
            String aviso = marcarVisto(code);
            if (aviso == null) {
                Fila f = ultimaMarcada;
                long vistos = alcance.stream().filter(Fila::vista).count();
                resultado.setText("✓ " + f.producto.getCodigo()
                    + (code.equalsIgnoreCase(f.producto.getCodigo()) ? "" : " (antes " + code + ")")
                    + " — " + f.producto.getNombre()
                    + "  (" + vistos + " de " + alcance.size() + ")");
                resultado.getStyleClass().add("text-ok");
            } else {
                resultado.setText(aviso);
                resultado.getStyleClass().add("text-warn");
            }
            codeField.clear();
            codeField.requestFocus();
        });

        VBox content = new VBox(10, codeField, new HBox(8, btnCargar, btnBuscar), resultado);
        content.setPadding(new Insets(14));
        scanDlg.getDialogPane().setContent(new VBox(0, header, content));
        // A handheld scanner types a SIBIM QR's every line: take the código
        // (first line) and swallow the rest instead of searching each line.
        scanDlg.setOnShown(ev -> com.sibim.util.BarcodeScanner.attach(scanDlg.getDialogPane().getScene(), leido -> {
            codeField.setText(com.sibim.util.QrUtils.codigoLeido(leido));
            btnBuscar.fire();
        }));
        scanDlg.showAndWait();
    }
}
