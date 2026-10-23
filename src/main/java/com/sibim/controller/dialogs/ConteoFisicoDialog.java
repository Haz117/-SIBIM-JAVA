package com.sibim.controller.dialogs;

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
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppColors;
import com.sibim.util.AppExecutor;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

/** A physical inventory count (toma de inventario): walks the user through
 *  every bien in scope, lets them enter what they actually counted, assign a
 *  condition state, and add per-item notes. On confirmation it persists the
 *  session and reconciles stock discrepancies via audited AJUSTE movements. */
public final class ConteoFisicoDialog {

    private static final Logger log = LoggerFactory.getLogger(ConteoFisicoDialog.class);

    private ConteoFisicoDialog() {}

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

    private static boolean vista(Row r) {
        return vista(r.touched().get(), r.contado().getValue(), r.producto().getStockActual(), r.estado().getValue());
    }

    @FunctionalInterface
    private interface Exportacion { File generar() throws Exception; }

    private static void exportar(Dialog<?> dialog, String formato, Exportacion exportacion) {
        javafx.scene.Scene scene = dialog.getDialogPane().getScene();
        AppExecutor.submit(() -> {
            try {
                File f = exportacion.generar();
                javafx.application.Platform.runLater(() -> {
                    if (scene != null) DialogUtil.showExportResultDialog(scene, f);
                });
            } catch (Exception ex) {
                log.error("Error al exportar el conteo físico a {}", formato, ex);
                javafx.application.Platform.runLater(() -> {
                    if (scene != null) NotificacionUtil.error(scene, "No se pudo generar el " + formato);
                });
            }
        });
    }

    // Width of each column, shared by the header and by every row so they line up.
    private static final double ANCHO_VISTO = 58, ANCHO_SISTEMA = 78, ANCHO_CONTADO = 100,
                                ANCHO_DIF = 52, ANCHO_ESTADO = 150, ANCHO_NOTA = 44;

    /** The estado of a row nobody has marked yet reads "Pendiente", not
     *  "Encontrado": every bien said "Encontrado" before anyone looked. */
    private static final class CeldaEstado extends ListCell<EstadoConteo> {
        private final BooleanProperty visto;
        CeldaEstado(BooleanProperty visto) { this.visto = visto; }
        @Override protected void updateItem(EstadoConteo v, boolean empty) {
            super.updateItem(v, empty);
            getStyleClass().remove("conteo-pendiente");
            if (empty || v == null) { setText(null); return; }
            boolean pendiente = !visto.get() && v == EstadoConteo.ENCONTRADO;
            setText(pendiente ? "Pendiente" : v.toString());
            if (pendiente) getStyleClass().add("conteo-pendiente");
        }
        void refrescar() { updateItem(getItem(), getItem() == null); }
    }

    /** True when {@code target} is {@code control} or inside it. */
    private static boolean dentroDe(Object target, Node... controles) {
        for (Node n = target instanceof Node t ? t : null; n != null; n = n.getParent())
            for (Node c : controles) if (n == c) return true;
        return false;
    }

    private static Label encabezado(String texto, double ancho) {
        Label l = new Label(texto);
        l.getStyleClass().add("nav-section-label");
        l.setMinWidth(ancho); l.setPrefWidth(ancho); l.setMaxWidth(ancho);
        return l;
    }

    /** What a row is saved as when nobody looked at it: the record of a
     *  conteo must not say "encontrado" about a bien that was never checked. */
    static final String SIN_REVISAR = "SIN_REVISAR";

    record DraftValues(int contado, EstadoConteo estado, String nota, boolean verificado) {}
    private record Row(Producto producto, Spinner<Integer> contado, ComboBox<EstadoConteo> estado,
                       TextField notaField, BooleanProperty touched) {}
    private record Captured(Producto producto, int contado, String estadoConteo, String nota) {}

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

    /** The draft keeps what was captured for each bien, whatever conteo it
     *  came from: saving the conteo of one área leaves the rows of another
     *  área's draft where they were. (It used to be one conteo per account —
     *  opening a second one deleted or overwrote the first.) */
    private static void saveDraft(List<Row> rows) throws java.io.IOException {
        String[] previo = leerBorrador();
        Map<String, String> filas = filasCrudas(previo != null ? previo[1] : "");
        for (Row r : rows) {
            String id = r.producto().getId();
            filas.put(id, serializar(id, r.contado().getValue(),
                r.estado().getValue() != null ? r.estado().getValue().name() : "ENCONTRADO",
                r.notaField().getText(), vista(r)));
        }
        escribirBorrador(LocalDate.now().toString(), filas);
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

    // ── Feature 1: Scope selector ─────────────────────────────────────────────

    private static Optional<List<Producto>> seleccionarAlcance(List<Producto> todos) {
        List<String> areas = todos.stream()
            .map(p -> p.getArea() != null && !p.getArea().isBlank() ? p.getArea() : "Sin área")
            .distinct().sorted().toList();
        List<String> categorias = todos.stream()
            .map(p -> p.getCategoriaNombre() != null && !p.getCategoriaNombre().isBlank()
                ? p.getCategoriaNombre() : "Sin categoría")
            .distinct().sorted().toList();

        ButtonType btnContinuar = new ButtonType("Continuar", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancelar  = new ButtonType("Cancelar",  ButtonBar.ButtonData.CANCEL_CLOSE);

        Dialog<ButtonType> dlg = new Dialog<>();
        DialogUtil.applyOwner(dlg);
        dlg.getDialogPane().getButtonTypes().addAll(btnContinuar, btnCancelar);
        dlg.getDialogPane().setPrefWidth(420);
        DialogUtil.applyStylesheet(dlg.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2m-magnify-scan", "Alcance del conteo",
            "Selecciona qué bienes incluir en este levantamiento", AppColors.CYAN, AppColors.CYAN_D);

        RadioButton rbTodo = new RadioButton("Todo el inventario (" + todos.size() + " bienes)");
        RadioButton rbArea = new RadioButton("Por área");
        RadioButton rbCat  = new RadioButton("Por categoría");
        ToggleGroup tg = new ToggleGroup();
        rbTodo.setToggleGroup(tg); rbArea.setToggleGroup(tg); rbCat.setToggleGroup(tg);
        rbTodo.setSelected(true);

        ComboBox<String> areaCombo = new ComboBox<>();
        areaCombo.getItems().addAll(areas);
        if (!areas.isEmpty()) areaCombo.setValue(areas.get(0));
        areaCombo.setMaxWidth(Double.MAX_VALUE);
        areaCombo.setDisable(true);

        ComboBox<String> catCombo = new ComboBox<>();
        catCombo.getItems().addAll(categorias);
        if (!categorias.isEmpty()) catCombo.setValue(categorias.get(0));
        catCombo.setMaxWidth(Double.MAX_VALUE);
        catCombo.setDisable(true);

        rbArea.selectedProperty().addListener((o, a, b) -> areaCombo.setDisable(!b));
        rbCat.selectedProperty().addListener((o, a, b)  -> catCombo.setDisable(!b));

        VBox content = new VBox(10, rbTodo, rbArea, areaCombo, rbCat, catCombo);
        content.setPadding(new Insets(16, 22, 20, 22));
        dlg.getDialogPane().setContent(new VBox(header, content));

        Node okBtn = dlg.getDialogPane().lookupButton(btnContinuar);
        okBtn.getStyleClass().add("dialog-ok-btn");

        Optional<ButtonType> result = dlg.showAndWait();
        if (result.isEmpty() || result.get() != btnContinuar) return Optional.empty();

        if (rbArea.isSelected() && areaCombo.getValue() != null) {
            String sel = areaCombo.getValue();
            return Optional.of(todos.stream()
                .filter(p -> sel.equals(p.getArea() != null && !p.getArea().isBlank() ? p.getArea() : "Sin área"))
                .toList());
        }
        if (rbCat.isSelected() && catCombo.getValue() != null) {
            String sel = catCombo.getValue();
            return Optional.of(todos.stream()
                .filter(p -> sel.equals(p.getCategoriaNombre() != null && !p.getCategoriaNombre().isBlank()
                    ? p.getCategoriaNombre() : "Sin categoría"))
                .toList());
        }
        return Optional.of(todos);
    }

    // ── Feature 5: Resumen modal before finalizing ────────────────────────────

    private static boolean showResumen(List<Row> rows) {
        long concordantes = rows.stream()
            .filter(r -> r.touched().get() && r.contado().getValue() == r.producto().getStockActual()
                      && r.estado().getValue() == EstadoConteo.ENCONTRADO).count();
        long conDiff    = rows.stream()
            .filter(r -> r.contado().getValue() != r.producto().getStockActual()).count();
        long malEstado  = rows.stream()
            .filter(r -> r.estado().getValue() == EstadoConteo.MAL_ESTADO).count();
        long otraArea   = rows.stream()
            .filter(r -> r.estado().getValue() == EstadoConteo.EN_OTRA_AREA).count();
        long faltante   = rows.stream()
            .filter(r -> r.estado().getValue() == EstadoConteo.FALTANTE).count();
        long sinRevisar = rows.stream().filter(r -> !vista(r)).count();

        ButtonType btnConfirmar = new ButtonType("Confirmar y finalizar", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnVolver    = new ButtonType("Volver",                ButtonBar.ButtonData.CANCEL_CLOSE);

        Dialog<ButtonType> dlg = new Dialog<>();
        DialogUtil.applyOwner(dlg);
        dlg.getDialogPane().getButtonTypes().addAll(btnConfirmar, btnVolver);
        dlg.getDialogPane().setPrefWidth(440);
        DialogUtil.applyStylesheet(dlg.getDialogPane());

        HBox hdr = DialogUtil.gradientHeader("mdi2c-clipboard-check-outline", "Resumen del conteo",
            "Revisa los resultados antes de finalizar", AppColors.CYAN, AppColors.CYAN_D);

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
            Label aviso = new Label("Los " + sinRevisar + " bien(es) que no marcaste como vistos quedarán en el "
                + "registro como \"sin revisar\", no como encontrados. Con \"Guardar borrador\" puedes seguir después.");
            aviso.getStyleClass().add("muted-sm");
            aviso.setWrapText(true);
            stats.getChildren().add(aviso);
        }
        if (conDiff > 0) {
            Label note = new Label("Se registrarán " + conDiff + " ajuste(s) de cantidad al confirmar.");
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
        Label val = new Label(String.valueOf(count));
        val.getStyleClass().addAll("dlg-detail-value", countStyle);
        row.getChildren().addAll(lbl, val);
        return row;
    }

    // ── Main dialog ───────────────────────────────────────────────────────────

    public static void show(List<Producto> productos, MovimientoService movimientoService, Runnable onReconciled) {

        // Feature 1: scope selector (skip if empty)
        List<Producto> scope;
        if (productos.isEmpty()) {
            scope = productos;
        } else {
            Optional<List<Producto>> scopeOpt = seleccionarAlcance(productos);
            if (scopeOpt.isEmpty()) return; // user cancelled
            scope = scopeOpt.get();
        }

        // Draft check — only what the draft has for the bienes of this conteo:
        // the rows of another conteo are neither offered nor thrown away.
        Map<String, DraftValues> draftMap;
        String[] borrador = leerBorrador();
        String draftFecha = borrador != null ? borrador[0] : "";
        Set<String> enAlcance = scope.stream().map(Producto::getId).collect(Collectors.toSet());
        Map<String, DraftValues> rawDraft = new HashMap<>(parsearBorrador(borrador != null ? borrador[1] : ""));
        rawDraft.keySet().retainAll(enAlcance);
        boolean hasDraft = !rawDraft.isEmpty();
        if (hasDraft) {
            boolean resume = ConfirmacionUtil.confirmar("Reanudar borrador",
                "Hay un borrador guardado el " + draftFecha + " con " + rawDraft.size() + " bien(es) de este conteo.\n"
                + "¿Deseas retomarlo? (Cancelar = empezar desde cero)");
            draftMap = resume ? rawDraft : Collections.emptyMap();
            if (!resume) clearDraft(enAlcance);
        } else {
            draftMap = Collections.emptyMap();
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        // As wide as the screen allows (730 px cut the toolbar, the column
        // headers and the footer buttons down to "…"), and resizable.
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefWidth(Math.max(760, Math.min(1120, pantalla.getWidth() - 80)));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2c-clipboard-list-outline", "Conteo Físico de Inventario",
            "Marca cada bien que tengas a la vista (o escanea su etiqueta) y anota lo que no coincida — "
                + FormatUtils.formatDate(LocalDate.now()),
            AppColors.CYAN, AppColors.CYAN_D);

        // Toolbar
        TextField searchField = new TextField();
        searchField.setPromptText("Buscar por nombre, código o serie…");
        searchField.getStyleClass().add("search-field");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        // Which rows to show, as tabs with their count (two loose check boxes
        // could both be ticked and said nothing about how many there were).
        ToggleGroup vista = new ToggleGroup();
        ToggleButton fTodos = new ToggleButton(), fPendientes = new ToggleButton(),
                     fVistos = new ToggleButton(), fIncidencias = new ToggleButton();
        for (ToggleButton t : List.of(fTodos, fPendientes, fVistos, fIncidencias)) {
            t.setToggleGroup(vista);
            t.getStyleClass().add("filter-chip");
            t.setMinWidth(Region.USE_PREF_SIZE);
        }
        fTodos.setSelected(true);
        // A tab can't be "unselected" into showing nothing.
        vista.selectedToggleProperty().addListener((o, a, b) -> { if (b == null) a.setSelected(true); });
        fPendientes.setTooltip(new Tooltip("Los bienes que todavía nadie marcó como vistos"));
        fIncidencias.setTooltip(new Tooltip("Cantidad distinta, mal estado, en otra área o faltante"));
        Button btnHistorial = new Button("Historial");
        btnHistorial.setGraphic(new FontIcon("mdi2h-history"));
        btnHistorial.setContentDisplay(ContentDisplay.LEFT);
        btnHistorial.getStyleClass().add("btn-ghost");
        btnHistorial.setMinWidth(Region.USE_PREF_SIZE);
        btnHistorial.setOnAction(e -> {
            if (SessionManager.isAdmin()) {
                HistorialConteosDialog.show(dialog.getDialogPane().getScene());
            } else {
                NotificacionUtil.info(dialog.getDialogPane().getScene(),
                    "Solo el administrador puede ver el historial de conteos");
            }
        });
        Button btnScan = new Button("Escanear etiqueta");
        btnScan.setGraphic(new FontIcon("mdi2q-qrcode-scan"));
        btnScan.getStyleClass().add("btn-secondary");
        btnScan.setContentDisplay(ContentDisplay.LEFT);
        btnScan.setMinWidth(Region.USE_PREF_SIZE);
        btnScan.setDisable(scope.isEmpty());

        HBox toolbar = new HBox(10, searchField, btnScan, btnHistorial);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(14, 14, 8, 14));
        HBox filtros = new HBox(6, fTodos, fPendientes, fVistos, fIncidencias);
        filtros.setAlignment(Pos.CENTER_LEFT);

        // Feature 4: progress bar
        int totalItems = scope.size();
        SimpleIntegerProperty touchedCount = new SimpleIntegerProperty(0);
        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(220);
        Label progressLabel = new Label(totalItems == 0 ? "" : "0 de " + totalItems + " vistos");
        progressLabel.getStyleClass().add("muted-sm");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox progressBox = new HBox(10, filtros, espacio, progressBar, progressLabel);
        progressBox.setAlignment(Pos.CENTER_LEFT);
        progressBox.setPadding(new Insets(0, 14, 10, 14));
        progressBox.setVisible(!scope.isEmpty());
        progressBox.setManaged(!scope.isEmpty());

        // Column headers: one label per column with the width its column has in
        // every row. (It used to be a single line of text padded with spaces,
        // which never lined up with what was below it.)
        Label hVisto = encabezado("VISTO", ANCHO_VISTO), hSistema = encabezado("SISTEMA", ANCHO_SISTEMA),
              hContado = encabezado("CONTADO", ANCHO_CONTADO), hDif = encabezado("DIF.", ANCHO_DIF),
              hEstado = encabezado("ESTADO", ANCHO_ESTADO), hNota = encabezado("NOTA", ANCHO_NOTA);
        hSistema.setTooltip(new Tooltip("La cantidad que tiene registrada SIBIM"));
        hContado.setTooltip(new Tooltip("La cantidad que encontraste"));
        Label hBien = new Label("BIEN");
        hBien.getStyleClass().add("nav-section-label");
        HBox.setHgrow(hBien, Priority.ALWAYS);
        hBien.setMaxWidth(Double.MAX_VALUE);
        HBox colHeaders = new HBox(10, hVisto, hBien, hSistema, hContado, hDif, hEstado, hNota);
        colHeaders.setAlignment(Pos.CENTER_LEFT);
        colHeaders.setPadding(new Insets(0, 30, 0, 16));   // rows: 4 (list) + 12 (row); right adds the scrollbar

        // Row list
        VBox list = new VBox(5);
        list.setPadding(new Insets(4));
        List<Row> rows = new ArrayList<>();

        for (Producto p : scope) {
            DraftValues dv = draftMap.get(p.getId());
            int initialCount    = dv != null ? dv.contado() : p.getStockActual();
            EstadoConteo initEst = dv != null ? dv.estado()  : EstadoConteo.ENCONTRADO;
            String initialNota  = dv != null && dv.nota() != null ? dv.nota() : "";

            // Spinner
            Spinner<Integer> contado = new Spinner<>(0, Integer.MAX_VALUE, initialCount);
            contado.setEditable(true);
            contado.setPrefWidth(ANCHO_CONTADO); contado.setMinWidth(ANCHO_CONTADO);
            DialogUtil.commitOnFocusLoss(contado);

            // Feature 2: estado ComboBox
            ComboBox<EstadoConteo> estadoCb = new ComboBox<>();
            estadoCb.getItems().addAll(EstadoConteo.values());
            estadoCb.setValue(initEst);
            estadoCb.setPrefWidth(ANCHO_ESTADO); estadoCb.setMinWidth(ANCHO_ESTADO);
            estadoCb.setTooltip(new Tooltip("Cómo encontraste el bien"));

            // Feature 3: note field (collapsible)
            TextField notaField = new TextField();
            notaField.setPromptText("Observación...");
            notaField.getStyleClass().add("form-input");
            VBox.setMargin(notaField, new Insets(0, 12, 4, 60));
            if (!initialNota.isBlank()) notaField.setText(initialNota);
            notaField.setVisible(!initialNota.isBlank());
            notaField.setManaged(!initialNota.isBlank());

            Button btnNota = new Button();
            btnNota.setGraphic(new FontIcon("mdi2n-note-text-outline"));
            btnNota.getStyleClass().add("btn-ghost");
            btnNota.setTooltip(new Tooltip("Agregar observación"));
            btnNota.setAccessibleText("Agregar observación");
            btnNota.setOnAction(e -> {
                boolean show = !notaField.isVisible();
                notaField.setVisible(show);
                notaField.setManaged(show);
                if (show) notaField.requestFocus();
            });

            btnNota.setMinWidth(ANCHO_NOTA);

            // "Visto": the bien is in front of whoever counts. Almost every bien
            // is one piece that is simply there, and before this there was no way
            // to say so — only changing the count or the estado counted as
            // reviewed, so a conteo with everything in order ended "0 revisados".
            BooleanProperty touched = new SimpleBooleanProperty(
                vista(dv != null && dv.verificado(), initialCount, p.getStockActual(), initEst));
            CheckBox visto = new CheckBox();
            visto.setMinWidth(ANCHO_VISTO);
            visto.setTooltip(new Tooltip("Márcalo cuando tengas el bien a la vista (o da clic en la fila)"));
            visto.setAccessibleText("Visto: " + p.getNombre());
            visto.selectedProperty().bindBidirectional(touched);
            CeldaEstado celdaEstado = new CeldaEstado(touched);
            estadoCb.setButtonCell(celdaEstado);
            // A row with a difference or an estado stays marked: its ajuste is
            // applied on finalizing, so it can't be unticked into "sin revisar".
            // Put the count and the estado back first to unmark it.
            visto.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> vista(false, contado.getValue(), p.getStockActual(), estadoCb.getValue()),
                contado.valueProperty(), estadoCb.valueProperty()));

            // Delta label
            Label delta = new Label();
            delta.setMinWidth(ANCHO_DIF);

            // Row container — defined first so updateDelta can reference it via getUserData
            HBox rowHBox = new HBox(10);

            Runnable updateDelta = () -> {
                int diff = contado.getValue() - p.getStockActual();
                delta.setText(diff == 0 ? "✓" : (diff > 0 ? "+" + diff : String.valueOf(diff)));
                delta.getStyleClass().removeAll("dlg-stock-new-ok", "dlg-stock-new-warn");
                delta.getStyleClass().add(diff == 0 ? "dlg-stock-new-ok" : "dlg-stock-new-warn");
                // The row only takes color when it says something: seen, or
                // with an incidencia. (Every row had the same guinda edge.)
                boolean incidencia = diff != 0 || estadoCb.getValue() != EstadoConteo.ENCONTRADO;
                rowHBox.getStyleClass().removeAll("conteo-row-visto", "conteo-row-incidencia");
                if (incidencia) rowHBox.getStyleClass().add("conteo-row-incidencia");
                else if (touched.get()) rowHBox.getStyleClass().add("conteo-row-visto");
                celdaEstado.refrescar();
                if (rowHBox.getUserData() instanceof Runnable r) r.run();
            };
            updateDelta.run();

            contado.valueProperty().addListener((o, a, b) -> { touched.set(true); updateDelta.run(); });
            estadoCb.valueProperty().addListener((o, a, b) -> {
                if (b != EstadoConteo.ENCONTRADO) touched.set(true);
                // A bien that is missing is not there: its count follows, and comes
                // back if "Faltante" was a slip. (It used to stay at the system's
                // quantity, so a missing bien produced no adjustment at all.)
                if (b == EstadoConteo.FALTANTE) contado.getValueFactory().setValue(0);
                else if (a == EstadoConteo.FALTANTE && contado.getValue() == 0)
                    contado.getValueFactory().setValue(p.getStockActual());
                updateDelta.run();
            });
            touched.addListener((o, a, b) -> updateDelta.run());

            // Name on one line (it wrapped into four), the código as a tag, the área below.
            VBox info = new VBox(2);
            Label nombre = new Label(p.getNombre());
            nombre.getStyleClass().add("conteo-nombre");
            nombre.setTooltip(new Tooltip(p.getNombre()));
            nombre.setMinWidth(0);
            Label codigo = new Label(p.getCodigo());
            codigo.getStyleClass().add("codigo-cell");
            codigo.setMinWidth(Region.USE_PREF_SIZE);
            HBox lineaNombre = new HBox(8, codigo, nombre);
            lineaNombre.setAlignment(Pos.CENTER_LEFT);
            Label area = new Label(p.getArea() != null ? p.getArea() : "Sin área");
            area.getStyleClass().add("muted-sm");
            info.getChildren().addAll(lineaNombre, area);
            info.setMinWidth(0);
            HBox.setHgrow(info, Priority.ALWAYS);

            Label sistema = new Label(String.valueOf(p.getStockActual()));
            sistema.getStyleClass().add("dlg-stock-val");
            sistema.setMinWidth(ANCHO_SISTEMA);

            rowHBox.getStyleClass().add("conteo-row");
            rowHBox.setPadding(new Insets(8, 12, 8, 12));
            rowHBox.setAlignment(Pos.CENTER_LEFT);
            rowHBox.getChildren().addAll(visto, info, sistema, contado, delta, estadoCb, btnNota);
            updateDelta.run();
            // A click anywhere on the row (not on its fields) marks it as seen,
            // or unmarks it — the check box alone was a 16-pixel target.
            rowHBox.setOnMouseClicked(e -> {
                if (dentroDe(e.getTarget(), visto, contado, estadoCb, btnNota) || visto.isDisabled()) return;
                touched.set(!touched.get());
            });

            VBox rowWrapper = new VBox(0, rowHBox, notaField);

            // Visibility filter (captures rowWrapper, contado, estadoCb — defined after rowWrapper)
            Runnable applyVisibility = () -> {
                String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
                boolean matchesSearch = q.isBlank()
                    || p.getNombre().toLowerCase().contains(q)
                    || p.getCodigo().toLowerCase().contains(q)
                    || (p.getNumeroSerie() != null && p.getNumeroSerie().toLowerCase().contains(q));
                boolean hasIncidencia = contado.getValue() != p.getStockActual()
                    || estadoCb.getValue() != EstadoConteo.ENCONTRADO;
                boolean visible = matchesSearch
                    && (!fIncidencias.isSelected() || hasIncidencia)
                    && (!fPendientes.isSelected() || !touched.get())
                    && (!fVistos.isSelected() || touched.get());
                rowWrapper.setVisible(visible);
                rowWrapper.setManaged(visible);
            };
            rowHBox.setUserData(applyVisibility);   // used by updateDelta
            rowWrapper.setUserData(applyVisibility); // used by applyAllVisibility

            list.getChildren().add(rowWrapper);
            rows.add(new Row(p, contado, estadoCb, notaField, touched));
        }

        // Wire progress bar — a bien can also be unmarked, so count instead of adding one.
        Runnable actualizarAvance = () -> {
            int n = (int) rows.stream().filter(r -> r.touched().get()).count();
            int inc = (int) rows.stream().filter(r -> r.contado().getValue() != r.producto().getStockActual()
                || r.estado().getValue() != EstadoConteo.ENCONTRADO).count();
            touchedCount.set(n);
            progressLabel.setText(n + " de " + totalItems + " vistos"
                + (n < totalItems ? " · faltan " + (totalItems - n) : " · completo"));
            progressBar.setProgress(totalItems > 0 ? (double) n / totalItems : 0);
            fTodos.setText("Todos (" + totalItems + ")");
            fPendientes.setText("Pendientes (" + (totalItems - n) + ")");
            fVistos.setText("Vistos (" + n + ")");
            fIncidencias.setText("Con incidencia (" + inc + ")");
        };
        for (Row r : rows) {
            r.touched().addListener((o, a, b) -> actualizarAvance.run());
            r.contado().valueProperty().addListener((o, a, b) -> actualizarAvance.run());
            r.estado().valueProperty().addListener((o, a, b) -> actualizarAvance.run());
        }
        actualizarAvance.run();   // a resumed draft already has bienes verified

        // The entrance animation is per row: with a whole inventory (thousands of
        // rows) it only made the dialog take longer to become usable.
        if (!list.getChildren().isEmpty() && list.getChildren().size() <= 60)
            AnimationUtils.staggeredFadeInUp(list.getChildren(), 180, 38);

        Runnable applyAllVisibility = () -> list.getChildren().forEach(n -> {
            if (n.getUserData() instanceof Runnable r) r.run();
        });
        searchField.textProperty().addListener((o, a, b) -> applyAllVisibility.run());
        vista.selectedToggleProperty().addListener((o, a, b) -> applyAllVisibility.run());

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(Math.max(320, pantalla.getHeight() - 400));
        VBox.setVgrow(scroll, Priority.ALWAYS);
        scroll.getStyleClass().add("dlg-tabs-scroll");

        // Footer
        Label summary = new Label(scope.isEmpty()
            ? "No hay bienes en el alcance seleccionado."
            : "Con una pistola lectora basta escanear cada etiqueta: el bien queda marcado como visto.");
        summary.getStyleClass().add("muted-sm");
        summary.setWrapText(true);

        Button btnBorrador = new Button("Guardar borrador");
        btnBorrador.setGraphic(new FontIcon("mdi2c-content-save-outline"));
        btnBorrador.getStyleClass().add("btn-secondary");
        btnBorrador.setDisable(scope.isEmpty());
        btnBorrador.setTooltip(new Tooltip("Guarda lo capturado en esta computadora para seguir después"));
        btnBorrador.setOnAction(e -> {
            try {
                saveDraft(rows);
            } catch (java.io.IOException ex) {
                log.error("No se pudo guardar el borrador del conteo", ex);
                NotificacionUtil.error(dialog.getDialogPane().getScene(),
                    "No se pudo guardar el borrador: " + ex.getMessage());
                return;   // stay open: closing now would lose the capture
            }
            javafx.scene.Scene principal = com.sibim.MainApp.getPrimaryStage() != null
                ? com.sibim.MainApp.getPrimaryStage().getScene() : null;
            dialog.close();
            if (principal != null) NotificacionUtil.exito(principal,
                "Borrador guardado — al abrir de nuevo el conteo físico podrás retomarlo");
        });

        ReporteConteoService reporteService = new ReporteConteoService();
        String tituloConteo = "Conteo del " + FormatUtils.formatDate(LocalDate.now());
        Usuario userForPdf = SessionManager.getCurrentUser();
        String userNameForPdf = userForPdf != null ? userForPdf.getNombre() : "Sistema";

        // One "Exportar" menu, like every other screen (it used to be two buttons
        // that squeezed the footer). What it exports is the capture as it is now.
        java.util.function.Supplier<List<ConteoItem>> capturaActual = () -> rows.stream().map(r -> {
            ConteoItem ci = new ConteoItem();
            ci.setProductoId(r.producto().getId());
            ci.setProductoNombre(r.producto().getNombre());
            ci.setProductoCodigo(r.producto().getCodigo());
            ci.setArea(r.producto().getArea());
            ci.setStockSistema(r.producto().getStockActual());
            ci.setStockContado(r.contado().getValue());
            ci.setEstadoConteo(estadoGuardado(vista(r),
                r.estado().getValue() != null ? r.estado().getValue().name() : "ENCONTRADO"));
            ci.setNota(r.notaField().getText() != null ? r.notaField().getText() : "");
            return ci;
        }).toList();

        MenuItem miPdf = new MenuItem("PDF");
        miPdf.setGraphic(new FontIcon("mdi2f-file-pdf-box"));
        MenuItem miExcel = new MenuItem("Excel");
        miExcel.setGraphic(new FontIcon("mdi2f-file-excel"));
        MenuButton btnExportar = new MenuButton("Exportar", new FontIcon("mdi2d-download-outline"), miPdf, miExcel);
        btnExportar.getStyleClass().add("btn-secondary");
        btnExportar.setDisable(scope.isEmpty());
        btnExportar.setTooltip(new Tooltip("Exporta la captura como está ahora, sin finalizar el conteo"));
        miPdf.setOnAction(ev -> exportar(dialog, "PDF",
            () -> reporteService.exportConteoPdf(tituloConteo, userNameForPdf, capturaActual.get())));
        miExcel.setOnAction(ev -> exportar(dialog, "Excel",
            () -> reporteService.exportConteoExcel(tituloConteo, userNameForPdf, capturaActual.get())));

        // Wire scan button after scroll/list/rows are ready
        btnScan.setOnAction(e -> showScanDialog(dialog, scroll, list, rows));

        Button btnFinalizar = new Button("Finalizar conteo");
        btnFinalizar.setGraphic(new FontIcon("mdi2c-check-circle-outline"));
        btnFinalizar.getStyleClass().add("btn-primary");
        btnFinalizar.setDisable(scope.isEmpty());

        for (Control b : List.of(btnBorrador, btnExportar, btnFinalizar)) b.setMinWidth(Region.USE_PREF_SIZE);
        HBox actions = new HBox(10, summary, btnBorrador, btnExportar, btnFinalizar);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setPadding(new Insets(10, 4, 4, 4));
        HBox.setHgrow(summary, Priority.ALWAYS);

        btnFinalizar.setOnAction(e -> {
            btnFinalizar.setDisable(true);

            // Feature 5: resumen modal (replaces plain ConfirmacionUtil.confirmar)
            if (!showResumen(rows)) {
                btnFinalizar.setDisable(false);
                return;
            }

            btnFinalizar.setText("Guardando...");
            btnFinalizar.setGraphic(new FontIcon("mdi2l-loading"));

            // Snapshot on FX thread — background thread must not touch live nodes
            List<Captured> snapshot = new ArrayList<>();
            for (Row r : rows) {
                r.contado().setDisable(true);
                r.estado().setDisable(true);
                r.notaField().setDisable(true);
                snapshot.add(new Captured(
                    r.producto(),
                    r.contado().getValue(),
                    estadoGuardado(vista(r),
                        r.estado().getValue() != null ? r.estado().getValue().name() : "ENCONTRADO"),
                    r.notaField().getText()
                ));
            }

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
                javafx.application.Platform.runLater(() -> {
                    String base;
                    if (totalDiscrepancias == 0) {
                        base = "Conteo guardado — sin diferencias en " + (items.size() - sinRevisar)
                            + " bien(es) revisado(s)";
                    } else if (fallidosFinal.isEmpty()) {
                        base = okFinal + " ajuste(s) aplicado(s) correctamente";
                    } else {
                        String nombres = fallidosFinal.stream().limit(3)
                            .collect(Collectors.joining(", "))
                            + (fallidosFinal.size() > 3 ? "…" : "");
                        base = okFinal + " ajuste(s) aplicado(s), " + fallidosFinal.size()
                            + " fallaron: " + nombres;
                    }
                    // A conteo closed halfway is not a clean inventory: say how many are left.
                    if (sinRevisar > 0) base += " — " + sinRevisar + " quedaron sin revisar";
                    if (!savedFinal) base += " (no se pudo guardar el registro del conteo)";
                    javafx.scene.Scene scene = dialog.getDialogPane().getScene();
                    if (scene != null) {
                        if (!fallidosFinal.isEmpty() || !savedFinal || sinRevisar > 0) NotificacionUtil.advertencia(scene, base);
                        else if (totalDiscrepancias == 0) NotificacionUtil.exitoConteo(scene, items.size());
                        else NotificacionUtil.exito(scene, base);
                    }
                    clearDraft(items.stream().map(ConteoItem::getProductoId).toList());
                    if (onReconciled != null) onReconciled.run();
                    dialog.close();
                });
            });
        });

        AnimationUtils.staggeredFadeInUp(
            java.util.List.of(header, toolbar, progressBox, colHeaders, scroll, actions), 260, 60);
        dialog.getDialogPane().setContent(
            new VBox(0, header, toolbar, progressBox, colHeaders, scroll, actions));

        dialog.setOnShowing(ev -> dialog.getDialogPane().getScene().getWindow().addEventFilter(
            javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST, we -> {
                // Bienes marked as seen are work too, not only differences and notes.
                long changed = rows.stream()
                    .filter(r -> r.touched().get()
                              || r.contado().getValue() != r.producto().getStockActual()
                              || r.estado().getValue() != EstadoConteo.ENCONTRADO
                              || !r.notaField().getText().isBlank()).count();
                if (changed > 0 && !ConfirmacionUtil.confirmar("Salir sin guardar",
                        "Lo capturado en " + changed + " bien(es) se perderá.\n"
                        + "Para conservarlo cancela y usa \"Guardar borrador\".\n¿Salir de todos modos?"))
                    we.consume();
            }));
        // A handheld scanner works right here, without opening "Escanear": each
        // label read marks its bien as seen.
        dialog.setOnShown(ev -> com.sibim.util.BarcodeScanner.attach(dialog.getDialogPane().getScene(), leido -> {
            String code = com.sibim.util.QrUtils.codigoLeido(leido);
            if (!code.isBlank() && marcarVisto(code, scroll, list, rows) < 0)
                NotificacionUtil.advertencia(dialog.getDialogPane().getScene(),
                    "\"" + code + "\" no está en este conteo");
        }));

        dialog.showAndWait();
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

    /** Marks the bien with this código as seen and brings its row into view.
     *  @return its position, or -1 when it is not part of this conteo */
    private static int marcarVisto(String code, ScrollPane scroll, VBox list, List<Row> rows) {
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (!code.equalsIgnoreCase(r.producto().getCodigo())) continue;
            r.touched().set(true);
            Node fila = list.getChildren().get(i);
            javafx.application.Platform.runLater(() -> {
                double alto = list.getBoundsInLocal().getHeight();
                if (alto > 0 && fila.isManaged()) scroll.setVvalue(fila.getBoundsInParent().getMinY() / alto);
                AnimationUtils.flashClass(fila, "row-highlight-flash", 900);
            });
            return i;
        }
        return -1;
    }

    private static void showScanDialog(Dialog<?> parent, ScrollPane scroll, VBox list, List<Row> rows) {
        Dialog<ButtonType> scanDlg = new Dialog<>();
        DialogUtil.applyOwner(scanDlg);
        scanDlg.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);
        scanDlg.getDialogPane().setPrefWidth(420);
        DialogUtil.applyStylesheet(scanDlg.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2q-qrcode-scan", "Escanear código",
            "Escribe o pega el código, o carga una imagen con QR/código de barras",
            AppColors.CYAN, AppColors.CYAN_D);

        TextField codeField = new TextField();
        codeField.setPromptText("Código del bien (ej: BIEN-001)");
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

        Button btnBuscar = new Button("Buscar");
        btnBuscar.getStyleClass().add("btn-primary");
        btnBuscar.setDefaultButton(true);
        // Stays open and marks each bien as seen: counting is scanning one label
        // after another. It used to close after every code and only scroll to
        // the row, so each bien took reopening this window and a click besides.
        Label resultado = new Label("Escanea la primera etiqueta.");
        resultado.getStyleClass().add("muted-sm");
        resultado.setWrapText(true);
        btnBuscar.setText("Marcar como visto");
        btnBuscar.setOnAction(ev -> {
            String code = com.sibim.util.QrUtils.codigoLeido(codeField.getText());
            if (code.isBlank()) return;
            resultado.getStyleClass().removeAll("text-ok", "text-warn");
            int i = marcarVisto(code, scroll, list, rows);
            if (i >= 0) {
                long vistos = rows.stream().filter(r -> r.touched().get()).count();
                resultado.setText("✓ " + rows.get(i).producto().getCodigo() + " — " + rows.get(i).producto().getNombre()
                    + "  (" + vistos + " de " + rows.size() + ")");
                resultado.getStyleClass().add("text-ok");
            } else {
                resultado.setText("\"" + code + "\" no está en este conteo: es de otra área o categoría, "
                    + "o no está registrado. Anótalo aparte.");
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
